package it.unicas.omnimove.repository;

import it.unicas.omnimove.model.JourneyLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface JourneyLogRepository extends JpaRepository<JourneyLog, Long> {
    List<JourneyLog> findByUserId(Long userId);
    List<JourneyLog> findByUserIdAndCreatedAtAfter(Long userId, ZonedDateTime since);

    /** Returns [originName, destName, count, avgGreenIndex] for the top N routes. */
    @Query("""
        SELECT j.originName, j.destName,
               COUNT(j)            AS uses,
               AVG(j.greenIndex)   AS avgGi
        FROM   JourneyLog j
        WHERE  j.createdAt > :since
          AND  j.originName IS NOT NULL
          AND  j.destName   IS NOT NULL
        GROUP BY j.originName, j.destName
        ORDER BY uses DESC
        """)
    List<Object[]> findTopRoutes(@Param("since") ZonedDateTime since, Pageable pageable);
}