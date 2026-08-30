# Contribuir en OposicionApp

Este documento define cómo trabajaremos durante Alpha 1.0

El objetivo de esta fase es construir una primera versión funcional en local, manteniendo el repositorio ordenado y evitando introducir procesos innecesarios

## Alcance de Alpha 1.0

Durante esta fase se trabajará principalmente en:

- estructura inicial del proyecto
- configuración de Spring Boot
- conexión con PostgreSQL
- migraciones con Flyway
- autenticación básica
- verificación de correo
- recuperación de contraseña
- estructura inicial de C1 TAI
- preguntas oficiales
- modo práctica
- modo simulacro
- progreso básico
- panel interno mínimo
- pruebas automatizadas básicas

No se trabajará todavía en despliegue público, acceso por invitación ni registro público abierto

## Flujo de trabajo

La rama `main` debe mantenerse estable

Cada cambio se realizará en una rama independiente

Formato recomendado:

```text
feature/nombre-corto
fix/nombre-corto
docs/nombre-corto
refactor/nombre-corto
test/nombre-corto
```

Ejemplos:

```text
feature/autenticacion
feature/banco-preguntas
fix/validacion-email
docs/modelo-datos
```

## Antes de empezar una tarea

Comprobar que existe una Issue o una tarea claramente definida

Actualizar la rama local:

```bash
git switch main
git pull
```

Crear la nueva rama:

```bash
git switch -c feature/nombre-tarea
```

## Commits

Los commits deben ser pequeños y representar un cambio coherente

Formato recomendado:

```text
tipo: descripción breve
```

Ejemplos:

```text
feat: añade entidad de usuario
fix: corrige validación del correo
docs: actualiza decisiones de arquitectura
test: añade pruebas de autenticación
refactor: simplifica servicio de preguntas
```

Tipos habituales:

* `feat` nueva funcionalidad
* `fix` corrección de errores
* `docs` documentación
* `test` pruebas
* `refactor` cambios internos sin modificar comportamiento
* `chore` configuración o mantenimiento

## Pull Requests

Antes de integrar una rama en `main` se utilizará Pull Request

La Pull Request debe indicar:

* qué cambia
* qué Issue resuelve
* cómo se ha probado
* si modifica alguna decisión técnica existente

Si el cambio introduce una decisión relevante de arquitectura o producto deberá actualizarse `DECISIONS.md`

## Revisión

Durante Alpha 1.0 la revisión será sencilla

Antes de hacer merge deberá comprobarse:

* el proyecto compila
* las pruebas existentes pasan
* no se han añadido secretos
* no se han añadido archivos generados innecesarios
* el cambio cumple el alcance de la Issue
* la documentación afectada está actualizada

## Seguridad

Nunca se subirán al repositorio:

* contraseñas
* tokens
* claves API
* secretos
* certificados privados
* credenciales de base de datos
* archivos `.env` reales

Cuando sea necesario documentar variables de entorno se utilizará `.env.example` sin valores sensibles

## Dependencias

No se añadirá una nueva librería sin una necesidad concreta

Antes de incorporar una dependencia se comprobará:

* que resuelve un problema real
* que tiene mantenimiento activo
* que su licencia es compatible con el proyecto
* que no existe una solución sencilla con las herramientas ya utilizadas

## Definición de terminado

Una tarea de Alpha 1.0 podrá considerarse terminada cuando:

* cumple los requisitos definidos
* compila correctamente
* tiene las pruebas necesarias
* no introduce errores conocidos
* no contiene secretos
* la documentación relevante está actualizada
* está integrada correctamente en `main`

## Fuera de alcance

Durante Alpha 1.0 no se introducirán soluciones pensadas únicamente para fases posteriores salvo que sean necesarias para evitar una limitación arquitectónica importante

Se priorizará siempre la solución más sencilla que permita avanzar sin comprometer la evolución del proyecto
