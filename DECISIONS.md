# Registro de decisiones

Este archivo recoge las decisiones relevantes de producto, arquitectura y operación de OposicionApp

Su objetivo es evitar contradicciones, mantener trazabilidad y dejar claro qué decisiones están cerradas y cuáles siguen pendientes

## D-001 — Nombre del proyecto

Estado: aceptada

Nombre oficial: `OposicionApp`

Repositorio principal: `OposicionApp`

El repositorio será público desde el inicio

## D-002 — Alcance inicial

Estado: aceptada

La primera oposición soportada será C1 TAI de la Administración General del Estado

La arquitectura deberá permitir incorporar otras oposiciones en el futuro sin rehacer el núcleo de la aplicación

## D-003 — Plataforma inicial

Estado: aceptada

La primera etapa será exclusivamente web responsive

No se desarrollará aplicación móvil durante las primeras versiones Alpha

La integración móvil queda prevista para una fase posterior

## D-004 — Arquitectura

Estado: aceptada

La aplicación se desarrollará como monolito modular

Se evitarán microservicios mientras no exista una necesidad técnica real

La prioridad inicial será mantener una arquitectura clara, mantenible y sencilla de desplegar

## D-005 — Stack principal

Estado: aceptada

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

Build y pruebas:

- Maven
- JUnit
- Testcontainers

Infraestructura:

- Docker
- Docker Compose

## D-006 — Persistencia

Estado: aceptada

PostgreSQL será la fuente principal de verdad durante las primeras versiones

La separación lógica inicial será mediante los schemas:

- `core`
- `c1_tai`
- `study`

Redis, almacenamiento de objetos, motores de búsqueda y otras tecnologías se incorporarán solo cuando exista una necesidad técnica demostrable

## D-007 — Cuentas de usuario

Estado: aceptada

El acceso se realizará mediante correo electrónico y contraseña

La verificación del correo será obligatoria antes de utilizar las funciones de estudio

La recuperación de contraseña estará disponible desde las primeras versiones

No se utilizará OAuth inicialmente

## D-008 — Contenido inicial

Estado: aceptada

La primera versión utilizará contenido procedente de fuentes oficiales

El contenido inicial incluirá:

- bloques y temas oficiales
- normativa y enlaces oficiales
- conceptos relevantes
- preguntas oficiales de convocatorias anteriores

No se redactará inicialmente un temario propio completo

## D-009 — Modelo de preguntas

Estado: aceptada

Una pregunta podrá estar vinculada a varios temas y conceptos

Cada pregunta tendrá un tema principal para clasificación y métricas

Las preguntas deberán conservar trazabilidad suficiente para identificar su fuente oficial

## D-010 — Cambios normativos

Estado: aceptada

Las preguntas potencialmente afectadas por cambios normativos podrán pasar al estado `REVIEW_REQUIRED`

Las preguntas en ese estado no deberán mostrarse a los usuarios hasta que sean revisadas y validadas

## D-011 — Modos de estudio

Estado: aceptada

Existirán al menos dos modos:

- práctica, con corrección inmediata
- simulacro, con corrección al finalizar

El sistema de corrección deberá respetar las reglas oficiales de la convocatoria correspondiente

## D-012 — Administración interna

Estado: aceptada

Existirá un panel privado mínimo para:

- revisar preguntas importadas
- corregir errores
- activar o desactivar preguntas
- reclasificar preguntas
- revisar contenido afectado por cambios normativos

## D-013 — Estrategia de versiones

Estado: aceptada

Alpha representa el MVP en maduración progresiva

Versiones inicialmente previstas:

- Alpha 1.0 — funcionamiento local
- Alpha 1.1 — primer despliegue
- Alpha 1.2 — acceso mediante invitación
- Alpha 1.3 — registro público

Beta 1.0 representará la aplicación web con el alcance funcional completo previsto

Beta 1.2 incorporará la integración móvil

## D-014 — Publicidad

Estado: aceptada

Google AdSense se integrará desde la fase Alpha

La publicidad deberá:

- mantenerse separada del contenido de estudio
- no interferir con preguntas ni simulacros
- respetar accesibilidad y privacidad
- poder activarse o desactivarse mediante configuración
- cumplir las políticas publicitarias aplicables

## D-015 — Seguridad del repositorio

Estado: aceptada

No se almacenarán en Git:

- contraseñas
- tokens
- claves API
- certificados privados
- secretos
- credenciales de base de datos

Las configuraciones sensibles deberán mantenerse fuera del control de versiones

## D-016 — Flujo de desarrollo

Estado: aceptada

El trabajo se organizará mediante:

- GitHub Issues
- ramas de trabajo
- Pull Requests
- revisión antes de integrar cambios relevantes

`DECISIONS.md` será la referencia principal para las decisiones ya cerradas

## D-017 — Documentación operativa privada

Estado: aceptada

Los archivos utilizados para coordinar agentes de terminal y Spec Driven Development no se publicarán durante Alpha 1.0

Se mantendrán fuera del control de versiones:

- `AGENTS.md`
- especificaciones completas de trabajo
- documentación operativa interna relacionada con la ejecución asistida por IA

Antes del despliegue de la versión se podrán publicar ejemplos sanitizados con finalidad documental:

- `AGENTS.example.md`
- una o varias especificaciones SDD de ejemplo

Los ejemplos públicos deberán mostrar el método de trabajo sin revelar información interna innecesaria ni reproducir las especificaciones completas utilizadas durante el desarrollo

## Decisiones pendientes

- licencia del repositorio
- dominio definitivo
- proveedor de correo transaccional
- estrategia exacta de importación de preguntas
- modelo detallado de permisos del panel interno
- política definitiva de ramas