package it.unicas.cassitrack.repository;

import it.unicas.cassitrack.model.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TripRepository extends JpaRepository<Trip, String> {

    @Query("SELECT t FROM Trip t JOIN FETCH t.route JOIN FETCH t.bus WHERE t.id IN :ids")
    List<Trip> findAllByIdInWithRouteAndBus(@Param("ids") List<String> ids);
}