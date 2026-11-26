package es.oposicionapp;

import java.util.UUID;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Cada clase usa una base nueva con los mismos roles restringidos que la aplicación. */
public final class TestDatabase {
	private TestDatabase() { }

	public static void configure(PostgreSQLContainer postgres, DynamicPropertyRegistry registry) {
		postgres.start();
		String appPassword = UUID.randomUUID().toString();
		String migratorPassword = UUID.randomUUID().toString();
		OposicionAppApplicationTests.bootstrapRoles(postgres);
		OposicionAppApplicationTests.setRolePasswords(postgres, appPassword, migratorPassword);
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "oposicionapp_app");
		registry.add("spring.datasource.password", () -> appPassword);
		registry.add("spring.flyway.user", () -> "oposicionapp_migrator");
		registry.add("spring.flyway.password", () -> migratorPassword);
	}
}
