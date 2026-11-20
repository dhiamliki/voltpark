package tn.voltpark.session.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.voltpark.session.domain.model.Reservation;

import java.time.Instant;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByDriverId(Long driverId);

    List<Reservation> findByChargerId(String chargerId);

    @Query("""
        SELECT r FROM Reservation r
        WHERE r.chargerId = :chargerId
          AND r.status IN ('PENDING', 'CONFIRMED', 'ACTIVE')
          AND r.startTime < :endTime
          AND r.endTime > :startTime
    """)
    List<Reservation> findOverlappingReservations(
            @Param("chargerId") String chargerId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);
}
