# Données des stations

## `stations-tn.json` : bornes de recharge réelles en Tunisie

- **Source** : [Open Charge Map](https://openchargemap.org), registre ouvert et collaboratif des
  bornes de recharge ([liste Tunisie](https://openchargemap.org/poi?CountryIDs=228&CountryName=tunisia)).
- **Licence des données** : [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) : réutilisation
  libre en citant la source. VoltPark l'affiche dans l'attribution de la carte
  (« Bornes : Open Charge Map (CC BY 4.0) »).
- **Date** : relevé du 26/11/2026 (`fetchedAt` dans le fichier).
- **Méthode** : `method: "manual"` : sans clé d'API, les valeurs ont été relevées une par une sur les
  fiches publiques `https://openchargemap.org/poi/details/<ocmId>`, puis passées dans le même script
  de normalisation que l'import par l'API. Ces fiches n'affichent pas la date de dernière
  vérification : `lastVerified` vaut donc `null`.

Contenu : les **16** lieux de Tunisie à la fois **opérationnels** et **publics** (sur 29 fiches) ;
les lieux privés (hôtels, résidences, concessionnaires réservés aux clients) et ceux réservés aux
membres sont écartés. Pour chaque station : `ocmId`, `name`, `address`, `town`, `latitude`,
`longitude`, `operator` (`null` quand Open Charge Map ne le connaît pas), `usageType`,
`connections` (type `CCS` / `CHAdeMO` / `Type 2`, `powerKw`, `current` AC/DC, `quantity`),
`lastVerified` et `corridor`.

`corridor` vaut `"A1"` quand l'adresse cite l'A1 ou que la station est à moins de 3 km du tracé de
`a1-path.json`.

### Regénérer le fichier depuis l'API

Avec une clé gratuite Open Charge Map (`OCM_API_KEY` dans l'environnement ou dans le `.env` local,
jamais dans un fichier versionné) et Node.js :

```bash
./scripts/data/fetch-stations.sh
.\scripts\data\fetch-stations.ps1
```

Le script appelle `GET https://api.openchargemap.io/v3/poi/?countrycode=TN&maxresults=500` (clé dans
l'en-tête `X-API-Key`), puis `scripts/data/normalize-stations.mjs` applique les filtres ci-dessus.
Recopier ensuite le fichier dans les services (règle dans [contracts/README.md](../contracts/README.md)).

## `a1-path.json` : tracé approximatif de l'autoroute A1

12 points de Tunis (Ben Arous) à Sfax, par Grombalia, Hammamet, Enfidha, Sousse et El Jem. Le tracé
est **simplifié** : il sert à dessiner le corridor sur la carte et à rattacher les stations à l'A1,
pas à la navigation. L'écart avec la route réelle peut atteindre quelques kilomètres.

## Ce qui est réel et ce qui est simulé

| Réel (Open Charge Map) | Simulé par VoltPark |
|---|---|
| nom, adresse et position des stations | disponibilité des bornes (libre, occupée, hors service) |
| opérateur quand il est connu | prix du kWh (heures pleines et creuses) et frais d'occupation |
| connecteurs, puissance et nombre de points de charge | occupation, sessions, réservations et factures |

Les bornes ne sont pas connectées à VoltPark : les sessions de charge sont jouées par
`charger-simulator`.
