package es.oposicionapp.core.security;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

@org.testcontainers.junit.jupiter.Testcontainers
@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest(properties={"spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.minimum-idle=1"})
class AccesoIntegrationTests {
 @org.testcontainers.junit.jupiter.Container
 static org.testcontainers.postgresql.PostgreSQLContainer postgres = new org.testcontainers.postgresql.PostgreSQLContainer("postgres:18");
 @org.springframework.test.context.DynamicPropertySource
 static void database(org.springframework.test.context.DynamicPropertyRegistry registry) {
  es.oposicionapp.TestDatabase.configure(postgres, registry);
 }

 @Autowired JdbcTemplate jdbc; @Autowired PasswordEncoder passwords;
 @Autowired CuentasService cuentas; @Autowired CorreoAcceso correo;
 @Autowired WebApplicationContext context;
 MockMvc mvc;
 final String secret=UUID.randomUUID()+"-"+UUID.randomUUID();
 @BeforeEach void web(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
 UUID fixture(String role,boolean verified,boolean active,boolean owner){
  UUID id=UUID.randomUUID();
  jdbc.update("insert into core.cuenta(id,correo,password_hash,rol,correo_verificado_en,activa,propietario_inicial) values (?,?,?,?,?,?,?)",
    id,email(id),passwords.encode(secret),role,verified?Timestamp.from(Instant.now()):null,active,owner);
  return id;
 }
 String email(UUID id){return id+"@example.invalid";}
 ResultActions login(String email,String password)throws Exception{
  return mvc.perform(post("/login").contentType("application/x-www-form-urlencoded").with(csrf()).param("correo",email).param("clave",password));
 }
 MockHttpSession entrar(UUID id)throws Exception{
  return (MockHttpSession)login(email(id),secret).andExpect(status().isFound()).andReturn().getRequest().getSession(false);
 }
 @Test void loginSoloCamposNecesariosRotaSesionYNoConservaCredenciales()throws Exception{
  UUID id=fixture("USER",true,true,false);var session=new MockHttpSession();String old=session.getId();
  var result=mvc.perform(post("/login").session(session).contentType("application/x-www-form-urlencoded").with(csrf()).param("correo",email(id)).param("clave",secret))
   .andExpect(redirectedUrl("/c1-tai/plan/")).andReturn();
  assertThat(session.getId()).isNotEqualTo(old);
  var auth=((SecurityContext)session.getAttribute("SPRING_SECURITY_CONTEXT")).getAuthentication();
  assertThat(auth.getCredentials()).isNull();assertThat(auth.getName()).isEqualTo(id.toString());
  assertThat(auth.getDetails()).isNull();
  assertThat(auth.getPrincipal()).isInstanceOf(PrincipalCuenta.class);
  assertThat(auth.getPrincipal().toString().contains(secret)).isFalse();
  mvc.perform(get("/c1-tai/examenes").session(session)).andExpect(status().isOk());
  mvc.perform(post("/login").contentType("application/x-www-form-urlencoded").with(csrf()).param("correo",email(id)).param("clave",secret).param("rol","ADMIN")).andExpect(status().isBadRequest());
  mvc.perform(post("/login").contentType("application/x-www-form-urlencoded").with(csrf()).param("correo",email(id),email(id)).param("clave",secret)).andExpect(status().isBadRequest());
  mvc.perform(post("/login?correo=prohibido").contentType("application/x-www-form-urlencoded").with(csrf()).param("correo",email(id)).param("clave",secret)).andExpect(status().isBadRequest());
 }
 @Test void erroresGenericosSinEnumeracionNiAccesoBasico()throws Exception{
  for(UUID id:new UUID[]{fixture("USER",true,false,false),fixture("USER",false,true,false),UUID.randomUUID()}){
   login(email(id),secret).andExpect(redirectedUrl("/login?error"));
  }
  UUID id=fixture("USER",true,true,false);login(email(id),UUID.randomUUID().toString()).andExpect(redirectedUrl("/login?error"));
  mvc.perform(get("/c1-tai/plan/api/estado").with(httpBasic(email(id),secret))).andExpect(status().isUnauthorized());
  var html=mvc.perform(get("/login?error")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertThat(html.contains(secret)||html.contains(email(id))||html.contains("password_hash")).isFalse();
 }
 @Test void csrfEnAccesoRecuperacionAdministracionYLogout()throws Exception{
  mvc.perform(post("/login").param("correo","x@example.invalid").param("clave",secret)).andExpect(status().isForbidden());
  mvc.perform(post("/auth/recuperar").param("correo","x@example.invalid")).andExpect(status().isForbidden());
  var session=entrar(fixture("ADMIN",true,true,false));
  mvc.perform(post("/admin/usuarios/invitar").session(session).param("correo","x@example.invalid").param("rol","ADMIN")).andExpect(status().isForbidden());
  mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
  mvc.perform(post("/logout").session(session).with(csrf())).andExpect(redirectedUrl("/login?salida"));
  assertThat(session.isInvalid()).isTrue();
 }
 @Test void propietarioSinVerificarSoloAdminYUsuarioNoAdmin()throws Exception{
  UUID owner=fixture("ADMIN",false,true,true);var session=entrar(owner);
  mvc.perform(get("/admin/usuarios").session(session)).andExpect(status().isOk());
  mvc.perform(get("/c1-tai/examenes").session(session)).andExpect(status().isForbidden());
  assertThat(jdbc.queryForObject("select correo_verificado_en is null from core.cuenta where id=?",Boolean.class,owner)).isTrue();
  var user=entrar(fixture("USER",true,true,false));
  mvc.perform(get("/admin/usuarios").session(user)).andExpect(status().isForbidden());
  mvc.perform(post("/admin/usuarios/invitar").session(user).with(csrf()).param("correo","x@example.invalid").param("rol","ADMIN")).andExpect(status().isForbidden());
 }
 @Test void revocaSesionAlDesactivarOCambiarVersionYExpiraA8Horas()throws Exception{
  UUID id=fixture("USER",true,true,false);var session=entrar(id);
  jdbc.update("update core.cuenta set activa=false where id=?",id);
  mvc.perform(get("/c1-tai/examenes").session(session)).andExpect(status().isUnauthorized());assertThat(session.isInvalid()).isTrue();
  id=fixture("USER",true,true,false);session=entrar(id);jdbc.update("update core.cuenta set version_seguridad=version_seguridad+1 where id=?",id);
  mvc.perform(get("/c1-tai/examenes").session(session)).andExpect(status().isUnauthorized());
  id=fixture("USER",true,true,false);var p=new PrincipalCuenta(id,"USER",true,0,Instant.now().minus(Duration.ofHours(8)));
  mvc.perform(get("/c1-tai/examenes").with(authentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.autoridades())))).andExpect(status().isUnauthorized());
 }
 @Test void administracionMinimaSinHashesYNoPuedeQuitarseSuAcceso()throws Exception{
  UUID admin=fixture("ADMIN",true,true,false);var session=entrar(admin);
  var html=mvc.perform(get("/admin/usuarios").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertThat(html.contains(secret)||html.contains("pbkdf2")||html.contains("token_hash")||html.contains("version_seguridad")).isFalse();
  assertThatThrownBy(()->cuentas.actualizar(admin,admin,"USER",true)).isInstanceOf(ResponseStatusException.class);
  UUID invited=cuentas.invitar(admin,UUID.randomUUID()+"@example.invalid","USER");
  assertThat(jdbc.queryForObject("select password_hash is null and correo_verificado_en is null from core.cuenta where id=?",Boolean.class,invited)).isTrue();
  assertThatThrownBy(()->cuentas.listar(invited,0)).isInstanceOf(ResponseStatusException.class);
 }
 @Test void invitacionDeUnUsoHashEnBaseYVerificacionReal(){
  UUID admin=fixture("ADMIN",true,true,false),id=cuentas.invitar(admin,UUID.randomUUID()+"@example.invalid","USER");
  var carta=cuentas.emitir(id,"INVITACION");
  assertThat(jdbc.queryForObject("select token_hash=? from core.token_cuenta where cuenta_id=? and usado_en is null",Boolean.class,CuentasService.digest(carta.token()),id)).isTrue();
  assertThat(carta.toString()).isEqualTo("Carta[contenido reservado]");
  cuentas.consumir(carta.token(),secret);
  assertThat(jdbc.queryForObject("select correo_verificado_en is not null from core.cuenta where id=?",Boolean.class,id)).isTrue();
  assertThat(passwords.matches(secret,jdbc.queryForObject("select password_hash from core.cuenta where id=?",String.class,id))).isTrue();
  assertThatThrownBy(()->cuentas.consumir(carta.token(),secret)).isInstanceOf(ResponseStatusException.class);
  assertThatThrownBy(()->cuentas.emitir(id,"INVITACION")).isInstanceOf(ResponseStatusException.class);
 }
 @Test void recuperacionCaducaNoReactivaCuentaYRevocaSesion()throws Exception{
  UUID id=fixture("USER",true,true,false);var session=entrar(id);var carta=cuentas.emitir(id,"RECUPERAR");
  cuentas.consumir(carta.token(),secret);
  mvc.perform(get("/c1-tai/examenes").session(session)).andExpect(status().isUnauthorized());
  var expired=cuentas.emitir(id,"RECUPERAR");jdbc.update("update core.token_cuenta set caduca_en=current_timestamp-interval '1 second' where token_hash=?",CuentasService.digest(expired.token()));
  assertThatThrownBy(()->cuentas.consumir(expired.token(),secret)).isInstanceOf(ResponseStatusException.class);
  var disabled=cuentas.emitir(id,"RECUPERAR");jdbc.update("update core.cuenta set activa=false where id=?",id);
  assertThatThrownBy(()->cuentas.consumir(disabled.token(),secret)).isInstanceOf(ResponseStatusException.class);
  assertThat(jdbc.queryForObject("select activa from core.cuenta where id=?",Boolean.class,id)).isFalse();
 }
 @Test void verificacionSinCambiarClaveYEmisionAnteriorInvalidada(){
  UUID id=fixture("ADMIN",false,true,true);String hash=jdbc.queryForObject("select password_hash from core.cuenta where id=?",String.class,id);
  var old=cuentas.emitir(id,"VERIFICAR");var fresh=cuentas.emitir(id,"VERIFICAR");
  assertThatThrownBy(()->cuentas.consumir(old.token(),null)).isInstanceOf(ResponseStatusException.class);
  cuentas.consumir(fresh.token(),null);
  assertThat(jdbc.queryForObject("select password_hash=? and correo_verificado_en is not null from core.cuenta where id=?",Boolean.class,hash,id)).isTrue();
 }
 @Test void tokenNoSeConsumeConClaveInvalidaYDosSolicitudesSoloLoConsumenUnaVez()throws Exception{
  UUID admin=fixture("ADMIN",true,true,false),id=cuentas.invitar(admin,UUID.randomUUID()+"@example.invalid","USER");
  var carta=cuentas.emitir(id,"INVITACION");
  assertThatThrownBy(()->cuentas.consumir(carta.token(),"corta")).isInstanceOf(ResponseStatusException.class);
  assertThat(jdbc.queryForObject("select usado_en is null from core.token_cuenta where token_hash=?",Boolean.class,CuentasService.digest(carta.token()))).isTrue();
  try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
   java.util.concurrent.Callable<Boolean> consume=()->{try{cuentas.consumir(carta.token(),secret);return true;}catch(ResponseStatusException ex){return false;}};
   var a=executor.submit(consume);var b=executor.submit(consume);
   assertThat(java.util.List.of(a.get(15,java.util.concurrent.TimeUnit.SECONDS),b.get(15,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
  }
 }
 @Test void smtpNoDisponibleNoFingeEnvioYNuncaIncluyeTokenEnQuery()throws Exception{
  assertThat(correo.disponible()).isFalse();
  mvc.perform(post("/auth/recuperar").with(csrf()).param("correo",UUID.randomUUID()+"@example.invalid")).andExpect(status().isServiceUnavailable());
  mvc.perform(get("/auth/correo?token=oculto")).andExpect(status().isBadRequest());
  mvc.perform(get("/auth/correo?modo=clave")).andExpect(status().isOk());
 }
 @Test void limiteDeIntentosYExpiracionDeVentana(){
  Instant start=Instant.parse("2026-01-01T00:00:00Z");var limit=new LimiteAcceso(Clock.fixed(start,ZoneOffset.UTC));
  for(int i=0;i<8;i++)assertThat(limit.permitir("login","127.0.0.1","cuenta")).isTrue();
  assertThat(limit.permitir("login","otra-ip","cuenta")).isFalse();
  var ipLimit=new LimiteAcceso(Clock.fixed(start,ZoneOffset.UTC));
  for(int i=0;i<120;i++)assertThat(ipLimit.permitir("login","ip",UUID.randomUUID().toString())).isTrue();
  assertThat(ipLimit.permitir("login","ip","nueva")).isFalse();
 }
 @Test void politicaClaveYBootstrapNoSobrescribe(){
  assertThatThrownBy(()->CuentasService.validarClave("123456789012345")).isInstanceOf(ResponseStatusException.class);
  assertThatThrownBy(()->CuentasService.validarClave("corta")).isInstanceOf(ResponseStatusException.class);
  fixture("ADMIN",true,true,false);
  assertThat(cuentas.crearPropietario("no-sobrescribir@example.invalid",secret)).isFalse();
  assertThat(jdbc.queryForObject("select count(*) from core.cuenta where correo='no-sobrescribir@example.invalid'",Integer.class)).isZero();
 }
}
