#!/bin/sh
# Telecharge les bornes de recharge de Tunisie depuis l'API Open Charge Map (v3)
# et regenere data/stations-tn.json (voir data/README.md).
#
# Usage : ./scripts/data/fetch-stations.sh
# La cle API est lue dans la variable d'environnement OCM_API_KEY, sinon dans le .env local
# (jamais dans un fichier versionne). Elle est envoyee dans l'en-tete X-API-Key.
# Ensuite : recopier data/stations-tn.json dans les services (voir contracts/README.md).
set -eu
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

KEY="${OCM_API_KEY:-}"
if [ -z "$KEY" ] && [ -f "$ROOT/.env" ]; then
  KEY="$(sed -n 's/^[[:space:]]*OCM_API_KEY[[:space:]]*=[[:space:]]*//p' "$ROOT/.env" | head -n 1 | tr -d '"\r')"
fi
if [ -z "$KEY" ]; then
  echo "OCM_API_KEY absent (variable d'environnement ou .env). Cle gratuite : https://openchargemap.org/site/developerinfo" >&2
  exit 1
fi

RAW="$(mktemp)"
trap 'rm -f "$RAW"' EXIT
URL="https://api.openchargemap.io/v3/poi/?output=json&countrycode=TN&maxresults=500&compact=false&verbose=false"
echo "Telechargement : $URL"
curl -fsS -A "voltpark-data-script" -H "X-API-Key: $KEY" -o "$RAW" "$URL"

OCM_METHOD=api node "$ROOT/scripts/data/normalize-stations.mjs" "$RAW" "$ROOT/data/stations-tn.json"
