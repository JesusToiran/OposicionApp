package es.oposicionapp.c1tai.biblioteca;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "documento_oficial", schema = "c1_tai")
public class DocumentoOficial {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "identificacion_original", length = 500, nullable = false)
	private String identificacionOriginal;

	@Column(name = "procedencia", length = 500, nullable = false)
	private String procedencia;

	@Column(name = "tipo", length = 100, nullable = false)
	private String tipo;

	@Column(name = "referencia_origen", length = 500, nullable = true)
	private String referenciaOrigen;

	@Column(name = "url_origen", length = 2048, nullable = true)
	private String urlOrigen;

	@Column(name = "verificado", nullable = false)
	private boolean verificado;

	@Column(name = "evidencia_verificacion", length = 1000, nullable = true)
	private String evidenciaVerificacion;

	protected DocumentoOficial() {
	}

	public UUID getId() {
		return id;
	}

	public String getIdentificacionOriginal() {
		return identificacionOriginal;
	}

	public String getProcedencia() {
		return procedencia;
	}

	public String getTipo() {
		return tipo;
	}

	public String getReferenciaOrigen() {
		return referenciaOrigen;
	}

	public String getUrlOrigen() {
		return urlOrigen;
	}

	public boolean getVerificado() {
		return verificado;
	}

	public String getEvidenciaVerificacion() {
		return evidenciaVerificacion;
	}
}
