package tn.voltpark.session.client.soap;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.ws.client.core.WebServiceTemplate;
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

    @CircuitBreaker(name = "chargerGateway", fallbackMethod = "fallbackRemoteStart")
    @Retry(name = "chargerGateway")
    public RemoteStartResult remoteStartTransaction(String chargerId, int connectorId, String idTag, String reservationId) {
        log.info("Sending SOAP RemoteStartTransaction to charger: chargerId={}, idTag={}", chargerId, idTag);

        RemoteStartTransactionRequest request = new RemoteStartTransactionRequest();
        request.setChargerId(chargerId);
        request.setConnectorId(connectorId);
        request.setIdTag(idTag);
        request.setReservationId(reservationId);

        RemoteStartTransactionResponse response = (RemoteStartTransactionResponse) webServiceTemplate.marshalSendAndReceive(request);

        if (response != null && response.getStatus() == RemoteStartStopStatus.ACCEPTED) {
            log.info("Charger accepted transaction: txId={}", response.getTransactionId());
            return new RemoteStartResult(true, response.getTransactionId(), response.getMessage(), false);
        } else {
            String msg = response != null ? response.getMessage() : "No response from charger";
            log.warn("Charger rejected transaction: {}", msg);
            return new RemoteStartResult(false, null, msg, false);
        }
    }

    public RemoteStartResult fallbackRemoteStart(String chargerId, int connectorId, String idTag, String reservationId, Throwable t) {
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
