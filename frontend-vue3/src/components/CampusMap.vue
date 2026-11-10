<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import type { MapPlace } from '../services/api'
import { wgs84ToGcj02 } from '../services/coordinates'

interface MapInstance { destroy(): void; setCenter(position: [number, number]): void; clearMap(): void; add(markers: MarkerInstance[]): void }
interface MarkerInstance { on(event: string, callback: () => void): void }
interface MapApi { Map: new (element: HTMLElement, options: { zoom: number; center: [number, number]; resizeEnable: boolean }) => MapInstance; Marker: new (options: { position: [number, number]; title: string }) => MarkerInstance }
interface MapWindow extends Window { AMap?: MapApi; _AMapSecurityConfig?: { serviceHost: string }; rcdisMapReady?: () => void }
const props = defineProps<{ x: number; y: number; places: MapPlace[]; selectedId: string }>()
const emit = defineEmits<{ select: [id: string] }>()
const container = ref<HTMLElement>()
const error = ref('')
let map: MapInstance | undefined
let disposed = false
let timer: number | undefined
function renderMarkers(): void {
  const api = (window as MapWindow).AMap
  if (!map || !api) return
  map.clearMap()
  const markers: MarkerInstance[] = []
  for (const place of props.places) {
    const parts = place.location.split(',').map(Number)
    if (parts.length === 2 && Number.isFinite(parts[0]) && Number.isFinite(parts[1])) { const marker = new api.Marker({ position: [parts[0]!, parts[1]!], title: place.name }); marker.on('click', () => emit('select', place.id)); markers.push(marker) }
  }
  map.add(markers)
}
function initialize(): void {
  if (disposed || !container.value) return
  const api = (window as MapWindow).AMap
  if (!api) { error.value = '地图加载失败，请检查网络或高德 Key 配置。'; return }
  if (timer) window.clearTimeout(timer)
  map = new api.Map(container.value, { zoom: 15, center: wgs84ToGcj02(props.x, props.y), resizeEnable: true })
  renderMarkers()
}
onMounted(() => {
  const key = import.meta.env.VITE_AMAP_JS_KEY
  if (!key) { error.value = '地图尚未配置，请联系管理员。'; return }
  const mapWindow = window as MapWindow
  if (mapWindow.AMap) { initialize(); return }
  mapWindow._AMapSecurityConfig = { serviceHost: `${window.location.origin}/_AMapService` }
  mapWindow.rcdisMapReady = initialize
  const script = document.createElement('script')
  script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(key)}&callback=rcdisMapReady`
  script.onerror = () => { error.value = '地图加载失败，请检查网络。' }
  document.head.appendChild(script)
  timer = window.setTimeout(() => { if (!map) error.value = '地图加载超时，请检查网络或高德 Key 配置。' }, 15000)
})
watch(() => [props.x, props.y], () => { map?.setCenter(wgs84ToGcj02(props.x, props.y)) })
watch(() => props.places, renderMarkers)
watch(() => props.selectedId, (id) => { const place = props.places.find((item) => item.id === id); if (!place) return; const point = place.location.split(',').map(Number); if (point.length === 2 && Number.isFinite(point[0]) && Number.isFinite(point[1])) map?.setCenter([point[0]!, point[1]!]) })
onUnmounted(() => { disposed = true; if (timer) window.clearTimeout(timer); map?.destroy() })
</script>
<template><div class="map-shell"><div ref="container" class="map-canvas" aria-label="校园附近高德地图"></div><p v-if="error" class="map-error" role="alert">{{error}}</p></div></template>
<style scoped>.map-shell{position:relative;margin-top:18px}.map-canvas{width:100%;height:320px;border-radius:10px;overflow:hidden;background:#eef2ee}.map-error{position:absolute;inset:0;display:grid;place-items:center;margin:0;padding:20px;color:#6d786e;font-size:13px;text-align:center;background:#f3f6f3;border-radius:10px}@media(max-width:700px){.map-canvas{height:250px}}</style>
