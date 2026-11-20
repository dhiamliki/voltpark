package tn.voltpark.charger.endpoint;

import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import tn.voltpark.charger.service.ChargerGatewayService;
import tn.voltpark.charger.ws.*;

@Endpoint
public class ChargerEndpoint {

    public static final String NAMESPACE_URI = "http://voltpark.tn/charger/ws";

    private final ChargerGatewayService service;

    public ChargerEndpoint(ChargerGatewayService service) {
        this.service = service;
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "RemoteStartTransactionRequest")
    @ResponsePayload
    public RemoteStartTransactionResponse remoteStartTransaction(@RequestPayload RemoteStartTransactionRequest request) {
        return service.remoteStart(request);
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "RemoteStopTransactionRequest")
    @ResponsePayload
    public RemoteStopTransactionResponse remoteStopTransaction(@RequestPayload RemoteStopTransactionRequest request) {
        return service.remoteStop(request);
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "GetChargerStatusRequest")
    @ResponsePayload
    public GetChargerStatusResponse getChargerStatus(@RequestPayload GetChargerStatusRequest request) {
        return service.getStatus(request);
    }
}
