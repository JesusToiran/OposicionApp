package es.oposicionapp.c1tai.importacion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportacionPilotoService {

	private final JdbcTemplate jdbc;

	public ImportacionPilotoService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional(rollbackFor = Exception.class)
	public Resultado importar(PilotoManifest manifest, Path directorio) throws IOException {
		manifest.verificarArchivos(directorio);
		UUID convocatoria = identidad("convocatoria:" + manifest.clave());
		Integer equivalentes = jdbc.queryForObject("select count(*) from c1_tai.convocatoria where referencia_oficial=? and id<>?",
				Integer.class, manifest.clave(), convocatoria);
		if (equivalentes != 0) throw new IllegalStateException("Existe una convocatoria equivalente; requiere conciliación manual.");
		int convocatorias = insertar("convocatoria", List.of("id", "denominacion", "anio_referencia", "referencia_oficial"),
				List.of(convocatoria, manifest.nombre(), manifest.anio(), manifest.clave()));
		int examenes = 0, ejercicios = 0, documentos = 0, vinculos = 0;
		for (String modalidad : List.of("ordinario", "extraordinario")) {
			var archivos = manifest.documentos().stream().filter(d -> d.examen().equals(modalidad)).toList();
			if (archivos.isEmpty()) continue;
			UUID examen = identidad(convocatoria + ":" + modalidad);
			UUID ejercicio = identidad(examen + ":unico");
			examenes += insertar("examen", List.of("id", "convocatoria_id", "denominacion"),
					List.of(examen, convocatoria, modalidad.equals("ordinario") ? "Examen ordinario — modelos A y B" : "Llamamiento extraordinario"));
			ejercicios += insertar("ejercicio", List.of("id", "examen_id", "denominacion"), List.of(ejercicio, examen, "Ejercicio único"));
			for (var documento : archivos) {
				UUID id = identidad("documento:sha256:" + documento.sha256());
				String referencia = "SHA-256:" + documento.sha256();
				Integer otros = jdbc.queryForObject("select count(*) from c1_tai.documento_oficial where referencia_origen=? and id<>?",
						Integer.class, referencia, id);
				if (otros != 0) throw new IllegalStateException("Existe una identidad documental equivalente; requiere conciliación manual.");
				documentos += insertar("documento_oficial", List.of("id", "identificacion_original", "procedencia", "tipo",
						"referencia_origen", "url_origen", "verificado", "evidencia_verificacion"), List.of(id, documento.titulo(),
						"Documentación facilitada por el usuario; procedencia declarada INAP, pendiente de corroboración",
						documento.tipo(), referencia, documento.url(), false,
						documento.evidencia() + " Índice SHA-256:" + manifest.indiceSha256()));
				vinculos += insertar("vinculo_documental", List.of("id", "documento_id", "ejercicio_id"),
						List.of(identidad(id + ":" + ejercicio), id, ejercicio));
			}
		}
		// Detectar una modificación de originales durante la carga antes de confirmar la transacción.
		manifest.verificarArchivos(directorio);
		return new Resultado(convocatorias, examenes, ejercicios, documentos, vinculos);
	}

	// Tabla y columnas son constantes internas; todos los valores documentales se parametrizan.
	private int insertar(String tabla, List<String> columnas, List<Object> valores) {
		String campos = String.join(",", columnas);
		int creados = jdbc.update("insert into c1_tai." + tabla + " (" + campos + ") values ("
				+ String.join(",", java.util.Collections.nCopies(columnas.size(), "?")) + ") on conflict (id) do nothing", valores.toArray());
		var existente = jdbc.queryForMap("select " + campos + " from c1_tai." + tabla + " where id=?", valores.getFirst());
		List<Object> comparacion = new ArrayList<>();
		columnas.forEach(c -> comparacion.add(existente.get(c)));
		if (!comparacion.equals(valores)) throw new IllegalStateException("Registro existente incompatible: carga cancelada sin sobrescritura.");
		return creados;
	}

	public static UUID identidad(String clave) {
		return UUID.nameUUIDFromBytes(("oposicionapp:sdd002:" + clave).getBytes(StandardCharsets.UTF_8));
	}

	public record Resultado(int convocatorias, int examenes, int ejercicios, int documentos, int vinculos) { }
}
