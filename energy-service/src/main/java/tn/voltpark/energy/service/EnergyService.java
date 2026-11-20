package tn.voltpark.energy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.voltpark.energy.config.TariffProperties;
import tn.voltpark.energy.model.MeterReading;
import tn.voltpark.energy.model.SessionEnergy;
import tn.voltpark.energy.repository.MeterReadingRepository;
import tn.voltpark.energy.repository.SessionEnergyRepository;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/**
 * Logique metier des mesures d'energie. Aucune classe gRPC ici : la couche grpc
 * convertit les messages protobuf avant d'appeler ce service.
 */
@Service
public class EnergyService {

    private static final Logger log = LoggerFactory.getLogger(EnergyService.class);

    private final MeterReadingRepository readingRepository;
    private final SessionEnergyRepository sessionRepository;
    private final TariffProperties tariff;

    public EnergyService(MeterReadingRepository readingRepository,
                         SessionEnergyRepository sessionRepository,
                         TariffProperties tariff) {
        this.readingRepository = readingRepository;
        this.sessionRepository = sessionRepository;
        this.tariff = tariff;
    }

    /**
     * Valide et enregistre une mesure, puis met a jour le bilan de la session.
     * La premiere mesure d'une session ouvre la session.
     */
    @Transactional
    public SessionEnergy recordReading(NewReading reading) {
        validate(reading);

        SessionEnergy session = sessionRepository.findById(reading.sessionId()).orElse(null);
        if (session == null) {
            session = sessionRepository.save(
                    SessionEnergy.open(reading.sessionId(), reading.chargerId(), reading.timestamp()));
            log.info("Session {} opened on charger {}", reading.sessionId(), reading.chargerId());
        } else {
            checkConsistency(session, reading);
        }

        // La session est geree par JPA : ses modifications sont enregistrees a la fin de la transaction.
        session.addReading(reading.timestamp(), reading.energyKwh(), tariff.isPeak(reading.timestamp()));
        readingRepository.save(new MeterReading(
                reading.sessionId(), reading.chargerId(), reading.timestamp(),
                reading.powerKw(), reading.energyKwh(), reading.socPercent()));

        log.debug("Reading for session {}: {} kW, {} kWh, soc {}%",
                reading.sessionId(), reading.powerKw(), reading.energyKwh(), reading.socPercent());
        return session;
    }

    /** Cloture la session. Sans effet si elle est deja cloturee. */
    @Transactional
    public SessionEnergy closeSession(String sessionId) {
        SessionEnergy session = findSession(sessionId);
        if (!session.isClosed()) {
            session.close();
            log.info("Session {} closed: {} readings, {} kWh",
                    sessionId, session.getReadingsCount(), session.getTotalEnergyKwh());
        }
        return session;
    }

    @Transactional(readOnly = true)
    public SessionEnergy getSessionEnergy(String sessionId) {
        return findSession(sessionId);
    }

    /** Derniere mesure de la session avec l'estimation du temps restant. */
    @Transactional(readOnly = true)
    public SessionProgressView getLatestProgress(String sessionId) {
        SessionEnergy session = findSession(sessionId);
        List<MeterReading> recent = readingRepository.findTop10BySessionIdOrderByTimestampDesc(sessionId);
        if (recent.isEmpty()) {
            throw new SessionNotFoundException(sessionId);
        }
        MeterReading latest = recent.get(0);
        return new SessionProgressView(
                sessionId,
                latest.getTimestamp(),
                latest.getPowerKw(),
                latest.getEnergyKwh(),
                latest.getSocPercent(),
                estimateMinutesRemaining(recent),
                session.isClosed());
    }

    /**
     * Estime le temps pour atteindre 100 % a partir de la pente du soc entre la plus
     * ancienne et la plus recente des mesures donnees.
     *
     * @return minutes restantes, 0 si la batterie est pleine, -1 si l'estimation est impossible
     *         (moins de 2 mesures ou soc qui n'augmente pas)
     */
    public int estimateMinutesRemaining(List<MeterReading> readings) {
        if (readings.size() < 2) {
            return -1;
        }
        List<MeterReading> sorted = readings.stream()
                .sorted(Comparator.comparing(MeterReading::getTimestamp))
                .toList();
        MeterReading first = sorted.get(0);
        MeterReading last = sorted.get(sorted.size() - 1);

        if (last.getSocPercent() >= 100) {
            return 0;
        }
        double minutes = Duration.between(first.getTimestamp(), last.getTimestamp()).toMillis() / 60_000.0;
        int socGain = last.getSocPercent() - first.getSocPercent();
        if (minutes <= 0 || socGain <= 0) {
            return -1;
        }
        double socPerMinute = socGain / minutes;
        return (int) Math.ceil((100 - last.getSocPercent()) / socPerMinute);
    }

    private SessionEnergy findSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("session_id is required");
        }
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
    }

    private void validate(NewReading reading) {
        if (reading.sessionId() == null || reading.sessionId().isBlank()) {
            throw new InvalidReadingException("session_id is required");
        }
        if (reading.chargerId() == null || reading.chargerId().isBlank()) {
            throw new InvalidReadingException("charger_id is required");
        }
        if (reading.timestamp() == null) {
            throw new InvalidReadingException("timestamp is required");
        }
        if (!(reading.powerKw() >= 0)) {
            throw new InvalidReadingException("power_kw must be positive or zero");
        }
        if (!(reading.energyKwh() >= 0)) {
            throw new InvalidReadingException("energy_kwh must be positive or zero");
        }
        if (reading.socPercent() < 0 || reading.socPercent() > 100) {
            throw new InvalidReadingException("soc_percent must be between 0 and 100");
        }
    }

    private void checkConsistency(SessionEnergy session, NewReading reading) {
        if (session.isClosed()) {
            throw new SessionClosedException(session.getSessionId());
        }
        if (!session.getChargerId().equals(reading.chargerId())) {
            throw new InvalidReadingException("session " + session.getSessionId()
                    + " belongs to charger " + session.getChargerId());
        }
        if (reading.energyKwh() < session.getLastEnergyKwh()) {
            throw new InvalidReadingException("energy_kwh " + reading.energyKwh()
                    + " is lower than the previous reading " + session.getLastEnergyKwh());
        }
        if (reading.timestamp().isBefore(session.getLastReadingAt())) {
            throw new InvalidReadingException("timestamp is older than the previous reading");
        }
    }
}
