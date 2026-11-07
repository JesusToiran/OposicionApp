package es.oposicionapp.c1tai.biblioteca;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.Repository;

public interface EjercicioRepository extends Repository<Ejercicio, UUID> {
	List<Ejercicio> findAllByExamenConvocatoriaIdOrderByDenominacionAscIdAsc(UUID convocatoriaId);
}
