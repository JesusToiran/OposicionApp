package es.oposicionapp.c1tai.importacion;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "oposicionapp.csv.plan")
public class ImportacionCsvRunner implements CommandLineRunner {

	private final ImportacionCsvService service;
	private final ConfigurableApplicationContext context;
	private final Path plan;
	private final Path csv;
	private final Path anexos;

	public ImportacionCsvRunner(ImportacionCsvService service, ConfigurableApplicationContext context,
			@Value("${oposicionapp.csv.plan}") Path plan, @Value("${oposicionapp.csv.fuente}") Path csv,
			@Value("${oposicionapp.csv.anexos}") Path anexos) {
		this.service = service;
		this.context = context;
		this.plan = plan;
		this.csv = csv;
		this.anexos = anexos;
	}

	@Override
	public void run(String... args) throws Exception {
		System.out.println("CSV_V201_CARGA=" + service.importar(plan, csv, anexos));
		context.close();
	}
}
