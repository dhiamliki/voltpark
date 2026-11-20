package tn.voltpark.gateway.fallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;
import java.time.Instant;
import java.util.Set;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR;

/**
 * Reponse de repli du circuit breaker : 503 au format RFC 7807 (ProblemDetail).
 * La requete est transmise ici par "forward:", quelle que soit sa methode HTTP.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    private static final Logger log = LoggerFactory.getLogger(FallbackController.class);

    static final URI PROBLEM_TYPE = URI.create("urn:voltpark:problem:service-unavailable");

    @RequestMapping("/session-service")
    public ResponseEntity<ProblemDetail> sessionServiceFallback(ServerWebExchange exchange) {
        String path = originalPath(exchange);
        Throwable cause = exchange.getAttribute(CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        log.warn("Fallback for {}: {}", path, cause == null ? "unknown cause" : cause.toString());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Le service des sessions ne repond pas. Reessayez dans quelques instants.");
        problem.setType(PROBLEM_TYPE);
        problem.setTitle("Service indisponible");
        problem.setInstance(URI.create(path));
        problem.setProperty("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    /** Chemin demande par le client, avant le forward vers /fallback. */
    private static String originalPath(ServerWebExchange exchange) {
        Set<URI> originalUrls = exchange.getAttribute(GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
        if (originalUrls != null && !originalUrls.isEmpty()) {
            return originalUrls.iterator().next().getPath();
        }
        return exchange.getRequest().getPath().value();
    }
}
