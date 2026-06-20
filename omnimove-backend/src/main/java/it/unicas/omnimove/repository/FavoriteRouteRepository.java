package it.unicas.omnimove.repository;

import it.unicas.omnimove.model.FavoriteRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteRouteRepository extends JpaRepository<FavoriteRoute, Long> {
    List<FavoriteRoute> findByUserId(Long userId);

    Optional<FavoriteRoute> findByUserIdAndModeAndOriginNameAndDestName(
            Long userId, String mode, String originName, String destName);
}