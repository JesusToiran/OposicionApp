# Biblioteca de exámenes C1 TAI

La biblioteca comienza vacía. No incluye datos iniciales, fuentes inventadas,
preguntas ni operaciones web de mantenimiento.

| Ruta GET | Pantalla |
| --- | --- |
| `/c1-tai/examenes` | Convocatorias y estado vacío |
| `/c1-tai/convocatorias/{uuid}` | Exámenes, ejercicios y referencias de una convocatoria |

Ambas rutas requieren autenticación. Se mantienen HTTP Basic y el formulario
de acceso de Spring Security, que redirige a la biblioteca tras iniciar sesión;
el resto de rutas se deniega por defecto, salvo sus flujos de autenticación.

V2 añade cinco tablas a `c1_tai` sin alterar V1 ni incorporar registros. Las
relaciones documentales apuntan a un único nivel por vínculo y permiten reutilizar
la identidad de un documento. No hay borrado en cascada.

Una referencia pendiente se presenta como tal, sin enlace ejecutable. Para
presentar un enlace se requieren verificación con evidencia y una URL HTTP(S)
válida, sin credenciales incrustadas. La aplicación no descarga esas URL.
Los textos se escapan con Thymeleaf y la pantalla usa únicamente CSS local,
sin scripts, estilos inline ni nuevas herramientas frontend.

La suite `BibliotecaIntegrationTests` verifica funcionalidad, autorización,
CSRF, XSS, entradas inválidas, enlaces, cabeceras e integridad sobre PostgreSQL
temporal. La prueba de actualización V1 → V2 conserva datos e historial.
Los datos sintéticos se limitan a esos contenedores y transacciones de pruebas.
La importación de exámenes oficiales no forma parte de esta versión publicable.
