package tn.voltpark.charger.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.voltpark.charger.client.SimulatorClient;
import tn.voltpark.charger.model.AuthorizedBadge;
import tn.voltpark.charger.model.ChargerStation;
import tn.voltpark.charger.repository.AuthorizedBadgeRepository;
import tn.voltpark.charger.repository.ChargerStationRepository;
import tn.voltpark.charger.ws.*;

import javax.xml.datatype.DatatypeFactory;
import java.time.Instant;
import java.util.GregorianCalendar;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ChargerGatewayService {

    private static final Logger log = LoggerFactory.getLogger(ChargerGatewayService.class);
    private final ChargerStationRepository chargerRepository;
    private final AuthorizedBadgeRepository badgeRepository;
    private final SimulatorClient simulatorClient;
    private final AtomicInteger transactionSequence = new AtomicInteger(1000);

    public ChargerGatewayService(ChargerStationRepository chargerRepository,
                                 AuthorizedBadgeRepository badgeRepository,
                                 SimulatorClient simulatorClient) {
        this.chargerRepository = chargerRepository;
        this.badgeRepository = badgeRepository;
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

        // 4. Demarrage de la transaction
        int txId = transactionSequence.incrementAndGet();
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
