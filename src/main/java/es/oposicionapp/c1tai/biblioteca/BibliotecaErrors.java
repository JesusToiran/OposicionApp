package es.oposicionapp.c1tai.biblioteca;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice(assignableTypes = BibliotecaController.class)
public class BibliotecaErrors {

	@ExceptionHandler(ResponseStatusException.class)
	public ModelAndView recursoNoDisponible(ResponseStatusException exception) {
		ModelAndView result = new ModelAndView("biblioteca/error");
		result.setStatus(exception.getStatusCode());
		result.addObject("mensaje", exception.getStatusCode().value() == 400
				? "El identificador de la convocatoria no es válido."
				: "No se ha encontrado esta convocatoria.");
		return result;
	}
}
