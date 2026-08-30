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

El trabajo se organizará de forma colaborativa mediante GitHub, utilizando Issues, ramas de trabajo y Pull Requests