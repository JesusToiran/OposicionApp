#!/usr/bin/env python3
"""Valida producción con secretos sintéticos y recursos Docker propios de la prueba."""
import argparse
import base64
import hashlib
import html.parser
import http.client
import ipaddress
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import sys
import urllib.parse

ROOT = Path(__file__).resolve().parents[1]


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


class Inputs(html.parser.HTMLParser):
    def __init__(self):
        super().__init__()
        self.values = {}

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == 'input' and 'name' in attrs:
            self.values[attrs['name']] = attrs.get('value', '')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--image', default='oposicionapp:production-check')
    parser.add_argument('--configuration-only', action='store_true')
    parser.add_argument('--credentials-only', action='store_true')
    args = parser.parse_args()
    project = 'oposicionapp-prod-check-' + secrets.token_hex(5)
    evidence = ROOT / '.local' / 'production-prep' / project
    evidence.mkdir(parents=True)
    passwords = {name: secrets.token_urlsafe(32) for name in
                 ('postgres_password', 'app_db_password', 'flyway_db_password', 'account_password')}
    for name, value in passwords.items():
        path = evidence / name
        path.write_text(value, encoding='utf-8')
        path.chmod(0o444)
    settings = {
        'APP_IMAGE': args.image,
        'POSTGRES_IMAGE_DIGEST': 'sha256:4ef4dbc939d61acea57712655ddb4b4ab27419c913f94cca0cd57cb3ea3c2280',
        'POSTGRES_DB': 'production_check', 'POSTGRES_USER': 'postgres',
        'APP_DB_USER': 'oposicionapp_app', 'FLYWAY_DB_USER': 'oposicionapp_migrator',
        'JDBC_DATABASE_URL': 'jdbc:postgresql://postgres:5432/production_check',
        'APP_PUBLIC_ORIGIN': 'https://mercamento.com',
        'POSTGRES_PASSWORD_FILE': (evidence / 'postgres_password').as_posix(),
        'APP_DB_PASSWORD_FILE': (evidence / 'app_db_password').as_posix(),
        'FLYWAY_DB_PASSWORD_FILE': (evidence / 'flyway_db_password').as_posix(),
        'POSTGRES_PASSWORD': '', 'APP_DB_PASSWORD': '', 'FLYWAY_DB_PASSWORD': '',
        'PROXY_NETWORK_SUBNET': '172.30.241.0/28',
        'PROXY_NETWORK_GATEWAY': '172.30.241.1', 'TRUSTED_PROXY_CIDR': '172.30.241.1/32',
    }
    env = os.environ.copy()
    for name in settings:
        env.pop(name, None)
    for name in ('COMPOSE_FILE', 'COMPOSE_PROJECT_NAME', 'COMPOSE_PROFILES', 'SPRING_APPLICATION_JSON'):
        env.pop(name, None)
    env['COMPOSE_DISABLE_ENV_FILE'] = '1'
    env_file = evidence / 'test.env'
    env_file.write_text(''.join(f'{key}={value}\n' for key, value in settings.items()), encoding='utf-8')
    command = ['docker', 'compose', '--project-directory', str(ROOT), '--env-file', str(env_file),
               '-p', project, '-f', str(ROOT / 'compose.yaml'), '-f', str(ROOT / 'compose.prod.yaml')]
    sequence = 0

    def run(parts, *, data=None, success=True):
        nonlocal sequence
        sequence += 1
        result = subprocess.run(parts, input=data, capture_output=True, cwd=ROOT, env=env)
        safe_log = (result.stdout + result.stderr).decode('utf-8', errors='replace')
        for value in passwords.values():
            safe_log = safe_log.replace(value, '[CONTRASEÑA SINTÉTICA OMITIDA]')
        (evidence / f'{sequence:03d}.log').write_text(safe_log, encoding='utf-8')
        if success and result.returncode:
            if 'up' in parts and 'app' in parts:
                logs = subprocess.run(command + ['logs', '--no-color', '--tail', '160', 'app'], capture_output=True, cwd=ROOT, env=env)
                safe = (logs.stdout + logs.stderr).decode('utf-8', errors='replace')
                for value in passwords.values():
                    safe = safe.replace(value, '[CONTRASEÑA SINTÉTICA OMITIDA]')
                (evidence / 'application-failure.log').write_text(safe, encoding='utf-8')
            raise RuntimeError(f'Falló el paso {sequence}; ver evidencia local {evidence.name}/{sequence:03d}.log')
        return result

    def compose(*parts, **kwargs):
        return run(command + list(parts), **kwargs)

    def sql(statement, *, database='production_check', success=True):
        shell = ('export PGPASSWORD="$(cat /run/secrets/postgres_password)"; '
                 'exec psql -X --no-password -U "$POSTGRES_USER" -d "$1" -A -t -v ON_ERROR_STOP=1')
        return compose('exec', '-T', 'postgres', 'bash', '-ec', shell, 'psql', database,
                       data=statement.encode(), success=success)

    created = False
    try:
        # El modelo resuelto se inspecciona en memoria, nunca se imprime.
        model = json.loads(compose('config', '--format', 'json').stdout)
        app, postgres = (model['services'][name] for name in ('app', 'postgres'))
        require('build' not in app, 'Producción no debe construir en el VPS.')
        require(app['environment']['SPRING_PROFILES_ACTIVE'] == 'docker,prod', 'Perfil incorrecto.')
        require(not any(key.endswith('PASSWORD') for key in app['environment']), 'Contraseña heredada en entorno web.')
        require('POSTGRES_USER' not in app['environment'], 'Administrador entregado al proceso web.')
        require('POSTGRES_PASSWORD' not in postgres['environment'], 'Contraseña administrativa en entorno.')
        require(app['ports'] == [{'mode': 'ingress', 'host_ip': '127.0.0.1', 'target': 8080,
                                 'published': '8080', 'protocol': 'tcp'}], 'Puerto de aplicación inesperado.')
        require(not postgres.get('ports'), 'PostgreSQL publicado.')
        require(int(app['mem_limit']) == 2 * 1024 ** 3 and float(app['cpus']) == 2, 'Recursos de aplicación incorrectos.')
        require(int(postgres['mem_limit']) == 1024 ** 3 and float(postgres['cpus']) == 1, 'Recursos PostgreSQL incorrectos.')
        require(model['networks']['backend']['internal'], 'Red PostgreSQL no interna.')
        require(app['read_only'] and app['user'] == '10001:10001', 'Restricciones de aplicación perdidas.')
        require('ALL' in app['cap_drop'] and 'no-new-privileges:true' in app['security_opt'], 'Restricciones de privilegios perdidas.')
        require(set(app['networks']) == {'backend', 'web'} and set(postgres['networks']) == {'backend'}, 'Redes mezcladas.')
        require(app['restart'] == postgres['restart'] == 'unless-stopped', 'Política de reinicio incorrecta.')
        for service in (app, postgres):
            require(service['logging']['options']['max-size'] == '10m' and service['logging']['options']['max-file'] == '3', 'Logs sin límite.')
        negative_env = env_file.with_name('missing-secret.env')
        negative_env.write_text(env_file.read_text().replace(settings['APP_DB_PASSWORD_FILE'], ''), encoding='utf-8')
        negative_command = [str(negative_env) if item == str(env_file) else item for item in command]
        require(run(negative_command + ['config', '--quiet'], success=False).returncode != 0, 'Se aceptó un archivo obligatorio ausente.')
        run(['docker', 'compose', '--env-file', str(env_file), '-f', 'compose.yaml', 'config', '--quiet'])
        print('OK: Compose base y combinado; entorno, secretos, puertos, redes, límites y logs.', flush=True)
        if args.configuration_only:
            return

        # No arrancar sobre un puerto ocupado ni tocar proyectos existentes.
        if not args.credentials_only:
            with socket.socket() as probe:
                probe.bind(('127.0.0.1', 8080))
        network_ids = run(['docker', 'network', 'ls', '-q']).stdout.decode().split()
        for network_id in network_ids:
            network = json.loads(run(['docker', 'network', 'inspect', network_id]).stdout)[0]
            for config in network.get('IPAM', {}).get('Config', []) or []:
                if config.get('Subnet'):
                    existing = ipaddress.ip_network(config['Subnet'])
                    proposed = ipaddress.ip_network(settings['PROXY_NETWORK_SUBNET'])
                    require(existing.version != proposed.version or not existing.overlaps(proposed), 'La subred de prueba ya está ocupada.')
        created = True
        empty_secret = evidence / 'empty_app_db_password'
        empty_secret.write_text('', encoding='utf-8')
        empty_env = evidence / 'empty-secret.env'
        empty_env.write_text(env_file.read_text().replace(settings['APP_DB_PASSWORD_FILE'], empty_secret.as_posix()), encoding='utf-8')
        empty_command = [str(empty_env) if item == str(env_file) else item for item in command]
        rejected = run(empty_command + ['run', '--rm', '--no-deps', 'app'], success=False)
        require(rejected.returncode != 0 and 'Falta una contraseña obligatoria.' in
                (rejected.stdout + rejected.stderr).decode(errors='replace'),
                'Una contraseña vacía no fue rechazada antes de iniciar Java.')
        print('OK: una contraseña vacía impide iniciar Java.', flush=True)
        if args.credentials_only:
            return
        compose('up', '-d', '--wait', '--wait-timeout', '120', 'postgres')
        compose('run', '--rm', '--no-deps', 'db-bootstrap')
        compose('up', '-d', '--wait', '--wait-timeout', '180', 'app')
        compose('run', '--rm', '--no-deps', 'db-bootstrap')
        require(sql("select count(*) from public.flyway_schema_history where version in ('1','2','3','4','5') and success;").stdout.strip() == b'5', 'Migraciones V1–V5 no confirmadas.')
        flags = sql("select rolsuper or rolcreatedb or rolcreaterole or rolreplication or rolbypassrls from pg_roles where rolname in ('oposicionapp_app','oposicionapp_migrator');").stdout.decode().split()
        require(flags == ['f', 'f'], 'Roles con privilegios administrativos.')
        require(sql("select has_database_privilege('oposicionapp_migrator', current_database(), 'CREATE') or has_schema_privilege('oposicionapp_migrator','public','CREATE');").stdout.strip() == b'f', 'CREATE temporal no retirado.')
        require(sql('set role oposicionapp_app; create table c1_tai.prohibited(id int);', success=False).returncode != 0, 'Aplicación con DDL.')
        require(sql('set role oposicionapp_migrator; create schema prohibited;', success=False).returncode != 0, 'Migrador con CREATE arbitrario.')
        print('OK: PostgreSQL vacío, Flyway V1–V5, roles separados y retirada de CREATE temporal.', flush=True)

        forwarded = {'X-Forwarded-Proto': 'https', 'X-Forwarded-Host': 'mercamento.com', 'X-Forwarded-Port': '443', 'X-Forwarded-For': '198.51.100.8'}
        def request(path, method='GET', headers=None, body=None, secure=True):
            conn = http.client.HTTPConnection('127.0.0.1', 8080, timeout=10)
            request_headers = dict(forwarded) if secure else {}
            request_headers.update(headers or {})
            conn.request(method, path, body=body, headers=request_headers)
            response = conn.getresponse()
            result = response.status, dict(response.getheaders()), response.read().decode(errors='replace')
            conn.close()
            return result

        salt = secrets.token_bytes(16)
        encoded = '{pbkdf2-sha256-600k}' + (salt + hashlib.pbkdf2_hmac('sha256', passwords['account_password'].encode(), salt, 600_000, dklen=32)).hex()
        sql("set role oposicionapp_app; insert into core.cuenta(id,correo,password_hash,rol,correo_verificado_en) values (gen_random_uuid(),'production-check@example.invalid','" + encoded + "','USER',current_timestamp);")
        auth = 'Basic ' + base64.b64encode(('production-check@example.invalid:' + passwords['account_password']).encode()).decode()
        require(request('/login', secure=False)[0] == 400, 'HTTP sin HTTPS aceptado.')
        require(request('/c1-tai/examenes')[0] == 401, 'Acceso anónimo permitido.')
        require(request('/c1-tai/examenes', headers={'Authorization': auth})[0] == 401, 'HTTP Basic no debe autenticar cuentas.')
        status, headers, content = request('/login')
        require(status == 200, 'Formulario de login no disponible.')
        cookie = headers.get('Set-Cookie', '')
        require(all(flag.lower() in cookie.lower() for flag in ('Secure', 'HttpOnly', 'SameSite=Strict')), 'Cookie insegura.')
        require("object-src 'none'" in headers.get('Content-Security-Policy', ''), 'CSP perdida.')
        inputs = Inputs()
        inputs.feed(content)
        require('_csrf' in inputs.values, 'Token CSRF ausente.')
        without_token = urllib.parse.urlencode({'correo': 'production-check@example.invalid', 'clave': passwords['account_password']})
        rejected_status, rejected_headers, _ = request('/login', method='POST', body=without_token,
                                                       headers={'Content-Type': 'application/x-www-form-urlencoded', 'Cookie': cookie.split(';')[0]})
        # Un usuario anónimo puede recibir 401 o una redirección de autenticación;
        # lo decisivo es que las credenciales válidas sin CSRF no abran una sesión.
        require(rejected_status in (401, 403) or
                (rejected_status == 302 and '/login' in rejected_headers.get('Location', '')),
                'Login sin CSRF no rechazado: ' + str(rejected_status))
        require(request('/c1-tai/examenes', headers={'Cookie': cookie.split(';')[0]})[0] != 200,
                'Se abrió una sesión sin CSRF.')
        _, headers, content = request('/login')
        cookie = headers.get('Set-Cookie', '')
        inputs = Inputs()
        inputs.feed(content)
        body = urllib.parse.urlencode({'correo': 'production-check@example.invalid', 'clave': passwords['account_password'], '_csrf': inputs.values['_csrf']})
        status, login_headers, _ = request('/login', method='POST', body=body,
                                          headers={'Content-Type': 'application/x-www-form-urlencoded', 'Cookie': cookie.split(';')[0]})
        require(status == 302 and '/c1-tai/plan/' in login_headers.get('Location', ''), 'Login de formulario falló.')
        session_cookie = login_headers.get('Set-Cookie', cookie).split(';')[0]
        require(request('/c1-tai/examenes', headers={'Cookie': session_cookie})[0] == 200, 'La sesión autenticada no funciona.')
        require(request('/logout', method='POST', headers={'Cookie': session_cookie})[0] == 403,
                'Logout autenticado sin CSRF permitido.')
        require(request('/admin/usuarios', headers={'Cookie': session_cookie})[0] == 403, 'Administración accesible a usuario sin rol.')
        require('Strict-Transport-Security' in request('/login', headers=forwarded)[1], 'Proxy autorizado no reconocido.')
        untrusted_probe = '''import json,urllib.request,urllib.error
try:
 r=urllib.request.urlopen(urllib.request.Request('http://app:8080/login',headers={'X-Forwarded-Proto':'https','X-Forwarded-Host':'attacker.invalid','X-Forwarded-Port':'443'}))
except urllib.error.HTTPError as e:
 r=e
print(json.dumps({'status':r.code,'hsts':'Strict-Transport-Security' in r.headers}))
'''
        peer = json.loads(run(['docker', 'run', '--rm', '--pull=never', '--network', project + '_backend', '-i', 'python:3.12-alpine', 'python', '-'], data=untrusted_probe.encode()).stdout)
        require(peer == {'status': 400, 'hsts': False}, 'Cabeceras confiadas desde un contenedor no autorizado.')
        print('OK: cuentas y formulario, rechazo de Basic, CSRF, cookies, CSP y proxy de confianza limitado.', flush=True)

        for service, memory, cpus in [('app', 2 * 1024 ** 3, 2_000_000_000), ('postgres', 1024 ** 3, 1_000_000_000)]:
            container_id = compose('ps', '-q', service).stdout.decode().strip()
            inspected = json.loads(run(['docker', 'inspect', container_id]).stdout)[0]
            host = inspected['HostConfig']
            require(host['Memory'] == memory and host['NanoCpus'] == cpus, 'Límites no aplicados por Docker.')
            if service == 'app':
                require(host['ReadonlyRootfs'] and inspected['Config']['User'] == '10001:10001', 'UID/filesystem incorrectos.')
                require(host['PidsLimit'] == 256 and 'ALL' in host['CapDrop'] and 'no-new-privileges:true' in host['SecurityOpt'], 'Privilegios/procesos incorrectos.')
                require(host['PortBindings'] == {'8080/tcp': [{'HostIp': '127.0.0.1', 'HostPort': '8080'}]}, 'Binding no limitado a loopback.')
            else:
                require(not host.get('PortBindings'), 'PostgreSQL expuesto al anfitrión.')
        require(compose('exec', '-T', 'app', 'sh', '-c', 'touch /should-not-write', success=False).returncode != 0, 'Raíz de contenedor escribible.')
        print('OK: recursos efectivos, puertos, UID, capabilities, procesos y filesystem.', flush=True)

        marker = 'b2e7a1a0-2581-4321-9a40-23fbf74888c1'
        sql(f"set role oposicionapp_app; insert into c1_tai.convocatoria(id,denominacion,anio_referencia) values ('{marker}','Persistencia sintética',2025);")
        dump_shell = 'export PGPASSWORD="$(cat /run/secrets/postgres_password)"; exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner'
        dump = compose('exec', '-T', 'postgres', 'bash', '-ec', dump_shell).stdout
        require(dump.startswith(b'PGDMP'), 'Backup no es un archivo pg_dump válido.')
        (evidence / 'synthetic.dump').write_bytes(dump)
        compose('up', '-d', '--force-recreate', '--wait', '--wait-timeout', '120', 'postgres')
        compose('up', '-d', '--force-recreate', '--wait', '--wait-timeout', '180', 'app')
        require(sql(f"select count(*) from c1_tai.convocatoria where id='{marker}';").stdout.strip() == b'1', 'Datos perdidos tras recreación.')
        sql('create database production_restore;')
        restore_shell = 'export PGPASSWORD="$(cat /run/secrets/postgres_password)"; exec pg_restore -U "$POSTGRES_USER" -d production_restore --no-owner --exit-on-error'
        compose('exec', '-T', 'postgres', 'bash', '-ec', restore_shell, data=dump)
        require(sql(f"select count(*) from c1_tai.convocatoria where id='{marker}';", database='production_restore').stdout.strip() == b'1', 'Restauración sin el dato sintético.')
        print('OK: persistencia, reinicio de Flyway sin errores y backup/restauración.', flush=True)
        (evidence / 'result.json').write_text(json.dumps({'result': 'success', 'project': project, 'image': args.image}, indent=2), encoding='utf-8')
    finally:
        if created:
            # Solo recursos nuevos con la etiqueta exacta del proyecto aleatorio de esta prueba.
            compose('down', '--timeout', '30', success=False)
            volume = project + '_postgres_data'
            inspection = run(['docker', 'volume', 'inspect', volume], success=False)
            if inspection.returncode == 0:
                labels = json.loads(inspection.stdout)[0].get('Labels') or {}
                if labels.get('com.docker.compose.project') == project:
                    run(['docker', 'volume', 'rm', volume])
        print('Evidencia privada: ' + str(evidence), flush=True)


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('ERROR: ' + str(error), file=sys.stderr)
        sys.exit(1)
