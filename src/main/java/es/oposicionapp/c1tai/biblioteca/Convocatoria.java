package es.oposicionapp.c1tai.biblioteca;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "convocatoria", schema = "c1_tai")
public class Convocatoria {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "denominacion", length = 300, nullable = false)
	private String denominacion;

	@Column(name = "anio_referencia", nullable = true)
	private Integer anioReferencia;

	@Column(name = "referencia_oficial", length = 500, nullable = true)
	private String referenciaOficial;

	protected Convocatoria() {
	}

	public UUID getId() {
		return id;
	}

	public String getDenominacion() {
		return denominacion;
	}

	public Integer getAnioReferencia() {
		return anioReferencia;
	}

	public String getReferenciaOficial() {
		return referenciaOficial;
	}
}
