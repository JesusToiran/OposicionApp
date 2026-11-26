package es.oposicionapp.c1tai.biblioteca;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import es.oposicionapp.c1tai.importacion.ImportacionCsvService;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(readOnly = true)
public class BibliotecaCsvService {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private final JdbcTemplate jdbc;
	private final Path anexos;

	public BibliotecaCsvService(JdbcTemplate jdbc, @Value("${oposicionapp.csv.anexos:.local/anexos}") String anexos) {
		this.jdbc = jdbc;
		this.anexos = Path.of(anexos);
	}

	public List<Seccion> secciones(UUID convocatoria) {
		return jdbc.query("""
			select s.*, (select count(*) from c1_tai.csv_pregunta q where q.seccion_id=s.id) as preguntas
			from c1_tai.csv_seccion s join c1_tai.ejercicio e on e.id=s.ejercicio_id
			join c1_tai.examen x on x.id=e.examen_id where x.convocatoria_id=?
			order by x.denominacion,e.denominacion,s.orden
			""", (rs, n) -> new Seccion(rs.getObject("id", UUID.class), rs.getObject("ejercicio_id", UUID.class),
				rs.getString("denominacion"), rs.getString("estado_plantilla"), rs.getString("contexto"), rs.getInt("preguntas")), convocatoria);
	}

	public Pagina pagina(UUID convocatoria, UUID seccionId, int posicion, UUID variante, String respuesta) {
		Seccion section = secciones(convocatoria).stream().filter(s -> s.id().equals(seccionId)).findFirst()
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		if (posicion < 1 || posicion > Math.max(1, section.preguntas()) || (respuesta != null && !respuesta.matches("[ABCD]"))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		}
		var anexos = jdbc.query("""
			select a.id, a.ruta from c1_tai.csv_seccion_anexo sa join c1_tai.csv_anexo a on a.id=sa.anexo_id
			where sa.seccion_id=? order by sa.orden
			""", (rs, n) -> new Anexo(rs.getObject(1, UUID.class), rs.getString(2)), seccionId);
		var extractions = jdbc.query("""
			select f.numero,x.documento,x.pagina,x.contenido,x.estado from c1_tai.csv_extraccion x
			join c1_tai.csv_fila f on f.id=x.fila_id where x.seccion_id=? order by x.pagina,f.numero
			""", (rs, n) -> new Extraccion(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getString(5)), seccionId);
		Integer templates = jdbc.queryForObject("select count(*) from c1_tai.csv_plantilla where seccion_id=?", Integer.class, seccionId);
		if (section.preguntas() == 0) {
			if (variante != null || respuesta != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
			return new Pagina(section, posicion, null, List.of(), List.of(), anexos, extractions, templates, null, null, false);
		}
		var base = jdbc.queryForMap("select q.*,f.numero as fila_csv from c1_tai.csv_pregunta q join c1_tai.csv_fila f on f.id=q.fila_id where q.seccion_id=? order by q.orden limit 1 offset ?", seccionId, posicion - 1);
		UUID qid = (UUID) base.get("id");
		var variants = jdbc.query("""
			select v.id,f.numero,f.datos->>'source_file_name' as archivo from c1_tai.csv_variante v
			join c1_tai.csv_fila f on f.id=v.fila_id where v.pregunta_id=? order by f.numero
			""", (rs, n) -> new Variante(rs.getObject(1, UUID.class), rs.getInt(2), rs.getString(3)), qid);
		String stem = (String) base.get("enunciado"), options = base.get("opciones").toString();
		int row = ((Number) base.get("fila_csv")).intValue();
		if (variante != null) {
			if (variants.stream().noneMatch(v -> v.id().equals(variante))) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
			var alt = jdbc.queryForMap("select v.*,f.numero as fila_csv from c1_tai.csv_variante v join c1_tai.csv_fila f on f.id=v.fila_id where v.id=? and v.pregunta_id=?", variante, qid);
			stem = (String) alt.get("enunciado"); options = alt.get("opciones").toString(); row = ((Number) alt.get("fila_csv")).intValue();
		}
		List<Opcion> alternatives = new ArrayList<>();
		var parsed = JSON.readTree(options);
		for (int i = 0; i < 4; i++) alternatives.add(new Opcion(String.valueOf((char) ('A' + i)), parsed.get(i).asString()));
		String key = (String) base.get("clave");
		boolean anulada = Boolean.TRUE.equals(base.get("anulada"));
		boolean gradable = key != null && !anulada && variante == null;
		String warning = variante != null ? "Transcripción alternativa pendiente de cotejo: práctica sin calificación."
				: anulada ? "Pregunta anulada: no se califica."
				: key == null ? "Sin plantilla oficial: práctica sin calificación."
				: ((String) base.get("estado_plantilla")).toLowerCase().contains("provisional")
						? "Plantilla provisional: corrección orientativa, pendiente de plantilla definitiva."
						: "Clave declarada por la fuente; no se ha realizado un nuevo cotejo visual independiente.";
		String feedback = respuesta == null ? null : !gradable ? "Respuesta seleccionada: " + respuesta + ". No se califica."
				: respuesta.equals(key) ? "Coincide con la clave declarada (" + key + ")." : "No coincide. Clave declarada: " + key + ".";
		return new Pagina(section, posicion, new Pregunta(qid, (String) base.get("identificador_origen"),
				((Number) base.get("numero")).intValue(), Boolean.TRUE.equals(base.get("reserva")), stem, row),
				alternatives, variants, anexos, extractions, templates, warning, feedback, gradable);
	}

	public byte[] anexo(UUID convocatoria, UUID id) throws IOException {
		var records = jdbc.queryForList("""
			select distinct a.* from c1_tai.csv_anexo a join c1_tai.csv_seccion_anexo sa on sa.anexo_id=a.id
			join c1_tai.csv_seccion s on s.id=sa.seccion_id join c1_tai.ejercicio e on e.id=s.ejercicio_id
			join c1_tai.examen x on x.id=e.examen_id where x.convocatoria_id=? and a.id=?
			""", convocatoria, id);
		if (records.size() != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		var descriptor = records.getFirst();
		Path root = anexos.toRealPath(), file = root.resolve((String) descriptor.get("ruta")).normalize();
		if (!file.startsWith(root) || Files.isSymbolicLink(file) || !file.toRealPath().startsWith(root)
				|| Files.size(file) > 16 * 1024 * 1024) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		byte[] bytes = Files.readAllBytes(file);
		if (bytes.length != ((Number) descriptor.get("bytes")).intValue() || !ImportacionCsvService.hash(bytes).equals(descriptor.get("sha256"))) {
			throw new ResponseStatusException(HttpStatus.CONFLICT);
		}
		return bytes;
	}

	public record Seccion(UUID id, UUID ejercicioId, String denominacion, String estadoPlantilla, String contexto, int preguntas) { }
	public record Pregunta(UUID id, String origen, int numero, boolean reserva, String enunciado, int filaCsv) { }
	public record Opcion(String letra, String texto) { }
	public record Variante(UUID id, int fila, String archivo) { }
	public record Anexo(UUID id, String ruta) { }
	public record Extraccion(int fila, String documento, int pagina, String contenido, String estado) { }
	public record Pagina(Seccion seccion, int posicion, Pregunta pregunta, List<Opcion> opciones,
			List<Variante> variantes, List<Anexo> anexos, List<Extraccion> extracciones, int plantillas,
			String aviso, String resultado, boolean calificable) { }
}
