// Transforme une reponse brute de l'API Open Charge Map (GET /v3/poi, compact=false)
// en data/stations-tn.json, le fichier lu par session-service et charger-gateway-service.
//
// Usage : node scripts/data/normalize-stations.mjs <reponse-ocm.json> [sortie.json]
//
// Regles :
//   - on garde les lieux operationnels et publics (Public, Public - Pay At Location,
//     Public - Notice Required) ; les lieux prives ou reserves aux membres sont ecartes ;
//   - les concessionnaires automobiles (showrooms) sont ecartes, meme s'ils sont marques publics :
//     seules restent les stations-service, aires d'autoroute, municipalites et centres commerciaux ;
//   - les connecteurs sont regroupes par type (CCS, CHAdeMO, Type 2), puissance et courant ;
//   - une station est rattachee au corridor "A1" si son adresse cite l'A1 ou si elle est
//     a moins de toleranceKm (3 km) du trace approximatif de data/a1-path.json.
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const [input, output = join(root, "data", "stations-tn.json")] = process.argv.slice(2);
if (!input) {
  console.error("usage : node scripts/data/normalize-stations.mjs <reponse-ocm.json> [sortie.json]");
  process.exit(1);
}

const PUBLIC_USAGE = new Map([[1, "public"], [5, "semi-public"], [7, "semi-public"]]);
// concessionnaires automobiles : nom contenant showroom / concession, ou fiches connues
// (460464 BYD Showroom Bir El Kassaa, 462507 MG MAXI Marsa, 460465 Showroom MG Charguia)
const DEALER_NAME = /showroom|concession|dealer/i;
const DEALER_IDS = new Set([460464, 462507, 460465]);
// pseudo-operateurs d'Open Charge Map : "(Unknown Operator)", "(Business Owner at Location)"
const NO_OPERATOR = new Set([1, 45]);

const a1 = JSON.parse(readFileSync(join(root, "data", "a1-path.json"), "utf8"));
const pois = JSON.parse(readFileSync(input, "utf8"));

/** Distance (km) d'un point au segment [a, b], en projection plane locale (suffisant a cette echelle). */
function distanceToSegmentKm(p, a, b) {
  const kmLat = 111.32;
  const kmLon = 111.32 * Math.cos((p.lat * Math.PI) / 180);
  const ax = (a.lon - p.lon) * kmLon, ay = (a.lat - p.lat) * kmLat;
  const bx = (b.lon - p.lon) * kmLon, by = (b.lat - p.lat) * kmLat;
  const dx = bx - ax, dy = by - ay;
  const len2 = dx * dx + dy * dy;
  const t = len2 === 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2));
  return Math.hypot(ax + t * dx, ay + t * dy);
}

function distanceToA1Km(lat, lon) {
  let best = Infinity;
  for (let i = 1; i < a1.points.length; i++) {
    best = Math.min(best, distanceToSegmentKm({ lat, lon }, a1.points[i - 1], a1.points[i]));
  }
  return best;
}

function connectorType(connection) {
  const title = connection.ConnectionType?.Title ?? "";
  if (/CCS/i.test(title)) return "CCS";
  if (/CHAdeMO/i.test(title)) return "CHAdeMO";
  if (/Type 2/i.test(title)) return "Type 2";
  return title || "Inconnu";
}

function currentType(connection) {
  const title = connection.CurrentType?.Title;
  if (!title) return null;
  return /DC/.test(title) ? "DC" : "AC";
}

function connections(poi) {
  const groups = new Map();
  for (const c of poi.Connections ?? []) {
    if (c.StatusType && c.StatusType.IsOperational === false) continue;
    const entry = { type: connectorType(c), powerKw: c.PowerKW ?? null, current: currentType(c), quantity: c.Quantity ?? 1 };
    const key = `${entry.type}|${entry.powerKw}|${entry.current}`;
    if (groups.has(key)) groups.get(key).quantity += entry.quantity;
    else groups.set(key, entry);
  }
  return [...groups.values()];
}

function day(value) {
  return value ? String(value).slice(0, 10) : null;
}

const stations = pois
  .filter((poi) => poi.StatusType?.IsOperational === true)
  .filter((poi) => PUBLIC_USAGE.has(poi.UsageTypeID ?? poi.UsageType?.ID))
  .filter((poi) => !DEALER_IDS.has(poi.ID) && !DEALER_NAME.test(poi.AddressInfo?.Title ?? ""))
  .map((poi) => {
    const info = poi.AddressInfo;
    const address = [info.AddressLine1, info.AddressLine2].filter(Boolean).join(", ");
    const operatorId = poi.OperatorID ?? poi.OperatorInfo?.ID;
    const station = {
      ocmId: poi.ID,
      name: info.Title,
      address,
      town: info.Town || null,
      latitude: info.Latitude,
      longitude: info.Longitude,
      operator: operatorId == null || NO_OPERATOR.has(operatorId) ? null : poi.OperatorInfo?.Title ?? null,
      usageType: PUBLIC_USAGE.get(poi.UsageTypeID ?? poi.UsageType?.ID),
      connections: connections(poi),
      lastVerified: day(poi.DateLastVerified ?? poi.DateLastStatusUpdate),
    };
    const mentionsA1 = /\bA1\b/.test(`${info.Title} ${address}`);
    const nearA1 = distanceToA1Km(info.Latitude, info.Longitude) <= a1.toleranceKm;
    station.corridor = mentionsA1 || nearA1 ? "A1" : null;
    return station;
  })
  .filter((s) => s.connections.length > 0)
  .sort((a, b) => a.ocmId - b.ocmId);

const result = {
  source: "Open Charge Map (https://openchargemap.org)",
  licence: "CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/)",
  fetchedAt: process.env.OCM_FETCHED_AT ?? new Date().toISOString().slice(0, 10),
  method: process.env.OCM_METHOD ?? "api",
  stations,
};
writeFileSync(output, JSON.stringify(result, null, 2) + "\n", "utf8");
console.log(`${stations.length} stations ecrites dans ${output} (${stations.filter((s) => s.corridor === "A1").length} sur l'A1)`);
