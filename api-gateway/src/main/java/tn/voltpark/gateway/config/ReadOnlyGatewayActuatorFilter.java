package tn.voltpark.gateway.config;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * L'endpoint actuator "gateway" permet aussi d'ajouter, supprimer ou recharger des routes
 * (POST / DELETE). On l'expose uniquement pour consulter les routes : toute autre methode
 * que GET ou HEAD est refusee avec 405.
 */
@Component
public class ReadOnlyGatewayActuatorFilter implements WebFilter {

    private static final String GATEWAY_ACTUATOR_PATH = "/actuator/gateway";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();
        boolean readOnly = HttpMethod.GET.equals(method) || HttpMethod.HEAD.equals(method);

        if (path.startsWith(GATEWAY_ACTUATOR_PATH) && !readOnly) {
            exchange.getResponse().setStatusCode(HttpStatus.METHOD_NOT_ALLOWED);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }
}
