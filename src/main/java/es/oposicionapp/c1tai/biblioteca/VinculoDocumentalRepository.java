package es.oposicionapp.c1tai.biblioteca;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface VinculoDocumentalRepository extends Repository<VinculoDocumental, UUID> {
	@Query("""
			select v from VinculoDocumental v
			join fetch v.documento
			left join v.examen ex
			left join v.ejercicio ej
			left join ej.examen examenEjercicio
			where v.convocatoria.id = :convocatoriaId
				or ex.convocatoria.id = :convocatoriaId
				or examenEjercicio.convocatoria.id = :convocatoriaId
			order by v.documento.identificacionOriginal, v.id
			""")
	List<VinculoDocumental> findDeConvocatoria(UUID convocatoriaId);
}
