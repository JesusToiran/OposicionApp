package es.oposicionapp.c1tai.biblioteca;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class BibliotecaController {

	private final BibliotecaService biblioteca;
	private final BibliotecaCsvService csv;

	public BibliotecaController(BibliotecaService biblioteca, BibliotecaCsvService csv) {
		this.biblioteca = biblioteca;
		this.csv = csv;
	}

	@GetMapping("/c1-tai/examenes")
	public String listado(Model model) {
		model.addAttribute("convocatorias", biblioteca.listarConvocatorias());
		return "biblioteca/listado";
	}

	@GetMapping("/c1-tai/convocatorias/{identificador}")
	public String detalle(@PathVariable String identificador,
			@RequestParam(required = false) UUID seccion,
			@RequestParam(defaultValue = "1") int posicion,
			@RequestParam(required = false) UUID variante,
			@RequestParam(required = false) String respuesta, Model model) {
		// UUID.fromString acepta formas abreviadas: exigir la representación canónica.
		if (!identificador.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		}
		model.addAttribute("convocatoria", biblioteca.obtenerConvocatoria(UUID.fromString(identificador))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
		model.addAttribute("secciones", csv.secciones(UUID.fromString(identificador)));
		if (seccion != null) {
			model.addAttribute("pagina", csv.pagina(UUID.fromString(identificador), seccion, posicion, variante, respuesta));
			model.addAttribute("varianteActual", variante);
		}
		return "biblioteca/detalle";
	}

	@GetMapping(value = "/c1-tai/convocatorias/{identificador}", params = "anexo")
	public ResponseEntity<byte[]> anexo(@PathVariable UUID identificador, @RequestParam UUID anexo) throws java.io.IOException {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType("image/webp"))
				.body(csv.anexo(identificador, anexo));
	}
}
