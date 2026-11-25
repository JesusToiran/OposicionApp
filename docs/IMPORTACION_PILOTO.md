# Importación documental piloto

Importación local explícita mediante `oposicionapp.importacion.manifiesto` y
`oposicionapp.importacion.documentos`. No se ejecuta durante el arranque normal
ni dispone de endpoints HTTP. Después de importar cierra el contexto del proceso
de carga; no detiene los otros contenedores de la aplicación.

El manifiesto UTF-8 usa propiedades: `convocatoria.clave` (referencia de origen
estable), `convocatoria.nombre`, `convocatoria.anio=2025`, `indice.sha256` y
`documentos` (de 1 a 6). Por cada `documento.N`: `archivo`, `sha256`, `tipo`,
`examen`, `titulo`, `url` y `evidencia`. Los tipos admitidos son `CUESTIONARIO`
y `PLANTILLA_PROVISIONAL`; las modalidades, `ordinario` y `extraordinario`.
Solo se admiten referencias HTTPS de Sede INAP presentes en el material
facilitado. No se realizan conexiones externas para validarlas.

Los documentos se montan en modo de solo lectura. Se rechazan rutas con
directorios, enlaces simbólicos, archivos de más de 32 MiB, firmas no PDF y
hashes distintos del inventario. Se comprueban antes de escribir y antes de
confirmar la transacción. Los títulos y referencias se mantienen separados
de los binarios; no se importan preguntas ni respuestas individuales.

La carga utiliza el rol de aplicación, identificadores deterministas por
convocatoria y SHA-256 documental y una única transacción. Todos los valores
SQL están parametrizados; los identificadores de tablas y columnas son
constantes internas. Solo inserta registros nuevos: los existentes se comparan
y cualquier incompatibilidad cancela toda la carga. No sobrescribe ni elimina.
No hay cambios de esquema ni de autenticación.

La identidad de documento es su contenido, no el nombre del archivo. Su
referencia guarda el SHA-256 y la evidencia apunta al hash del índice y la fila
que lo identifica. El inventario privado relaciona esos hashes con las rutas
relativas y conserva las observaciones. Una correspondencia previa con otra
identidad interna requiere conciliación manual, nunca una fusión automática.

En este piloto todas las referencias quedan pendientes de corroboración,
aunque un índice auxiliar las describa como verificadas. No se activan enlaces
ni se inventa una referencia de resolución que no conste en los documentos.
El inventario, manifiesto y documentos no se incluyen en Git ni en la imagen.
