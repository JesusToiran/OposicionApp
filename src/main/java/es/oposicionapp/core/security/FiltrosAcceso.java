package es.oposicionapp.core.security;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

final class FiltrosAcceso {
	private FiltrosAcceso(){ }
	static void sinSesion(HttpServletRequest request,HttpServletResponse response) throws IOException {
		response.setHeader("Cache-Control","no-store");
		if(request.getHeader("Accept")!=null&&request.getHeader("Accept").contains("text/html")&&!request.getRequestURI().contains("/api/")&&!request.getRequestURI().contains("/data/"))response.sendRedirect("/login?caducada");
		else{response.setStatus(401);response.setContentType("application/json");response.getWriter().write("{\"error\":\"autenticacion_requerida\"}");}
	}
	static final class Solicitudes extends OncePerRequestFilter {
		private final LimiteAcceso limite;private final boolean https;
		Solicitudes(LimiteAcceso limite,boolean https){this.limite=limite;this.https=https;}
		@Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
			if(https&&!req.isSecure()){res.setStatus(400);return;}
			if(req.getMethod().equals("POST")&&Set.of("/login","/auth/recuperar","/auth/correo").contains(req.getRequestURI())&&req.getQueryString()!=null){res.setStatus(400);return;}
			if(req.getMethod().equals("POST")&&req.getRequestURI().equals(req.getContextPath()+"/login")){
				String type=req.getContentType();
				if(req.getContentLengthLong()>8192||type==null||!type.toLowerCase(java.util.Locale.ROOT).startsWith("application/x-www-form-urlencoded")){res.setStatus(400);return;}
				if(!Set.of("correo","clave","_csrf").containsAll(req.getParameterMap().keySet())||req.getParameterMap().values().stream().anyMatch(v->v.length!=1)){res.setStatus(400);return;}
				String email=req.getParameter("correo"),password=req.getParameter("clave");
				if(email==null||email.length()>254||password==null||password.length()>256){res.sendRedirect("/login?error");return;}
				if(!limite.permitir("login",req.getRemoteAddr(),email.strip().toLowerCase(java.util.Locale.ROOT))){res.setStatus(429);res.setHeader("Retry-After","900");res.setContentType("text/plain;charset=UTF-8");res.getWriter().write("Demasiados intentos. Espera unos minutos.");return;}
			}
			chain.doFilter(req,res);
		}
	}
	static final class Vigencia extends OncePerRequestFilter {
		private final CuentasService cuentas;
		Vigencia(CuentasService cuentas){this.cuentas=cuentas;}
		@Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
			var authentication=SecurityContextHolder.getContext().getAuthentication();
			if(authentication!=null&&authentication.isAuthenticated()&&!(authentication instanceof AnonymousAuthenticationToken)){
				boolean valid=authentication.getPrincipal() instanceof PrincipalCuenta principal
						&& principal.autenticadaEn().plus(Duration.ofHours(8)).isAfter(Instant.now())&&cuentas.vigente(principal);
				if(!valid){var session=req.getSession(false);if(session!=null)session.invalidate();SecurityContextHolder.clearContext();sinSesion(req,res);return;}
			}
			chain.doFilter(req,res);
		}
	}
}
