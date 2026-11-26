package es.oposicionapp.core.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import es.oposicionapp.c1tai.importacion.ImportacionCsvService;

@Component
public class LimiteAcceso {
	private final Clock clock;
	private final Map<String,Ventana> ventanas=new HashMap<>();
	private long limpieza;
	public LimiteAcceso() { this(Clock.systemUTC()); }
	public LimiteAcceso(Clock clock) { this.clock=clock; }
	public synchronized boolean permitir(String operacion,String ip,String correo) {
		long now=clock.millis();
		if (now-limpieza>=60_000) { ventanas.values().removeIf(v->v.hasta<=now);limpieza=now; }
		String address=clave(operacion+":ip:"+ip), account=clave(operacion+":cuenta:"+correo);
		int nuevas=(ventanas.containsKey(address)?0:1)+(ventanas.containsKey(account)?0:1);
		if (ventanas.size()+nuevas>20_000) return false;
		return consumir(address,120,now)&&consumir(account,8,now);
	}
	private boolean consumir(String key,int max,long now) {
		Ventana v=ventanas.get(key);
		if(v==null||v.hasta<=now) { v=new Ventana(now+Duration.ofMinutes(15).toMillis());ventanas.put(key,v); }
		if(v.intentos>=max)return false;
		v.intentos++;return true;
	}
	private String clave(String value) { return ImportacionCsvService.hash(value.getBytes(StandardCharsets.UTF_8)); }
	private static final class Ventana { final long hasta;int intentos;Ventana(long hasta){this.hasta=hasta;} }
}
