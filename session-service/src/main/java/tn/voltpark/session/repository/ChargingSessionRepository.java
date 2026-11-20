package tn.voltpark.session.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.voltpark.session.domain.model.ChargingSession;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChargingSessionRepository extends JpaRepository<ChargingSession, String> {
    List<ChargingSession> findByDriverId(Long driverId);
    List<ChargingSession> findByChargerId(String chargerId);
    List<ChargingSession> findByStatus(ChargingSession.SessionStatus status);
    Optional<ChargingSession> findFirstByChargerIdAndStatusOrderByStartedAtDesc(String chargerId, ChargingSession.SessionStatus status);
}
