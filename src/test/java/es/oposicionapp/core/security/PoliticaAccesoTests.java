package es.oposicionapp.core.security;

import static org.assertj.core.api.Assertions.*;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;

class PoliticaAccesoTests {
 MockEnvironment prod(){
  var env=new MockEnvironment().withProperty("oposicionapp.auth.https-obligatorio","true")
   .withProperty("server.servlet.session.cookie.secure","true").withProperty("server.servlet.session.cookie.http-only","true")
   .withProperty("server.servlet.session.cookie.name","__Host-OPOSICIONAPP_SESSION").withProperty("server.servlet.session.cookie.path","/")
   .withProperty("server.servlet.session.cookie.same-site","strict").withProperty("server.address","127.0.0.1")
   .withProperty("oposicionapp.auth.origen-publico","https://oposicion.example.invalid");
  env.setActiveProfiles("prod");return env;
 }
 @Test void produccionRechazaCookiesInsegurasYRevision(){
  assertThatCode(()->new PoliticaProduccion(prod())).doesNotThrowAnyException();
  for(String key:new String[]{"server.servlet.session.cookie.secure","server.servlet.session.cookie.http-only","oposicionapp.auth.https-obligatorio"}){
   assertThatThrownBy(()->new PoliticaProduccion(prod().withProperty(key,"false"))).isInstanceOf(IllegalStateException.class);
  }
  assertThatThrownBy(()->new PoliticaProduccion(prod().withProperty("server.servlet.session.cookie.domain","example.invalid"))).isInstanceOf(IllegalStateException.class);
  var mixed=prod();mixed.setActiveProfiles("prod","revision");assertThatThrownBy(()->new PoliticaProduccion(mixed)).isInstanceOf(IllegalStateException.class);
 }
 @Test void peticionSinHttpsSeRechazaAntesDeAutenticar()throws Exception{
  var req=new MockHttpServletRequest("GET","/login");var res=new MockHttpServletResponse();
  new FiltrosAcceso.Solicitudes(new LimiteAcceso(),true).doFilter(req,res,(a,b)->{throw new AssertionError("No debe continuar sin HTTPS");});
  assertThat(res.getStatus()).isEqualTo(400);
 }
 @Test void dockerPermiteEscucharEnSuRedSinRelajarCookiesNiHttps(){
  var env=prod().withProperty("server.address","0.0.0.0");
  assertThatThrownBy(()->new PoliticaProduccion(env)).isInstanceOf(IllegalStateException.class);
  env.setActiveProfiles("docker","prod");
  assertThatCode(()->new PoliticaProduccion(env)).doesNotThrowAnyException();
  env.withProperty("oposicionapp.auth.https-obligatorio","false");
  assertThatThrownBy(()->new PoliticaProduccion(env)).isInstanceOf(IllegalStateException.class);
 }
 @Test void cabecerasFalsificadasNoEvitanElLimiteDeIntentos()throws Exception{
  var filter=new FiltrosAcceso.Solicitudes(new LimiteAcceso(),false);
  for(int i=0;i<9;i++){
   var req=new MockHttpServletRequest("POST","/login");req.setRemoteAddr("127.0.0.1");req.setContentType("application/x-www-form-urlencoded");
   req.addHeader("X-Forwarded-For","192.0.2."+i);req.addParameter("correo","prueba@example.invalid");req.addParameter("clave","no importa");var res=new MockHttpServletResponse();
   filter.doFilter(req,res,(a,b)->{});assertThat(res.getStatus()).isEqualTo(i<8?200:429);
  }
 }
 @Test void ventanaTemporalYMemoriaAcotada(){
  var clock=mock(Clock.class);long start=Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();when(clock.millis()).thenReturn(start);
  var limit=new LimiteAcceso(clock);for(int i=0;i<8;i++)assertThat(limit.permitir("a","ip","correo")).isTrue();
  assertThat(limit.permitir("a","ip","correo")).isFalse();when(clock.millis()).thenReturn(start+900_001);
  assertThat(limit.permitir("a","ip","correo")).isTrue();
  for(int i=0;i<10_100;i++)limit.permitir("a","ip"+i,"correo"+i);
  assertThat(((java.util.Map<?,?>)ReflectionTestUtils.getField(limit,"ventanas")).size()).isLessThanOrEqualTo(20_000);
 }
}
