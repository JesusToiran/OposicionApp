package es.oposicionapp.c1tai.biblioteca;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@Controller
public class PlanTaiController {
	private final PlanTaiService service;
	public PlanTaiController(PlanTaiService service) { this.service=service; }
	@GetMapping({"/c1-tai/plan/","/c1-tai/plan/index.html"})
	public String inicio() { return "plan/index"; }
	@GetMapping("/c1-tai/plan")
	public String redirect() { return "redirect:/c1-tai/plan/"; }
	@GetMapping("/c1-tai/plan/data/{nombre}") @ResponseBody
	public ResponseEntity<JsonNode> datos(@PathVariable String nombre) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.datos(nombre)); }
	@GetMapping("/c1-tai/plan/api/estado") @ResponseBody
	public ResponseEntity<PlanTaiService.Estado> estado(Principal principal) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.estado(principal.getName())); }
	@PutMapping("/c1-tai/plan/api/estado") @ResponseBody
	public ResponseEntity<Map<String,Long>> guardar(Principal principal,@RequestBody JsonNode request) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("revision",service.guardar(principal.getName(),request))); }
	@GetMapping("/c1-tai/plan/{*ruta}")
	public ResponseEntity<byte[]> recurso(@PathVariable String ruta) throws IOException {
		String path=ruta.startsWith("/") ? ruta.substring(1) : ruta;
		if (java.util.Set.of("app.js","historical.js","scoring.js","styles.css","bridge.js","bridge.css").contains(path)) {
			var resource=new ClassPathResource("static/plan/"+path);
			if (!resource.exists()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
			return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(path.endsWith(".js")?"text/javascript":"text/css")).body(resource.getContentAsByteArray());
		}
		var resource=service.recurso(path);
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(resource.tipo())).body(resource.contenido());
	}
}
