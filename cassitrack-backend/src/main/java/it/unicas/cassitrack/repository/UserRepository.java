package it.unicas.cassitrack.repository;

import it.unicas.cassitrack.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // Este es el metodo que necesitamos ahora:
    Optional<User> findByEmail(String email);
}