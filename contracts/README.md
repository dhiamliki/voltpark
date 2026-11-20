# Contrats

- REST (OpenAPI) : `session-service/src/main/resources/openapi/openapi.yaml`
- SOAP (XSD, WSDL genere) : `charger-gateway-service/src/main/resources/xsd/charger.xsd`
- gRPC (proto) : `energy-service/src/main/proto/energy/v1/energy.proto`

## Contrat gRPC partage

L'original de `energy.proto` appartient a `energy-service`. Deux copies existent pour generer les stubs clients :

- `session-service/src/main/proto/energy/v1/energy.proto`
- `charger-simulator/src/main/proto/energy/v1/energy.proto`

Toute modification de l'original doit etre recopiee dans les deux copies, dans le meme commit.
