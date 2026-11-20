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
import tn.voltpark.session.api.error.InvalidSessionStateException;
import tn.voltpark.session.api.error.ResourceNotFoundException;
import tn.voltpark.session.client.grpc.EnergyGrpcClient;
import tn.voltpark.session.client.soap.ChargerSoapClient;
import tn.voltpark.session.domain.model.*;
import tn.voltpark.session.repository.ChargerRepository;
import tn.voltpark.session.repository.ChargingSessionRepository;
import tn.voltpark.session.repository.DriverRepository;
import tn.voltpark.session.repository.InvoiceRepository;

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
    private final ChargerSoapClient chargerSoapClient;
    private final EnergyGrpcClient energyGrpcClient;
    private final TariffCalculationService tariffCalculationService;

    public SessionManagementService(ChargingSessionRepository sessionRepository,
                                    InvoiceRepository invoiceRepository,
                                    DriverRepository driverRepository,
                                    ChargerRepository chargerRepository,
                                    ChargerSoapClient chargerSoapClient,
                                    EnergyGrpcClient energyGrpcClient,
                                    TariffCalculationService tariffCalculationService) {
        this.sessionRepository = sessionRepository;
        this.invoiceRepository = invoiceRepository;
        this.driverRepository = driverRepository;
        this.chargerRepository = chargerRepository;
        this.chargerSoapClient = chargerSoapClient;
        this.energyGrpcClient = energyGrpcClient;
        this.tariffCalculationService = tariffCalculationService;
    }

    @Transactional
    public SessionResponse startSession(StartSessionRequest request) {
        Driver driver = driverRepository.findById(request.driverId())
                .orElseThrow(() -> new ResourceNotFoundException("Conducteur", request.driverId()));

        Charger charger = chargerRepository.findById(request.chargerId())
                .orElseThrow(() -> new ResourceNotFoundException("Borne", request.chargerId()));

        if (charger.getStatus() == Charger.ChargerStatus.CHARGING) {
            throw new InvalidSessionStateException("La borne " + charger.getId() + " est deja en cours de recharge");
        }

        ChargingSession session = new ChargingSession(driver.getId(), charger.getId(), request.reservationId());

        int connector = request.connectorId() != null ? request.connectorId() : 1;
        String resIdStr = request.reservationId() != null ? request.reservationId().toString() : null;

        // Appel SOAP inter-service avec CircuitBreaker Resilience4j & Retry
        ChargerSoapClient.RemoteStartResult soapResult = chargerSoapClient.remoteStartTransaction(
                charger.getId(), connector, driver.getBadgeId(), resIdStr);

        if (soapResult.accepted()) {
            session.setStatus(ChargingSession.SessionStatus.ACTIVE);
            session.setTransactionId(soapResult.transactionId());
            charger.setStatus(Charger.ChargerStatus.CHARGING);
            log.info("Session {} started actively with transactionId {}", session.getId(), soapResult.transactionId());
        } else if (soapResult.fallbackTriggered()) {
            // Fallback Resilience4j : borne SOAP inaccessible ou circuit ouvert -> PENDING_START
            session.setStatus(ChargingSession.SessionStatus.PENDING_START);
            session.setTransactionId(null);
            charger.setStatus(Charger.ChargerStatus.OCCUPIED);
            log.warn("Session {} set to PENDING_START due to SOAP gateway fallback: {}", session.getId(), soapResult.message());
        } else {
            session.setStatus(ChargingSession.SessionStatus.FAILED);
            sessionRepository.save(session);
            throw new InvalidSessionStateException("Demarrage refuse par la borne: " + soapResult.message());
        }

        chargerRepository.save(charger);
        ChargingSession saved = sessionRepository.save(session);
        return toSessionResponse(saved);
    }

    @Transactional
    public SessionResponse stopSession(String sessionId, StopSessionRequest request) {
        ChargingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session de charge", sessionId));

        if (session.getStatus() == ChargingSession.SessionStatus.COMPLETED ||
                session.getStatus() == ChargingSession.SessionStatus.STOPPED) {
            throw new InvalidSessionStateException("La session " + sessionId + " est deja cloturee");
        }

        Charger charger = chargerRepository.findById(session.getChargerId())
                .orElse(null);

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

        SessionEnergy energyProto = energyGrpcClient.getSessionEnergy(sessionId);
        if (energyProto != null && energyProto.getTotalEnergyKwh() > 0) {
            totalEnergy = BigDecimal.valueOf(energyProto.getTotalEnergyKwh());
            peakKwh = BigDecimal.valueOf(energyProto.getPeakKwh());
            offPeakKwh = BigDecimal.valueOf(energyProto.getOffPeakKwh());
            log.info("Retrieved energy from gRPC for session {}: total={}, peak={}, offPeak={}",
                    sessionId, totalEnergy, peakKwh, offPeakKwh);
        } else if (request != null && request.totalEnergyKwh() != null) {
            totalEnergy = request.totalEnergyKwh();
            peakKwh = request.peakKwh() != null ? request.peakKwh() : BigDecimal.ZERO;
            offPeakKwh = request.offPeakKwh() != null ? request.offPeakKwh() : BigDecimal.ZERO;
        } else {
            // Mesures par defaut si simulation non connectee
            totalEnergy = BigDecimal.valueOf(18.500);
            peakKwh = BigDecimal.valueOf(12.000);
            offPeakKwh = BigDecimal.valueOf(6.500);
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
            invoiceRepository.save(invoice);
            log.info("Invoice created for session {}: totalAmount={} TND (energy={}, idleFee={})",
                    sessionId, calc.totalAmount(), calc.energyCost(), calc.idleFee());
        }

        return toSessionResponse(savedSession);
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
