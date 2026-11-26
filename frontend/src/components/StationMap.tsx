import { useEffect, useMemo, useRef, useState } from 'react'
import L from 'leaflet'
import { CircleMarker, MapContainer, Marker, Polyline, Popup, TileLayer, useMap } from 'react-leaflet'
import { ChevronRight, LocateFixed, Minus, Plus } from 'lucide-react'
import type { Station } from '../api/voltpark'
import { shortName } from '../lib/format'
import { A1_PATH } from '../lib/regions'
import { stationCounts, stationTone } from '../lib/status'
import { ICON_STROKE, StatusDot } from './ui'

const TUNIS: [number, number] = [36.8065, 10.1815]
/** Nombre d'erreurs de tuiles sans aucune tuile chargee avant de passer a la liste (hors ligne). */
const TILE_ERRORS_BEFORE_FALLBACK = 4

/**
 * Fond de carte clair et sobre, sans cle d'API : Esri World Light Gray Canvas.
 * (CARTO Positron demande desormais une cle.) Une autre source peut etre choisie au build
 * avec VITE_MAP_TILES_URL et VITE_MAP_TILES_ATTRIBUTION.
 */
const TILES_URL =
  import.meta.env.VITE_MAP_TILES_URL ??
  'https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Light_Gray_Base/MapServer/tile/{z}/{y}/{x}'
const TILES_ATTRIBUTION =
  import.meta.env.VITE_MAP_TILES_ATTRIBUTION ?? 'Tiles &copy; <a href="https://www.esri.com">Esri</a> &mdash; Esri, HERE, Garmin, OpenStreetMap contributors'
/** Vert sauge du theme (--accent) : couleur du trace de l'A1. */
const A1_COLOR = '#718875'
/** Licence des donnees des stations (data/README.md). */
const STATIONS_ATTRIBUTION =
  'Bornes : <a href="https://openchargemap.org">Open Charge Map</a> (<a href="https://creativecommons.org/licenses/by/4.0/">CC BY 4.0</a>)'

/** Distance minimale (px) entre deux epingles ; plus proches, elles sont ecartees en cercle. */
const MIN_PIN_DISTANCE = 42

type Offsets = Map<number | undefined, [number, number]>

/** Eclair de l'icone lucide "zap" (meme trace que dans le reste de l'interface). */
const BOLT_SVG =
  '<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" stroke="currentColor" stroke-width="2" stroke-linejoin="round" aria-hidden="true"><path d="M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z"/></svg>'

/**
 * Epingle sombre avec un eclair (couleur accent si selectionnee) et une pastille :
 * nombre de bornes disponibles, colore selon la disponibilite de la station.
 */
function pinIcon(station: Station, selected: boolean, offset: [number, number] = [0, 0]): L.DivIcon {
  const { available } = stationCounts(station)
  const tone = stationTone(station)
  const [dx, dy] = offset
  return L.divIcon({
    className: `pin ${selected ? 'pin--selected' : ''}`,
    html:
      `<svg class="pin__shape" viewBox="0 0 32 42" aria-hidden="true"><path d="M16 41C16 41 30 25.6 30 15A14 14 0 0 0 2 15C2 25.6 16 41 16 41Z"/></svg>` +
      `<span class="pin__bolt">${BOLT_SVG}</span>` +
      `<span class="pin__count pin__count--${tone}">${available}</span>`,
    iconSize: selected ? [38, 50] : [32, 42],
    iconAnchor: selected ? [19 - dx, 49 - dy] : [16 - dx, 41 - dy],
    popupAnchor: [dx, (selected ? -46 : -38) + dy],
  })
}

/**
 * Ecarte les epingles qui se chevauchent au niveau de zoom courant : chaque groupe d'epingles
 * trop proches est dispose en cercle autour de son centre. Ce n'est pas un regroupement :
 * chaque station garde son epingle. Recalcule a chaque zoom.
 */
function Declutter({ stations, onChange }: { stations: Station[]; onChange: (offsets: Offsets) => void }) {
  const map = useMap()
  useEffect(() => {
    const compute = () => {
      const points = stations
        .filter((s) => s.latitude != null && s.longitude != null)
        .map((s) => ({ id: s.id, p: map.latLngToLayerPoint([s.latitude as number, s.longitude as number]) }))

      // groupes initiaux : epingles reliees de proche en proche a moins de MIN_PIN_DISTANCE
      let groups: Array<typeof points> = []
      for (const point of points) {
        const near = groups.filter((g) => g.some((other) => other.p.distanceTo(point.p) < MIN_PIN_DISTANCE))
        groups = groups.filter((g) => !near.includes(g))
        groups.push([point, ...near.flat()])
      }

      // chaque groupe est dispose en cercle ; si deux epingles se gênent encore, on fusionne leurs groupes
      const layout = () => {
        const placed: Array<{ id: number | undefined; x: number; y: number; group: number }> = []
        groups.forEach((group, index) => {
          if (group.length === 1) {
            placed.push({ id: group[0].id, x: group[0].p.x, y: group[0].p.y, group: index })
            return
          }
          const cx = group.reduce((sum, g) => sum + g.p.x, 0) / group.length
          const cy = group.reduce((sum, g) => sum + g.p.y, 0) / group.length
          const radius = Math.max(MIN_PIN_DISTANCE / (2 * Math.sin(Math.PI / group.length)), MIN_PIN_DISTANCE / 2 + 6)
          group.forEach((g, i) => {
            const angle = (2 * Math.PI * i) / group.length - Math.PI / 2
            placed.push({ id: g.id, x: cx + radius * Math.cos(angle), y: cy + radius * Math.sin(angle), group: index })
          })
        })
        return placed
      }
      let placed = layout()
      for (let round = 0; round < 6; round++) {
        let merged = false
        outer: for (const a of placed) {
          for (const b of placed) {
            if (a.group !== b.group && Math.hypot(a.x - b.x, a.y - b.y) < MIN_PIN_DISTANCE) {
              const [keep, drop] = a.group < b.group ? [a.group, b.group] : [b.group, a.group]
              groups[keep] = [...groups[keep], ...groups[drop]]
              groups.splice(drop, 1)
              merged = true
              break outer
            }
          }
        }
        if (!merged) break
        placed = layout()
      }

      const offsets: Offsets = new Map()
      for (const pin of placed) {
        const origin = points.find((pt) => pt.id === pin.id)
        if (origin) offsets.set(pin.id, [pin.x - origin.p.x, pin.y - origin.p.y])
      }
      onChange(offsets)
    }
    compute()
    map.on('zoomend', compute)
    return () => {
      map.off('zoomend', compute)
    }
  }, [map, stations, onChange])
  return null
}

/**
 * Au chargement (et quand la liste change), la carte montre toutes les stations, ou tout le
 * corridor de l'A1 avec le filtre "Autoroute A1".
 * Si la zone de la carte change de taille (panneau du bas charge ensuite), on recadre,
 * tant que l'utilisateur n'a pas deplace ou zoome la carte lui-meme.
 */
function FitStations({ stations, corridor }: { stations: Station[]; corridor?: [number, number][] }) {
  const map = useMap()
  const key = (corridor ? 'corridor:' : '') + stations.map((s) => s.id).join(',')
  useEffect(() => {
    let userMoved = false
    let fitting = false
    const fit = () => {
      if (userMoved) return
      const coords =
        corridor ??
        stations
          .filter((s) => s.latitude != null && s.longitude != null)
          .map((s) => [s.latitude, s.longitude] as [number, number])
      fitting = true
      map.invalidateSize()
      if (coords.length > 1) // marge plus grande en haut : la legende y est posee
        map.fitBounds(coords, { paddingTopLeft: [64, 96], paddingBottomRight: [64, 48], maxZoom: 14, animate: false })
      else if (coords.length === 1) map.setView(coords[0], 14, { animate: false })
      fitting = false
    }
    const onUserMove = () => {
      if (!fitting) userMoved = true
    }
    fit()
    map.on('dragstart zoomstart', onUserMove)
    const observer = new ResizeObserver(() => fit())
    observer.observe(map.getContainer())
    return () => {
      observer.disconnect()
      map.off('dragstart zoomstart', onUserMove)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- recadrer seulement si la liste change
  }, [key, map])
  return null
}

/** Boutons ronds flottants comme dans la maquette : localisation, zoom avant et arriere. */
function MapControls({ onLocated }: { onLocated: (position: [number, number]) => void }) {
  const map = useMap()
  const [busy, setBusy] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  // les clics sur les boutons ne doivent pas deplacer la carte
  useEffect(() => {
    if (ref.current) L.DomEvent.disableClickPropagation(ref.current)
  }, [])

  return (
    <div className="map-controls" ref={ref}>
      <button
        type="button"
        className="map-button"
        aria-label="Me localiser"
        disabled={busy}
        onClick={() => {
          if (!navigator.geolocation) return
          setBusy(true)
          navigator.geolocation.getCurrentPosition(
            (pos) => {
              const here: [number, number] = [pos.coords.latitude, pos.coords.longitude]
              onLocated(here)
              map.flyTo(here, 13)
              setBusy(false)
            },
            () => setBusy(false),
            { timeout: 8000 },
          )
        }}
      >
        <LocateFixed size={20} strokeWidth={ICON_STROKE} />
      </button>
      <div className="map-zoom">
        <button type="button" className="map-button" aria-label="Zoom avant" onClick={() => map.zoomIn()}>
          <Plus size={20} strokeWidth={ICON_STROKE} />
        </button>
        <button type="button" className="map-button" aria-label="Zoom arrière" onClick={() => map.zoomOut()}>
          <Minus size={20} strokeWidth={ICON_STROKE} />
        </button>
      </div>
    </div>
  )
}

export function StationMap({ stations, selectedId, popupId, onSelect, onOpen, onLocated, userPosition, onTilesFailed, fitCorridor = false }: {
  stations: Station[]
  /** cadre la carte sur tout le trace de l'A1 (filtre "Autoroute A1") */
  fitCorridor?: boolean
  selectedId?: number
  /** station choisie par l'utilisateur : sa bulle est ouverte (pas au premier affichage) */
  popupId?: number
  onSelect: (station: Station) => void
  onOpen: (station: Station) => void
  onLocated: (position: [number, number]) => void
  userPosition: [number, number] | null
  onTilesFailed: () => void
}) {
  const [tiles, setTiles] = useState({ loaded: 0, failed: 0 })
  const [offsets, setOffsets] = useState<Offsets>(new Map())
  const markers = useRef(new Map<number, L.Marker>())
  const icons = useMemo(
    () =>
      new Map(
        stations.map((s) => [
          s.id,
          { normal: pinIcon(s, false, offsets.get(s.id)), selected: pinIcon(s, true, offsets.get(s.id)) },
        ]),
      ),
    [stations, offsets],
  )

  useEffect(() => {
    if (tiles.loaded === 0 && tiles.failed >= TILE_ERRORS_BEFORE_FALLBACK) onTilesFailed()
  }, [tiles, onTilesFailed])

  // la bulle de la station choisie est ouverte, comme dans la maquette
  useEffect(() => {
    if (popupId == null) return
    const timer = setTimeout(() => markers.current.get(popupId)?.openPopup(), 300)
    return () => clearTimeout(timer)
  }, [popupId, stations])

  return (
    <div className="map">
      <MapContainer center={TUNIS} zoom={9} zoomControl={false} scrollWheelZoom className="map__leaflet">
        <TileLayer
          attribution={`${TILES_ATTRIBUTION} | ${STATIONS_ATTRIBUTION}`}
          url={TILES_URL}
          maxZoom={16}
          eventHandlers={{
            tileload: () => setTiles((t) => (t.loaded > 0 ? t : { ...t, loaded: 1 })),
            tileerror: () => setTiles((t) => ({ ...t, failed: t.failed + 1 })),
          }}
        />
        <Polyline positions={A1_PATH} pathOptions={{ className: 'map-a1', color: A1_COLOR, weight: 2.5, dashArray: '6 7', interactive: false }} />
        <FitStations stations={stations} corridor={fitCorridor ? A1_PATH : undefined} />
        <Declutter stations={stations} onChange={setOffsets} />
        {stations.map((station) => {
          const counts = stationCounts(station)
          const selected = station.id === selectedId
          const icon = icons.get(station.id)
          return (
            <Marker
              key={station.id}
              position={[station.latitude ?? TUNIS[0], station.longitude ?? TUNIS[1]]}
              icon={selected ? icon?.selected : icon?.normal}
              zIndexOffset={selected ? 1000 : 0}
              eventHandlers={{ click: () => onSelect(station) }}
              title={station.name}
              ref={(marker) => {
                if (station.id == null) return
                if (marker) markers.current.set(station.id, marker)
                else markers.current.delete(station.id)
              }}
            >
              <Popup className="station-popup" closeButton={false} maxWidth={280} autoPanPaddingTopLeft={[24, 64]} autoPanPaddingBottomRight={[24, 24]}>
                <button type="button" className="station-popup__body" onClick={() => onOpen(station)}>
                  <span>
                    <strong>{shortName(station.name)}</strong>
                    <span className="station-popup__meta">
                      {counts.available}/{counts.total} disponible{counts.available > 1 ? 's' : ''}
                    </span>
                  </span>
                  <ChevronRight size={18} strokeWidth={ICON_STROKE} />
                </button>
              </Popup>
            </Marker>
          )
        })}
        {userPosition && <CircleMarker center={userPosition} radius={8} pathOptions={{ className: 'map-me' }} />}
        <MapControls onLocated={onLocated} />
      </MapContainer>
      <div className="map-legend" aria-label="Légende">
        <span><StatusDot tone="available" /> Disponible</span>
        <span><StatusDot tone="occupied" /> Occupée</span>
        <span><StatusDot tone="out" /> Hors service</span>
        <span><span className="map-legend__a1" aria-hidden /> Autoroute A1</span>
      </div>
    </div>
  )
}
