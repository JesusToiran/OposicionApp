package es.oposicionapp.c1tai.importacion;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ImportacionCsvService {

	private static final String SOURCE_SHA = "fda49a7e7c4442197f7ce76fdd4aa315717363d0f72436f898f34c05c34e8e51";
	private static final JsonMapper JSON = JsonMapper.builder().build();
	// Identificadores SQL fijos; ni las cabeceras ni el plan proporcionan SQL ejecutable.
	private static final List<Tabla> TABLAS = List.of(
		new Tabla("convocatoria", "id:uuid,denominacion:text,anio_referencia:integer,referencia_oficial:text"),
		new Tabla("examen", "id:uuid,convocatoria_id:uuid,denominacion:text"),
		new Tabla("ejercicio", "id:uuid,examen_id:uuid,denominacion:text"),
		new Tabla("documento_oficial", "id:uuid,identificacion_original:text,procedencia:text,tipo:text,referencia_origen:text,url_origen:text,verificado:boolean,evidencia_verificacion:text"),
		new Tabla("vinculo_documental", "id:uuid,documento_id:uuid,convocatoria_id:uuid,examen_id:uuid,ejercicio_id:uuid"),
		new Tabla("csv_fila", "id:uuid,lote_id:uuid,numero:integer,tipo:text,datos:jsonb,contenido_sha256:text"),
		new Tabla("csv_seccion", "id:uuid,ejercicio_id:uuid,examen_origen:text,codigo:text,orden:integer,denominacion:text,estado_plantilla:text,contexto:text,datos:jsonb"),
		new Tabla("csv_anexo", "id:uuid,ruta:text,sha256:text,bytes:integer,tipo:text"),
		new Tabla("csv_seccion_anexo", "id:uuid,seccion_id:uuid,anexo_id:uuid,orden:integer"),
		new Tabla("csv_pregunta", "id:uuid,seccion_id:uuid,fila_id:uuid,orden:integer,numero:integer,reserva:boolean,anulada:boolean,identificador_origen:text,enunciado:text,opciones:jsonb,clave:text,estado_plantilla:text,evidencia_plantilla:jsonb,datos:jsonb"),
		new Tabla("csv_procedencia", "id:uuid,fila_id:uuid,pregunta_id:uuid,relacion:text"),
		new Tabla("csv_variante", "id:uuid,fila_id:uuid,pregunta_id:uuid,enunciado:text,opciones:jsonb,clave_declarada:text,estado:text"),
		new Tabla("csv_plantilla", "id:uuid,fila_id:uuid,seccion_id:uuid,pregunta_id:uuid,orden:integer,numero:integer,reserva:boolean,clave_origen:text,estado:text"),
		new Tabla("csv_extraccion", "id:uuid,fila_id:uuid,seccion_id:uuid,documento:text,pagina:integer,contenido:text,estado:text"),
		new Tabla("csv_resolucion", "id:uuid,fila_id:uuid,campo:text,valor_original:text,valor_corregido:text,motivo:text,evidencia:jsonb")
	);
	private final JdbcTemplate jdbc;
	private final String sourceSha;

	@Autowired
	public ImportacionCsvService(JdbcTemplate jdbc) {
		this(jdbc, SOURCE_SHA);
	}

	// El contrato alternativo solo permite probar el importador con datos sintéticos.
	ImportacionCsvService(JdbcTemplate jdbc, String sourceSha) {
		this.jdbc = jdbc;
		this.sourceSha = sourceSha;
	}

	@Transactional(rollbackFor = Exception.class)
	public Map<String, Integer> importar(Path plan, Path csv, Path anexos) throws IOException {
		byte[] bytes = leer(plan, 64 * 1024 * 1024);
		JsonNode root = JSON.readTree(bytes);
		Map<Path, String> originales = validar(root, csv, anexos);
		originales.put(plan.toRealPath(), hash(bytes));
		jdbc.execute("select pg_advisory_xact_lock(849201)");
		Map<String, Integer> creados = new LinkedHashMap<>();
		var lote = JSON.createObjectNode();
		lote.put("id", root.path("lote_id").asString());
		lote.put("version", root.path("version").asString());
		lote.put("csv_sha256", sourceSha);
		lote.put("plan_sha256", hash(bytes));
		lote.set("cabeceras", root.path("cabeceras"));
		lote.put("filas", root.path("filas_csv").asInt());
		creados.put("csv_lote", insertar(new Tabla("csv_lote", "id:uuid,version:text,csv_sha256:text,plan_sha256:text,cabeceras:jsonb,filas:integer"), lote));
		for (Tabla tabla : TABLAS) {
			creados.put(tabla.nombre(), insertarLote(tabla, root.path("tablas").path(tabla.nombre())));
		}
		for (var original : originales.entrySet()) {
			if (!hash(leer(original.getKey(), 64 * 1024 * 1024)).equals(original.getValue())) {
				throw new IllegalStateException("Un original cambió durante la carga; se revierte el lote completo.");
			}
		}
		return creados;
	}

	private Map<Path, String> validar(JsonNode root, Path csv, Path anexos) throws IOException {
		if (!sourceSha.equals(root.path("csv_sha256").asString()) || root.path("filas_csv").asInt() != 5994
				|| !"Mi Plan TAI v2.0.1".equals(root.path("version").asString())) {
			throw new IllegalArgumentException("No es el lote completo autorizado v2.0.1.");
		}
		byte[] bytes = leer(csv, 8 * 1024 * 1024);
		if (!hash(bytes).equals(sourceSha)) throw new IllegalArgumentException("SHA-256 del CSV incompatible.");
		Map<Path, String> originales = new LinkedHashMap<>();
		originales.put(csv.toRealPath(), sourceSha);
		var records = csv(bytes);
		if (records.size() != 5995) throw new IllegalArgumentException("Número de filas CSV incompatible.");
		var header = records.getFirst();
		if (header.size() != 43 || root.path("cabeceras").size() != 43) throw new IllegalArgumentException("Cabeceras incompatibles.");
		for (int i = 0; i < header.size(); i++) {
			if (!header.get(i).equals(root.path("cabeceras").get(i).asString())) throw new IllegalArgumentException("Cabecera alterada.");
		}
		JsonNode tables = root.path("tablas");
		if (tables.size() != TABLAS.size()) throw new IllegalArgumentException("Tablas fuera del contrato.");
		Map<String, JsonNode> sourceRows = new LinkedHashMap<>();
		int expected = 2;
		for (JsonNode row : tables.path("csv_fila")) {
			int number = row.path("numero").asInt();
			if (number != expected++ || records.get(number - 1).size() != header.size()) throw new IllegalArgumentException("Orden CSV incompatible.");
			var data = row.path("datos");
			if (data.size() != header.size()) throw new IllegalArgumentException("Anchura CSV incompatible.");
			for (int i = 0; i < header.size(); i++) {
				if (!records.get(number - 1).get(i).equals(data.path(header.get(i)).asString())) {
					throw new IllegalArgumentException("CSV alterado en fila " + number + ", campo " + header.get(i));
				}
			}
			if (sourceRows.put(row.path("id").asString(), row) != null) throw new IllegalArgumentException("Procedencia repetida.");
		}
		if (expected != 5996) throw new IllegalArgumentException("El lote no da cuenta de todas las filas.");
		Map<String, JsonNode> questions = new LinkedHashMap<>();
		for (JsonNode q : tables.path("csv_pregunta")) {
			validarTexto(q, sourceRows.get(q.path("fila_id").asString()));
			questions.put(q.path("id").asString(), q);
		}
		Map<String, JsonNode> variants = new LinkedHashMap<>();
		for (JsonNode v : tables.path("csv_variante")) {
			validarTexto(v, sourceRows.get(v.path("fila_id").asString()));
			variants.put(v.path("fila_id").asString(), v);
		}
		var accounted = new java.util.HashSet<String>();
		for (JsonNode p : tables.path("csv_procedencia")) {
			String rowId = p.path("fila_id").asString();
			JsonNode representation = variants.getOrDefault(rowId, questions.get(p.path("pregunta_id").asString()));
			validarTexto(representation, sourceRows.get(rowId));
			if (!accounted.add(rowId)) throw new IllegalArgumentException("Fila de pregunta duplicada.");
		}
		for (String name : List.of("csv_plantilla", "csv_extraccion")) {
			for (JsonNode row : tables.path(name)) {
				if (!accounted.add(row.path("fila_id").asString())) throw new IllegalArgumentException("Fila de datos duplicada.");
			}
		}
		if (!accounted.equals(sourceRows.keySet())) throw new IllegalArgumentException("Existen filas sin destino.");
		Path dir = anexos.toRealPath();
		for (JsonNode asset : tables.path("csv_anexo")) {
			Path file = dir.resolve(asset.path("ruta").asString()).normalize();
			if (!file.startsWith(dir) || !file.toRealPath().startsWith(dir)) throw new IllegalArgumentException("Anexo fuera de alcance.");
			byte[] content = leer(file, 16 * 1024 * 1024);
			String fingerprint = hash(content);
			if (content.length != asset.path("bytes").asInt() || !fingerprint.equals(asset.path("sha256").asString())
					|| content.length < 12 || !new String(content, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) {
				throw new IllegalArgumentException("Anexo alterado.");
			}
			originales.put(file.toRealPath(), fingerprint);
		}
		return originales;
	}

	private static void validarTexto(JsonNode representation, JsonNode source) {
		if (representation == null || source == null) throw new IllegalArgumentException("Fila de pregunta sin representación.");
		JsonNode raw = source.path("datos");
		if (!representation.path("enunciado").asString().equals(raw.path("pregunta").asString()) || representation.path("opciones").size() != 4) {
			throw new IllegalArgumentException("Texto de pregunta alterado en fila " + source.path("numero").asInt());
		}
		for (int i = 0; i < 4; i++) {
			if (!representation.path("opciones").get(i).asString().equals(raw.path("opcion_" + (char) ('a' + i)).asString())) {
				throw new IllegalArgumentException("Opción alterada en fila " + source.path("numero").asInt());
			}
		}
	}

	private int insertar(Tabla tabla, JsonNode row) {
		List<Campo> fields = tabla.campos();
		if (row.size() != fields.size()) throw new IllegalArgumentException("Columnas incompatibles: " + tabla.nombre());
		Object[] args = fields.stream().map(field -> {
			JsonNode value = row.get(field.nombre());
			if (value == null) throw new IllegalArgumentException("Falta columna " + field.nombre());
			return value.isNull() ? null : field.tipo().equals("jsonb") ? value.toString() : value.asString();
		}).toArray();
		String names = fields.stream().map(Campo::nombre).collect(Collectors.joining(","));
		String placeholders = fields.stream().map(field -> "?::" + field.tipo()).collect(Collectors.joining(","));
		int created = jdbc.update("insert into c1_tai." + tabla.nombre() + " (" + names + ") values (" + placeholders + ") on conflict (id) do nothing", args);
		String conditions = fields.stream().map(field -> field.nombre() + " is not distinct from ?::" + field.tipo()).collect(Collectors.joining(" and "));
		Object[] indexedArgs = new Object[args.length + 1];
		indexedArgs[0] = row.path("id").asString();
		System.arraycopy(args, 0, indexedArgs, 1, args.length);
		Integer matching = jdbc.queryForObject("select count(*) from c1_tai." + tabla.nombre() + " where id=?::uuid and " + conditions, Integer.class, indexedArgs);
		if (matching == null || matching != 1) throw new IllegalStateException("Registro incompatible: " + tabla.nombre() + " " + row.path("id").asString());
		return created;
	}

	private int insertarLote(Tabla tabla, JsonNode rows) {
		List<Campo> fields = tabla.campos();
		List<Object[]> batch = new ArrayList<>();
		for (JsonNode row : rows) {
			if (row.size() != fields.size()) throw new IllegalArgumentException("Columnas incompatibles: " + tabla.nombre());
			batch.add(fields.stream().map(field -> {
				JsonNode value = row.get(field.nombre());
				if (value == null) throw new IllegalArgumentException("Falta columna " + field.nombre());
				return value.isNull() ? null : field.tipo().equals("jsonb") ? value.toString() : value.asString();
			}).toArray());
		}
		String names = fields.stream().map(Campo::nombre).collect(Collectors.joining(","));
		String placeholders = fields.stream().map(field -> "?::" + field.tipo()).collect(Collectors.joining(","));
		int total = 0;
		for (int count : jdbc.batchUpdate("insert into c1_tai." + tabla.nombre() + " (" + names + ") values (" + placeholders + ") on conflict (id) do nothing", batch)) {
			if (count < 0) throw new IllegalStateException("El controlador no confirmó el resultado del lote.");
			total += count;
		}
		Map<String, JsonNode> existing = new LinkedHashMap<>();
		jdbc.query("select id,to_jsonb(t)::text as contenido from c1_tai." + tabla.nombre() + " t", rs -> {
			existing.put(rs.getString("id"), JSON.readTree(rs.getString("contenido")));
		});
		for (JsonNode expected : rows) {
			if (!expected.equals(existing.get(expected.path("id").asString()))) {
				throw new IllegalStateException("Registro incompatible: " + tabla.nombre() + " " + expected.path("id").asString());
			}
		}
		return total;
	}

	static byte[] leer(Path path, int limit) throws IOException {
		if (Files.isSymbolicLink(path) || !Files.isRegularFile(path) || Files.size(path) > limit) throw new IllegalArgumentException("Archivo local no admitido.");
		try (var in = Files.newInputStream(path)) {
			byte[] bytes = in.readNBytes(limit + 1);
			if (bytes.length > limit) throw new IllegalArgumentException("Archivo demasiado grande.");
			return bytes;
		}
	}

	public static String hash(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (java.security.NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	// RFC 4180: se conservan las celdas multilínea y las comillas escapadas literalmente.
	static List<List<String>> csv(byte[] bytes) throws IOException {
		String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes)).toString();
		if (text.startsWith("\ufeff")) text = text.substring(1);
		List<List<String>> rows = new ArrayList<>();
		List<String> row = new ArrayList<>();
		StringBuilder cell = new StringBuilder();
		boolean quoted = false, closed = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (quoted) {
				if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
				else if (c == '"') { quoted = false; closed = true; }
				else cell.append(c);
			} else if (c == ',') { row.add(cell.toString()); cell.setLength(0); closed = false; }
			else if (c == '\r' || c == '\n') {
				if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
				row.add(cell.toString()); rows.add(List.copyOf(row)); row.clear(); cell.setLength(0); closed = false;
			} else if (c == '"' && cell.isEmpty() && !closed) quoted = true;
			else if (closed || c == '"') throw new IllegalArgumentException("CSV mal formado.");
			else cell.append(c);
		}
		if (quoted) throw new IllegalArgumentException("Celda CSV sin cierre.");
		if (!row.isEmpty() || !cell.isEmpty() || closed) { row.add(cell.toString()); rows.add(List.copyOf(row)); }
		return rows;
	}

	private record Campo(String nombre, String tipo) { }
	private record Tabla(String nombre, String contrato) {
		List<Campo> campos() {
			return java.util.Arrays.stream(contrato.split(",")).map(s -> s.split(":"))
					.map(pair -> new Campo(pair[0], pair[1])).toList();
		}
	}
}
