package tn.voltpark.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Identifiant de requete X-Request-Id :
 * - garde celui envoye par le client, ou en genere un nouveau (UUID) ;
 * - le transmet au service en aval et le renvoie dans la reponse ;
 * - ecrit une ligne de log par requete (methode, chemin, statut, duree, identifiant).
 *
 * Ce filtre ne s'applique qu'aux requetes routees (pas a /actuator), ce qui evite
 * de remplir les logs avec les healthchecks.
 */
@Component
public class RequestIdFilter implements GlobalFilter, Ordered {

    public static final String HEADER = "X-Request-Id";

    /** Au-dela, l'identifiant recu est ignore (protection contre des en-tetes enormes). */
    private static final int MAX_LENGTH = 128;

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = resolveRequestId(exchange.getRequest());
        long start = System.nanoTime();

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(HEADER, requestId))
                .build();
        // ajoute juste avant l'envoi, y compris pour les reponses du fallback
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(HEADER, requestId);
            return Mono.empty();
        });

        String method = request.getMethod().name();
        String path = request.getPath().value();
        return chain.filter(exchange.mutate().request(request).build())
                .doFinally(signal -> {
                    HttpStatusCode status = exchange.getResponse().getStatusCode();
                    long durationMs = (System.nanoTime() - start) / 1_000_000;
                    log.info("{} {} {} {}ms requestId={}",
                            method, path, status == null ? "-" : status.value(), durationMs, requestId);
                });
    }

    private static String resolveRequestId(ServerHttpRequest request) {
        String incoming = request.getHeaders().getFirst(HEADER);
        if (incoming != null && !incoming.isBlank() && incoming.length() <= MAX_LENGTH) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }

    @Override
    public int getOrder() {
        // juste apres OriginalRequestUrlFilter, avant tous les autres filtres
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
