package tn.voltpark.session.client.soap;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.SoapFaultClientException;
import tn.voltpark.session.api.error.ChargerFaultException;
import tn.voltpark.session.client.soap.model.*;

@Service
public class ChargerSoapClient {

    private static final Logger log = LoggerFactory.getLogger(ChargerSoapClient.class);

    private final WebServiceTemplate webServiceTemplate;

    public ChargerSoapClient(WebServiceTemplate webServiceTemplate) {
        this.webServiceTemplate = webServiceTemplate;
    }

    public record RemoteStartResult(
            boolean accepted,
            Integer transactionId,
            String message,
            boolean fallbackTriggered
    ) {}

    /**
     * RemoteStartTransaction avec Resilience4j.
     *
     * Ordre des aspects : Resilience4j place Retry a l'exterieur et CircuitBreaker a l'interieur,
     * soit Retry( CircuitBreaker( appel SOAP ) ). Le fallbackMethod est donc sur @Retry :
     * le fallback ne s'execute qu'apres tous les essais (avec backoff). Place sur @CircuitBreaker,
     * il renverrait un resultat des le premier echec et Retry ne referait jamais l'appel.
     *
     * - panne (connexion refusee, timeout, HTTP 5xx, faute Server) : nouvel essai, puis fallback
     *   PENDING_START ;
     * - circuit ouvert (CallNotPermittedException) : pas de nouvel essai, fallback immediat ;
     * - SOAP Fault metier (borne inconnue, occupee, badge refuse) : ChargerFaultException,
     *   ignoree par Retry et CircuitBreaker (voir application.yml) et relancee par le fallback.
     */
    @Retry(name = "chargerGateway", fallbackMethod = "fallbackRemoteStart")
    @CircuitBreaker(name = "chargerGateway")
    public RemoteStartResult remoteStartTransaction(String chargerId, int connectorId, String idTag, String reservationId, String sessionId) {
        log.info("Sending SOAP RemoteStartTransaction to charger: chargerId={}, idTag={}", chargerId, idTag);

        RemoteStartTransactionRequest request = new RemoteStartTransactionRequest();
        request.setChargerId(chargerId);
        request.setConnectorId(connectorId);
        request.setIdTag(idTag);
        request.setReservationId(reservationId);
        request.setSessionId(sessionId);

        RemoteStartTransactionResponse response;
        try {
            response = (RemoteStartTransactionResponse) webServiceTemplate.marshalSendAndReceive(request);
        } catch (SoapFaultClientException e) {
            ChargerFaultException business = ChargerFaultMapper.toBusinessException(e);
            if (business == null) {
                throw e; // faute Server : panne de la passerelle
            }
            log.warn("Charger {} refused the start: {} ({})", chargerId, business.getMessage(), business.getErrorCode());
            throw business;
        }

        if (response != null && response.getStatus() == RemoteStartStopStatus.ACCEPTED) {
            log.info("Charger accepted transaction: txId={}", response.getTransactionId());
            return new RemoteStartResult(true, response.getTransactionId(), response.getMessage(), false);
        } else {
            String msg = response != null ? response.getMessage() : "No response from charger";
            log.warn("Charger rejected transaction: {}", msg);
            return new RemoteStartResult(false, null, msg, false);
        }
    }

    public RemoteStartResult fallbackRemoteStart(String chargerId, int connectorId, String idTag, String reservationId, String sessionId, Throwable t) {
        if (t instanceof ChargerFaultException business) {
            // reponse metier de la borne, pas une panne : pas de PENDING_START
            throw business;
        }
        log.warn("Charger gateway SOAP communication failed for charger {} (circuit breaker / retry fallback active): {}",
                chargerId, t.getMessage());
        return new RemoteStartResult(
                false,
                null,
                "Passerelle borne inaccessible (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ") - session placee en PENDING_START",
                true
        );
    }

    public boolean remoteStopTransaction(String chargerId, int transactionId) {
        log.info("Sending SOAP RemoteStopTransaction: chargerId={}, txId={}", chargerId, transactionId);
        try {
            RemoteStopTransactionRequest request = new RemoteStopTransactionRequest();
            request.setChargerId(chargerId);
            request.setTransactionId(transactionId);

            RemoteStopTransactionResponse response = (RemoteStopTransactionResponse) webServiceTemplate.marshalSendAndReceive(request);
            return response != null && response.getStatus() == RemoteStartStopStatus.ACCEPTED;
        } catch (Exception e) {
            log.warn("Failed to stop remote transaction on charger {}: {}", chargerId, e.getMessage());
            return false;
        }
    }

    public ChargerStatus getChargerStatus(String chargerId) {
        try {
            GetChargerStatusRequest request = new GetChargerStatusRequest();
            request.setChargerId(chargerId);

            GetChargerStatusResponse response = (GetChargerStatusResponse) webServiceTemplate.marshalSendAndReceive(request);
            return response != null ? response.getStatus() : null;
        } catch (Exception e) {
            log.warn("Failed to get status for charger {}: {}", chargerId, e.getMessage());
            return null;
        }
    }
}
