package es.oposicionapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
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
import es.oposicionapp.c1tai.biblioteca.ConvocatoriaRepository;
import es.oposicionapp.c1tai.biblioteca.EjercicioRepository;
import es.oposicionapp.c1tai.biblioteca.ExamenRepository;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BibliotecaIntegrationTests {

	private static final String LISTADO = "/c1-tai/examenes";
	private static final String DETALLE = "/c1-tai/convocatorias/";
	private static final String APP_PASSWORD = UUID.randomUUID().toString();
	private static final String MIGRATOR_PASSWORD = UUID.randomUUID().toString();
	private static final String WEB_PASSWORD = UUID.randomUUID().toString();

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
		registry.add("spring.security.user.name", () -> "lector-prueba");
		registry.add("spring.security.user.password", () -> WEB_PASSWORD);
	}

	@Autowired WebApplicationContext context;
	@Autowired JdbcTemplate jdbc;
	@Autowired BibliotecaService biblioteca;
	@Autowired ConvocatoriaRepository convocatorias;
	@Autowired ExamenRepository examenes;
	@Autowired EjercicioRepository ejercicios;
	private MockMvc mvc;

	@BeforeEach
	void web() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void loginRedirigeALaBiblioteca() throws Exception {
		var result = mvc.perform(post("/login").with(csrf())
				.param("username", "lector-prueba").param("password", WEB_PASSWORD))
				.andExpect(status().isFound()).andExpect(redirectedUrl(LISTADO)).andReturn();
		MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
		assertThat(session).isNotNull();
		mvc.perform(get(LISTADO).session(session)).andExpect(status().isOk());
		mvc.perform(get("/").session(session)).andExpect(status().isForbidden());
	}

	@Test
	void loginIncorrectoNoAutentica() throws Exception {
		var result = mvc.perform(post("/login").with(csrf())
				.param("username", "lector-prueba").param("password", WEB_PASSWORD + "incorrecta"))
				.andExpect(status().isFound()).andExpect(redirectedUrl("/login?error")).andReturn();
		mvc.perform(get(LISTADO).accept("application/json")
				.session((MockHttpSession) result.getRequest().getSession()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void bibliotecaVaciaSinDependenciasFuncionalesNiDatosIniciales() throws Exception {
		assertThat(convocatorias.findAllByOrderByAnioReferenciaDescDenominacionAscIdAsc()).isEmpty();
		assertThat(biblioteca.listarConvocatorias()).isEmpty();
		assertThat(examenes.findAllByConvocatoriaIdOrderByDenominacionAscIdAsc(UUID.randomUUID())).isEmpty();
		assertThat(ejercicios.findAllByExamenConvocatoriaIdOrderByDenominacionAscIdAsc(UUID.randomUUID())).isEmpty();
		String html = mvc.perform(get(LISTADO).with(user("lector"))).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
		assertThat(html).contains("Exámenes oficiales C1 TAI", "Biblioteca de convocatorias y exámenes oficiales.",
				"Todavía no hay convocatorias disponibles.", "Estamos preparando la biblioteca de exámenes oficiales C1 TAI.");
		assertThat(jdbc.queryForList("select tablename from pg_tables where schemaname='c1_tai'", String.class))
				.containsExactlyInAnyOrder("convocatoria", "examen", "ejercicio", "documento_oficial", "vinculo_documental");
	}

	@Test
	void lecturaProtegidaYRestoDenegado() throws Exception {
		mvc.perform(get(LISTADO).accept("application/json")).andExpect(status().isUnauthorized());
		mvc.perform(get(DETALLE + UUID.randomUUID()).accept("application/json")).andExpect(status().isUnauthorized());
		mvc.perform(get("/css/biblioteca.css").accept("application/json")).andExpect(status().isUnauthorized());
		mvc.perform(get("/administracion").with(user("lector"))).andExpect(status().isForbidden());
		mvc.perform(get("/actuator/env").with(user("lector"))).andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
	void escrituraDenegadaInclusoConAutenticacionYCsrf(String method) throws Exception {
		UUID id = convocatoria("Conservar", 2025);
		mvc.perform(request(HttpMethod.valueOf(method), LISTADO).with(user("lector")).with(csrf()))
				.andExpect(status().isForbidden());
		mvc.perform(request(HttpMethod.valueOf(method), DETALLE + id).with(user("lector")).with(csrf()))
				.andExpect(status().isForbidden());
		assertThat(convocatorias.findById(id)).isPresent();
	}

	@Test
	void csrfSigueActivo() throws Exception {
		mvc.perform(post(LISTADO).with(user("lector"))).andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@ValueSource(strings = {"no-es-uuid", "1-1-1-1-1", "' OR 1=1--", "00000000-0000-0000-0000-00000000000Z"})
	void identificadoresInvalidosSinInyeccionNiDetallesInternos(String input) throws Exception {
		convocatoria("Conservar", 2025);
		String html = mvc.perform(get(DETALLE + "{id}", input).with(user("lector")))
				.andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
		assertThat(html).contains("identificador").doesNotContain("Exception", "jdbc:", "SELECT ", "password", "java.lang", input);
		assertThat(biblioteca.listarConvocatorias()).hasSize(1);
	}

	@Test
	void convocatoriaInexistenteResponde404() throws Exception {
		assertThat(biblioteca.obtenerConvocatoria(UUID.randomUUID())).isEmpty();
		String html = mvc.perform(get(DETALLE + UUID.randomUUID()).with(user("lector")))
				.andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
		assertThat(html).doesNotContain("Exception", "jdbc:", "password", "org.springframework");
	}

	@Test
	void variosExamenesEjerciciosYDocumentoCompartidoConAlcanceCorrecto() throws Exception {
		UUID convocatoria = convocatoria("Convocatoria de prueba", 2025);
		convocatoria("Otra convocatoria del mismo año", 2025);
		UUID examen = examen(convocatoria, "Primer examen");
		examen(convocatoria, "Segundo examen");
		UUID ejercicio = ejercicio(examen, "Ejercicio único");
		UUID documento = documento("Referencia sintética", "https://example.invalid/documento", true);
		vinculo(documento, convocatoria, null, null);
		vinculo(documento, null, examen, null);
		vinculo(documento, null, null, ejercicio);
		UUID ajena = convocatoria("Ajena", null);
		vinculo(documento("Documento de otra convocatoria", null, false), ajena, null, null);
		var detalle = biblioteca.obtenerConvocatoria(convocatoria).orElseThrow();
		assertThat(detalle.examenes()).hasSize(2);
		assertThat(detalle.examenes().getFirst().ejercicios()).hasSize(1);
		assertThat(detalle.documentos()).hasSize(1);
		assertThat(detalle.examenes().getFirst().documentos()).hasSize(1);
		assertThat(detalle.examenes().getFirst().ejercicios().getFirst().documentos()).hasSize(1);
		assertThat(jdbc.queryForObject("select count(*) from c1_tai.documento_oficial where id=?", Integer.class, documento)).isEqualTo(1);
		String html = mvc.perform(get(DETALLE + convocatoria).with(user("lector"))).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
		assertThat(html).contains("Primer examen", "Segundo examen", "Ejercicio único", "https://example.invalid/documento", "noopener noreferrer")
				.doesNotContain("Documento de otra convocatoria");
	}

	@Test
	void htmlEscapadoYEnlacesPendientesOInvalidosNoEjecutables() throws Exception {
		String malicioso = "<script>alert('xss')</script>";
		UUID convocatoria = convocatoria(malicioso, null);
		UUID examen = examen(convocatoria, malicioso);
		ejercicio(examen, malicioso);
		vinculo(documento(malicioso, "https://example.invalid/pendiente", false), convocatoria, null, null);
		vinculo(documento("URL malformada", "https://example.invalid/<script>", true), null, examen, null);
		String listado = mvc.perform(get(LISTADO).with(user("lector"))).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
		String html = mvc.perform(get(DETALLE + convocatoria).with(user("lector"))).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
		assertThat(listado).contains("&lt;script&gt;").doesNotContain("<script>");
		assertThat(html).contains("&lt;script&gt;", "Pendiente de verificación").doesNotContain("<script>",
				"href=\"https://example.invalid/pendiente", "href=\"https://example.invalid/&lt;script", "javascript:", "data:");
	}

	@Test
	void cabecerasYRecursosCompatiblesConCsp() throws Exception {
		mvc.perform(get(LISTADO).with(user("lector"))).andExpect(status().isOk())
				.andExpect(header().string("Content-Security-Policy", "default-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string("X-Frame-Options", "DENY"))
				.andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
				.andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
				.andExpect(header().doesNotExist("Strict-Transport-Security"));
		mvc.perform(get(LISTADO).secure(true).with(user("lector")))
				.andExpect(header().exists("Strict-Transport-Security"));
		String css = mvc.perform(get("/css/biblioteca.css").with(user("lector"))).andExpect(status().isOk())
				.andExpect(header().string("X-Content-Type-Options", "nosniff")).andReturn().getResponse().getContentAsString();
		assertThat(css).contains(":focus-visible", "@media").doesNotContain("@import", "https:");
	}

	@ParameterizedTest
	@ValueSource(strings = {"examen_sin_padre", "ejercicio_sin_padre", "nombre_vacio", "documento_sin_evidencia",
			"url_javascript", "url_data", "vinculo_sin_destino", "vinculo_dos_destinos", "vinculo_sin_documento",
			"vinculo_duplicado", "borrado_padre"})
	void restriccionesRealesDePostgresql(String caso) {
		UUID c = convocatoria("Prueba de integridad", 2025);
		UUID e = examen(c, "Examen");
		UUID d = documento("Referencia", null, false);
		vinculo(d, c, null, null);
		assertThatThrownBy(() -> {
			switch (caso) {
				case "examen_sin_padre" -> examen(UUID.randomUUID(), "Inválido");
				case "ejercicio_sin_padre" -> ejercicio(UUID.randomUUID(), "Inválido");
				case "nombre_vacio" -> convocatoria(" ", 2025);
				case "documento_sin_evidencia" -> jdbc.update("update c1_tai.documento_oficial set verificado=true where id=?", d);
				case "url_javascript" -> documento("Inseguro", "javascript:alert(1)", false);
				case "url_data" -> documento("Inseguro", "data:text/html,test", false);
				case "vinculo_sin_destino" -> vinculo(d, null, null, null);
				case "vinculo_dos_destinos" -> vinculo(d, c, e, null);
				case "vinculo_sin_documento" -> vinculo(UUID.randomUUID(), c, null, null);
				case "vinculo_duplicado" -> vinculo(d, c, null, null);
				case "borrado_padre" -> jdbc.update("delete from c1_tai.convocatoria where id=?", c);
				default -> throw new AssertionError(caso);
			}
		}).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void v2SeAplicaSobreV1ConservandoDatosEHistorial() {
		try (PostgreSQLContainer existente = new PostgreSQLContainer("postgres:18")) {
			existente.start();
			OposicionAppApplicationTests.bootstrapRoles(existente);
			OposicionAppApplicationTests.setRolePasswords(existente, APP_PASSWORD, MIGRATOR_PASSWORD);
			var inicial = Flyway.configure().dataSource(existente.getJdbcUrl(), "oposicionapp_migrator", MIGRATOR_PASSWORD)
					.locations("classpath:db/migration").target("1").load();
			inicial.migrate();
			JdbcTemplate migrador = OposicionAppApplicationTests.jdbcTemplateForRole(existente, "oposicionapp_migrator", MIGRATOR_PASSWORD);
			var historial = migrador.queryForMap("select * from public.flyway_schema_history where version='1'");
			migrador.execute("create table core.conservar_sdd001 (id integer primary key, valor text)");
			migrador.update("insert into core.conservar_sdd001 values (?, ?)", 1, "conservado");
			OposicionAppApplicationTests.bootstrapRoles(existente);
			Flyway.configure().dataSource(existente.getJdbcUrl(), "oposicionapp_migrator", MIGRATOR_PASSWORD)
					.locations("classpath:db/migration").load().migrate();
			assertThat(migrador.queryForMap("select * from public.flyway_schema_history where version='1'")).isEqualTo(historial);
			assertThat(migrador.queryForObject("select valor from core.conservar_sdd001 where id=1", String.class)).isEqualTo("conservado");
			JdbcTemplate app = OposicionAppApplicationTests.jdbcTemplateForRole(existente, "oposicionapp_app", APP_PASSWORD);
			assertThat(app.queryForObject("select count(*) from c1_tai.convocatoria", Integer.class)).isZero();
			assertThat(app.queryForObject("select has_schema_privilege(current_user,'c1_tai','CREATE')", Boolean.class)).isFalse();
		}
	}

	private UUID convocatoria(String nombre, Integer anio) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into c1_tai.convocatoria(id,denominacion,anio_referencia) values (?,?,?)", id, nombre, anio);
		return id;
	}

	private UUID examen(UUID convocatoria, String nombre) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into c1_tai.examen(id,convocatoria_id,denominacion) values (?,?,?)", id, convocatoria, nombre);
		return id;
	}

	private UUID ejercicio(UUID examen, String nombre) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into c1_tai.ejercicio(id,examen_id,denominacion) values (?,?,?)", id, examen, nombre);
		return id;
	}

	private UUID documento(String nombre, String url, boolean verificado) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into c1_tai.documento_oficial(id,identificacion_original,procedencia,tipo,url_origen,verificado,evidencia_verificacion) values (?,?,?,?,?,?,?)",
				id, nombre, "Procedencia sintética de prueba", "ENUNCIADO", url, verificado, verificado ? "Evidencia sintética aislada" : null);
		return id;
	}

	private void vinculo(UUID documento, UUID convocatoria, UUID examen, UUID ejercicio) {
		jdbc.update("insert into c1_tai.vinculo_documental(id,documento_id,convocatoria_id,examen_id,ejercicio_id) values (?,?,?,?,?)",
				UUID.randomUUID(), documento, convocatoria, examen, ejercicio);
	}
}
