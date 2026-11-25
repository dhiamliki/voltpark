import { useEffect, useMemo, useRef, useState } from 'react'
import L from 'leaflet'
import { CircleMarker, MapContainer, Marker, Popup, TileLayer, useMap } from 'react-leaflet'
import { ChevronRight, LocateFixed } from 'lucide-react'
import type { Station } from '../api/voltpark'
import { stationCounts } from '../lib/status'
import { ICON_STROKE } from './ui'

const TUNIS: [number, number] = [36.8065, 10.1815]
/** Nombre d'erreurs de tuiles sans aucune tuile chargee avant de passer a la liste (hors ligne). */
const TILE_ERRORS_BEFORE_FALLBACK = 4

/** Eclair de l'icone lucide "zap" (meme trace que dans le reste de l'interface). */
const BOLT_SVG =
  '<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" stroke="currentColor" stroke-width="2" stroke-linejoin="round" aria-hidden="true"><path d="M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z"/></svg>'

/** Epingle sombre (ou couleur accent si selectionnee) avec un eclair, en SVG. */
function pinIcon(selected: boolean): L.DivIcon {
  const bolt = BOLT_SVG
  return L.divIcon({
    className: `pin ${selected ? 'pin--selected' : ''}`,
    html: `<svg class="pin__shape" viewBox="0 0 32 42" aria-hidden="true"><path d="M16 41C16 41 30 25.6 30 15A14 14 0 0 0 2 15C2 25.6 16 41 16 41Z"/></svg><span class="pin__bolt">${bolt}</span>`,
    iconSize: selected ? [38, 50] : [32, 42],
    iconAnchor: selected ? [19, 49] : [16, 41],
    popupAnchor: [0, selected ? -46 : -38],
  })
}

function FitStations({ stations }: { stations: Station[] }) {
  const map = useMap()
  const key = stations.map((s) => s.id).join(',')
  useEffect(() => {
    const coords = stations
      .filter((s) => s.latitude != null && s.longitude != null)
      .map((s) => [s.latitude, s.longitude] as [number, number])
    if (coords.length > 1) map.fitBounds(coords, { padding: [60, 60], maxZoom: 14 })
    else if (coords.length === 1) map.setView(coords[0], 14)
    // eslint-disable-next-line react-hooks/exhaustive-deps -- recadrer seulement si la liste change
  }, [key, map])
  return null
}

function LocateButton({ onLocated }: { onLocated: (position: [number, number]) => void }) {
  const map = useMap()
  const [busy, setBusy] = useState(false)
  return (
    <button
      type="button"
      className="map-locate"
      aria-label="Me localiser"
      disabled={busy}
      onClick={() => {
        if (!navigator.geolocation) return
        setBusy(true)
        navigator.geolocation.getCurrentPosition(
          (pos) => {
            const here: [number, number] = [pos.coords.latitude, pos.coords.longitude]
            onLocated(here)
            map.flyTo(here, 14)
            setBusy(false)
          },
          () => setBusy(false),
          { timeout: 8000 },
        )
      }}
    >
      <LocateFixed size={20} strokeWidth={ICON_STROKE} />
    </button>
  )
}

export function StationMap({ stations, selectedId, onSelect, onOpen, onLocated, userPosition, onTilesFailed }: {
  stations: Station[]
  selectedId?: number
  onSelect: (station: Station) => void
  onOpen: (station: Station) => void
  onLocated: (position: [number, number]) => void
  userPosition: [number, number] | null
  onTilesFailed: () => void
}) {
  const icons = useMemo(() => ({ normal: pinIcon(false), selected: pinIcon(true) }), [])
  const markers = useRef(new Map<number, L.Marker>())

  // la bulle de la station selectionnee est ouverte, comme dans la maquette
  useEffect(() => {
    if (selectedId == null) return
    const timer = setTimeout(() => markers.current.get(selectedId)?.openPopup(), 300)
    return () => clearTimeout(timer)
  }, [selectedId, stations])
  const [tiles, setTiles] = useState({ loaded: 0, failed: 0 })

  useEffect(() => {
    if (tiles.loaded === 0 && tiles.failed >= TILE_ERRORS_BEFORE_FALLBACK) onTilesFailed()
  }, [tiles, onTilesFailed])

  return (
    <div className="map">
      <MapContainer center={TUNIS} zoom={13} zoomControl={false} scrollWheelZoom className="map__leaflet">
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          eventHandlers={{
            tileload: () => setTiles((t) => (t.loaded > 0 ? t : { ...t, loaded: 1 })),
            tileerror: () => setTiles((t) => ({ ...t, failed: t.failed + 1 })),
          }}
        />
        <FitStations stations={stations} />
        {stations.map((station) => {
          const counts = stationCounts(station)
          const selected = station.id === selectedId
          return (
            <Marker
              key={station.id}
              position={[station.latitude ?? TUNIS[0], station.longitude ?? TUNIS[1]]}
              icon={selected ? icons.selected : icons.normal}
              zIndexOffset={selected ? 1000 : 0}
              eventHandlers={{ click: () => onSelect(station) }}
              title={station.name}
              ref={(marker) => {
                if (station.id == null) return
                if (marker) markers.current.set(station.id, marker)
                else markers.current.delete(station.id)
              }}
            >
              <Popup className="station-popup" closeButton={false} maxWidth={260}>
                <button type="button" className="station-popup__body" onClick={() => onOpen(station)}>
                  <span>
                    <strong>{station.name}</strong>
                    <span className="station-popup__meta">
                      {counts.available} disponible{counts.available > 1 ? 's' : ''}
                    </span>
                  </span>
                  <ChevronRight size={18} strokeWidth={ICON_STROKE} />
                </button>
              </Popup>
            </Marker>
          )
        })}
        {userPosition && (
          <CircleMarker center={userPosition} radius={8} pathOptions={{ className: 'map-me' }} />
        )}
        <LocateButton onLocated={onLocated} />
      </MapContainer>
    </div>
  )
}
