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
@Table(name = "ejercicio", schema = "c1_tai")
public class Ejercicio {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "denominacion", length = 300, nullable = false)
	private String denominacion;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "examen_id", nullable = false)
	private Examen examen;

	protected Ejercicio() {
	}

	public UUID getId() {
		return id;
	}

	public String getDenominacion() {
		return denominacion;
	}

	public Examen getExamen() {
		return examen;
	}
}
