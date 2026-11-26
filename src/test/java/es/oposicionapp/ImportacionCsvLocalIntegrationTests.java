package es.oposicionapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static es.oposicionapp.TestAccounts.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import es.oposicionapp.c1tai.importacion.ImportacionCsvService;
import tools.jackson.databind.json.JsonMapper;

@org.springframework.context.annotation.Import(es.oposicionapp.c1tai.importacion.CargaSintetica.class)
@org.testcontainers.junit.jupiter.Testcontainers
@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest(properties={"spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.minimum-idle=1"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ImportacionCsvLocalIntegrationTests {
 @org.testcontainers.junit.jupiter.Container
 static org.testcontainers.postgresql.PostgreSQLContainer postgres = new org.testcontainers.postgresql.PostgreSQLContainer("postgres:18");
 @org.springframework.test.context.DynamicPropertySource
 static void database(org.springframework.test.context.DynamicPropertyRegistry registry) {
  es.oposicionapp.TestDatabase.configure(postgres, registry);
 }


	private static final Path BASE = es.oposicionapp.c1tai.importacion.CargaSintetica.BASE;
	private static final Path PLAN = BASE.resolve("carga/plan.json");
	private static final Path CSV = es.oposicionapp.c1tai.importacion.CargaSintetica.CSV;
	private static final Path ASSETS = BASE.resolve("carga/anexos");
	@Autowired ImportacionCsvService importer;
	@Autowired JdbcTemplate jdbc;
	@Autowired WebApplicationContext context;
	private MockMvc mvc;

	@DynamicPropertySource
	static void assets(DynamicPropertyRegistry registry) {
		registry.add("oposicionapp.csv.anexos", ASSETS::toString);
	}

	@BeforeEach
	void setup() {
		TestAccounts.bind(jdbc);
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test @Order(0)
	void contratoPublicoRechazaElCorpusSinteticoSinEscribir() {
		assertThatThrownBy(() -> new ImportacionCsvService(jdbc).importar(PLAN, CSV, ASSETS))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("lote completo autorizado");
		assertThat(jdbc.queryForObject("select count(*) from c1_tai.csv_lote", Integer.class)).isZero();
	}

	@Test @Order(1)
	void conflictoTardioRevierteElLoteYLaCargaCompletaEsIdempotente() throws Exception {
		assertThat(jdbc.queryForObject("select count(*) from c1_tai.csv_lote", Integer.class)).isZero();
		var plan = JsonMapper.builder().build().readTree(Files.readAllBytes(PLAN));
		var last = plan.path("tablas").path("csv_anexo").get(399);
		UUID id = UUID.fromString(last.path("id").asString());
		jdbc.update("insert into c1_tai.csv_anexo(id,ruta,sha256,bytes,tipo) values (?,?,?,?,?)", id,
				last.path("ruta").asString(), "0".repeat(64), last.path("bytes").asInt(), "image/webp");
		try {
			assertThatThrownBy(() -> importer.importar(PLAN, CSV, ASSETS)).isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("csv_anexo");
			for (String table : List.of("convocatoria", "examen", "csv_fila", "csv_lote", "csv_seccion", "csv_pregunta")) {
				assertThat(jdbc.queryForObject("select count(*) from c1_tai." + table, Integer.class)).as(table).isZero();
			}
			assertThat(jdbc.queryForObject("select count(*) from c1_tai.csv_anexo", Integer.class)).isEqualTo(1);
			assertThat(jdbc.queryForObject("select sha256 from c1_tai.csv_anexo where id=?", String.class, id)).isEqualTo("0".repeat(64));
		} finally {
			// Solo la fila sintética de conflicto en la base separada de pruebas.
			jdbc.update("delete from c1_tai.csv_anexo where id=? and sha256=?", id, "0".repeat(64));
		}
		var first = importer.importar(PLAN, CSV, ASSETS);
		assertThat(first.get("csv_fila")).isEqualTo(5994);
		assertThat(first.get("csv_pregunta")).isEqualTo(2367);
		assertThat(first.get("csv_variante")).isEqualTo(226);
		assertThat(importer.importar(PLAN, CSV, ASSETS).values()).allMatch(value -> value == 0);
		assertThat(jdbc.queryForObject("select count(*) from c1_tai.csv_resolucion where campo='source_year'", Integer.class)).isEqualTo(153);
		assertThat(jdbc.queryForObject("select count(*) from c1_tai.csv_resolucion where campo='vinculo_plantilla'", Integer.class)).isEqualTo(24);
		assertThat(jdbc.queryForObject("select count(*) from c1_tai.csv_pregunta where clave is null and anulada is null", Integer.class)).isEqualTo(66);
	}

	@Test @Order(2)
	void todasLasConvocatoriasYEjerciciosSonConsultablesPorLaBibliotecaNormal() throws Exception {
		mvc.perform(get("/c1-tai/examenes").accept(org.springframework.http.MediaType.TEXT_HTML)).andExpect(status().is3xxRedirection());
		mvc.perform(get("/c1-tai/examenes").with(user("revision"))).andExpect(status().isOk());
		var calls = jdbc.queryForList("select id from c1_tai.convocatoria", UUID.class);
		assertThat(calls).hasSize(17);
		for (UUID id : calls) {
			String html = mvc.perform(get("/c1-tai/convocatorias/" + id).with(user("revision"))).andExpect(status().isOk())
					.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
			assertThat(html).contains("seccion=");
		}
		var sections = jdbc.queryForList("select s.id,x.convocatoria_id from c1_tai.csv_seccion s join c1_tai.ejercicio e on e.id=s.ejercicio_id join c1_tai.examen x on x.id=e.examen_id");
		assertThat(sections).hasSize(63);
		for (var section : sections) {
			mvc.perform(get("/c1-tai/convocatorias/" + section.get("convocatoria_id")).param("seccion", section.get("id").toString())
					.with(user("revision"))).andExpect(status().isOk());
		}
	}

	@Test @Order(3)
	void practicaRespetaAusenciasProvisionalidadAnulacionesYVariantes() throws Exception {
		for (String kind : List.of("sin_plantilla", "provisional", "anulada", "variante")) {
			String condition = switch (kind) {
				case "sin_plantilla" -> "q.clave is null and q.anulada is null";
				case "provisional" -> "q.estado_plantilla like 'provisional%' and q.clave is not null";
				case "anulada" -> "q.anulada=true";
				default -> "exists(select 1 from c1_tai.csv_variante v where v.pregunta_id=q.id)";
			};
			var q = jdbc.queryForList("select q.*,x.convocatoria_id from c1_tai.csv_pregunta q join c1_tai.csv_seccion s on s.id=q.seccion_id join c1_tai.ejercicio e on e.id=s.ejercicio_id join c1_tai.examen x on x.id=e.examen_id where " + condition + " order by q.id limit 1").getFirst();
			var request = get("/c1-tai/convocatorias/" + q.get("convocatoria_id")).param("seccion", q.get("seccion_id").toString())
					.param("posicion", q.get("orden").toString()).param("respuesta", "A").with(user("revision"));
			if (kind.equals("variante")) request.param("variante", jdbc.queryForObject("select id from c1_tai.csv_variante where pregunta_id=? limit 1", UUID.class, q.get("id")).toString());
			String html = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
			if (kind.equals("provisional")) assertThat(html).contains("Plantilla provisional");
			else assertThat(html).contains("No se califica.").doesNotContain("No coincide. Clave declarada:");
		}
		var asset = jdbc.queryForList("select sa.anexo_id,x.convocatoria_id from c1_tai.csv_seccion_anexo sa join c1_tai.csv_seccion s on s.id=sa.seccion_id join c1_tai.ejercicio e on e.id=s.ejercicio_id join c1_tai.examen x on x.id=e.examen_id limit 1").getFirst();
		String url = "/c1-tai/convocatorias/" + asset.get("convocatoria_id");
		mvc.perform(get(url).param("anexo", asset.get("anexo_id").toString())).andExpect(status().isUnauthorized());
		mvc.perform(get(url).param("anexo", asset.get("anexo_id").toString()).with(user("revision"))).andExpect(status().isOk());
		mvc.perform(post(url).with(user("revision"))).andExpect(status().isForbidden());
		mvc.perform(get(url).param("seccion", UUID.randomUUID().toString()).with(user("revision"))).andExpect(status().isNotFound());
	}
}
