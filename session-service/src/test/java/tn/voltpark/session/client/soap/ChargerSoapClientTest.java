package tn.voltpark.session.client.soap;

import org.junit.jupiter.api.Test;
import org.springframework.ws.client.core.WebServiceTemplate;
import tn.voltpark.session.client.soap.model.RemoteStartStopStatus;
import tn.voltpark.session.client.soap.model.RemoteStartTransactionResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChargerSoapClientTest {

    @Test
    void testRemoteStartAccepted() {
        WebServiceTemplate template = mock(WebServiceTemplate.class);
        RemoteStartTransactionResponse resp = new RemoteStartTransactionResponse();
        resp.setStatus(RemoteStartStopStatus.ACCEPTED);
        resp.setTransactionId(777);
        resp.setMessage("OK");

        when(template.marshalSendAndReceive(any())).thenReturn(resp);

        ChargerSoapClient client = new ChargerSoapClient(template);
        ChargerSoapClient.RemoteStartResult res = client.remoteStartTransaction("CP-460375-1", 1, "TAG-001", null, "session-1");

        assertTrue(res.accepted());
        assertEquals(777, res.transactionId());
        assertFalse(res.fallbackTriggered());
    }

    @Test
    void testFallbackRemoteStart() {
        WebServiceTemplate template = mock(WebServiceTemplate.class);
        ChargerSoapClient client = new ChargerSoapClient(template);

        ChargerSoapClient.RemoteStartResult fallback = client.fallbackRemoteStart(
                "CP-460375-1", 1, "TAG-001", null, "session-1", new RuntimeException("Timeout"));

        assertFalse(fallback.accepted());
        assertTrue(fallback.fallbackTriggered());
        assertTrue(fallback.message().contains("PENDING_START"));
    }
}
