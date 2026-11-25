# Infraestructura local

Requisitos: Java 25, Docker Desktop con contenedores Linux y Maven Wrapper.
El perfil `docker` conecta con `postgres:5432` por la red interna de Docker.
PostgreSQL no publica puertos hacia el anfitrión. El acceso web se limita a
`http://127.0.0.1:8080/c1-tai/examenes` y requiere autenticación.

## Credenciales y preparación inicial

Mantener `.env` fuera de Git, sin compartir su contenido. Variables necesarias:
`POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `APP_DB_USER`,
`APP_DB_PASSWORD`, `FLYWAY_DB_USER` y `FLYWAY_DB_PASSWORD`.
Los roles previstos son `postgres`, `oposicionapp_app` y `oposicionapp_migrator`.
No reutilizar la contraseña administrativa para los otros roles.

Compose permite iniciar PostgreSQL aunque falten las contraseñas de los roles.
La aplicación necesita ambas contraseñas y los roles ya preparados:

```powershell
docker compose up -d --wait postgres
```

Antes de aplicar el bootstrap, revisar los propietarios y privilegios existentes.
El script es transaccional: crea los roles si faltan y normaliza los permisos y
propietarios de los objetos del proyecto. No asigna ni cambia contraseñas.
Para una base revisada, ejecutarlo desde PowerShell:

```powershell
Get-Content .\src\main\resources\db\bootstrap\postgresql_roles.sql -Raw |
    docker compose exec -T postgres sh -c 'exec psql -X -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -v POSTGRES_DB="$POSTGRES_DB"'
```

Si los roles son nuevos o aún no tienen contraseña, introducirlas manualmente
en la terminal interactiva, sin incluirlas en comandos, scripts ni informes:

```powershell
docker compose exec postgres sh -c 'exec psql -X -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

Dentro de `psql`, usar `\password oposicionapp_app` y
`\password oposicionapp_migrator`, y salir con `\q`.
Si ya tienen contraseña, conservarla. Completar las variables correspondientes
de `.env` con los mismos valores mediante un editor local, sin pegarlos al chat.

```powershell
docker compose config --quiet
docker compose up -d --build app
```

En una base nueva, después de que Flyway aplique V1, repetir el bootstrap
administrativo para retirar los permisos temporales de creación sobre la base
y `public`. En una base con V1 aplicada, el bootstrap ya realiza esa finalización.
No modificar las migraciones compartidas ni usar `down -v`.

## Perfiles y pruebas

- `local`: conserva una URL JDBC en `127.0.0.1:5433`, pero no es operativo
  con esta topología, ya que PostgreSQL no publica puertos. No utilizarlo para
  conectar a otra instalación local; el arranque soportado se realiza en Docker.
- `docker`: activado por Compose, con red interna y puerto HTTP local 8080.
- `test`: recursos exclusivos de pruebas y conexiones asignadas dinámicamente
  por Testcontainers; nunca usa `.env` ni bases personales.
- `prod`: requiere `JDBC_DATABASE_URL`, los cuatro parámetros de roles y
  `SECURITY_USER`/`SECURITY_PASSWORD` externos mientras siga la autenticación
  provisional de Spring Security. Cookies seguras y errores sin detalles internos.
  Requiere HTTPS y configurar la terminación TLS y cualquier proxy antes de
  desplegar. No se incluye un despliegue de producción.

Verificación integrada, sin credenciales locales:

```powershell
docker build -t oposicionapp:local .
.\mvnw.cmd -B -ntp -Doposicionapp.test.image=oposicionapp:local verify
```

La prueba de imagen se activa con esa propiedad: arranca PostgreSQL temporal,
aplica Flyway y exige HTTP 401, UID 10001 y filesystem de solo lectura.
Sin la propiedad, Maven ejecuta la suite habitual y omite únicamente esa prueba
que necesita una imagen previamente construida. CI construye la imagen y la activa.
No se publican imágenes ni informes de pruebas con posibles datos sensibles.
El workflow CI está preparado para construir y verificar el proyecto, pendiente
de su primera ejecución en GitHub.

## Controles Docker

La imagen usa bases Temurin fijadas por digest y contiene solo el JRE y el JAR.
El proceso Java recibe directamente las señales como PID 1. Compose limita
la aplicación a UID 10001, filesystem de solo lectura, `/tmp` temporal,
sin capabilities y sin elevación de privilegios. Ambos servicios disponen de
30 segundos para detenerse. PostgreSQL conserva su volumen y su inicialización
oficial; no se fuerza un UID distinto sobre datos existentes.
La red de base de datos es interna. La aplicación dispone además de una red
bridge de acceso web para que Docker publique HTTP hacia el anfitrión,
exclusivamente en loopback. PostgreSQL no pertenece a la red web.
Las variables de entorno son visibles a administradores de Docker: no compartir
la salida de `docker inspect` ni de `docker compose config` sin `--quiet`.
Los digest requieren revisión periódica para incorporar correcciones de seguridad;
fijarlos no acredita ausencia de vulnerabilidades.
