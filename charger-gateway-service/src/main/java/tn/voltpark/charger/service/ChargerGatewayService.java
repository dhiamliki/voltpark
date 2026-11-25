package tn.voltpark.charger.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.voltpark.charger.client.SimulatorClient;
import tn.voltpark.charger.model.AuthorizedBadge;
import tn.voltpark.charger.model.ChargerStation;
import tn.voltpark.charger.model.ChargerTransaction;
import tn.voltpark.charger.repository.AuthorizedBadgeRepository;
import tn.voltpark.charger.repository.ChargerStationRepository;
import tn.voltpark.charger.repository.ChargerTransactionRepository;
import tn.voltpark.charger.ws.*;

import javax.xml.datatype.DatatypeFactory;
import java.time.Instant;
import java.util.GregorianCalendar;

@Service
public class ChargerGatewayService {

    private static final Logger log = LoggerFactory.getLogger(ChargerGatewayService.class);
    private final ChargerStationRepository chargerRepository;
    private final AuthorizedBadgeRepository badgeRepository;
    private final ChargerTransactionRepository transactionRepository;
    private final SimulatorClient simulatorClient;

    public ChargerGatewayService(ChargerStationRepository chargerRepository,
                                 AuthorizedBadgeRepository badgeRepository,
                                 ChargerTransactionRepository transactionRepository,
                                 SimulatorClient simulatorClient) {
        this.chargerRepository = chargerRepository;
        this.badgeRepository = badgeRepository;
        this.transactionRepository = transactionRepository;
        this.simulatorClient = simulatorClient;
    }

    @Transactional
    public RemoteStartTransactionResponse remoteStart(RemoteStartTransactionRequest request) {
        log.info("SOAP RemoteStartTransaction request: chargerId={}, idTag={}, connectorId={}",
                request.getChargerId(), request.getIdTag(), request.getConnectorId());

        // 1. Verification du badge
        AuthorizedBadge badge = badgeRepository.findByIdTag(request.getIdTag()).orElse(null);
        if (badge == null) {
            throw new BadgeRefusedException(request.getIdTag(), "Badge non reconnu");
        }
        if (!badge.isActive()) {
            throw new BadgeRefusedException(request.getIdTag(), "Badge desactive ou bloque");
        }

        // 2. Verification de la borne
        ChargerStation charger = chargerRepository.findByChargerId(request.getChargerId())
                .orElseThrow(() -> new ChargerNotFoundException(request.getChargerId()));

        // 3. Verification si borne libre
        if (charger.getStatus() == ChargerStatus.OCCUPIED || charger.getStatus() == ChargerStatus.CHARGING) {
            throw new ChargerOccupiedException(charger.getChargerId(), charger.getCurrentTransactionId());
        }

        // 4. Demarrage de la transaction : numero donne par la sequence de la base (sans etat en memoire)
        ChargerTransaction transaction = transactionRepository.save(
                new ChargerTransaction(charger.getChargerId(), request.getIdTag(), request.getSessionId()));
        int txId = transaction.getId();
        charger.setStatus(ChargerStatus.CHARGING);
        charger.setCurrentTransactionId(txId);
        charger.setCurrentIdTag(request.getIdTag());
        charger.setCurrentSessionId(request.getSessionId());
        charger.setUpdatedAt(Instant.now());
        chargerRepository.save(charger);

        // la borne (simulee) commence a envoyer ses mesures pour cette session
        if (request.getSessionId() != null && !request.getSessionId().isBlank()) {
            simulatorClient.startSimulation(request.getSessionId(), charger.getChargerId());
        }

        RemoteStartTransactionResponse response = new RemoteStartTransactionResponse();
        response.setStatus(RemoteStartStopStatus.ACCEPTED);
        response.setTransactionId(txId);
        response.setMessage("Transaction demarree avec succes sur la borne " + charger.getChargerId());
        return response;
    }

    @Transactional
    public RemoteStopTransactionResponse remoteStop(RemoteStopTransactionRequest request) {
        log.info("SOAP RemoteStopTransaction request: chargerId={}, transactionId={}",
                request.getChargerId(), request.getTransactionId());

        ChargerStation charger = chargerRepository.findByChargerId(request.getChargerId())
                .orElseThrow(() -> new ChargerNotFoundException(request.getChargerId()));

        // seule la transaction en cours de la borne peut etre arretee
        Integer currentTransactionId = charger.getCurrentTransactionId();
        if (currentTransactionId == null || currentTransactionId != request.getTransactionId()) {
            throw new InvalidTransactionException(charger.getChargerId(), request.getTransactionId(), currentTransactionId);
        }
        transactionRepository.findById(currentTransactionId).ifPresent(transaction -> {
            transaction.setStoppedAt(Instant.now());
            transactionRepository.save(transaction);
        });

        String sessionId = charger.getCurrentSessionId();
        charger.setStatus(ChargerStatus.AVAILABLE);
        charger.setCurrentTransactionId(null);
        charger.setCurrentIdTag(null);
        charger.setCurrentSessionId(null);
        charger.setUpdatedAt(Instant.now());
        chargerRepository.save(charger);

        if (sessionId != null) {
            simulatorClient.stopSimulation(sessionId);
        }

        RemoteStopTransactionResponse response = new RemoteStopTransactionResponse();
        response.setStatus(RemoteStartStopStatus.ACCEPTED);
        response.setMessage("Transaction " + request.getTransactionId() + " arretee avec succes sur la borne " + charger.getChargerId());
        return response;
    }

    @Transactional(readOnly = true)
    public GetChargerStatusResponse getStatus(GetChargerStatusRequest request) {
        log.info("SOAP GetChargerStatus request: chargerId={}", request.getChargerId());

        ChargerStation charger = chargerRepository.findByChargerId(request.getChargerId())
                .orElseThrow(() -> new ChargerNotFoundException(request.getChargerId()));

        GetChargerStatusResponse response = new GetChargerStatusResponse();
        response.setChargerId(charger.getChargerId());
        response.setStatus(charger.getStatus());
        response.setCurrentTransactionId(charger.getCurrentTransactionId());
        response.setErrorCode("NoError");

        try {
            GregorianCalendar cal = new GregorianCalendar();
            cal.setTimeInMillis(charger.getUpdatedAt() != null ? charger.getUpdatedAt().toEpochMilli() : System.currentTimeMillis());
            response.setTimestamp(DatatypeFactory.newInstance().newXMLGregorianCalendar(cal));
        } catch (Exception e) {
            log.warn("Could not set XMLGregorianCalendar timestamp", e);
        }

        return response;
    }
}
