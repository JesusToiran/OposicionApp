package es.oposicionapp.core.security;

import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class AccesoController {
	private final CuentasService cuentas;private final CorreoAcceso correo;private final LimiteAcceso limite;
	public AccesoController(CuentasService cuentas,CorreoAcceso correo,LimiteAcceso limite){this.cuentas=cuentas;this.correo=correo;this.limite=limite;}
	@GetMapping("/login")public String login(){return "acceso/login";}
	@GetMapping("/auth/recuperar")public String recuperar(Model model){model.addAttribute("correoDisponible",correo.disponible());return "acceso/recuperar";}
	@PostMapping("/auth/recuperar")public String recuperar(@RequestParam String correo,HttpServletRequest request){
		if(!limite.permitir("recuperar",request.getRemoteAddr(),correo.toLowerCase(java.util.Locale.ROOT)))throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
		this.correo.recuperar(correo);return "redirect:/auth/recuperar?solicitada";
	}
	@GetMapping("/auth/correo")public String confirmar(@RequestParam(defaultValue="clave")String modo,HttpServletRequest request,Model model){
		if(!java.util.Set.of("clave","verificar").contains(modo)||request.getParameterMap().containsKey("token"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		model.addAttribute("verificar",modo.equals("verificar"));return "acceso/confirmar";
	}
	@PostMapping("/auth/correo")public String confirmar(@RequestParam String token,@RequestParam(required=false)String clave,HttpServletRequest request){
		if(!limite.permitir("token",request.getRemoteAddr(),CuentasService.digest(token)))throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
		cuentas.consumir(token,clave);return "redirect:/login?actualizada";
	}
	@PostMapping("/auth/reenviar-verificacion")public String verificar(Authentication authentication){correo.enviar(principal(authentication).id(),"VERIFICAR");return "redirect:/admin/usuarios?correo";}
	@GetMapping("/admin/usuarios")public String usuarios(Authentication authentication,@RequestParam(defaultValue="0")int pagina,Model model){
		var p=principal(authentication);var rows=cuentas.listar(p.id(),pagina);
		model.addAttribute("cuentas",rows.stream().limit(20).toList());model.addAttribute("pagina",pagina);model.addAttribute("siguiente",rows.size()>20);
		model.addAttribute("actual",p);model.addAttribute("correoDisponible",correo.disponible());return "acceso/usuarios";
	}
	@PostMapping("/admin/usuarios/invitar")public String invitar(Authentication authentication,@RequestParam String correo,@RequestParam String rol){
		UUID id=cuentas.invitar(principal(authentication).id(),correo,rol);if(this.correo.disponible())this.correo.enviar(id,"INVITACION");return "redirect:/admin/usuarios?creada";
	}
	@PostMapping("/admin/usuarios/{id}/actualizar")public String actualizar(Authentication authentication,@PathVariable UUID id,@RequestParam String rol,@RequestParam boolean activa){
		cuentas.actualizar(principal(authentication).id(),id,rol,activa);return "redirect:/admin/usuarios?actualizada";
	}
	@PostMapping("/admin/usuarios/{id}/reenviar")public String reenviar(Authentication authentication,@PathVariable UUID id){
		cuentas.listar(principal(authentication).id(),0);correo.enviar(id,"INVITACION");return "redirect:/admin/usuarios?correo";
	}
	private PrincipalCuenta principal(Authentication authentication){if(authentication==null||!(authentication.getPrincipal() instanceof PrincipalCuenta p))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return p;}
	@ExceptionHandler(ResponseStatusException.class)public ModelAndView error(ResponseStatusException ex){
		var page=new ModelAndView("acceso/error");page.setStatus(ex.getStatusCode());
		page.addObject("mensaje",switch(ex.getStatusCode().value()){
			case 503->"El servicio de correo aún no está configurado. Contacta con la administración.";
			case 409->"No se ha aplicado el cambio. Revisa la cuenta o los permisos; debe conservarse un administrador activo.";
			case 429->"Demasiadas solicitudes. Espera unos minutos antes de intentarlo de nuevo.";
			default->"La solicitud o el enlace no es válido, ha caducado o ya se ha utilizado.";});return page;
	}
}
