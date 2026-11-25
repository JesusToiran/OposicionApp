package es.oposicionapp.c1tai.biblioteca;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.Repository;

public interface ExamenRepository extends Repository<Examen, UUID> {
	List<Examen> findAllByConvocatoriaIdOrderByDenominacionAscIdAsc(UUID convocatoriaId);
}
