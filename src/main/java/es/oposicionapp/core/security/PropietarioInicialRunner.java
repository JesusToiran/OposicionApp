package es.oposicionapp.core.security;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="oposicionapp.auth.preparar-propietario",havingValue="true")
public class PropietarioInicialRunner implements CommandLineRunner {
	private final CuentasService cuentas;private final String correo;
	public PropietarioInicialRunner(CuentasService cuentas,@Value("${oposicionapp.auth.correo-propietario}")String correo){this.cuentas=cuentas;this.correo=correo;}
	@Override public void run(String...args){
		if(cuentas.hayCuentas())return;
		var console=System.console();if(console==null)throw new IllegalStateException("La primera cuenta debe prepararse desde una consola interactiva.");
		console.printf("Administrador inicial: %s%nCorreo pendiente de verificación. No se mostrarán contraseñas ni tokens.%n",correo);
		while(true){char[] secret=console.readPassword("Elige una contraseña de al menos 15 caracteres: ");if(secret==null)throw new IllegalStateException("Preparación cancelada.");char[] confirmation=null;
			try{CuentasService.validarClave(new String(secret));confirmation=console.readPassword("Repite la contraseña: ");if(confirmation==null)throw new IllegalStateException("Preparación cancelada.");if(!Arrays.equals(secret,confirmation)){console.printf("Las contraseñas no coinciden. Vuelve a intentarlo.%n");continue;}cuentas.crearPropietario(correo,new String(secret));console.printf("Cuenta administrativa preparada. El acceso al estudio requiere verificar el correo.%n");return;}
			catch(org.springframework.web.server.ResponseStatusException ex){console.printf("Usa una frase de 15 a 128 caracteres que no sea una contraseña habitual.%n");}
			finally{Arrays.fill(secret,'\0');if(confirmation!=null)Arrays.fill(confirmation,'\0');}
		}
	}
}
