# Contrats

- REST (OpenAPI) : `session-service/src/main/resources/openapi/openapi.yaml`
- SOAP (XSD, WSDL genere) : `charger-gateway-service/src/main/resources/xsd/charger.xsd`
- gRPC (proto) : `energy-service/src/main/proto/energy/v1/energy.proto`

## Contrat gRPC partage

L'original de `energy.proto` appartient a `energy-service`. Deux copies existent pour generer les stubs clients :

- `session-service/src/main/proto/energy/v1/energy.proto`
- `charger-simulator/src/main/proto/energy/v1/energy.proto`

Toute modification de l'original doit etre recopiee dans les deux copies, dans le meme commit.

## Identifiant de session partage

Une session de charge porte le meme identifiant dans les trois services :

1. `session-service` cree la session et genere son identifiant (UUID, champ `ChargingSession.id`),
   par exemple `c00598d9-cf70-419d-900a-e7403c8ddaa9`.
2. Il l'envoie a la borne dans `RemoteStartTransactionRequest.sessionId` (SOAP, champ optionnel).
3. `charger-gateway-service` le transmet au simulateur de bornes (`POST /api/simulations`)
   et le garde en base avec la transaction en cours, pour l'arreter au `RemoteStopTransaction`.
4. La borne (simulateur) envoie ses mesures avec `MeterValue.session_id` = ce meme UUID.
5. `session-service` lit le bilan avec `GetSessionEnergy(session_id)` a l'arret, et suit la
   session en direct avec `WatchSession(session_id)`.

Le `transactionId` (entier) reste l'identifiant de la transaction cote borne, utilise uniquement
dans les echanges SOAP. Les sessions generees automatiquement par le simulateur (mode planifie,
`SIMULATOR_ENABLED=true`) utilisent des identifiants `SIM-<horodatage>` qui ne correspondent a
aucune session de `session-service`.

## Fichier partage des stations

L'original est `data/stations-tn.json` (bornes reelles, Open Charge Map, voir `data/README.md`).
Deux copies servent a initialiser les bases :

- `session-service/src/main/resources/data/stations-tn.json` (stations, bornes, prix simules)
- `charger-gateway-service/src/main/resources/data/stations-tn.json` (bornes et leur statut)

Toute modification de l'original doit etre recopiee dans les deux copies, dans le meme commit.
De meme, `data/a1-path.json` (trace approximatif de l'A1) est recopie dans
`frontend/src/data/a1-path.json` pour dessiner le corridor sur la carte.
Les deux services en tirent les memes identifiants et les memes statuts simules :

- station : `ST-OCM-<ocmId>` (ex. `ST-OCM-460375`) ;
- borne : `CP-<ocmId>-<n>`, une par point de charge, numerotees a partir de 1 dans l'ordre des
  connecteurs du fichier (ex. `CP-460375-1`) ;
- statut : la borne 1 de chaque station est libre ; les autres suivent `hashCode` de leur
  identifiant modulo 10 (0-5 libre, 6-8 occupee, 9 hors service). Les deux `DataInitializer`
  doivent garder la meme regle.
