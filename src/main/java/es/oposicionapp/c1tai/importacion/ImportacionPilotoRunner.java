package es.oposicionapp.c1tai.importacion;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.context.ConfigurableApplicationContext;

@Component
@ConditionalOnProperty(name = "oposicionapp.importacion.manifiesto")
public class ImportacionPilotoRunner implements CommandLineRunner {

	private final ImportacionPilotoService servicio;
	private final Path manifiesto;
	private final Path documentos;
	private final ConfigurableApplicationContext context;

	public ImportacionPilotoRunner(ImportacionPilotoService servicio,
			@Value("${oposicionapp.importacion.manifiesto}") Path manifiesto,
			@Value("${oposicionapp.importacion.documentos}") Path documentos,
			ConfigurableApplicationContext context) {
		this.servicio = servicio;
		this.manifiesto = manifiesto;
		this.documentos = documentos;
		this.context = context;
	}

	@Override
	public void run(String... args) throws Exception {
		var resultado = servicio.importar(PilotoManifest.leer(manifiesto), documentos);
		System.out.println("SDD-002: " + resultado);
		context.close();
	}
}
