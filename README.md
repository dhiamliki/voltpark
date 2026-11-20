# VoltPark

VoltPark est une plateforme de gestion d'un réseau de bornes de recharge pour véhicules électriques.
Les conducteurs recherchent une station, réservent une borne, démarrent et arrêtent une session de charge, puis reçoivent une facture.
Une partie du parc est composée d'anciennes bornes qui ne parlent qu'un protocole SOAP historique (inspiré d'OCPP 1.5) : une passerelle dédiée fait le lien avec ces bornes.
Les mesures d'énergie remontées par les bornes sont collectées en continu via gRPC afin de suivre les sessions en temps réel et de calculer le montant facturé.

## Architecture

| Service | Style | Port | Base de données |
|---|---|---|---|
| `api-gateway` | Spring Cloud Gateway | 8080 | aucune |
| `session-service` | REST `/api/v1` | 8081 | PostgreSQL `session-db` |
| `charger-gateway-service` | SOAP (contract-first) | 8082 | PostgreSQL `charger-db` |
| `energy-service` | gRPC | 8083 (HTTP/actuator), 9090 (gRPC) | PostgreSQL `energy-db` |
| `charger-simulator` | client gRPC | - | aucune |
| `frontend` | React + Vite + TypeScript (nginx) | 3000 | aucune |

Chaque service possède sa propre base de données. Les services se trouvent entre eux par le DNS de Docker Compose (réseau `voltpark-net`).

```
                     HTTP                    HTTP
  +----------+   /api/v1/**   +-------------+        +-----------------+
  | frontend | -------------> | api-gateway | -----> | session-service |
  +----------+                +-------------+        +-----------------+
                                                       |      |      ^
                       SOAP (WebServiceTemplate)       |      |      | gRPC server streaming
                 +-------------------------------------+      |      | WatchSession
                 v                                            |      | (expose par session-service)
  +-------------------------+          gRPC unary             |      |
  | charger-gateway-service |          GetSessionEnergy       v      |
  +-------------------------+                             +----------------+
                                                          | energy-service |
                                                          +----------------+
                                                                  ^
  +-------------------+   gRPC client streaming                   |
  | charger-simulator | ------------------------------------------+
  +-------------------+   StreamMeterValues
```

Flux :
- Frontend -> api-gateway -> session-service
- session-service -> charger-gateway-service (SOAP, `WebServiceTemplate`)
- session-service -> energy-service (gRPC unary `GetSessionEnergy`)
- charger-simulator -> energy-service (gRPC client streaming `StreamMeterValues`)
- frontend / session-service <- energy-service (gRPC server streaming `WatchSession`, exposé via session-service)

Le diagramme d'architecture complet sera ajouté dans `docs/`.

## Ressources métier

- **Driver** : conducteur inscrit sur la plateforme.
- **Station** : site de recharge regroupant plusieurs bornes.
- **Charger** : borne de recharge (récente ou historique SOAP).
- **Reservation** : réservation d'une borne sur un créneau.
- **ChargingSession** : session de charge entre un démarrage et un arrêt.
- **MeterValue** : mesure d'énergie envoyée par une borne pendant une session.
- **Invoice** : facture d'une session terminée.

## Prérequis

- Docker et Docker Compose
- Pour le développement local : Java 17, Maven 3.9, Node 20

## Lancement

```bash
cp .env.example .env
docker compose up --build
```

| Élément | URL |
|---|---|
| API Gateway | http://localhost:8080/api/v1 |
| Frontend | http://localhost:3000 |
| Swagger UI (session-service) | http://localhost:8081/swagger-ui.html |
| WSDL (charger-gateway-service) | http://localhost:8082/ws/charger.wsdl |
| gRPC (energy-service) | localhost:9090 |
| Health api-gateway | http://localhost:8080/actuator/health |
| Health session-service | http://localhost:8081/actuator/health |
| Health charger-gateway-service | http://localhost:8082/actuator/health |
| Health energy-service | http://localhost:8083/actuator/health |

Le charger-simulator n'est pas exposé sur l'hôte (healthcheck interne sur le port 8084).

## API Gateway

Le frontend n'appelle que l'api-gateway (port 8080), qui transmet les requêtes à session-service.

**Routes**

| Route | Chemins | Destination |
|---|---|---|
| `session-api` | `/api/v1/**` | session-service (`SESSION_SERVICE_URL`) |
| `session-docs` | `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | session-service (Swagger UI via la gateway) |

La liste des routes actives est visible sur http://localhost:8080/actuator/gateway/routes. Cet endpoint est en lecture seule : les requêtes qui modifieraient les routes (POST, DELETE) sont refusées avec 405.

**Délais** : connexion `GATEWAY_CONNECT_TIMEOUT_MS` (2000 ms par défaut), réponse `GATEWAY_RESPONSE_TIMEOUT` (5 s par défaut).

**Résilience (route `session-api`)**
- Retry : uniquement pour les requêtes GET, 2 nouveaux essais sur 502, 503, 504 ou erreur de connexion, avec un délai croissant (100 ms, puis 200 ms, maximum 1 s). POST, PUT, PATCH et DELETE ne sont jamais rejoués : ils ne sont pas idempotents (risque de double réservation, par exemple).
- Circuit breaker `sessionService` (Resilience4j) : fenêtre de 10 appels, ouverture à 50 % d'échecs (à partir de 5 appels), 10 s en état ouvert avant de retester. Le time limiter utilise la même durée que le délai de réponse.
- Fallback : si session-service est injoignable ou si le circuit est ouvert, la gateway répond `503` au format RFC 7807 (`application/problem+json`) avec `type`, `title`, `status`, `detail`, `instance` et `timestamp`.

**CORS** : origines autorisées dans `CORS_ALLOWED_ORIGINS` (liste séparée par des virgules, par défaut l'URL du frontend), méthodes GET, POST, PUT, PATCH, DELETE et OPTIONS, en-têtes `Location` et `X-Request-Id` exposés au navigateur, cache du preflight 1 h.

**X-Request-Id** : chaque requête reçoit un identifiant (celui envoyé par le client, sinon un UUID). Il est transmis à session-service, renvoyé dans la réponse et écrit dans le log de la gateway :

```
GET /api/v1/stations 200 12ms requestId=4281b2a3-dcb2-4818-ab32-806d8e0fcdf5
```

Tests (sans Maven installé) :

```bash
docker run --rm -v "$PWD/api-gateway:/app" -w /app maven:3.9-eclipse-temurin-17 mvn -q test
```

## Répartition des tâches

### Rafaa : REST + SOAP

- session-service : entités, repositories, DTOs + validation, contrôleurs sous `/api/v1`, codes HTTP corrects, `@RestControllerAdvice` (ProblemDetail), spécification OpenAPI exportée dans `openapi.yaml`, logique de tarification (kWh x tarif, heures pleines/creuses, frais d'occupation).
- charger-gateway-service : XSD écrit en premier (contract-first), WSDL généré, opérations `RemoteStartTransaction`, `RemoteStopTransaction`, `GetChargerStatus`, SOAP Faults (borne inconnue, occupée, badge refusé).
- Appel inter-services REST -> SOAP avec `WebServiceTemplate`, timeouts, circuit breaker Resilience4j + retry avec backoff et fallback (`PENDING_START`).
- Tests : tests unitaires sur la tarification et les réservations, test d'intégration REST (MockMvc) et SOAP.
- Collection Postman pour les scénarios REST et SOAP.

### Dhia : gRPC + Gateway + Frontend

- energy-service : `energy.proto` versionné (`voltpark.energy.v1`), RPCs `StreamMeterValues` (client streaming), `WatchSession` (server streaming), `GetSessionEnergy` (unary), codes de statut gRPC adaptés (`NOT_FOUND`, `INVALID_ARGUMENT`, `FAILED_PRECONDITION`), persistance des mesures.
- Client gRPC dans session-service (package `client/grpc`) avec deadline + fallback Resilience4j, en coordination avec le Membre A.
- charger-simulator : envoi de mesures simulées chaque seconde.
- api-gateway : routes, CORS pour le frontend, (bonus) validation JWT.
- frontend : liste des stations, réservation, démarrage/arrêt de charge, suivi de session en direct, facture.
- Tests : tests unitaires sur le calcul d'énergie, test d'intégration gRPC (serveur in-process).
- Scripts grpcurl pour les scénarios gRPC.

### Commun (les deux)

- `docker-compose.yml`, healthchecks, `.env`, README, diagramme d'architecture, rapport (8 à 12 pages), préparation de la soutenance, démonstration d'une panne simulée (`docker stop charger-gateway-service`).
- Bonus possibles : événement Kafka `SessionCompleted`, JWT, tracing (Micrometer + OpenTelemetry), CI GitHub Actions, Testcontainers.

### Interfaces entre nous

- Le Membre B est responsable de `energy.proto` et doit partager toute modification (la copie dans `charger-simulator` et le client de session-service en dépendent).
- Le Membre A est responsable de `charger.xsd` et de `openapi.yaml`.
- Le frontend suit le contrat `openapi.yaml`.

## Organisation Git

- Une branche par fonctionnalité : `feature/rest-sessions`, `feature/soap-charger`, `feature/grpc-energy`, `feature/gateway`, `feature/frontend`.
- Intégration dans `main` par pull request.
- Petits commits réguliers de chaque membre.
- Messages de commit conventionnels : `feat:`, `fix:`, `test:`, `docs:`, `chore:`.

## Tests

- Tests Java : `mvn test` dans le dossier de chaque service (à compléter).
- Collection Postman : dossier `postman/` (à compléter).
- Exemples grpcurl : voir « Tests gRPC » ci-dessous.

### Tests gRPC

energy-service a des tests unitaires (calcul heures pleines / creuses, validation, estimation du temps restant) et un test d'intégration qui démarre le serveur gRPC en mémoire avec une base H2. Sans Maven installé :

```bash
docker run --rm -v "$PWD/energy-service:/app" -w /app maven:3.9-eclipse-temurin-17 mvn -q test
```

Le dossier `scripts/grpc/` contient des scripts de test manuel (versions `.sh` et `.ps1`). Ils utilisent l'image Docker `fullstorydev/grpcurl` sur le réseau Docker Compose, il faut donc que la stack soit démarrée (`docker compose up -d`). La reflection gRPC est activée, le fichier `.proto` n'est pas nécessaire.

| Script | Rôle |
|---|---|
| `list-services` | Liste les services et décrit `EnergyService` |
| `get-session-energy [session_id]` | Bilan d'une session (par défaut la dernière session du simulateur) |
| `watch-session [session_id] [durée]` | Suivi en direct d'une session (à lancer pendant une simulation) |
| `not-found` | Appel sur une session inconnue, réponse attendue `NotFound` |

Exemples : `./scripts/grpc/get-session-energy.sh` ou `.\scripts\grpc\get-session-energy.ps1`.

## Planning indicatif

| Semaine | Objectif |
|---|---|
| 1 | Contrats : XSD (SOAP), `energy.proto` (gRPC), `openapi.yaml` (REST) |
| 2-3 | Implémentation des services : session-service, charger-gateway-service, energy-service, charger-simulator |
| 4 | Appels inter-services (SOAP, gRPC), résilience (timeouts, retry, circuit breaker), api-gateway, frontend |
| 5 | Docker Compose, tests, README, diagramme, rapport et préparation de la soutenance |
