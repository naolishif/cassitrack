package it.unicas.cassitrack.repository;

import it.unicas.cassitrack.model.Stop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StopRepository extends JpaRepository<Stop, String> {

    // Un metodo utile se in futuro vorrai esportare solo le fermate attive!
    List<Stop> findByActiveTrue();
}