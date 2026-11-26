# Versión integrada de Alpha 1.0

El repositorio contiene biblioteca documental, importación CSV v2.0.1, plan TAI,
progreso persistido y cuentas. Los datos originales no se distribuyen con el código
ni se importan automáticamente. Una base nueva comienza vacía.

## Código y datos

Flyway conserva V1/V2 y añade V3 (CSV y trazabilidad), V4 (recursos y progreso del
plan) y V5 (cuentas, tokens y auditoría). No editar migraciones ya compartidas.
La importación CSV exige el SHA-256 del lote autorizado, sus 5.994 filas y 43
columnas. El plan exige 846 recursos con rutas y hashes verificados. Los runners
de importación se activan expresamente; no hay endpoints de carga.

Las rutas `/c1-tai/examenes`, `/c1-tai/convocatorias/{uuid}` y `/c1-tai/plan/`
exigen correo verificado. Las respuestas conservan sus estados de provisionalidad,
anulación y revisión; la presencia de una clave no acredita un nuevo cotejo oficial.
La práctica por unidades y el simulacro definidos para fases posteriores no se
consideran completados por esta integración.

## Cuentas y límites

El formulario `/login` usa `correo` y `clave`, CSRF y sesiones; no acepta HTTP
Basic. Una cuenta desactivada o con permisos cambiados pierde la sesión vigente.
La administración mínima está en `/admin/usuarios`. No existe registro público.

La primera cuenta se prepara en una consola interactiva mediante
`oposicionapp.auth.preparar-propietario=true` y
`oposicionapp.auth.correo-propietario`, con contraseña introducida sin eco. Solo
actúa si no hay cuentas y nunca marca el correo como verificado. No habilitar
esa opción en el arranque habitual.

La interfaz `CorreoAcceso.Enviador` aún no tiene adaptador de producción. Hasta
configurarlo, no se envían invitaciones, verificaciones ni recuperaciones, y la
primera cuenta no puede estudiar sin verificar su correo. La aplicación muestra
esa limitación y no finge envíos correctos. No se incluyen credenciales ni cuentas
iniciales en Git. La incorporación del transporte de correo requiere otra tarea.

## Ejecución y verificación

Producción exige HTTPS, origen público explícito y cookies `__Host-` seguras.
En Docker, Java escucha en la red del contenedor; Compose publica exclusivamente
`127.0.0.1:8080` del anfitrión y PostgreSQL permanece interno. El perfil `revision`
se limita a revisión local y es incompatible con producción.

```bash
docker build -t oposicionapp:local .
./mvnw -B -ntp -Doposicionapp.test.image=oposicionapp:local verify
python3 scripts/verify-production.py --configuration-only
python3 scripts/verify-production.py --image oposicionapp:local
```

Las pruebas generan cuentas y corpus sintéticos, usan bases nuevas y verifican
rollback, idempotencia, revisión de respuestas, aislamiento del progreso y control
de acceso. El contrato de importación de producción conserva su fingerprint fijo;
el contrato sintético solo se construye desde el código de pruebas. La verificación
de producción usa un proyecto Docker aleatorio y no debe ejecutarse en el VPS.

La [guía de despliegue](DEPLOYMENT.md) documenta la configuración candidata y el
rollback. El envío de correo, el análisis actualizado de CVE y la validación de la
configuración real del servidor siguen pendientes antes de habilitar producción.
