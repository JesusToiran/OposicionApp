package es.oposicionapp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
class OposicionAppApplicationTests {

	@Container
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

	@DynamicPropertySource
	static void configurePostgres(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {
	}

	@Test
	void flywayCreatesInitialSchemas() {
		assertThat(schemaExists("core")).isTrue();
		assertThat(schemaExists("c1_tai")).isTrue();
		assertThat(schemaExists("study")).isTrue();

		Integer installedRank = jdbcTemplate.queryForObject(
				"select installed_rank from flyway_schema_history where version = '1' and success = true",
				Integer.class);

		assertThat(installedRank).isNotNull();
	}

	private boolean schemaExists(String schemaName) {
		Boolean exists = jdbcTemplate.queryForObject(
				"select exists (select 1 from information_schema.schemata where schema_name = ?)",
				Boolean.class,
				schemaName);
		return Boolean.TRUE.equals(exists);
	}

}
