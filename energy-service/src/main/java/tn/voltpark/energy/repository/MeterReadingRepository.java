package tn.voltpark.energy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.voltpark.energy.model.MeterReading;

import java.util.List;

public interface MeterReadingRepository extends JpaRepository<MeterReading, Long> {

    /** Les 10 mesures les plus recentes d'une session, de la plus recente a la plus ancienne. */
    List<MeterReading> findTop10BySessionIdOrderByTimestampDesc(String sessionId);
}
