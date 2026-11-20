package tn.voltpark.session.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.voltpark.session.domain.model.Driver;

import java.util.Optional;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {
    Optional<Driver> findByEmail(String email);
    Optional<Driver> findByBadgeId(String badgeId);
    boolean existsByEmail(String email);
    boolean existsByBadgeId(String badgeId);
}
