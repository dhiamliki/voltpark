package tn.voltpark.charger.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.voltpark.charger.model.AuthorizedBadge;

import java.util.Optional;

@Repository
public interface AuthorizedBadgeRepository extends JpaRepository<AuthorizedBadge, Long> {
    Optional<AuthorizedBadge> findByIdTag(String idTag);
}
