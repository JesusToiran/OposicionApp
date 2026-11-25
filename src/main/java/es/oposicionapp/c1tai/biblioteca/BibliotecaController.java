package es.oposicionapp.c1tai.biblioteca;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class BibliotecaController {

	private final BibliotecaService biblioteca;

	public BibliotecaController(BibliotecaService biblioteca) {
		this.biblioteca = biblioteca;
	}

	@GetMapping("/c1-tai/examenes")
	public String listado(Model model) {
		model.addAttribute("convocatorias", biblioteca.listarConvocatorias());
		return "biblioteca/listado";
	}

	@GetMapping("/c1-tai/convocatorias/{identificador}")
	public String detalle(@PathVariable String identificador, Model model) {
		// UUID.fromString acepta formas abreviadas: exigir la representación canónica.
		if (!identificador.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		}
		model.addAttribute("convocatoria", biblioteca.obtenerConvocatoria(UUID.fromString(identificador))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
		return "biblioteca/detalle";
	}
}
