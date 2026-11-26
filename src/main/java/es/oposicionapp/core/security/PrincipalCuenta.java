package es.oposicionapp.core.security;

import java.io.Serializable;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

// La sesión nunca contiene correo, contraseña o hash de contraseña.
public record PrincipalCuenta(UUID id, String rol, boolean verificada, long version, Instant autenticadaEn)
		implements Principal, Serializable {
	@Override public String getName() { return id.toString(); }
	public List<SimpleGrantedAuthority> autoridades() {
		return verificada ? List.of(new SimpleGrantedAuthority("ROLE_"+rol),new SimpleGrantedAuthority("CORREO_VERIFICADO"))
				: List.of(new SimpleGrantedAuthority("ROLE_"+rol));
	}
}
