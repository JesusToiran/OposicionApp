# OposicionApp

OposicionApp es una aplicación web orientada a la preparación de oposiciones en España

La primera versión estará centrada en C1 TAI de la Administración General del Estado

El proyecto nace con dos objetivos claros:

- crear una herramienta de estudio útil desde las primeras versiones
- construir una base técnica suficientemente limpia para poder ampliar después a otras oposiciones

## Estado del proyecto

La infraestructura local está implementada con Docker Compose, PostgreSQL interno a Docker, Flyway y pruebas con Testcontainers

La biblioteca de exámenes C1 TAI permite consultar convocatorias, exámenes, ejercicios y referencias documentales mediante acceso autenticado en `http://127.0.0.1:8080/c1-tai/examenes`

La versión publicable comienza con la biblioteca vacía y no incorpora la importación de exámenes oficiales. El workflow CI está preparado y pendiente de ejecución en GitHub

El registro de usuarios, las prácticas, los simulacros y el despliegue público continúan pendientes

La estrategia de versiones seguirá una evolución progresiva mediante versiones Alpha

El trabajo actual forma parte de Alpha 1.0 y funciona en el entorno local

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

El proyecto utiliza un monolito modular

PostgreSQL es la fuente principal de verdad

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

La guía de [infraestructura](docs/INFRASTRUCTURE.md) describe los perfiles,
el arranque con Docker y la verificación integrada de CI.

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

El arranque soportado se realiza mediante Docker Compose desde la raíz del proyecto:

```powershell
docker compose up -d --wait postgres
```

En una base nueva, preparar primero los roles y sus contraseñas siguiendo la
[guía de infraestructura](docs/INFRASTRUCTURE.md). Después:

```powershell
docker compose up -d --build app
```

PostgreSQL solo es accesible desde la red interna de Docker, mediante
`postgres:5432`. No publica puertos hacia Windows ni utiliza otras instalaciones
locales. Únicamente HTTP se publica en `127.0.0.1:8080`.

El perfil `local` conserva una URL JDBC en `127.0.0.1:5433`, pero no es operativo
con esta topología. Debe utilizarse el perfil `docker`, activado por Compose.

La [biblioteca](docs/BIBLIOTECA.md) requiere autenticación y no ofrece operaciones
web de creación, modificación ni eliminación. La migración V2 crea su estructura
sin incorporar datos iniciales. La importación de exámenes oficiales queda fuera de esta
versión publicable.

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
