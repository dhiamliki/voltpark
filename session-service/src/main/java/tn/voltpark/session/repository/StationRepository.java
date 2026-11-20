package tn.voltpark.session.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.voltpark.session.domain.model.Station;

@Repository
public interface StationRepository extends JpaRepository<Station, Long> {
}
