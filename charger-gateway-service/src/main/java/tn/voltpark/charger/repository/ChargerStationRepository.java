package tn.voltpark.charger.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.voltpark.charger.model.ChargerStation;

import java.util.Optional;

@Repository
public interface ChargerStationRepository extends JpaRepository<ChargerStation, Long> {
    Optional<ChargerStation> findByChargerId(String chargerId);
}
