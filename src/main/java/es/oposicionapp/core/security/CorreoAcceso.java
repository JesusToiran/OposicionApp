package es.oposicionapp.core.security;

import java.net.URI;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CorreoAcceso {
	public interface Enviador { void enviar(String destinatario,String asunto,String cuerpo); }
	private final CuentasService cuentas;
	private final ObjectProvider<Enviador> enviador;
	private final URI origen;
	public CorreoAcceso(CuentasService cuentas,ObjectProvider<Enviador> enviador,@Value("${oposicionapp.auth.origen-publico:}") String origen){
		this.cuentas=cuentas;this.enviador=enviador;this.origen=origen.isBlank()?null:URI.create(origen);
		if(this.origen!=null&&(!"https".equals(this.origen.getScheme())||this.origen.getHost()==null||this.origen.getUserInfo()!=null||this.origen.getQuery()!=null||this.origen.getFragment()!=null||!(this.origen.getPath().isEmpty()||this.origen.getPath().equals("/"))))throw new IllegalArgumentException("Origen público HTTPS no válido.");
	}
	public boolean disponible(){return origen!=null&&enviador.getIfAvailable()!=null;}
	public void recuperar(String correo){
		exigirDisponible();UUID id=cuentas.cuentaRecuperable(correo);if(id!=null)enviar(id,"RECUPERAR");
	}
	public void enviar(UUID id,String purpose){
		exigirDisponible();var carta=cuentas.emitir(id,purpose);
		String base=origen.toString().replaceAll("/$","");
		String link=base+"/auth/correo?modo="+(purpose.equals("VERIFICAR")?"verificar":"clave")+"#token="+carta.token();
		try{enviador.getObject().enviar(carta.destinatario(),"Acceso a OposicionApp","Has recibido una solicitud de acceso a OposicionApp. Si no la has solicitado, ignora este mensaje.\n\n"+link+"\n\nEl enlace es temporal y solo puede utilizarse una vez.");}
		catch(RuntimeException ex){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);}
	}
	private void exigirDisponible(){if(!disponible())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);}
}
