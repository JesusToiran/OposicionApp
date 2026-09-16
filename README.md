# OposicionApp

OposicionApp es una aplicación web orientada a la preparación de oposiciones en España

La primera versión estará centrada en C1 TAI de la Administración General del Estado

El proyecto nace con dos objetivos claros:

- crear una herramienta de estudio útil desde las primeras versiones
- construir una base técnica suficientemente limpia para poder ampliar después a otras oposiciones

## Estado del proyecto

El repositorio se encuentra en fase inicial de preparación

Actualmente se está definiendo la estructura del proyecto, la arquitectura, la documentación y las reglas de desarrollo antes de comenzar con la implementación funcional

La estrategia de versiones seguirá una evolución progresiva mediante versiones Alpha

La primera versión funcional será Alpha 1.0 y funcionará inicialmente en local

## Alcance inicial

La primera etapa de OposicionApp incluirá:

- registro mediante correo electrónico y contraseña
- verificación de correo electrónico
- recuperación de contraseña
- navegación por bloques y temas de C1 TAI
- preguntas oficiales de convocatorias anteriores
- modo práctica con corrección inmediata
- modo simulacro con corrección al finalizar
- progreso básico del usuario
- panel privado para revisión y gestión de preguntas
- trazabilidad de las fuentes oficiales utilizadas

El contenido inicial se apoyará exclusivamente en fuentes oficiales

No se pretende redactar un temario propio completo durante esta primera fase

## Stack previsto

Backend:

- Java 25 LTS
- Spring Boot 4.1.x
- Spring MVC
- Spring Security
- Spring Data JPA
- Hibernate

Frontend:

- Thymeleaf
- HTMX
- Tailwind CSS

Persistencia:

- PostgreSQL
- Flyway

Pruebas:

- JUnit
- Testcontainers

Infraestructura:

- Docker
- Docker Compose
- Maven

## Arquitectura

El proyecto partirá de un monolito modular

PostgreSQL será inicialmente la fuente principal de verdad

La separación lógica prevista en base de datos será:

- `core`
- `c1_tai`
- `study`

No se añadirán Redis, colas, almacenamiento de objetos, motores de búsqueda, microservicios o Kubernetes salvo que exista una necesidad técnica real

## Fuentes y trazabilidad

Las preguntas oficiales deberán conservar información suficiente para poder identificar su procedencia

Entre otros datos se almacenarán:

- convocatoria
- ejercicio
- número de pregunta
- tipo de pregunta
- opciones de respuesta
- respuesta oficial
- preguntas de reserva cuando existan
- documento de origen
- enlace o referencia oficial
- estado de revisión

Las preguntas afectadas por cambios normativos podrán pasar al estado `REVIEW_REQUIRED`

Mientras permanezcan en ese estado no deberán mostrarse como contenido válido de estudio

## Desarrollo

El desarrollo se organizará mediante GitHub Issues, ramas de trabajo y Pull Requests

Las decisiones relevantes del proyecto se documentarán en `DECISIONS.md`

## Base de datos local

El contenedor local de PostgreSQL se inicia con el usuario administrador de arranque definido en `.env`. Ese usuario solo debe usarse para preparar la base inicialmente.

La aplicación usa dos roles separados:

- `oposicionapp_app`: conexión normal de la aplicación. Puede leer y escribir datos en los schemas del proyecto, pero no puede crear ni eliminar tablas, crear bases, crear roles ni actuar como superusuario.
- `oposicionapp_migrator`: ejecución de Flyway. Puede crear y gestionar objetos dentro de `core`, `c1_tai` y `study`, pero no puede crear bases, crear roles ni actuar como superusuario.

Variables esperadas en `.env`:

```properties
POSTGRES_DB=oposicionapp
POSTGRES_USER=postgres
POSTGRES_PASSWORD=
APP_DB_USER=oposicionapp_app
APP_DB_PASSWORD=
FLYWAY_DB_USER=oposicionapp_migrator
FLYWAY_DB_PASSWORD=
```

Preparación local desde PowerShell, desde una sesión nueva:

```powershell
function Import-OposicionAppComposeEnv {
    param([string] $EnvFile = '.\.env')

    $names = @(
        'POSTGRES_DB',
        'POSTGRES_USER',
        'POSTGRES_PASSWORD',
        'APP_DB_USER',
        'APP_DB_PASSWORD',
        'FLYWAY_DB_USER',
        'FLYWAY_DB_PASSWORD'
    )

    foreach ($name in $names) {
        [Environment]::SetEnvironmentVariable($name, $null, 'Process')
    }

    $probeFile = Join-Path ([System.IO.Path]::GetTempPath()) ('oposicionapp-env.' + [guid]::NewGuid() + '.compose.yaml')

    try {
        @'
services:
  env_probe:
    image: scratch
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-}
      POSTGRES_USER: ${POSTGRES_USER:-}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-}
      APP_DB_USER: ${APP_DB_USER:-}
      APP_DB_PASSWORD: ${APP_DB_PASSWORD:-}
      FLYWAY_DB_USER: ${FLYWAY_DB_USER:-}
      FLYWAY_DB_PASSWORD: ${FLYWAY_DB_PASSWORD:-}
'@ | Set-Content -LiteralPath $probeFile -Encoding utf8

        $composeConfig = docker compose --env-file $EnvFile -f $probeFile config --format json
        if ($LASTEXITCODE -ne 0) {
            throw 'No se pudo leer .env con Docker Compose'
        }

        $environment = ($composeConfig | ConvertFrom-Json).services.env_probe.environment
        foreach ($name in $names) {
            $value = $environment.PSObject.Properties[$name].Value
            [Environment]::SetEnvironmentVariable($name, [string] $value, 'Process')
        }
    }
    finally {
        Remove-Item -LiteralPath $probeFile -Force -ErrorAction SilentlyContinue
    }
}

Import-OposicionAppComposeEnv

docker compose up -d --wait --wait-timeout 60 postgres
if ($LASTEXITCODE -ne 0) {
    throw 'PostgreSQL no ha quedado saludable antes del timeout'
}

$bootstrap = Get-Content .\src\main\resources\db\bootstrap\postgresql_roles.sql -Raw
$bootstrap | docker compose exec -T postgres psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -v ON_ERROR_STOP=1 -v POSTGRES_DB="$env:POSTGRES_DB"
```

Asignar las contraseñas sin pegarlas en comandos ni guardarlas en SQL:

```powershell
docker compose exec postgres psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB
```

Dentro de `psql`:

```postgresql
\password oposicionapp_app
\password oposicionapp_migrator
\q
```

Después, completar `.env` con `APP_DB_PASSWORD` y `FLYWAY_DB_PASSWORD`. La configuración `local` conectará la aplicación con `oposicionapp_app` y Flyway con `oposicionapp_migrator`.

Desde una sesión nueva, vuelve a cargar las variables con la función `Import-OposicionAppComposeEnv` definida arriba y arranca la aplicación:

```powershell
Import-OposicionAppComposeEnv

.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

Si se reutiliza la misma terminal después de arrancar Spring Boot, detener primero la aplicación con `Ctrl+C` antes de ejecutar nuevos comandos en esa sesión.

En una base nueva, arrancar la aplicación una vez para que Flyway cree `public.flyway_schema_history` y aplique `V1`. Después debe ejecutarse una finalización administrativa:

```powershell
$bootstrap = Get-Content .\src\main\resources\db\bootstrap\postgresql_roles.sql -Raw
$bootstrap | docker compose exec -T postgres psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -v ON_ERROR_STOP=1 -v POSTGRES_DB="$env:POSTGRES_DB"
```

Esa finalización no cambia contraseñas. Conserva el historial, retira el permiso técnico `CREATE` sobre `public` y también retira `CREATE` sobre la base de datos. El migrador conserva capacidad DDL únicamente dentro de `core`, `c1_tai` y `study`.

Si la base ya tenía `V1__crear_esquemas.sql` aplicada por `postgres`, el bootstrap conserva `public.flyway_schema_history`, cambia propietario de los schemas y objetos del proyecto a `oposicionapp_migrator`, retira privilegios de `PUBLIC` y concede a `oposicionapp_app` solo permisos de datos. Si falla, no continuar arrancando la aplicación con `postgres`; corregir el error, volver a ejecutar el mismo bootstrap y después arrancar con el perfil local.


## Roadmap inicial

- Alpha 1.0 — funcionamiento local
- Alpha 1.1 — primer despliegue
- Alpha 1.2 — acceso mediante invitación
- Alpha 1.3 — registro público

Las siguientes versiones Alpha ampliarán progresivamente funcionalidades, seguridad, observabilidad, administración y documentación

## Licencia

Pendiente de definir

## Autores

Proyecto desarrollado conjuntamente por Jesús Toirán y Agnely Rivas

El trabajo se organizará de forma colaborativa mediante GitHub, utilizando Issues, GitHub Projects, ramas de trabajo y Pull Requests
