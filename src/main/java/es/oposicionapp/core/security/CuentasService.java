package es.oposicionapp.core.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import es.oposicionapp.c1tai.importacion.ImportacionCsvService;

@Service
public class CuentasService {
	private final JdbcTemplate jdbc;
	private final PasswordEncoder passwords;
	private final SecureRandom random=new SecureRandom();
	public CuentasService(JdbcTemplate jdbc,PasswordEncoder passwords){this.jdbc=jdbc;this.passwords=passwords;}
	public static String correo(String value){
		String email=value==null?"":value.strip().toLowerCase(Locale.ROOT);
		if(email.length()>254||!email.matches("[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\\.[a-z]{2,63}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		return email;
	}
	public static void validarClave(String value){
		if(value==null||value.codePointCount(0,value.length())<15||value.codePointCount(0,value.length())>128
				||java.util.Set.of("passwordpassword","123456789012345","1234567890123456","contraseñacontraseña").contains(value.toLowerCase(Locale.ROOT)))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
	}
	Registro buscar(String email){
		var found=jdbc.query("select id,password_hash,rol,activa,correo_verificado_en,propietario_inicial,version_seguridad from core.cuenta where correo=?",
			(rs,n)->new Registro(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getBoolean(4),rs.getTimestamp(5)!=null,rs.getBoolean(6),rs.getLong(7)),email);
		return found.isEmpty()?null:found.getFirst();
	}
	public boolean vigente(PrincipalCuenta p){
		Integer count=jdbc.queryForObject("select count(*) from core.cuenta where id=? and activa and version_seguridad=? and rol=? and (correo_verificado_en is not null)=?",
			Integer.class,p.id(),p.version(),p.rol(),p.verificada());
		return count!=null&&count==1;
	}
	@Transactional
	public boolean crearPropietario(String email,String clave){
		validarClave(clave);email=correo(email);jdbc.execute("select pg_advisory_xact_lock(849205)");
		if(jdbc.queryForObject("select count(*) from core.cuenta",Integer.class)!=0)return false;
		UUID id=UUID.randomUUID();
		jdbc.update("insert into core.cuenta(id,correo,password_hash,rol,propietario_inicial) values (?,?,?,'ADMIN',true)",id,email,passwords.encode(clave));
		auditar(id,id,"PROPIETARIO_CREADO_CORREO_PENDIENTE");return true;
	}
	public boolean hayCuentas(){return jdbc.queryForObject("select count(*) from core.cuenta",Integer.class)>0;}
	@Transactional(readOnly=true)
	public List<VistaCuenta> listar(UUID actor,int pagina){
		admin(actor);if(pagina<0||pagina>100_000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		return jdbc.query("select id,correo,rol,activa,correo_verificado_en is not null as verificada,password_hash is not null as con_clave from core.cuenta order by correo,id limit 21 offset ?",
			(rs,n)->new VistaCuenta(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getBoolean(4),rs.getBoolean(5),rs.getBoolean(6)),pagina*20);
	}
	@Transactional
	public UUID invitar(UUID actor,String email,String rol){
		jdbc.execute("select pg_advisory_xact_lock(849205)");admin(actor);email=correo(email);rol(rol);
		if(jdbc.queryForObject("select count(*) from core.cuenta where correo=?",Integer.class,email)>0)throw new ResponseStatusException(HttpStatus.CONFLICT);
		UUID id=UUID.randomUUID();jdbc.update("insert into core.cuenta(id,correo,rol) values (?,?,?)",id,email,rol);auditar(actor,id,"INVITACION_CREADA");return id;
	}
	@Transactional
	public void actualizar(UUID actor,UUID target,String rol,boolean activa){
		jdbc.execute("select pg_advisory_xact_lock(849205)");admin(actor);rol(rol);
		var found=jdbc.queryForList("select rol,activa from core.cuenta where id=? for update",target);
		if(found.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		if(actor.equals(target)&&(!activa||!rol.equals("ADMIN")))throw new ResponseStatusException(HttpStatus.CONFLICT);
		var before=found.getFirst();
		if(before.get("rol").equals(rol)&&before.get("activa").equals(activa))return;
		if(before.get("rol").equals("ADMIN")&&Boolean.TRUE.equals(before.get("activa"))&&(!activa||!rol.equals("ADMIN"))){
			int others=jdbc.queryForObject("select count(*) from core.cuenta where id<>? and rol='ADMIN' and activa and password_hash is not null and (correo_verificado_en is not null or propietario_inicial)",Integer.class,target);
			if(others==0)throw new ResponseStatusException(HttpStatus.CONFLICT);
		}
		jdbc.update("update core.cuenta set rol=?,activa=?,propietario_inicial=propietario_inicial and ?='ADMIN',version_seguridad=version_seguridad+1 where id=?",rol,activa,rol,target);
		auditar(actor,target,"PERMISOS_O_ESTADO_CAMBIADOS");
	}
	private void admin(UUID actor){
		if(jdbc.queryForObject("select count(*) from core.cuenta where id=? and activa and rol='ADMIN'",Integer.class,actor)!=1)throw new ResponseStatusException(HttpStatus.FORBIDDEN);
	}
	private void rol(String role){if(!List.of("USER","ADMIN").contains(role))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);}
	void auditar(UUID actor,UUID target,String event){jdbc.update("insert into core.auditoria_cuentas(id,actor_id,cuenta_id,evento) values (?,?,?,?)",UUID.randomUUID(),actor,target,event);}
	// Solo se invoca desde el transporte de correo o una prueba, nunca desde una respuesta web.
	@Transactional
	public Carta emitir(UUID cuenta,String purpose){
		if(!List.of("INVITACION","VERIFICAR","RECUPERAR").contains(purpose))throw new IllegalArgumentException();
		var row=jdbc.queryForMap("select correo,activa,password_hash is not null as con_clave,correo_verificado_en is not null as verificada from core.cuenta where id=? for update",cuenta);
		if(!Boolean.TRUE.equals(row.get("activa")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		if(purpose.equals("INVITACION")&&Boolean.TRUE.equals(row.get("con_clave")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		if(purpose.equals("RECUPERAR")&&(!Boolean.TRUE.equals(row.get("con_clave"))||!Boolean.TRUE.equals(row.get("verificada"))))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		jdbc.update("update core.token_cuenta set usado_en=current_timestamp where cuenta_id=? and proposito=? and usado_en is null",cuenta,purpose);
		byte[] bytes=new byte[32];random.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		Instant expiry=Instant.now().plus(purpose.equals("RECUPERAR")?Duration.ofMinutes(15):Duration.ofHours(24));
		jdbc.update("insert into core.token_cuenta(id,cuenta_id,proposito,token_hash,caduca_en) values (?,?,?,?,?)",UUID.randomUUID(),cuenta,purpose,digest(token),Timestamp.from(expiry));
		return new Carta((String)row.get("correo"),token,purpose);
	}
	@Transactional
	public void consumir(String token,String clave){
		if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		var records=jdbc.queryForList("select id,cuenta_id,proposito from core.token_cuenta where token_hash=? and usado_en is null and caduca_en>current_timestamp",digest(token));
		if(records.size()!=1)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		var row=records.getFirst();UUID id=(UUID)row.get("cuenta_id");String purpose=(String)row.get("proposito");
		// Mismo orden de bloqueo que la emisión: cuenta y después token.
		if(jdbc.queryForList("select id from core.cuenta where id=? and activa for update",UUID.class,id).isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		if(jdbc.queryForList("select id from core.token_cuenta where id=? and token_hash=? and usado_en is null and caduca_en>current_timestamp for update",UUID.class,row.get("id"),digest(token)).isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		if(purpose.equals("VERIFICAR")){
			if(clave!=null&&!clave.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
			jdbc.update("update core.cuenta set correo_verificado_en=current_timestamp,version_seguridad=version_seguridad+1 where id=?",id);
		}else{
			validarClave(clave);
			if(purpose.equals("INVITACION"))jdbc.update("update core.cuenta set password_hash=?,correo_verificado_en=current_timestamp,version_seguridad=version_seguridad+1 where id=?",passwords.encode(clave),id);
			else jdbc.update("update core.cuenta set password_hash=?,version_seguridad=version_seguridad+1 where id=?",passwords.encode(clave),id);
		}
		jdbc.update("update core.token_cuenta set usado_en=current_timestamp where cuenta_id=? and usado_en is null",id);
		auditar(id,id,purpose+"_COMPLETADA");
	}
	public static String digest(String token){return ImportacionCsvService.hash(token.getBytes(StandardCharsets.UTF_8));}
	public UUID cuentaRecuperable(String correo){var rows=jdbc.queryForList("select id from core.cuenta where correo=? and activa and correo_verificado_en is not null and password_hash is not null",UUID.class,correo(correo));return rows.isEmpty()?null:rows.getFirst();}
	public record VistaCuenta(UUID id,String correo,String rol,boolean activa,boolean verificada,boolean conClave) { }
	record Registro(UUID id,String passwordHash,String rol,boolean activa,boolean verificada,boolean propietaria,long version) { @Override public String toString(){return "Registro[contenido reservado]";} }
	public record Carta(String destinatario,String token,String proposito){@Override public String toString(){return "Carta[contenido reservado]";}}
}
