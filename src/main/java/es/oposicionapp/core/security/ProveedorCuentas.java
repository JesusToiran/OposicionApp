package es.oposicionapp.core.security;

import java.time.Instant;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class ProveedorCuentas implements AuthenticationProvider {
	private final CuentasService cuentas;
	private final PasswordEncoder passwords;
	private final String dummy;
	public ProveedorCuentas(CuentasService cuentas,PasswordEncoder passwords){this.cuentas=cuentas;this.passwords=passwords;this.dummy=passwords.encode(java.util.UUID.randomUUID().toString());}
	@Override public Authentication authenticate(Authentication authentication){
		CuentasService.Registro cuenta=null;
		try{cuenta=cuentas.buscar(CuentasService.correo(authentication.getName()));}catch(org.springframework.web.server.ResponseStatusException ignored){ }
		String supplied=authentication.getCredentials() instanceof String value?value:"";
		boolean valid=passwords.matches(supplied,cuenta!=null&&cuenta.passwordHash()!=null?cuenta.passwordHash():dummy);
		if(!valid||cuenta==null||!cuenta.activa()||(!cuenta.verificada()&&!(cuenta.propietaria()&&cuenta.rol().equals("ADMIN"))))throw new BadCredentialsException("Acceso no válido");
		var principal=new PrincipalCuenta(cuenta.id(),cuenta.rol(),cuenta.verificada(),cuenta.version(),Instant.now());
		return UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.autoridades());
	}
	@Override public boolean supports(Class<?> type){return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type);}
}
