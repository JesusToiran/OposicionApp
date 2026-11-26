package es.oposicionapp.c1tai.importacion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Conserva volumen, trazabilidad y casos de revisión sin publicar el corpus privado. */
@TestConfiguration(proxyBeanMethods = false)
public class CargaSintetica {
	private static final JsonMapper JSON = JsonMapper.builder().build();
	public static final Path BASE = preparar();
	public static final Path PLAN = BASE.resolve("carga/plan.json");
	public static final Path CSV = BASE.resolve("fuente/maestro.csv");
	public static final Path ASSETS = BASE.resolve("carga/anexos");

	@Bean @Primary
	ImportacionCsvService importadorSintetico(JdbcTemplate jdbc) throws IOException {
		return new ImportacionCsvService(jdbc, ImportacionCsvService.hash(Files.readAllBytes(CSV)));
	}

	private static String id(String name, int number) {
		return UUID.nameUUIDFromBytes((name + ":" + number).getBytes(StandardCharsets.UTF_8)).toString();
	}

	private static Map<String, Object> row(Object... pairs) {
		Map<String, Object> result = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
		return result;
	}

	private static void write(Path path, byte[] bytes) throws IOException {
		Files.createDirectories(path.getParent());
		Files.write(path, bytes);
	}

	private static Path preparar() {
		try {
			Path base = Path.of("target", "fixtures", UUID.randomUUID().toString()).toAbsolutePath();
			Map<String, List<Map<String, Object>>> tables = new LinkedHashMap<>();
			for (String name : List.of("convocatoria", "examen", "ejercicio", "documento_oficial", "vinculo_documental",
					"csv_fila", "csv_seccion", "csv_anexo", "csv_seccion_anexo", "csv_pregunta", "csv_procedencia",
					"csv_variante", "csv_plantilla", "csv_extraccion", "csv_resolucion")) tables.put(name, new ArrayList<>());
			for (int i = 0; i < 17; i++) tables.get("convocatoria").add(row("id", id("c", i), "denominacion", "Convocatoria sintética " + i, "anio_referencia", 2000 + i, "referencia_oficial", "Prueba"));
			for (int i = 0; i < 20; i++) tables.get("examen").add(row("id", id("e", i), "convocatoria_id", id("c", i % 17), "denominacion", "Examen sintético " + i));
			for (int i = 0; i < 25; i++) tables.get("ejercicio").add(row("id", id("ej", i), "examen_id", id("e", i % 20), "denominacion", "Ejercicio " + i));
			for (int i = 0; i < 63; i++) tables.get("csv_seccion").add(row("id", id("s", i), "ejercicio_id", id("ej", i % 25), "examen_origen", "Sintético " + i, "codigo", "S" + i, "orden", i / 25 + 1, "denominacion", "Sección " + i, "estado_plantilla", "definitiva", "contexto", "Contexto sintético", "datos", row("seccion", row("kind", "teoria"))));
			List<String> headers = new ArrayList<>(List.of("pregunta", "opcion_a", "opcion_b", "opcion_c", "opcion_d", "id_banco", "source_file_name"));
			while (headers.size() < 43) headers.add("campo_" + headers.size());
			StringBuilder csv = new StringBuilder(String.join(",", headers)).append('\n');
			String lote = id("lote", 0);
			List<String> options = List.of("Opción A", "Opción B", "Opción C", "Opción D");
			for (int i = 0; i < 5994; i++) {
				String question = "Pregunta sintética " + i;
				Map<String, Object> data = new LinkedHashMap<>();
				for (String header : headers) data.put(header, "");
				data.put("pregunta", question);
				for (int j = 0; j < 4; j++) data.put("opcion_" + (char) ('a' + j), options.get(j));
				data.put("id_banco", "q" + i); data.put("source_file_name", "sintetico.csv");
				csv.append(String.join(",", data.values().stream().map(Object::toString).toList())).append('\n');
				String type = i < 2593 ? "preguntas" : i < 4993 ? "plantilla_respuestas" : "extraccion_pendiente_revision";
				tables.get("csv_fila").add(row("id", id("f", i), "lote_id", lote, "numero", i + 2, "tipo", type, "datos", data, "contenido_sha256", ImportacionCsvService.hash(JSON.writeValueAsBytes(data))));
				int section = i % 63;
				if (i < 2367) {
					boolean annulled = i == 67;
					String key = i < 66 || annulled ? null : "A";
					tables.get("csv_pregunta").add(row("id", id("q", i), "seccion_id", id("s", section), "fila_id", id("f", i), "orden", i / 63 + 1, "numero", i / 63 + 1, "reserva", false, "anulada", annulled ? Boolean.TRUE : i < 66 ? null : Boolean.FALSE, "identificador_origen", "q" + i, "enunciado", question, "opciones", options, "clave", key, "estado_plantilla", i < 66 ? "sin plantilla" : i == 66 ? "provisional" : "definitiva", "evidencia_plantilla", row(), "datos", row()));
				}
				if (i < 2593) {
					int q = i < 2367 ? i : i - 2367;
					tables.get("csv_procedencia").add(row("id", id("p", i), "fila_id", id("f", i), "pregunta_id", id("q", q), "relacion", "sintética"));
					if (i >= 2367) tables.get("csv_variante").add(row("id", id("v", i), "fila_id", id("f", i), "pregunta_id", id("q", q), "enunciado", question, "opciones", options, "clave_declarada", "B", "estado", "pendiente"));
				} else if (i < 4993) {
					tables.get("csv_plantilla").add(row("id", id("pl", i), "fila_id", id("f", i), "seccion_id", id("s", section), "pregunta_id", null, "orden", i + 1, "numero", i + 1, "reserva", false, "clave_origen", "A", "estado", "sintética"));
				} else tables.get("csv_extraccion").add(row("id", id("x", i), "fila_id", id("f", i), "seccion_id", id("s", section), "documento", "Sintético", "pagina", 1, "contenido", "Extracción de prueba", "estado", "pendiente"));
				if (i < 177) tables.get("csv_resolucion").add(row("id", id("r", i), "fila_id", id("f", i), "campo", i < 153 ? "source_year" : "vinculo_plantilla", "valor_original", "Prueba", "valor_corregido", "Sintético", "motivo", "Fixture", "evidencia", row()));
			}
			byte[] webp = "RIFF0000WEBPfixture".getBytes(StandardCharsets.US_ASCII);
			for (int i = 0; i < 400; i++) {
				String path = "historico/prueba-" + i + ".webp";
				write(base.resolve("carga/anexos/" + path), webp);
				tables.get("csv_anexo").add(row("id", id("a", i), "ruta", path, "sha256", ImportacionCsvService.hash(webp), "bytes", webp.length, "tipo", "image/webp"));
				tables.get("csv_seccion_anexo").add(row("id", id("sa", i), "seccion_id", id("s", i % 63), "anexo_id", id("a", i), "orden", i / 63 + 1));
			}
			byte[] csvBytes = csv.toString().getBytes(StandardCharsets.UTF_8);
			write(base.resolve("fuente/maestro.csv"), csvBytes);
			write(base.resolve("carga/plan.json"), JSON.writeValueAsBytes(row("lote_id", lote, "version", "Mi Plan TAI v2.0.1", "csv_sha256", ImportacionCsvService.hash(csvBytes), "filas_csv", 5994, "cabeceras", headers, "tablas", tables)));
			prepararRecursos(base);
			return base;
		} catch (IOException ex) { throw new IllegalStateException("No se pudo crear el corpus sintético", ex); }
	}

	private static void prepararRecursos(Path base) throws IOException {
		List<Map<String, Object>> manifest = new ArrayList<>();
		for (String name : List.of("questions.json", "generated_questions.json", "historical_exams.json", "official_exams.json", "materiales_estudio.json", "topics.json", "historical_mobile.json", "documentary_audit.json")) {
			int count = switch (name) { case "questions.json" -> 2367; case "generated_questions.json" -> 480; case "historical_exams.json" -> 20; case "official_exams.json" -> 2; case "materiales_estudio.json" -> 33; default -> 1; };
			List<Map<String, Object>> content = new ArrayList<>();
			for (int i = 0; i < count; i++) content.add(row("id", "q" + i, "answer", 0, "explanation", "Explicación sintética " + i, "options", List.of("A", "B", "C", "D")));
			resource(base, manifest, "data/" + name, "application/json", JSON.writeValueAsBytes(content), content, null, null);
		}
		for (int i = 0; i < 33; i++) {
			String path = "lectura/tema-" + i + ".html";
			String html = "<!doctype html><html lang=\"es\"><head><link rel=\"stylesheet\" href=\"tema-" + i + ".html.css\"></head><body>Lectura sintética</body></html>";
			resource(base, manifest, path, "text/html", html.getBytes(StandardCharsets.UTF_8), null, html, "body { color: black; }");
		}
		while (manifest.size() < 846) resource(base, manifest, "lectura/imagen-" + manifest.size() + ".webp", "image/webp", "RIFF0000WEBPfixture".getBytes(StandardCharsets.US_ASCII), null, null, null);
		write(base.resolve("integracion-plan-tai/recursos.json"), JSON.writeValueAsBytes(manifest));
	}

	private static void resource(Path base, List<Map<String, Object>> manifest, String path, String type, byte[] bytes, Object json, String html, String css) throws IOException {
		write(base.resolve("integracion-plan-tai/original/" + path), bytes);
		manifest.add(row("ruta", path, "sha256", ImportacionCsvService.hash(bytes), "bytes", bytes.length, "tipo", type, "contenido_json", json, "html_lectura", html, "css_lectura", css));
	}
}
