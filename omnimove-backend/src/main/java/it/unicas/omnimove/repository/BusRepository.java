package it.unicas.omnimove.repository;

import it.unicas.omnimove.model.Bus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BusRepository extends JpaRepository<Bus, Integer> {
    // 🔍 Questo metodo cercherà nel database Postgres il pullman associato al codice dell'antenna MQTT
    Optional<Bus> findByCurrentVehicleId(String currentVehicleId);
}