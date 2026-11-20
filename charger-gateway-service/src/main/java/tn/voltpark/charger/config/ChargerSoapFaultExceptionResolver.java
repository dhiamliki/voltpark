package tn.voltpark.charger.config;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.SoapFaultDetail;
import org.springframework.ws.soap.server.endpoint.AbstractSoapFaultDefinitionExceptionResolver;
import org.springframework.ws.soap.server.endpoint.SoapFaultDefinition;
import tn.voltpark.charger.service.BadgeRefusedException;
import tn.voltpark.charger.service.ChargerNotFoundException;
import tn.voltpark.charger.service.ChargerOccupiedException;
import tn.voltpark.charger.ws.BadgeRefusedFault;
import tn.voltpark.charger.ws.ChargerNotFoundFault;
import tn.voltpark.charger.ws.ChargerOccupiedFault;
import tn.voltpark.charger.ws.ObjectFactory;

public class ChargerSoapFaultExceptionResolver extends AbstractSoapFaultDefinitionExceptionResolver {

    private static final Logger log = LoggerFactory.getLogger(ChargerSoapFaultExceptionResolver.class);
    private final ObjectFactory objectFactory = new ObjectFactory();
    private final JAXBContext jaxbContext;

    public ChargerSoapFaultExceptionResolver() {
        try {
            this.jaxbContext = JAXBContext.newInstance(
                    ChargerNotFoundFault.class,
                    ChargerOccupiedFault.class,
                    BadgeRefusedFault.class
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize JAXBContext for SOAP Faults", e);
        }
    }

    @Override
    protected SoapFaultDefinition getFaultDefinition(Object endpoint, Exception ex) {
        if (ex instanceof ChargerNotFoundException || ex instanceof ChargerOccupiedException || ex instanceof BadgeRefusedException) {
            SoapFaultDefinition faultDefinition = new SoapFaultDefinition();
            faultDefinition.setFaultCode(SoapFaultDefinition.CLIENT);
            faultDefinition.setFaultStringOrReason(ex.getMessage());
            return faultDefinition;
        }
        return null;
    }

    @Override
    protected void customizeFault(Object endpoint, Exception ex, SoapFault fault) {
        SoapFaultDetail detail = fault.addFaultDetail();
        try {
            Marshaller marshaller = jaxbContext.createMarshaller();
            if (ex instanceof ChargerNotFoundException notFound) {
                ChargerNotFoundFault faultObj = objectFactory.createChargerNotFoundFault();
                faultObj.setErrorCode("CHARGER_NOT_FOUND");
                faultObj.setMessage(notFound.getMessage());
                faultObj.setChargerId(notFound.getChargerId());
                marshaller.marshal(faultObj, detail.getResult());
            } else if (ex instanceof ChargerOccupiedException occupied) {
                ChargerOccupiedFault faultObj = objectFactory.createChargerOccupiedFault();
                faultObj.setErrorCode("CHARGER_OCCUPIED");
                faultObj.setMessage(occupied.getMessage());
                faultObj.setChargerId(occupied.getChargerId());
                faultObj.setCurrentTransactionId(occupied.getCurrentTransactionId());
                marshaller.marshal(faultObj, detail.getResult());
            } else if (ex instanceof BadgeRefusedException badgeRefused) {
                BadgeRefusedFault faultObj = objectFactory.createBadgeRefusedFault();
                faultObj.setErrorCode("BADGE_REFUSED");
                faultObj.setMessage(badgeRefused.getMessage());
                faultObj.setIdTag(badgeRefused.getIdTag());
                marshaller.marshal(faultObj, detail.getResult());
            }
        } catch (Exception e) {
            log.error("Could not add fault detail for exception", e);
        }
    }
}
