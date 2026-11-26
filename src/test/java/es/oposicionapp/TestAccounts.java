package es.oposicionapp;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import es.oposicionapp.core.security.PrincipalCuenta;

final class TestAccounts {
	private static final ThreadLocal<JdbcTemplate> DATABASE=new ThreadLocal<>();
	private static final String SECRET=UUID.randomUUID()+"-"+UUID.randomUUID();
	private static final String HASH="{pbkdf2-sha256-600k}"+new Pbkdf2PasswordEncoder("",16,600_000,Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256).encode(SECRET);
	static void bind(JdbcTemplate jdbc){DATABASE.set(jdbc);}
	static UUID id(String alias){return UUID.nameUUIDFromBytes(("test-account:"+alias).getBytes(StandardCharsets.UTF_8));}
	static String email(String alias){return id(alias)+"@example.invalid";}
	static RequestPostProcessor user(String alias){
		UUID id=id(alias);JdbcTemplate jdbc=DATABASE.get();if(jdbc==null)throw new IllegalStateException("Falta la base aislada de prueba");
		jdbc.update("insert into core.cuenta(id,correo,password_hash,rol,correo_verificado_en) values (?,?,?,'USER',current_timestamp) on conflict(id) do nothing",id,email(alias),HASH);
		var principal=new PrincipalCuenta(id,"USER",true,0,Instant.now());
		return authentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.autoridades()));
	}
}
