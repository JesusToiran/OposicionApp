package es.oposicionapp.c1tai.biblioteca;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import es.oposicionapp.c1tai.importacion.ImportacionCsvService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class PlanTaiService {
	private static final JsonMapper JSON = JsonMapper.builder().build();
	private final JdbcTemplate jdbc;
	private final Path recursos;
	public PlanTaiService(JdbcTemplate jdbc, @Value("${oposicionapp.plan.recursos:.local/plan/recursos}") String recursos) {
		this.jdbc=jdbc; this.recursos=Path.of(recursos);
	}

	@Transactional(readOnly = true)
	public JsonNode datos(String nombre) {
		if (!java.util.Set.of("questions.json","topics.json","generated_questions.json","official_exams.json","historical_exams.json","historical_mobile.json","materiales_estudio.json","documentary_audit.json").contains(nombre)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		JsonNode raw = json("data/" + nombre);
		if (!nombre.equals("questions.json")) return raw;
		Map<String, ObjectNode> source = new HashMap<>();
		for (JsonNode q : raw) source.put(q.path("id").asString(), (ObjectNode) q);
		var result = JSON.createArrayNode();
		jdbc.query("""
			select q.*,f.datos->>'id_banco' as banco,s.datos->'seccion'->>'kind' as clase
			from c1_tai.csv_pregunta q join c1_tai.csv_fila f on f.id=q.fila_id
			join c1_tai.csv_seccion s on s.id=q.seccion_id order by f.numero
			""", rs -> {
			ObjectNode original = source.get(rs.getString("banco"));
			if (original == null) throw new IllegalStateException("Pregunta sin metadatos originales.");
			ObjectNode q = original.deepCopy();
			q.put("question", rs.getString("enunciado"));
			q.set("options", JSON.readTree(rs.getString("opciones")));
			String key = rs.getString("clave");
			if (key == null) q.putNull("answer"); else q.put("answer", "ABCD".indexOf(key));
			if (rs.getString("clase") != null) q.put("kind", rs.getString("clase"));
			String status = rs.getString("estado_plantilla");
			boolean annulled = rs.getBoolean("anulada");
			if (annulled) { q.put("status", "ANULADA"); q.put("answer_status", "ANULADA"); }
			else if (status.toLowerCase().contains("provisional")) q.put("answer_status", "PROVISIONAL");
			else if (key == null) q.put("answer_status", "SIN_PLANTILLA");
			q.put("oposicionapp_id", rs.getString("id"));
			q.put("answer_source", status + " · trazabilidad conservada en OposicionApp");
			if (key != null && !original.path("answer").isNull() && original.path("answer").asInt() != "ABCD".indexOf(key)) {
				q.put("historical_warning", "La clave ha sido corregida por la plantilla. La explicación original permanece pendiente de revisión y puede referirse a la clave anterior. " + original.path("historical_warning").asString(""));
				q.put("explanation_verified", false);
			}
			result.add(q);
		});
		return result;
	}

	private JsonNode json(String path) {
		var values = jdbc.queryForList("select contenido_json::text from c1_tai.plan_tai_recurso where ruta=? and contenido_json is not null", String.class, path);
		if (values.size()!=1) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		return JSON.readTree(values.getFirst());
	}

	@Transactional(readOnly = true)
	public Recurso recurso(String path) throws IOException {
		boolean css = path.startsWith("lectura/") && path.endsWith(".html.css");
		String original = css ? path.substring(0,path.length()-4) : path;
		var records = jdbc.queryForList("select * from c1_tai.plan_tai_recurso where ruta=?", original);
		if (records.size()!=1) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		var row = records.getFirst();
		if (css) return new Recurso("text/css", ((String)row.get("css_lectura")).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		if (row.get("html_lectura") != null) return new Recurso("text/html", ((String)row.get("html_lectura")).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		Path root = recursos.toRealPath(), file = root.resolve((String)row.get("ruta")).normalize();
		if (!file.startsWith(root) || !file.toRealPath().startsWith(root) || Files.isSymbolicLink(file) || Files.size(file)>32*1024*1024) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		byte[] content=Files.readAllBytes(file);
		if (content.length!=((Number)row.get("bytes")).longValue() || !ImportacionCsvService.hash(content).equals(row.get("sha256"))) throw new ResponseStatusException(HttpStatus.CONFLICT);
		return new Recurso((String)row.get("tipo"), content);
	}

	@Transactional(readOnly = true)
	public Estado estado(String usuario) {
		var records=jdbc.queryForList("select revision,estado::text from study.plan_tai_estado where usuario=?", usuario);
		return records.isEmpty() ? new Estado(usuario,0,JSON.createObjectNode())
			: new Estado(usuario,((Number)records.getFirst().get("revision")).longValue(),JSON.readTree((String)records.getFirst().get("estado")));
	}

	@Transactional
	public long guardar(String usuario, JsonNode request) {
		if (request.size()!=3 || !request.path("revision").isIntegralNumber() || request.path("revision").asLong()<0
				|| !request.path("estado").isObject() || request.path("estado").toString().length()>8*1024*1024) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		UUID operation;
		try { operation=UUID.fromString(request.path("operacion").asString()); } catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
		String payload=request.path("estado").toString();
		String hash=ImportacionCsvService.hash(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		jdbc.update("insert into study.plan_tai_estado(usuario) values (?) on conflict(usuario) do nothing", usuario);
		Long revision=jdbc.queryForObject("select revision from study.plan_tai_estado where usuario=? for update", Long.class, usuario);
		var previous=jdbc.queryForList("select contenido_sha256,revision_resultado from study.plan_tai_operacion where usuario=? and id=?",usuario,operation);
		if (!previous.isEmpty()) {
			if (!hash.equals(previous.getFirst().get("contenido_sha256"))) throw new ResponseStatusException(HttpStatus.CONFLICT);
			return ((Number)previous.getFirst().get("revision_resultado")).longValue();
		}
		if (revision==null || revision!=request.path("revision").asLong()) throw new ResponseStatusException(HttpStatus.CONFLICT);
		long next=revision+1;
		jdbc.update("update study.plan_tai_estado set estado=?::jsonb,revision=?,actualizado_en=current_timestamp where usuario=? and revision=?",payload,next,usuario,revision);
		jdbc.update("insert into study.plan_tai_operacion(usuario,id,contenido_sha256,revision_resultado) values (?,?,?,?)",usuario,operation,hash,next);
		return next;
	}

	public record Recurso(String tipo,byte[] contenido) { }
	public record Estado(String usuario,long revision,JsonNode estado) { }
}
