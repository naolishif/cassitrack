package it.unicas.cassitrack.repository;

import it.unicas.cassitrack.model.VehiclePosition;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface VehiclePositionRepository extends CrudRepository<VehiclePosition, String> {

    // 💡 Aggiungi questo: Spring capisce in automatico che deve tornare tutti i record presenti
    List<VehiclePosition> findAll();
}