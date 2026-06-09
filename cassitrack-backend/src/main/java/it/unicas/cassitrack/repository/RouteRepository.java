package it.unicas.cassitrack.repository;

import it.unicas.cassitrack.model.Route;
import it.unicas.cassitrack.model.Stop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RouteRepository extends JpaRepository<Route, String> {

}