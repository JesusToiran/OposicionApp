# Primer despliegue controlado

Configuración candidata para Ubuntu 24.04, Docker Engine y Compose 2.24.4 o
posterior. No se ha aplicado al VPS. Reutiliza Nginx del anfitrión y el certificado
existente de `mercamento.com` y `www.mercamento.com`. La activación requiere
revisión y autorización humana; la preparación local no publica imágenes.

## Artefacto y recursos

Se necesitan 3 GiB de RAM para los límites de aplicación y PostgreSQL, además del
margen para Docker, Nginx y los servicios que ya utiliza el VPS. Los límites son
2 GiB/2 CPU para Java y 1 GiB/1 CPU para PostgreSQL; no reservan toda esa memoria.
Revisar el consumo real y el espacio de backups antes de activar. No modificar
los servicios existentes ni el cortafuegos.

Usar una revisión aprobada y un árbol Git limpio en la máquina de construcción:

```bash
test -z "$(git status --porcelain)" || exit 1
revision=$(git rev-parse HEAD)
image="oposicionapp:git-$revision"
docker build --label "org.opencontainers.image.revision=$revision" -t "$image" .
docker image inspect --format '{{.Id}}' "$image" > app-image.id
docker save --output app-image.tar "$image"
tar -cf deployment.tar compose.yaml compose.prod.yaml .env.prod.example deploy/ \
  src/main/resources/db/bootstrap/postgresql_roles.sql
sha256sum app-image.tar deployment.tar > SHA256SUMS
```

La etiqueta por commit, el identificador de imagen y el archivo con SHA-256 fijan
el artefacto desplegado. No se garantiza una reconstrucción idéntica bit a bit.
Conservar el artefacto anterior para rollback. El Dockerfile mantiene sus bases
Temurin fijadas por digest; no se cambian dependencias durante el despliegue.

PostgreSQL usa `18` con un digest obligatorio. La plantilla fija el digest de la
imagen ya disponible durante la preparación local. Antes de una entrega, revisar
sus avisos de seguridad y comprobar su plataforma `linux/amd64`. Una actualización
requiere revisar el nuevo digest, ejecutar las pruebas y conservar el anterior;
no usar `latest` ni actualizar automáticamente. Un cambio de versión mayor exige
un plan de migración independiente; no reutilizar el volumen con otra versión mayor.

## Transferencia y configuración privada

Tras la aprobación, transferir únicamente los dos archivos tar y `SHA256SUMS`
por el canal SSH/SFTP autorizado. Esta guía no autoriza la transferencia ni
una conexión al servidor. En el VPS, preparar una carpeta de versión bajo
`/opt/opositapp/app/`, comprobar `sha256sum -c SHA256SUMS`, cargar la imagen con
`docker load --input app-image.tar` y extraer `deployment.tar` allí.

No usar la raíz web para código, configuración o secretos. Mantener
`/opt/opositapp/config` con permisos `0750`, y `secrets` y `backups` con `0700`,
propiedad de root. Copiar `.env.prod.example` a
`/opt/opositapp/config/.env.prod`, con permisos `0600`. Ajustar `APP_IMAGE` a la
etiqueta aprobada y los parámetros no secretos. No copiar el `.env` de desarrollo.
Los campos `*_PASSWORD` de la plantilla deben permanecer vacíos; se usan archivos.

Crear tres contraseñas de base de datos distintas sin mostrarlas ni introducirlas en argumentos:

```bash
sudo -s
umask 077
for name in postgres_password app_db_password flyway_db_password; do
  test ! -e "/opt/opositapp/secrets/$name" || exit 1
  openssl rand -base64 48 | tr -d '\n' > "/opt/opositapp/secrets/$name"
done
```

Los secretos de Compose son montajes de archivos: `uid`, `gid` y `mode` no
cambian los permisos de un archivo del anfitrión. Obtener el UID `postgres` de
la imagen aprobada (`docker run --rm --entrypoint id IMAGEN_POSTGRES -u postgres`).
Asignar a `postgres_password` ese UID y grupo `0`; asignar los otros dos a UID
`10001`, grupo `0`; aplicar `0440` a los tres. El directorio padre continúa
`root:root 0700`. Así PostgreSQL y Java leen sus archivos, y el bootstrap con UID
0/GID 0 puede leer los tres secretos de base de datos sin capabilities adicionales.
Comprobar estos permisos en Linux antes de arrancar. Los archivos de prueba de
Docker Desktop no acreditan por sí solos los permisos del anfitrión Ubuntu.

PostgreSQL recibe solo el secreto administrativo. Java recibe el secreto de
aplicación y el del migrador, importados por
`configtree:/run/secrets/`. No recibe el usuario ni la contraseña administrativos.
No usar `set -x`, imprimir el entorno, compartir `docker inspect` completo o
ejecutar `docker compose config` sin `--quiet` con una configuración real.

## Compose efectivo y proxy de confianza

Desde la carpeta de versión, definir esta función para todos los comandos:

```bash
dc() {
  docker compose --env-file /opt/opositapp/config/.env.prod \
    -p oposicionapp-prod -f compose.yaml -f compose.prod.yaml "$@"
}
dc config --quiet
```

El orden de los archivos es obligatorio. `!override` sustituye entornos, puertos,
redes y restricciones sensibles; `!reset` retira la construcción y los puertos de
PostgreSQL. No añadir un tercer override de desarrollo. El Compose base admite
contraseña o `POSTGRES_PASSWORD_FILE`; la imagen oficial rechaza el arranque
sin ninguno o con ambos. Producción sustituye ese entorno y exige el archivo.

Comprobar que `172.30.240.0/28` no colisiona con las redes del VPS. Si se cambia,
ajustar juntos `PROXY_NETWORK_SUBNET`, `PROXY_NETWORK_GATEWAY` y
`TRUSTED_PROXY_CIDR`. Este último debe contener solo la puerta de enlace, con
prefijo `/32`. Tomcat confía únicamente en ese origen, por el que llega Nginx
nativo al puerto publicado en loopback; no en todas las redes privadas.
Comprobar el origen real en el VPS antes de activar el proxy. No ampliar la
confianza para corregir redirecciones sin identificar antes el origen.

## Inicialización y arranque

Antes del primer arranque, confirmar que el volumen del proyecto de producción
es nuevo o pertenece a una instalación revisada. Nunca reutilizar el volumen de
desarrollo ni eliminar un volumen existente para resolver un error.

```bash
dc up -d --wait --wait-timeout 120 postgres
dc run --rm --no-deps db-bootstrap
dc up -d --wait --wait-timeout 180 app
dc run --rm --no-deps db-bootstrap
dc ps
```

El primer bootstrap reutiliza `postgresql_roles.sql`, crea los roles restringidos
y asigna sus contraseñas mediante `psql \password`, sin SQL con contraseñas en
texto plano. Los nombres deben ser `oposicionapp_app` y `oposicionapp_migrator`.
Flyway aplica las migraciones existentes con el migrador; Hibernate solo valida.
El segundo bootstrap, obligatorio tras la primera migración, retira `CREATE` del
migrador sobre la base y `public`. No modificar migraciones compartidas ni sus checksums; esta versión incorpora V1–V5.

No activar Nginx hasta confirmar la retirada de los permisos temporales. Puede
comprobarse con `has_database_privilege` y `has_schema_privilege` desde una sesión
administrativa local al contenedor. Los dos resultados deben ser falsos. En los
schemas del proyecto se conserva DDL del migrador; el usuario de aplicación tiene
solo los permisos de datos previstos. Revisar el historial Flyway y los roles sin
consultar ni imprimir sus verificadores de contraseñas.

Flyway sigue integrado en el arranque web para reutilizar el comportamiento
existente. Por ello, el proceso web puede leer el secreto del migrador. Es un riesgo
residual: un compromiso de ese proceso podría obtener capacidad DDL dentro del
proyecto. Separar migraciones en un proceso previo y arrancar la web con Flyway
desactivado es la mejora propuesta para otra tarea, con pruebas de arranque y
actualización propias. No se ha introducido esa refactorización en esta entrega.

## Comprobaciones locales y activación de Nginx

El backend exige HTTPS. Una petición HTTP directa a `/login` debe rechazarse
con `400`; la comprobación de vida de Compose solo verifica que Java escucha.
Después de activar el proxy autorizado, `/login` por HTTPS debe devolver `200`
y la biblioteca sin sesión debe devolver `401` cuando no se solicita HTML.
Un navegador anónimo que solicita HTML recibe una redirección al formulario.

El acceso utiliza cuentas de PostgreSQL; `SECURITY_USER`, `SECURITY_PASSWORD` y
HTTP Basic ya no autentican. Preparar la primera cuenta únicamente desde una
consola interactiva conforme a [versión integrada](VERSION_INTEGRADA.md).
La cuenta inicial conserva el correo pendiente de verificación. El transporte de
correo aún no está implementado: no habilitar el estudio público ni marcar correos
manualmente como verificados para eludir este bloqueo.

Comprobar el formulario, sesión, CSRF y permisos con cuentas de prueba en un
entorno aislado. No introducir contraseñas en argumentos ni logs. Comprobar que PostgreSQL no publica puertos,
que HTTP solo enlaza `127.0.0.1:8080` y que Docker aplica `Memory`, `NanoCpus`,
`ReadonlyRootfs`, UID `10001`, `PidsLimit`, capabilities y `no-new-privileges`.
Inspeccionar solo esos campos, nunca el entorno completo. La JVM limita heap a
1 GiB y reserva margen para metaspace, código, memoria directa y threads dentro
de 2 GiB. PostgreSQL limita conexiones a 40 y buffers compartidos a 256 MiB;
estos valores requieren seguimiento bajo carga real, no sustituyen una prueba
de capacidad del VPS compartido.

Comparar los candidatos `deploy/nginx/oposiciontai.conf` y
`oposiciontai-proxy.conf` con el sitio existente antes de instalarlos. Conservar
una copia privada del sitio y del snippet anteriores. Confirmar las rutas reales
del certificado y clave existentes; el nombre de su línea puede diferir del dominio.
El sitio conserva la raíz ACME y la redirección a HTTPS. No copiar claves al
repositorio ni solicitar otro certificado innecesariamente.

Tras la aprobación de activación, instalar el snippet en
`/etc/nginx/snippets/oposiciontai-proxy.conf` y el sitio en
`/etc/nginx/sites-available/oposiciontai`, conservar su enlace de activación,
ejecutar `sudo nginx -t` y solo si pasa, `sudo systemctl reload nginx`.
No reiniciar ni editar los demás sitios o servicios.

El proxy sobrescribe `Host` y `X-Forwarded-*`, elimina `Forwarded` y
`X-Forwarded-Prefix` y conserva `Authorization`. Nginx emite HSTS y elimina el
duplicado del backend; Spring conserva CSP y sus otras cabeceras. Se mantienen
`noindex, nofollow, noarchive`, logs independientes, límites de peticiones y
conexiones. Los límites de login no sustituyen la autenticación ni Fail2ban.

Verificar HTTPS para ambos dominios, la cadena y fechas del certificado, la
redirección HTTP, la excepción ACME, la ausencia de enlaces HTTP en login y las
cookies `Secure`, `HttpOnly`, `SameSite=Strict`. Probar autenticación, CSRF y
cabeceras de seguridad; confirmar que cabeceras enviadas por el cliente no
alteran los valores que recibe el backend. Las cuentas exigen correo verificado para estudiar; el registro público y el envío de correo siguen pendientes.

## Backups y recuperación

Mantener backups con permisos `0600` bajo `/opt/opositapp/backups`, directorio
`0700`. Realizar un dump antes de cada actualización y planificar copias diarias
con retención y copia cifrada fuera del VPS bajo autorización independiente.
No copiar el directorio de datos en caliente ni incluir contraseñas en el nombre
del archivo. La copia local no protege frente a la pérdida del servidor.

```bash
umask 077
backup="/opt/opositapp/backups/oposicionapp-$(date -u +%Y%m%dT%H%M%SZ).dump"
dc exec -T postgres bash -ec \
  'export PGPASSWORD="$(cat /run/secrets/postgres_password)"; exec pg_dump --no-password -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner' \
  > "$backup.partial"
test -s "$backup.partial" || exit 1
mv "$backup.partial" "$backup"
sha256sum "$backup" > "$backup.sha256"
```

Verificar regularmente una restauración en un proyecto y volumen nuevos, con
subred y puerto de aplicación diferentes. Crear los roles mediante el bootstrap,
restaurar con `pg_restore --no-owner --exit-on-error`, volver a ejecutar el
bootstrap para normalizar propietarios y privilegios, y comprobar datos,
historial Flyway y autenticación. Mantener la base original intacta hasta la
aprobación de recuperación. Nunca restaurar sobre producción ni usar
`--clean`, `DROP`, `TRUNCATE` o `down -v` como paso rutinario.

El dump no contiene las contraseñas de roles. Conservar sus archivos privados
mediante una estrategia de copia cifrada separada; la restauración necesita
esas credenciales o una rotación coordinada de los tres roles. Las cuentas web
forman parte del dump y sus contraseñas están representadas por hashes.

## Actualización y rollback

Cargar la nueva imagen verificada, hacer backup y cambiar únicamente `APP_IMAGE`
en la configuración privada. Ejecutar `dc config --quiet` y
`dc up -d --no-deps --wait --wait-timeout 180 app`. No publicar imágenes ni
reconstruir en el VPS. Comprobar salud, autenticación y logs sin secretos.

Para un fallo de aplicación con esquema compatible, restaurar la etiqueta
anterior y recrear solo `app`. Si nuevas migraciones hacen incompatible el
esquema, un rollback de imagen no basta: recuperar el backup en otro volumen,
validarlo y planificar el cambio controlado. No revertir ni editar migraciones
compartidas y no eliminar el volumen anterior. Si falla el proxy, restaurar
únicamente sus archivos previos, validar con `nginx -t` y recargar Nginx.

## Verificación repetible y referencias

Desde el repositorio local, con Docker disponible y sin usar credenciales reales:

```bash
docker build -t oposicionapp:production-check .
./mvnw -B -ntp -Doposicionapp.test.image=oposicionapp:production-check verify
python3 scripts/verify-production.py --configuration-only
python3 scripts/verify-production.py --image oposicionapp:production-check
```

El script usa contraseñas sintéticas y un proyecto con nombre aleatorio, exige
el puerto 8080 y la subred de prueba libres y elimina solo sus propios recursos
Docker tras comprobar las etiquetas. Las evidencias quedan excluidas de Git en
`.local/production-prep`. La prueba de cabeceras desde otro contenedor utiliza
la imagen local `python:3.12-alpine`; debe estar disponible antes de ejecutarla.
No ejecutar el script en el VPS con servicios reales.

Antes del despliegue, exigir un análisis actualizado de CVE de las imágenes y
dependencias. Docker Scout está disponible, pero requiere una sesión iniciada;
un bloqueo de autenticación no equivale a un resultado sin vulnerabilidades.
No introducir credenciales de herramientas en el chat ni publicar informes
que incluyan datos privados. No actualizar imágenes o dependencias automáticamente
para ocultar un hallazgo; revisar y autorizar la remediación.

Referencias: [mezcla de Compose](https://docs.docker.com/reference/compose-file/merge/),
[secretos de Compose](https://docs.docker.com/compose/how-tos/use-secrets/),
[configuración externa de Spring Boot](https://docs.spring.io/spring-boot/4.1/reference/features/external-config.html),
[proxy de Tomcat en Spring Boot](https://docs.spring.io/spring-boot/4.1/how-to/webserver.html),
[cabeceras de proxy Nginx](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_set_header).
