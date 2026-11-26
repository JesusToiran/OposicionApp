package es.oposicionapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static es.oposicionapp.TestAccounts.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import es.oposicionapp.c1tai.biblioteca.PlanTaiService;
import es.oposicionapp.c1tai.importacion.PlanTaiImportacionService;
import tools.jackson.databind.json.JsonMapper;

@org.springframework.context.annotation.Import(es.oposicionapp.c1tai.importacion.CargaSintetica.class)
@org.testcontainers.junit.jupiter.Testcontainers
@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest(properties={"spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.minimum-idle=1"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlanTaiIntegrationTests {
 @org.testcontainers.junit.jupiter.Container
 static org.testcontainers.postgresql.PostgreSQLContainer postgres = new org.testcontainers.postgresql.PostgreSQLContainer("postgres:18");
 @org.springframework.test.context.DynamicPropertySource
 static void database(org.springframework.test.context.DynamicPropertyRegistry registry) {
  es.oposicionapp.TestDatabase.configure(postgres, registry);
 }

	static final Path BASE=es.oposicionapp.c1tai.importacion.CargaSintetica.BASE;
	@Autowired es.oposicionapp.c1tai.importacion.ImportacionCsvService csvImporter;
	@Autowired PlanTaiImportacionService importer;
	@Autowired PlanTaiService service;
	@Autowired JdbcTemplate jdbc;
	@Autowired WebApplicationContext context;
	MockMvc mvc;
	@DynamicPropertySource static void resources(DynamicPropertyRegistry r) {
		r.add("oposicionapp.plan.recursos",()->BASE.resolve("integracion-plan-tai/original").toString());
	}
	@BeforeAll void importar() throws Exception {
		csvImporter.importar(es.oposicionapp.c1tai.importacion.CargaSintetica.PLAN, es.oposicionapp.c1tai.importacion.CargaSintetica.CSV, es.oposicionapp.c1tai.importacion.CargaSintetica.ASSETS);
		importer.importar(BASE.resolve("integracion-plan-tai/recursos.json"),BASE.resolve("integracion-plan-tai/original"));
		assertThat(importer.importar(BASE.resolve("integracion-plan-tai/recursos.json"),BASE.resolve("integracion-plan-tai/original"))).isZero();
	}
	@BeforeEach void setup(){TestAccounts.bind(jdbc);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}

	@Test void inventarioCompletoYExplicacionesConservadas(){
		assertThat(jdbc.queryForObject("select count(*) from c1_tai.plan_tai_recurso",Integer.class)).isEqualTo(846);
		assertThat(jdbc.queryForObject("select count(*) from c1_tai.plan_tai_recurso where html_lectura is not null",Integer.class)).isEqualTo(33);
		var questions=service.datos("questions.json");assertThat(questions.size()).isEqualTo(2367);
		assertThat(questions).allSatisfy(q->{assertThat(q.path("explanation").asString()).isNotBlank();assertThat(q.path("options").size()).isEqualTo(4);});
		assertThat(service.datos("generated_questions.json").size()).isEqualTo(480);
		assertThat(service.datos("historical_exams.json").size()).isEqualTo(20);
		assertThat(service.datos("official_exams.json").size()).isEqualTo(2);
		assertThat(service.datos("materiales_estudio.json").size()).isEqualTo(33);
	}

	@Test void todasLasLecturasYFigurasSonAccesiblesConProteccion() throws Exception {
		mvc.perform(get("/c1-tai/plan/")).andExpect(status().isUnauthorized());
		mvc.perform(get("/c1-tai/plan/").with(user("lector"))).andExpect(status().isOk());
		for(String path:jdbc.queryForList("select ruta from c1_tai.plan_tai_recurso where ruta like 'lectura/%'",String.class)) {
			var response=mvc.perform(get("/c1-tai/plan/"+path).with(user("lector"))).andExpect(status().isOk()).andReturn().getResponse();
			if(path.endsWith(".html")) {
				assertThat(response.getHeader("X-Frame-Options")).isEqualTo("SAMEORIGIN");
				assertThat(response.getHeader("Content-Security-Policy")).contains("sandbox allow-same-origin","frame-ancestors 'self'");
				String html=response.getContentAsString(StandardCharsets.UTF_8);
				assertThat(html).doesNotContain("<script", " style=").contains(".html.css");
				mvc.perform(get("/c1-tai/plan/"+path+".css").with(user("lector"))).andExpect(status().isOk());
			}
		}
	}

	@Test void progresoIdempotenteAisladoYConConflictosSinSobrescritura() throws Exception {
		String u="plan-prueba-"+UUID.randomUUID();String v="plan-otro-"+UUID.randomUUID();
		String id=UUID.randomUUID().toString();
		String body="{\"revision\":0,\"operacion\":\""+id+"\",\"estado\":{\"total\":7,\"history\":[],\"errors\":{}}}";
		mvc.perform(put("/c1-tai/plan/api/estado").with(user(u)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
		for(int i=0;i<2;i++)mvc.perform(put("/c1-tai/plan/api/estado").with(user(u)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
		String uid=TestAccounts.id(u).toString(),vid=TestAccounts.id(v).toString();
		assertThat(service.estado(uid).revision()).isEqualTo(1);assertThat(service.estado(uid).estado().path("total").asInt()).isEqualTo(7);
		assertThat(service.estado(vid).revision()).isZero();assertThat(service.estado(vid).estado().size()).isZero();
		String stale=body.replace(id,UUID.randomUUID().toString()).replace("\"total\":7","\"total\":99");
		mvc.perform(put("/c1-tai/plan/api/estado").with(user(u)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(stale)).andExpect(status().isConflict());
		assertThat(service.estado(uid).estado().path("total").asInt()).isEqualTo(7);
		String replay=body.replace("\"total\":7","\"total\":12");
		mvc.perform(put("/c1-tai/plan/api/estado").with(user(u)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(replay)).andExpect(status().isConflict());
		assertThat(jdbc.queryForObject("select count(*) from study.plan_tai_operacion where usuario=?",Integer.class,uid)).isEqualTo(1);
	}
}
