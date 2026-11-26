package es.oposicionapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static es.oposicionapp.TestAccounts.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import es.oposicionapp.c1tai.biblioteca.BibliotecaService;
import es.oposicionapp.c1tai.importacion.PilotoManifest;
import es.oposicionapp.c1tai.importacion.ImportacionPilotoService;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ImportacionPilotoIntegrationTests {

	private static final String DETALLE = "/c1-tai/convocatorias/";
	private static final String APP_PASSWORD = UUID.randomUUID().toString();
	private static final String MIGRATOR_PASSWORD = UUID.randomUUID().toString();

	@Container
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		postgres.start();
		OposicionAppApplicationTests.bootstrapRoles(postgres);
		OposicionAppApplicationTests.setRolePasswords(postgres, APP_PASSWORD, MIGRATOR_PASSWORD);
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "oposicionapp_app");
		registry.add("spring.datasource.password", () -> APP_PASSWORD);
		registry.add("spring.flyway.user", () -> "oposicionapp_migrator");
		registry.add("spring.flyway.password", () -> MIGRATOR_PASSWORD);
	}

	@Autowired WebApplicationContext context;
	@Autowired JdbcTemplate jdbc;
	@Autowired BibliotecaService biblioteca;
	@Autowired ImportacionPilotoService importacion;
	private MockMvc mvc;

	@Test
	void pilotoIdempotenteConservaAnterioresYSeMuestraAutenticado(@TempDir Path directorio) throws Exception {
		var manifiesto = pilotoSintetico(directorio);
		UUID anterior = convocatoria("Registro anterior", 2024);
		var primera = importacion.importar(manifiesto, directorio);
		assertThat(primera).isEqualTo(new ImportacionPilotoService.Resultado(1, 2, 2, 6, 6));
		assertThat(importacion.importar(manifiesto, directorio)).isEqualTo(new ImportacionPilotoService.Resultado(0, 0, 0, 0, 0));
		assertThat(jdbc.queryForObject("select denominacion from c1_tai.convocatoria where id=?", String.class, anterior))
				.isEqualTo("Registro anterior");
		UUID id = ImportacionPilotoService.identidad("convocatoria:" + manifiesto.clave());
		var detalle = biblioteca.obtenerConvocatoria(id).orElseThrow();
		assertThat(detalle.examenes()).hasSize(2);
		assertThat(detalle.examenes()).allSatisfy(e -> assertThat(e.ejercicios()).hasSize(1));
		assertThat(detalle.examenes().stream().flatMap(e -> e.ejercicios().stream()).flatMap(e -> e.documentos().stream()))
				.hasSize(6).allSatisfy(d -> { assertThat(d.verificado()).isFalse(); assertThat(d.enlace()).isNull(); });
		String html = mvc.perform(get(DETALLE + id).with(user("lector"))).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(html).contains("Piloto sintético", "Pendiente de verificación", "Ordinario 1", "Extraordinario 6")
				.doesNotContain(directorio.toString(), "href=\"https://sede.inap.gob.es");
		assertThat(jdbc.queryForObject("select count(*) from c1_tai.documento_oficial where verificado=true", Integer.class)).isZero();
	}

	@Test
	void pilotoRechazaCambiosEnOriginalesAntesDeEscribir(@TempDir Path directorio) throws Exception {
		var manifiesto = pilotoSintetico(directorio);
		Files.writeString(directorio.resolve("documento1.pdf"), "%PDF-1.7 modificado");
		assertThatThrownBy(() -> importacion.importar(manifiesto, directorio)).isInstanceOf(IllegalArgumentException.class);
		assertThat(biblioteca.listarConvocatorias()).isEmpty();
	}

	@Test
	void pilotoNoSobrescribeRegistroIncompatible(@TempDir Path directorio) throws Exception {
		var manifiesto = pilotoSintetico(directorio);
		UUID id = ImportacionPilotoService.identidad("convocatoria:" + manifiesto.clave());
		jdbc.update("insert into c1_tai.convocatoria(id,denominacion,anio_referencia) values (?,?,?)", id, "Conservar exactamente", 2025);
		assertThatThrownBy(() -> importacion.importar(manifiesto, directorio)).isInstanceOf(IllegalStateException.class);
		assertThat(jdbc.queryForObject("select denominacion from c1_tai.convocatoria where id=?", String.class, id))
				.isEqualTo("Conservar exactamente");
	}

	@Test
	void pilotoDetectaEquivalenciaConIdentificadorAnterior(@TempDir Path directorio) throws Exception {
		var manifiesto = pilotoSintetico(directorio);
		UUID id = convocatoria("Convocatoria previa", 2025);
		jdbc.update("update c1_tai.convocatoria set referencia_oficial=? where id=?", manifiesto.clave(), id);
		assertThatThrownBy(() -> importacion.importar(manifiesto, directorio)).isInstanceOf(IllegalStateException.class);
		assertThat(biblioteca.listarConvocatorias()).hasSize(1);
	}

	@Test
	@Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
	void pilotoRevierteTodaLaCargaAnteConflictoDocumental(@TempDir Path directorio) throws Exception {
		var manifiesto = pilotoSintetico(directorio);
		UUID previo = documento("Documento previo que debe conservarse", null, false);
		jdbc.update("update c1_tai.documento_oficial set referencia_origen=? where id=?",
				"SHA-256:" + manifiesto.documentos().getFirst().sha256(), previo);
		try {
			assertThatThrownBy(() -> importacion.importar(manifiesto, directorio)).isInstanceOf(IllegalStateException.class);
			assertThat(biblioteca.listarConvocatorias()).isEmpty();
			assertThat(jdbc.queryForObject("select count(*) from c1_tai.examen", Integer.class)).isZero();
			assertThat(jdbc.queryForObject("select identificacion_original from c1_tai.documento_oficial where id=?", String.class, previo))
					.isEqualTo("Documento previo que debe conservarse");
		} finally {
			// Único registro sintético confirmado para esta prueba, en PostgreSQL desechable.
			jdbc.update("delete from c1_tai.documento_oficial where id=?", previo);
		}
	}

	@Test
	void pilotoRechazaFormatoPorFirmaNoPorExtension(@TempDir Path directorio) throws Exception {
		var manifiesto = pilotoSintetico(directorio);
		byte[] bytes = "Documento que no es PDF".getBytes(StandardCharsets.UTF_8);
		Files.write(directorio.resolve("falso.pdf"), bytes);
		var original = manifiesto.documentos().getFirst();
		var falso = new PilotoManifest.Documento("falso.pdf", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
				original.tipo(), original.examen(), original.titulo(), original.url(), original.evidencia());
		var invalido = new PilotoManifest(manifiesto.clave(), manifiesto.nombre(), 2025, manifiesto.indiceSha256(), List.of(falso));
		assertThatThrownBy(() -> importacion.importar(invalido, directorio)).isInstanceOf(IllegalArgumentException.class);
		assertThat(biblioteca.listarConvocatorias()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {"../fuera.pdf", "C:\\fuera.pdf", "/fuera.pdf", "sub/archivo.pdf"})
	void pilotoRechazaRutasFueraDelDirectorio(String archivo) {
		assertThatThrownBy(() -> new PilotoManifest.Documento(archivo, "0".repeat(64), "CUESTIONARIO", "ordinario", "Prueba",
				"https://sede.inap.gob.es/prueba.pdf", "Evidencia sintética")).isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"javascript:alert(1)", "data:text/html,prueba", "https://sede.inap.gob.es@otro.invalid/f.pdf"})
	void pilotoRechazaDestinosNoAdmitidos(String url) {
		assertThatThrownBy(() -> new PilotoManifest.Documento("prueba.pdf", "0".repeat(64), "CUESTIONARIO", "ordinario", "Prueba", url,
				"Evidencia sintética")).isInstanceOf(IllegalArgumentException.class);
	}

	private PilotoManifest pilotoSintetico(Path directorio) throws Exception {
		List<PilotoManifest.Documento> documentos = new ArrayList<>();
		for (int i = 1; i <= 6; i++) {
			String archivo = "documento" + i + ".pdf";
			byte[] bytes = ("%PDF-1.7\n% Fixture aislado " + i + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII);
			Files.write(directorio.resolve(archivo), bytes);
			documentos.add(new PilotoManifest.Documento(archivo, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
					i % 2 == 0 ? "PLANTILLA_PROVISIONAL" : "CUESTIONARIO", i <= 4 ? "ordinario" : "extraordinario",
					(i <= 4 ? "Ordinario " : "Extraordinario ") + i, "https://sede.inap.gob.es/fixture-" + i + ".pdf", "Evidencia sintética"));
		}
		return new PilotoManifest("https://sede.inap.gob.es/convocatoria-sintetica-2025", "Piloto sintético", 2025, "0".repeat(64), documentos);
	}

	@BeforeEach
	void web() {
		TestAccounts.bind(jdbc);
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	private UUID convocatoria(String nombre, Integer anio) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into c1_tai.convocatoria(id,denominacion,anio_referencia) values (?,?,?)", id, nombre, anio);
		return id;
	}

	private UUID documento(String nombre, String url, boolean verificado) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into c1_tai.documento_oficial(id,identificacion_original,procedencia,tipo,url_origen,verificado,evidencia_verificacion) values (?,?,?,?,?,?,?)",
				id, nombre, "Procedencia sintética de prueba", "ENUNCIADO", url, verificado, verificado ? "Evidencia sintética aislada" : null);
		return id;
	}

}
