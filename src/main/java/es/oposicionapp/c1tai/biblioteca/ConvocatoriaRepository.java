package es.oposicionapp.c1tai.biblioteca;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

public interface ConvocatoriaRepository extends Repository<Convocatoria, UUID> {
	List<Convocatoria> findAllByOrderByAnioReferenciaDescDenominacionAscIdAsc();
	Optional<Convocatoria> findById(UUID id);
}
