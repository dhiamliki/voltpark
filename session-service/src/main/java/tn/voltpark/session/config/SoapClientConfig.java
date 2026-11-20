package tn.voltpark.session.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender;

import java.time.Duration;

@Configuration
public class SoapClientConfig {

    @Value("${voltpark.clients.charger-gateway.url:http://localhost:8082/ws}")
    private String chargerGatewayUrl;

    @Value("${voltpark.clients.charger-gateway.connection-timeout-ms:3000}")
    private int connectionTimeoutMs;

    @Value("${voltpark.clients.charger-gateway.read-timeout-ms:5000}")
    private int readTimeoutMs;

    @Bean
    public Jaxb2Marshaller chargerMarshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath("tn.voltpark.session.client.soap.model");
        return marshaller;
    }

    @Bean
    public WebServiceTemplate chargerWebServiceTemplate(Jaxb2Marshaller chargerMarshaller) {
        WebServiceTemplate template = new WebServiceTemplate();
        template.setMarshaller(chargerMarshaller);
        template.setUnmarshaller(chargerMarshaller);
        template.setDefaultUri(chargerGatewayUrl);

        HttpUrlConnectionMessageSender sender = new HttpUrlConnectionMessageSender();
        sender.setConnectionTimeout(Duration.ofMillis(connectionTimeoutMs));
        sender.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        template.setMessageSender(sender);

        return template;
    }
}
