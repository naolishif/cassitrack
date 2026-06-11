package it.unicas.cassitrack.repository;

import it.unicas.cassitrack.model.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TripRepository extends JpaRepository<Trip, String> {
    // Pulita e pronta all'uso!
}