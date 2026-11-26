package es.oposicionapp.core.security;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Cuenta sintética verificada, exclusivamente en la base de pruebas de navegador. */
public final class NavegadorFixture {
 public static void main(String[] args){
  String correo=System.getenv("PLAN_TEST_EMAIL"),clave=System.getenv("PLAN_TEST_PASSWORD");
  if(correo==null||!correo.endsWith("@example.invalid")||clave==null)throw new IllegalArgumentException("Falta la cuenta sintética.");
  String url=System.getenv("PLAN_TEST_JDBC_URL"),user=System.getenv("PLAN_TEST_DB_USER"),password=System.getenv("PLAN_TEST_DB_PASSWORD");
  if(url==null||!url.matches("jdbc:postgresql://[^/]+/[a-zA-Z0-9_]+_tests")||user==null||password==null||password.isBlank())throw new IllegalArgumentException("Se requiere una base aislada de pruebas con credenciales externas.");
  var db=new DriverManagerDataSource(url,user,password);
  new JdbcTemplate(db).update("insert into core.cuenta(id,correo,password_hash,rol,correo_verificado_en) values (?,?,?,'ADMIN',current_timestamp)",
   UUID.randomUUID(),correo,new CriptografiaAcceso().passwordEncoder().encode(clave));
 }
}
