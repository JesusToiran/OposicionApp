package es.oposicionapp.core.security;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class PoliticaProduccion {
	public PoliticaProduccion(Environment env){
		if(!java.util.Arrays.asList(env.getActiveProfiles()).contains("prod"))return;
		if(!env.getProperty("oposicionapp.auth.https-obligatorio",Boolean.class,true)
				|| !env.getProperty("server.servlet.session.cookie.secure",Boolean.class,false)
				|| !env.getProperty("server.servlet.session.cookie.http-only",Boolean.class,false)
				|| !"__Host-OPOSICIONAPP_SESSION".equals(env.getProperty("server.servlet.session.cookie.name"))
				|| !"strict".equalsIgnoreCase(env.getProperty("server.servlet.session.cookie.same-site"))
				|| !"/".equals(env.getProperty("server.servlet.session.cookie.path"))
				|| env.getProperty("server.servlet.session.cookie.domain")!=null
				|| !("127.0.0.1".equals(env.getProperty("server.address"))
					|| java.util.Arrays.asList(env.getActiveProfiles()).contains("docker")
						&& "0.0.0.0".equals(env.getProperty("server.address")))
				|| java.util.Arrays.asList(env.getActiveProfiles()).contains("revision"))throw new IllegalStateException("Configuración de acceso de producción no segura.");
		String origin=env.getProperty("oposicionapp.auth.origen-publico","");
		if(!origin.startsWith("https://"))throw new IllegalStateException("El origen público HTTPS es obligatorio en producción.");
	}
}
