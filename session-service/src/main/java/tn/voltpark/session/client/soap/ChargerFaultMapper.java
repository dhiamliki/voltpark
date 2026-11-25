package tn.voltpark.session.client.soap;

import org.springframework.http.HttpStatus;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.SoapFaultDetail;
import org.springframework.ws.soap.SoapFaultDetailElement;
import org.springframework.ws.soap.client.SoapFaultClientException;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import tn.voltpark.session.api.error.ChargerFaultException;

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import java.util.Iterator;

/**
 * Traduit une SOAP Fault de charger-gateway-service en erreur metier.
 *
 * Faute "Client" (code SOAP 1.1 Client / 1.2 Sender) : la borne a repondu, mais refuse la demande ;
 * le detail contient un errorCode (CHARGER_NOT_FOUND, CHARGER_OCCUPIED, BADGE_REFUSED...).
 * Faute "Server" : erreur interne de la passerelle, traitee comme une panne (pas de traduction).
 */
final class ChargerFaultMapper {

    private ChargerFaultMapper() {
    }

    /** @return l'erreur metier, ou null si la faute est une panne de la passerelle (faute Server) */
    static ChargerFaultException toBusinessException(SoapFaultClientException e) {
        String faultCode = e.getFaultCode() != null ? e.getFaultCode().getLocalPart() : "";
        if (!faultCode.equals("Client") && !faultCode.equals("Sender")) {
            return null;
        }
        String errorCode = errorCode(e.getSoapFault());
        HttpStatus status = switch (errorCode) {
            case "CHARGER_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "BADGE_REFUSED" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.CONFLICT; // CHARGER_OCCUPIED, TRANSACTION_MISMATCH, autre refus
        };
        return new ChargerFaultException(status, errorCode, e.getFaultStringOrReason());
    }

    /** Valeur de l'element errorCode du detail de la faute, "CHARGER_REJECTED" s'il n'y en a pas. */
    private static String errorCode(SoapFault fault) {
        SoapFaultDetail detail = fault != null ? fault.getFaultDetail() : null;
        if (detail == null) {
            return "CHARGER_REJECTED";
        }
        try {
            Iterator<SoapFaultDetailElement> entries = detail.getDetailEntries();
            while (entries.hasNext()) {
                DOMResult dom = new DOMResult();
                TransformerFactory.newInstance().newTransformer().transform(entries.next().getSource(), dom);
                NodeList codes = ((Document) dom.getNode()).getElementsByTagNameNS("*", "errorCode");
                if (codes.getLength() > 0) {
                    return codes.item(0).getTextContent().trim();
                }
            }
        } catch (Exception ignored) {
            // detail illisible : refus generique
        }
        return "CHARGER_REJECTED";
    }
}
