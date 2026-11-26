package es.oposicionapp.c1tai.importacion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class PlanTaiImportacionService {
	private final JdbcTemplate jdbc;
	private static final JsonMapper JSON = JsonMapper.builder().build();
	public PlanTaiImportacionService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

	@Transactional(rollbackFor = Exception.class)
	public int importar(Path manifiesto, Path directorio) throws IOException {
		byte[] plan = ImportacionCsvService.leer(manifiesto, 64 * 1024 * 1024);
		JsonNode rows = JSON.readTree(plan);
		if (!rows.isArray() || rows.size() != 846) throw new IllegalArgumentException("Inventario de Plan TAI incompleto.");
		Path root = directorio.toRealPath();
		Map<Path, String> originals = new LinkedHashMap<>();
		List<Object[]> batch = new ArrayList<>();
		for (JsonNode row : rows) {
			String path = row.path("ruta").asString();
			Path file = root.resolve(path).normalize();
			if (path.contains("..") || !file.startsWith(root) || !file.toRealPath().startsWith(root)) throw new IllegalArgumentException("Recurso fuera de alcance.");
			byte[] data = ImportacionCsvService.leer(file, 32 * 1024 * 1024);
			String hash = ImportacionCsvService.hash(data);
			if (!hash.equals(row.path("sha256").asString()) || data.length != row.path("bytes").asLong()) throw new IllegalArgumentException("Recurso original alterado.");
			originals.put(file, hash);
			batch.add(new Object[] {path, hash, data.length, row.path("tipo").asString(),
					row.path("contenido_json").isNull() ? null : row.path("contenido_json").toString(),
					row.path("html_lectura").isNull() ? null : row.path("html_lectura").asString(),
					row.path("css_lectura").isNull() ? null : row.path("css_lectura").asString()});
		}
		jdbc.execute("select pg_advisory_xact_lock(849202)");
		int total = 0;
		for (int n : jdbc.batchUpdate("insert into c1_tai.plan_tai_recurso(ruta,sha256,bytes,tipo,contenido_json,html_lectura,css_lectura) values (?,?,?,?,?::jsonb,?,?) on conflict(ruta) do nothing", batch)) {
			if (n < 0) throw new IllegalStateException("Resultado de lote sin confirmar.");
			total += n;
		}
		Map<String, JsonNode> stored = new LinkedHashMap<>();
		jdbc.query("select ruta,to_jsonb(r)::text as datos from c1_tai.plan_tai_recurso r", rs -> { stored.put(rs.getString(1), JSON.readTree(rs.getString(2))); });
		for (JsonNode row : rows) if (!row.equals(stored.get(row.path("ruta").asString()))) throw new IllegalStateException("Recurso existente incompatible: " + row.path("ruta").asString());
		for (var entry : originals.entrySet()) if (!ImportacionCsvService.hash(Files.readAllBytes(entry.getKey())).equals(entry.getValue())) throw new IllegalStateException("Un original cambió durante la carga.");
		if (!ImportacionCsvService.hash(Files.readAllBytes(manifiesto)).equals(ImportacionCsvService.hash(plan))) throw new IllegalStateException("El inventario cambió durante la carga.");
		return total;
	}
}
