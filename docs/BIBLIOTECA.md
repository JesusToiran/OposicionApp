# Biblioteca de exámenes C1 TAI

La biblioteca comienza vacía. No incluye datos iniciales, fuentes inventadas,
preguntas ni operaciones web de mantenimiento.

| Ruta GET | Pantalla |
| --- | --- |
| `/c1-tai/examenes` | Convocatorias y estado vacío |
| `/c1-tai/convocatorias/{uuid}` | Exámenes, ejercicios y referencias de una convocatoria |

Ambas rutas requieren una cuenta activa y correo verificado. El formulario de
acceso utiliza correo y contraseña y redirige al plan TAI. HTTP Basic está
deshabilitado; la autorización deniega las rutas que no estén permitidas.

V2 añade cinco tablas a `c1_tai` sin alterar V1 ni incorporar registros. Las
relaciones documentales apuntan a un único nivel por vínculo y permiten reutilizar
la identidad de un documento. No hay borrado en cascada.

Una referencia pendiente se presenta como tal, sin enlace ejecutable. Para
presentar un enlace se requieren verificación con evidencia y una URL HTTP(S)
válida, sin credenciales incrustadas. La aplicación no descarga esas URL.
Los textos de la biblioteca se escapan con Thymeleaf. El plan TAI utiliza recursos
locales y consulta datos y progreso mediante rutas protegidas; sus lecturas tienen
CSP propia y no ejecutan los scripts de los documentos originales.

La suite `BibliotecaIntegrationTests` verifica funcionalidad, autorización,
CSRF, XSS, entradas inválidas, enlaces, cabeceras e integridad sobre PostgreSQL
temporal. La prueba de actualización V1 → V2 conserva datos e historial.
Los datos sintéticos se limitan a esos contenedores y transacciones de pruebas.

La [importación documental piloto](IMPORTACION_PILOTO.md) permite incorporar
convocatorias, exámenes, ejercicios y referencias documentales mediante una
ejecución local explícita, sin endpoints HTTP. No importa preguntas ni respuestas
individuales. Los documentos originales, inventarios y manifiestos permanecen
fuera de Git y de la imagen Docker.

V3 añade la importación del lote CSV autorizado, sus preguntas, variantes,
plantillas, anexos y trazabilidad. V4 incorpora los recursos del plan y el progreso
aislado por cuenta, con control de revisión e idempotencia. V5 incorpora cuentas,
tokens de un uso y auditoría. Véase [versión integrada](VERSION_INTEGRADA.md).
