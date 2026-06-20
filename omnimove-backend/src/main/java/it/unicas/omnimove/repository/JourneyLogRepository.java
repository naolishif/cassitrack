package it.unicas.omnimove.repository;

import it.unicas.omnimove.model.JourneyLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface JourneyLogRepository extends JpaRepository<JourneyLog, Long> {
    List<JourneyLog> findByUserId(Long userId);
    List<JourneyLog> findByUserIdAndCreatedAtAfter(Long userId, ZonedDateTime since);
}