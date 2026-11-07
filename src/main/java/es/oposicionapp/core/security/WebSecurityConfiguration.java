package es.oposicionapp.core.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

@Configuration
public class WebSecurityConfiguration {

	@Bean
	SecurityFilterChain bibliotecaSecurity(HttpSecurity http) throws Exception {
		return http
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.GET, "/c1-tai/examenes", "/c1-tai/convocatorias/*",
								"/css/biblioteca.css").authenticated()
						.anyRequest().denyAll())
				.httpBasic(Customizer.withDefaults())
				.formLogin(form -> form.defaultSuccessUrl("/c1-tai/examenes", true))
				.headers(headers -> headers
						.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; object-src 'none'; "
								+ "base-uri 'self'; frame-ancestors 'none'; form-action 'self'"))
						.referrerPolicy(policy -> policy.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
				.build();
	}
}
