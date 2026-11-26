package es.oposicionapp.core.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
public class WebSecurityConfiguration {
	@Bean SecurityFilterChain acceso(HttpSecurity http,ProveedorCuentas provider,CuentasService cuentas,LimiteAcceso limite,
			@Value("${oposicionapp.auth.https-obligatorio:true}")boolean https,
			@Value("${server.servlet.session.cookie.name:OPOSICIONAPP_SESSION_LOCAL}")String cookie)throws Exception{
		return http.authenticationProvider(provider)
			.authorizeHttpRequests(auth->auth
				.requestMatchers(HttpMethod.GET,"/login","/auth/recuperar","/auth/correo","/css/acceso.css","/js/acceso.js").permitAll()
				.requestMatchers(HttpMethod.POST,"/login","/auth/recuperar","/auth/correo").permitAll()
				.requestMatchers(HttpMethod.GET,"/admin/usuarios").hasRole("ADMIN")
				.requestMatchers(HttpMethod.POST,"/admin/usuarios/invitar","/admin/usuarios/*/actualizar","/admin/usuarios/*/reenviar").hasRole("ADMIN")
				.requestMatchers(HttpMethod.POST,"/auth/reenviar-verificacion").authenticated()
				.requestMatchers(HttpMethod.GET,"/c1-tai/examenes","/c1-tai/convocatorias/*","/css/biblioteca.css","/c1-tai/plan","/c1-tai/plan/**").hasAuthority("CORREO_VERIFICADO")
				.requestMatchers(HttpMethod.PUT,"/c1-tai/plan/api/estado").hasAuthority("CORREO_VERIFICADO")
				.anyRequest().denyAll())
			.formLogin(form->form.loginPage("/login").loginProcessingUrl("/login").usernameParameter("correo").passwordParameter("clave")
				.successHandler((request,response,authentication)->{((org.springframework.security.authentication.AbstractAuthenticationToken)authentication).setDetails(null);var p=(PrincipalCuenta)authentication.getPrincipal();response.sendRedirect(p.verificada()?"/c1-tai/plan/":"/admin/usuarios");})
				.failureUrl("/login?error").permitAll())
			.logout(logout->logout.logoutUrl("/logout").logoutSuccessUrl("/login?salida").invalidateHttpSession(true).clearAuthentication(true).deleteCookies(cookie))
			.sessionManagement(session->session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED).sessionFixation(f->f.changeSessionId()))
			.requestCache(cache->cache.requestCache(new NullRequestCache()))
			.exceptionHandling(errors->errors.authenticationEntryPoint((request,response,error)->FiltrosAcceso.sinSesion(request,response)))
			.addFilterBefore(new FiltrosAcceso.Solicitudes(limite,https),UsernamePasswordAuthenticationFilter.class)
			.addFilterBefore(new FiltrosAcceso.Vigencia(cuentas),AuthorizationFilter.class)
			.headers(headers->headers
				.contentSecurityPolicy(csp->csp.policyDirectives("default-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'"))
				.referrerPolicy(referrer->referrer.policy(ReferrerPolicy.NO_REFERRER))
				.httpStrictTransportSecurity(hsts->hsts.includeSubDomains(false).maxAgeInSeconds(31536000))
				.addHeaderWriter((request,response)->{
					if(request.getRequestURI().startsWith("/c1-tai/plan/lectura/")){
						response.setHeader("X-Frame-Options","SAMEORIGIN");
						response.setHeader("Content-Security-Policy","default-src 'none'; style-src 'self'; img-src 'self' data:; font-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'self'; sandbox allow-same-origin");
					}
				})).build();
	}
}
