package es.oposicionapp.c1tai.biblioteca;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BibliotecaService {

	private final ConvocatoriaRepository convocatorias;
	private final ExamenRepository examenes;
	private final EjercicioRepository ejercicios;
	private final VinculoDocumentalRepository documentos;

	public BibliotecaService(ConvocatoriaRepository convocatorias, ExamenRepository examenes,
			EjercicioRepository ejercicios, VinculoDocumentalRepository documentos) {
		this.convocatorias = convocatorias;
		this.examenes = examenes;
		this.ejercicios = ejercicios;
		this.documentos = documentos;
	}

	public List<ConvocatoriaResumen> listarConvocatorias() {
		return convocatorias.findAllByOrderByAnioReferenciaDescDenominacionAscIdAsc().stream()
				.map(c -> new ConvocatoriaResumen(c.getId(), c.getDenominacion(), c.getAnioReferencia()))
				.toList();
	}

	public Optional<ConvocatoriaDetalle> obtenerConvocatoria(UUID id) {
		return convocatorias.findById(id).map(convocatoria -> {
			List<Examen> examenesEncontrados = examenes.findAllByConvocatoriaIdOrderByDenominacionAscIdAsc(id);
			List<Ejercicio> ejerciciosEncontrados = ejercicios.findAllByExamenConvocatoriaIdOrderByDenominacionAscIdAsc(id);
			List<VinculoDocumental> vinculos = documentos.findDeConvocatoria(id);
			List<ExamenVista> vistas = examenesEncontrados.stream().map(examen -> new ExamenVista(
					examen.getDenominacion(),
					ejerciciosEncontrados.stream().filter(e -> e.getExamen().getId().equals(examen.getId()))
							.map(e -> new EjercicioVista(e.getDenominacion(), vinculos.stream()
									.filter(v -> v.getEjercicio() != null && v.getEjercicio().getId().equals(e.getId()))
									.map(v -> documentoVista(v.getDocumento())).toList())).toList(),
					vinculos.stream().filter(v -> v.getExamen() != null && v.getExamen().getId().equals(examen.getId()))
							.map(v -> documentoVista(v.getDocumento())).toList())).toList();
			return new ConvocatoriaDetalle(convocatoria.getDenominacion(), convocatoria.getAnioReferencia(),
					convocatoria.getReferenciaOficial(), vistas, vinculos.stream()
							.filter(v -> v.getConvocatoria() != null)
							.map(v -> documentoVista(v.getDocumento())).toList());
		});
	}

	private static DocumentoVista documentoVista(DocumentoOficial documento) {
		return new DocumentoVista(documento.getIdentificacionOriginal(), documento.getProcedencia(),
				documento.getTipo(), documento.getReferenciaOrigen(), documento.getVerificado(),
				documento.getVerificado() ? enlaceSeguro(documento.getUrlOrigen()) : null);
	}

	// Defensa adicional al CHECK SQL: la biblioteca nunca ejecuta ni descarga estas URL.
	static String enlaceSeguro(String valor) {
		if (valor == null) {
			return null;
		}
		try {
			URI uri = URI.create(valor);
			if (("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
					&& uri.getHost() != null && uri.getRawUserInfo() == null && uri.getPort() >= -1
					&& uri.getPort() <= 65535 && valor.chars().noneMatch(Character::isISOControl)) {
				return uri.toASCIIString();
			}
		} catch (IllegalArgumentException ignored) {
			// Una referencia inválida sigue siendo visible como documento, sin enlace.
		}
		return null;
	}

	public record ConvocatoriaResumen(UUID id, String denominacion, Integer anioReferencia) { }
	public record DocumentoVista(String identificacion, String procedencia, String tipo, String referencia,
			boolean verificado, String enlace) { }
	public record EjercicioVista(String denominacion, List<DocumentoVista> documentos) { }
	public record ExamenVista(String denominacion, List<EjercicioVista> ejercicios, List<DocumentoVista> documentos) { }
	public record ConvocatoriaDetalle(String denominacion, Integer anioReferencia, String referenciaOficial,
			List<ExamenVista> examenes, List<DocumentoVista> documentos) { }
}
