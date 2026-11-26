package es.oposicionapp.c1tai.importacion;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "oposicionapp.plan.manifiesto")
public class PlanTaiImportacionRunner implements CommandLineRunner {
	private final PlanTaiImportacionService service;
	private final ConfigurableApplicationContext context;
	private final Path manifiesto;
	private final Path recursos;
	public PlanTaiImportacionRunner(PlanTaiImportacionService service, ConfigurableApplicationContext context,
			@Value("${oposicionapp.plan.manifiesto}") Path manifiesto, @Value("${oposicionapp.plan.recursos}") Path recursos) {
		this.service=service; this.context=context; this.manifiesto=manifiesto; this.recursos=recursos;
	}
	@Override public void run(String... args) throws Exception {
		System.out.println("PLAN_TAI_RECURSOS=" + service.importar(manifiesto, recursos));
		System.out.println("PLAN_TAI_REPETICION=" + service.importar(manifiesto, recursos));
		context.close();
	}
}
