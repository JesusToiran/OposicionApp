package es.oposicionapp.c1tai.biblioteca;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
@Table(name = "vinculo_documental", schema = "c1_tai")
public class VinculoDocumental {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "documento_id", nullable = false)
	private DocumentoOficial documento;

	@ManyToOne(fetch = FetchType.LAZY, optional = true)
	@JoinColumn(name = "convocatoria_id", nullable = true)
	private Convocatoria convocatoria;

	@ManyToOne(fetch = FetchType.LAZY, optional = true)
	@JoinColumn(name = "examen_id", nullable = true)
	private Examen examen;

	@ManyToOne(fetch = FetchType.LAZY, optional = true)
	@JoinColumn(name = "ejercicio_id", nullable = true)
	private Ejercicio ejercicio;

	protected VinculoDocumental() {
	}

	public UUID getId() {
		return id;
	}

	public DocumentoOficial getDocumento() {
		return documento;
	}

	public Convocatoria getConvocatoria() {
		return convocatoria;
	}

	public Examen getExamen() {
		return examen;
	}

	public Ejercicio getEjercicio() {
		return ejercicio;
	}
}
