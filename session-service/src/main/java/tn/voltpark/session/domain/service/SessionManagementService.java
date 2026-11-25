package tn.voltpark.session.domain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.voltpark.energy.grpc.v1.SessionEnergy;
import tn.voltpark.session.api.dto.InvoiceResponse;
import tn.voltpark.session.api.dto.SessionResponse;
import tn.voltpark.session.api.dto.StartSessionRequest;
import tn.voltpark.session.api.dto.StopSessionRequest;
import tn.voltpark.session.api.error.EnergyDataNotFoundException;
import tn.voltpark.session.api.error.EnergyUnavailableException;
import tn.voltpark.session.api.error.ChargerCommunicationException;
import tn.voltpark.session.api.error.ChargerFaultException;
import tn.voltpark.session.api.error.InvalidSessionStateException;
import tn.voltpark.session.api.error.ResourceNotFoundException;
import tn.voltpark.session.client.grpc.EnergyGrpcClient;
import tn.voltpark.session.client.soap.ChargerSoapClient;
import tn.voltpark.session.domain.model.*;
import tn.voltpark.session.repository.ChargerRepository;
import tn.voltpark.session.repository.ChargingSessionRepository;
import tn.voltpark.session.repository.DriverRepository;
import tn.voltpark.session.repository.InvoiceRepository;
import tn.voltpark.session.repository.ReservationRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class SessionManagementService {

    private static final Logger log = LoggerFactory.getLogger(SessionManagementService.class);

    private final ChargingSessionRepository sessionRepository;
    private final InvoiceRepository invoiceRepository;
    private final DriverRepository driverRepository;
    private final ChargerRepository chargerRepository;
    private final ReservationRepository reservationRepository;
    private final ChargerSoapClient chargerSoapClient;
    private final EnergyGrpcClient energyGrpcClient;
    private final TariffCalculationService tariffCalculationService;

    public SessionManagementService(ChargingSessionRepository sessionRepository,
                                    InvoiceRepository invoiceRepository,
                                    DriverRepository driverRepository,
                                    ChargerRepository chargerRepository,
                                    ReservationRepository reservationRepository,
                                    ChargerSoapClient chargerSoapClient,
                                    EnergyGrpcClient energyGrpcClient,
                                    TariffCalculationService tariffCalculationService) {
        this.sessionRepository = sessionRepository;
        this.invoiceRepository = invoiceRepository;
        this.driverRepository = driverRepository;
        this.chargerRepository = chargerRepository;
        this.reservationRepository = reservationRepository;
        this.chargerSoapClient = chargerSoapClient;
        this.energyGrpcClient = energyGrpcClient;
        this.tariffCalculationService = tariffCalculationService;
    }

    /**
     * Demarre une session : appel SOAP RemoteStartTransaction.
     * - borne d'accord : session ACTIVE, borne CHARGING, reservation ACTIVE ;
     * - passerelle injoignable (fallback) : session PENDING_START, borne OCCUPIED (gardee pour
     *   cette session), a relancer avec POST /sessions/{id}/start-attempts ;
     * - SOAP Fault (borne inconnue, occupee, badge refuse) : ChargerFaultException (404/409/403),
     *   rien n'est enregistre.
     */
    @Transactional
    public SessionResponse startSession(StartSessionRequest request) {
        Driver driver = driverRepository.findById(request.driverId())
                .orElseThrow(() -> new ResourceNotFoundException("Conducteur", request.driverId()));

        Charger charger = chargerRepository.findById(request.chargerId())
                .orElseThrow(() -> new ResourceNotFoundException("Borne", request.chargerId()));

        if (charger.getStatus() == Charger.ChargerStatus.CHARGING
                || charger.getStatus() == Charger.ChargerStatus.OCCUPIED) {
            throw new InvalidSessionStateException("La borne " + charger.getId() + " est deja utilisee par une autre session");
        }

        Reservation reservation = null;
        if (request.reservationId() != null) {
            reservation = reservationRepository.findById(request.reservationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Reservation", request.reservationId()));
            if (!reservation.getDriverId().equals(driver.getId()) || !reservation.getChargerId().equals(charger.getId())
                    || reservation.getStatus() != Reservation.ReservationStatus.CONFIRMED) {
                throw new InvalidSessionStateException("La reservation " + reservation.getId()
                        + " n'est pas une reservation confirmee de ce conducteur sur cette borne");
            }
        }

        ChargingSession session = new ChargingSession(driver.getId(), charger.getId(), request.reservationId());
        int connector = request.connectorId() != null ? request.connectorId() : 1;

        // Appel SOAP inter-service avec Retry et CircuitBreaker Resilience4j.
        // Une SOAP Fault remonte en ChargerFaultException : la transaction est annulee.
        ChargerSoapClient.RemoteStartResult soapResult = sendRemoteStart(session, charger, driver, connector);

        if (soapResult.accepted()) {
            markActive(session, charger, reservation, soapResult.transactionId());
        } else if (soapResult.fallbackTriggered()) {
            // Fallback Resilience4j : passerelle SOAP injoignable ou circuit ouvert -> PENDING_START
            session.setStatus(ChargingSession.SessionStatus.PENDING_START);
            session.setTransactionId(null);
            charger.setStatus(Charger.ChargerStatus.OCCUPIED);
            log.warn("Session {} set to PENDING_START due to SOAP gateway fallback: {}", session.getId(), soapResult.message());
        } else {
            throw new InvalidSessionStateException("Demarrage refuse par la borne: " + soapResult.message());
        }

        chargerRepository.save(charger);
        ChargingSession saved = sessionRepository.save(session);
        return toSessionResponse(saved);
    }

    /**
     * Nouvel essai de demarrage d'une session PENDING_START (POST /sessions/{id}/start-attempts).
     * - borne d'accord : session ACTIVE ;
     * - passerelle toujours injoignable : 502, la session reste PENDING_START ;
     * - refus de la borne (SOAP Fault ou REJECTED) : session FAILED, borne liberee, erreur metier.
     */
    @Transactional(noRollbackFor = {ChargerFaultException.class, InvalidSessionStateException.class})
    public SessionResponse retryStart(String sessionId) {
        ChargingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session de charge", sessionId));
        if (session.getStatus() != ChargingSession.SessionStatus.PENDING_START) {
            throw new InvalidSessionStateException("La session " + sessionId + " n'est pas en attente de demarrage ("
                    + session.getStatus() + ")");
        }
        Charger charger = chargerRepository.findById(session.getChargerId())
                .orElseThrow(() -> new ResourceNotFoundException("Borne", session.getChargerId()));
        Driver driver = driverRepository.findById(session.getDriverId())
                .orElseThrow(() -> new ResourceNotFoundException("Conducteur", session.getDriverId()));
        Reservation reservation = session.getReservationId() != null
                ? reservationRepository.findById(session.getReservationId()).orElse(null) : null;

        ChargerSoapClient.RemoteStartResult soapResult;
        try {
            soapResult = sendRemoteStart(session, charger, driver, 1);
        } catch (ChargerFaultException e) {
            markFailed(session, charger);
            throw e;
        }

        if (soapResult.fallbackTriggered()) {
            throw new ChargerCommunicationException("Passerelle borne toujours injoignable, la session reste en PENDING_START");
        }
        if (!soapResult.accepted()) {
            markFailed(session, charger);
            throw new InvalidSessionStateException("Demarrage refuse par la borne: " + soapResult.message());
        }

        markActive(session, charger, reservation, soapResult.transactionId());
        chargerRepository.save(charger);
        return toSessionResponse(sessionRepository.save(session));
    }

    private ChargerSoapClient.RemoteStartResult sendRemoteStart(ChargingSession session, Charger charger,
                                                                Driver driver, int connector) {
        String resIdStr = session.getReservationId() != null ? session.getReservationId().toString() : null;
        return chargerSoapClient.remoteStartTransaction(
                charger.getId(), connector, driver.getBadgeId(), resIdStr, session.getId());
    }

    private void markActive(ChargingSession session, Charger charger, Reservation reservation, Integer transactionId) {
        session.setStatus(ChargingSession.SessionStatus.ACTIVE);
        session.setTransactionId(transactionId);
        charger.setStatus(Charger.ChargerStatus.CHARGING);
        if (reservation != null) {
            reservation.setStatus(Reservation.ReservationStatus.ACTIVE);
            reservationRepository.save(reservation);
        }
        log.info("Session {} started actively with transactionId {}", session.getId(), transactionId);
    }

    private void markFailed(ChargingSession session, Charger charger) {
        session.setStatus(ChargingSession.SessionStatus.FAILED);
        sessionRepository.save(session);
        charger.setStatus(Charger.ChargerStatus.AVAILABLE);
        chargerRepository.save(charger);
        log.warn("Session {} failed: start refused by the charger, charger {} released", session.getId(), charger.getId());
    }

    @Transactional
    public SessionResponse stopSession(String sessionId, StopSessionRequest request) {
        // PATCH /sessions/{id} : seule la transition vers STOPPED est autorisee (400 sinon)
        if (request == null || request.status() != ChargingSession.SessionStatus.STOPPED) {
            throw new IllegalArgumentException("Seule la mise a jour {\"status\":\"STOPPED\"} est acceptee");
        }

        ChargingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session de charge", sessionId));

        if (session.getStatus() == ChargingSession.SessionStatus.COMPLETED ||
                session.getStatus() == ChargingSession.SessionStatus.STOPPED ||
                session.getStatus() == ChargingSession.SessionStatus.FAILED ||
                session.getStatus() == ChargingSession.SessionStatus.CANCELLED) {
            throw new InvalidSessionStateException("La session " + sessionId + " est deja cloturee");
        }

        Charger charger = chargerRepository.findById(session.getChargerId())
                .orElse(null);

        // Session jamais demarree sur la borne (PENDING_START) : annulation, borne liberee,
        // pas de facture. La reservation reste CONFIRMED et peut servir a un nouvel essai.
        if (session.getStatus() == ChargingSession.SessionStatus.PENDING_START) {
            if (charger != null) {
                charger.setStatus(Charger.ChargerStatus.AVAILABLE);
                chargerRepository.save(charger);
            }
            session.setStoppedAt(Instant.now());
            session.setStatus(ChargingSession.SessionStatus.CANCELLED);
            log.info("Pending session {} cancelled, charger {} released", sessionId, session.getChargerId());
            return toSessionResponse(sessionRepository.save(session));
        }

        // Appel SOAP pour arreter la transaction
        if (session.getTransactionId() != null) {
            chargerSoapClient.remoteStopTransaction(session.getChargerId(), session.getTransactionId());
        }

        if (charger != null) {
            charger.setStatus(Charger.ChargerStatus.AVAILABLE);
            chargerRepository.save(charger);
        }

        // Recuperation des mesures energetiques depuis l'energy-service gRPC
        BigDecimal totalEnergy = BigDecimal.ZERO;
        BigDecimal peakKwh = BigDecimal.ZERO;
        BigDecimal offPeakKwh = BigDecimal.ZERO;
        // energy-service injoignable : facture creee en PENDING_ENERGY, a recalculer plus tard
        boolean energyPending = false;

        SessionEnergy energyProto = null;
        boolean noReadings = false;
        try {
            energyProto = energyGrpcClient.getSessionEnergy(sessionId);
        } catch (EnergyDataNotFoundException e) {
            // la borne n'a envoye aucune mesure pour cette session
            noReadings = true;
        }

        if (energyProto != null) {
            totalEnergy = BigDecimal.valueOf(energyProto.getTotalEnergyKwh());
            peakKwh = BigDecimal.valueOf(energyProto.getPeakKwh());
            offPeakKwh = BigDecimal.valueOf(energyProto.getOffPeakKwh());
            log.info("Retrieved energy from gRPC for session {}: total={}, peak={}, offPeak={}",
                    sessionId, totalEnergy, peakKwh, offPeakKwh);
        } else if (request != null && request.totalEnergyKwh() != null) {
            totalEnergy = request.totalEnergyKwh();
            peakKwh = request.peakKwh() != null ? request.peakKwh() : BigDecimal.ZERO;
            offPeakKwh = request.offPeakKwh() != null ? request.offPeakKwh() : BigDecimal.ZERO;
        } else if (noReadings) {
            log.warn("No meter values for session {}: invoicing 0 kWh", sessionId);
        } else {
            energyPending = true;
            log.warn("Energy unavailable for session {}: invoice left PENDING_ENERGY", sessionId);
        }

        Instant stoppedAt = Instant.now();
        Instant completedAt = (request != null && request.chargeCompletedAt() != null) ?
                request.chargeCompletedAt() : stoppedAt;

        session.setStoppedAt(stoppedAt);
        session.setChargeCompletedAt(completedAt);
        session.setTotalEnergyKwh(totalEnergy);
        session.setPeakKwh(peakKwh);
        session.setOffPeakKwh(offPeakKwh);
        session.setStatus(ChargingSession.SessionStatus.COMPLETED);

        ChargingSession savedSession = sessionRepository.save(session);

        // la reservation utilisee par cette session est terminee
        if (session.getReservationId() != null) {
            reservationRepository.findById(session.getReservationId()).ifPresent(reservation -> {
                reservation.setStatus(Reservation.ReservationStatus.COMPLETED);
                reservationRepository.save(reservation);
            });
        }

        // Generation de la facture selon la logique de tarification
        Station station = charger != null ? charger.getStation() : null;
        if (station != null) {
            TariffCalculationService.CalculationResult calc = tariffCalculationService.calculateInvoice(
                    station, totalEnergy, peakKwh, offPeakKwh, completedAt, stoppedAt);

            Invoice invoice = new Invoice(
                    savedSession.getId(),
                    savedSession.getDriverId(),
                    calc.totalEnergyKwh(),
                    calc.peakKwh(),
                    calc.offPeakKwh(),
                    calc.peakEnergyCost(),
                    calc.offPeakEnergyCost(),
                    calc.energyCost(),
                    calc.idleDurationMinutes(),
                    calc.idleFee(),
                    calc.totalAmount(),
                    "TND"
            );
            if (energyPending) {
                invoice.setStatus(Invoice.InvoiceStatus.PENDING_ENERGY);
            }
            invoiceRepository.save(invoice);
            log.info("Invoice created for session {}: status={}, totalAmount={} TND (energy={}, idleFee={})",
                    sessionId, invoice.getStatus(), calc.totalAmount(), calc.energyCost(), calc.idleFee());
        }

        return toSessionResponse(savedSession);
    }

    /**
     * Recalcule une facture PENDING_ENERGY quand energy-service est de nouveau joignable.
     */
    @Transactional
    public InvoiceResponse recalculateInvoice(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Facture", invoiceId));
        if (invoice.getStatus() != Invoice.InvoiceStatus.PENDING_ENERGY) {
            throw new InvalidSessionStateException("La facture " + invoiceId + " n'est pas en attente de mesures");
        }
        ChargingSession session = sessionRepository.findById(invoice.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Session de charge", invoice.getSessionId()));
        Station station = chargerRepository.findById(session.getChargerId())
                .map(Charger::getStation)
                .orElseThrow(() -> new ResourceNotFoundException("Borne", session.getChargerId()));

        // NOT_FOUND -> EnergyDataNotFoundException (404)
        SessionEnergy energy = energyGrpcClient.getSessionEnergy(session.getId());
        if (energy == null) {
            throw new EnergyUnavailableException("energy-service indisponible, reessayer plus tard");
        }

        BigDecimal totalEnergy = BigDecimal.valueOf(energy.getTotalEnergyKwh());
        BigDecimal peakKwh = BigDecimal.valueOf(energy.getPeakKwh());
        BigDecimal offPeakKwh = BigDecimal.valueOf(energy.getOffPeakKwh());
        TariffCalculationService.CalculationResult calc = tariffCalculationService.calculateInvoice(
                station, totalEnergy, peakKwh, offPeakKwh, session.getChargeCompletedAt(), session.getStoppedAt());

        session.setTotalEnergyKwh(totalEnergy);
        session.setPeakKwh(peakKwh);
        session.setOffPeakKwh(offPeakKwh);
        sessionRepository.save(session);

        invoice.applyCalculation(calc.totalEnergyKwh(), calc.peakKwh(), calc.offPeakKwh(),
                calc.peakEnergyCost(), calc.offPeakEnergyCost(), calc.energyCost(),
                calc.idleDurationMinutes(), calc.idleFee(), calc.totalAmount());
        Invoice saved = invoiceRepository.save(invoice);
        log.info("Invoice {} recalculated for session {}: totalAmount={} TND",
                invoiceId, session.getId(), calc.totalAmount());
        return toInvoiceResponse(saved);
    }

    @Transactional(readOnly = true)
    public SessionResponse getSession(String id) {
        ChargingSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session de charge", id));
        return toSessionResponse(session);
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> getSessions(Long driverId, String chargerId, ChargingSession.SessionStatus status) {
        if (driverId != null) {
            return sessionRepository.findByDriverId(driverId).stream().map(this::toSessionResponse).toList();
        }
        if (chargerId != null) {
            return sessionRepository.findByChargerId(chargerId).stream().map(this::toSessionResponse).toList();
        }
        if (status != null) {
            return sessionRepository.findByStatus(status).stream().map(this::toSessionResponse).toList();
        }
        return sessionRepository.findAll().stream().map(this::toSessionResponse).toList();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceBySessionId(String sessionId) {
        Invoice invoice = invoiceRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Facture pour la session " + sessionId));
        return toInvoiceResponse(invoice);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoice(Long id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Facture", id));
        return toInvoiceResponse(invoice);
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> getInvoices(Long driverId) {
        if (driverId != null) {
            return invoiceRepository.findByDriverId(driverId).stream().map(this::toInvoiceResponse).toList();
        }
        return invoiceRepository.findAll().stream().map(this::toInvoiceResponse).toList();
    }

    private SessionResponse toSessionResponse(ChargingSession s) {
        return new SessionResponse(
                s.getId(),
                s.getDriverId(),
                s.getChargerId(),
                s.getReservationId(),
                s.getStatus(),
                s.getTransactionId(),
                s.getStartedAt(),
                s.getStoppedAt(),
                s.getChargeCompletedAt(),
                s.getTotalEnergyKwh(),
                s.getPeakKwh(),
                s.getOffPeakKwh(),
                s.getCreatedAt()
        );
    }

    private InvoiceResponse toInvoiceResponse(Invoice i) {
        return new InvoiceResponse(
                i.getId(),
                i.getSessionId(),
                i.getDriverId(),
                i.getTotalEnergyKwh(),
                i.getPeakKwh(),
                i.getOffPeakKwh(),
                i.getPeakEnergyCost(),
                i.getOffPeakEnergyCost(),
                i.getEnergyCost(),
                i.getIdleDurationMinutes(),
                i.getIdleFee(),
                i.getTotalAmount(),
                i.getCurrency(),
                i.getStatus(),
                i.getIssuedAt()
        );
    }
}
