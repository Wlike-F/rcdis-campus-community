<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Location, Search } from '@element-plus/icons-vue'
import CampusMap from '../components/CampusMap.vue'
import { communityApi } from '../services/api'
import type { MapPlace } from '../services/api'
import type { ShopType } from '../types/api'
import { currentLocation, currentLocationLabel, setCurrentLocation } from '../services/campus'
import { cityPlaces } from '../services/cityGuide'
const route = useRoute()
const types = ref<ShopType[]>([])
const query = ref(String(route.query.q || ''))
const selectedType = ref(1)
const selectedId = ref('')
const places = ref<MapPlace[]>([])
const loading = ref(false)
const error = ref('')
let requestId = 0
const title = computed(() => types.value.find((item) => item.id === selectedType.value)?.name || '附近地点')
async function load(): Promise<void> {
  const request = ++requestId
  loading.value = true; error.value = ''; selectedId.value = ''
  const keyword = query.value.trim() || (selectedType.value ? title.value : '美食')
  try {
    const result = await communityApi.mapNearby(currentLocation.value.x, currentLocation.value.y, keyword)
    if (request === requestId) places.value = result
  } catch (exception) {
    if (request === requestId) { places.value = []; error.value = exception instanceof Error ? exception.message : '附近地点加载失败，请重试。' }
  } finally { if (request === requestId) loading.value = false }
}
function chooseType(id: number): void { selectedType.value = id; query.value = ''; void load() }
function selectPlace(id: string): void {
  selectedId.value = id
  void nextTick(() => document.getElementById(`place-${id}`)?.scrollIntoView({ block: 'nearest', behavior: 'smooth' }))
}
function updateLocation(): void {
  if (!navigator.geolocation) { currentLocationLabel.value = '当前浏览器不支持定位'; return }
  navigator.geolocation.getCurrentPosition((position) => setCurrentLocation(position.coords.longitude, position.coords.latitude), () => { currentLocationLabel.value = '定位失败，请检查浏览器定位权限' }, { timeout: 10000 })
}
onMounted(async () => {
  try { types.value = await communityApi.shopTypes() } catch { error.value = '商户分类加载失败，请刷新页面。' }
  const id = Number(route.query.typeId)
  selectedType.value = Number.isSafeInteger(id) && id > 0 ? id : 1
  await load()
})
watch(currentLocation, () => { void load() })
watch(() => route.query.q, (value) => { query.value = String(value || ''); selectedType.value = 0; void load() })
watch(() => route.query.typeId, (value) => { const id = Number(value); selectedType.value = Number.isSafeInteger(id) && id > 0 ? id : 0; query.value = ''; void load() })
</script>
<template>
  <div class="nearby">
    <header class="heading"><div><span class="eyebrow">校园周边</span><h1>发现附近的好去处</h1><p>从校园出发，看看身边有什么。</p></div><button class="location" @click="updateLocation"><el-icon><Location /></el-icon>{{currentLocationLabel}} <span>更新位置</span></button></header>
    <form class="search" @submit.prevent="load"><el-icon><Search /></el-icon><input v-model="query" maxlength="40" aria-label="搜索附近商户或地点" placeholder="搜索店铺、咖啡、书店…"><button :disabled="loading">{{loading ? '搜索中…' : '搜索附近'}}</button></form>
    <nav class="categories" aria-label="地点分类"><button v-for="type in types" :key="type.id" :class="{active: selectedType === type.id}" @click="chooseType(type.id)">{{type.name}}</button></nav>
    <div class="discovery"><section class="results" aria-label="附近地点列表"><div class="results-head"><div><h2>{{query.trim() ? `“${query.trim()}”的结果` : title}}</h2><p>高德地图地点 · 已关联商户可查看社区内容</p></div><span v-if="!loading && !error">{{places.length}} 个地点</span></div><div v-if="error" class="message" role="alert">{{error}} <button @click="load">重试</button></div><div v-else-if="loading" class="message">正在查找附近地点…</div><div v-else-if="!places.length" class="message">暂时没有找到相关地点，试试其他关键词。</div><div v-else class="place-list"><article v-for="(place,index) in places" :id="`place-${place.id}`" :key="place.id" class="place" :class="{selected: selectedId === place.id}" @click="selectPlace(place.id)"><button class="number" :aria-label="`在地图查看${place.name}`" @click.stop="selectPlace(place.id)">{{index + 1}}</button><div class="place-body"><div class="place-title"><strong>{{place.name}}</strong><span :class="{community: place.shopId}">{{place.shopId ? '社区商户' : '高德地点'}}</span></div><p>{{place.address}}</p><div class="place-foot"><span>{{place.distance ? `直线约 ${place.distance} 米` : '距离暂不可用'}}</span><RouterLink :to="place.shopId ? `/shop/${place.shopId}` : `/place/${place.id}`">查看详情 →</RouterLink></div></div></article></div></section><section class="map-panel" aria-label="附近地点地图"><CampusMap :x="currentLocation.x" :y="currentLocation.y" :places="places" :selected-id="selectedId" @select="selectPlace"/><p>位置与距离为近似值，营业状态请出行前确认。</p></section></div>
    <section class="city"><div class="city-head"><div><span class="eyebrow">城市指南</span><h2>再远一点，逛逛沈阳</h2></div><span>地点不代表校园步行距离</span></div><div class="city-list"><RouterLink v-for="place in cityPlaces" :key="place.id" :to="`/city/${place.id}`"><img :src="place.image" :alt="place.image === '/city-guide.svg' ? '城市插画' : place.name"><strong>{{place.name}}</strong><small>{{place.category}}</small></RouterLink></div><p class="credits">图片：<a href="https://commons.wikimedia.org/wiki/File:Mukden_Palace_drone_view_5.jpg" target="_blank" rel="noopener noreferrer">故宫</a>、<a href="https://commons.wikimedia.org/wiki/File:Zhongjie_drone_view_3.jpg" target="_blank" rel="noopener noreferrer">中街</a> © Techyan，<a href="https://creativecommons.org/licenses/by-sa/4.0/" target="_blank" rel="noopener noreferrer">CC BY-SA 4.0</a>；<a href="https://commons.wikimedia.org/?curid=154244625" target="_blank" rel="noopener noreferrer">张学良旧居</a>：ScareCriterion12；<a href="https://commons.wikimedia.org/?curid=155034959" target="_blank" rel="noopener noreferrer">青年公园</a>：E2568，均为 <a href="https://creativecommons.org/publicdomain/zero/1.0/" target="_blank" rel="noopener noreferrer">CC0</a>；其余为城市插画。</p></section>
  </div>
</template>
<style scoped>
.nearby{max-width:1120px;margin:auto;color:#27362b}.heading{display:flex;justify-content:space-between;align-items:end;gap:20px;margin-bottom:24px}.eyebrow{font-size:12px;color:#698774}.heading h1{font-size:30px;letter-spacing:-.03em;margin:8px 0}.heading p,.results-head p{font-size:13px;color:#829087;margin:0}.location{border:1px solid #e1e9e1;background:#fff;color:#566f5e;border-radius:22px;padding:10px 13px;font-size:12px;cursor:pointer;display:flex;align-items:center;gap:7px}.location span{color:#315c4b}.search{display:flex;align-items:center;gap:12px;background:#fff;border:1px solid #dfe7df;border-radius:12px;padding:7px 8px 7px 16px}.search .el-icon{font-size:18px;color:#829489}.search input{flex:1;min-width:0;border:0;outline:0;font:inherit;font-size:14px;background:transparent}.search button{border:0;border-radius:8px;background:#315c4b;color:#fff;padding:11px 19px;cursor:pointer}.search button:disabled{opacity:.6}.categories{display:flex;overflow-x:auto;gap:8px;margin:20px 0}.categories button{white-space:nowrap;border:1px solid #e1e8e1;border-radius:20px;background:#fff;padding:8px 13px;color:#65746a;font-size:12px;cursor:pointer}.categories button.active{background:#315c4b;border-color:#315c4b;color:#fff}.discovery{display:grid;grid-template-columns:minmax(320px,.85fr) minmax(0,1.15fr);gap:18px;align-items:start}.results,.map-panel{background:#fff;border:1px solid #e7ece7;border-radius:14px}.results{overflow:hidden}.results-head{padding:22px;display:flex;justify-content:space-between;gap:12px;align-items:start}.results-head h2{font-size:18px;margin:0 0 7px}.results-head p{font-size:11px;line-height:1.6}.results-head>span{font-size:11px;color:#8b998e;white-space:nowrap}.place-list{max-height:510px;overflow:auto;border-top:1px solid #eef1ee}.place{display:flex;gap:12px;padding:17px 20px;border-bottom:1px solid #f0f2f0;cursor:pointer}.place.selected{background:#eff6f0}.number{width:30px;height:30px;border:0;border-radius:9px;background:#e6f0e8;color:#416b4e;font-weight:600;cursor:pointer;flex:none}.place.selected .number{background:#315c4b;color:#fff}.place-body{flex:1;min-width:0}.place-title{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.place-title strong{font-size:14px;color:#28392d}.place-title span{font-size:10px;background:#f0f2ef;color:#778278;padding:4px 6px;border-radius:5px}.place-title span.community{background:#e4f1e7;color:#396d48}.place p{font-size:12px;line-height:1.5;color:#87948a;margin:8px 0}.place-foot{display:flex;justify-content:space-between;gap:8px;font-size:11px;color:#8c9990}.place-foot a{color:#315c4b;white-space:nowrap}.message{padding:40px 22px;color:#849188;font-size:13px;line-height:1.7}.message button{background:none;border:0;color:#315c4b;cursor:pointer}.map-panel{padding:0 10px 12px;position:sticky;top:95px}.map-panel :deep(.map-shell){margin-top:10px}.map-panel :deep(.map-canvas){height:500px}.map-panel p{color:#97a299;font-size:11px;margin:10px}.city{margin-top:52px}.city-head{display:flex;align-items:end;justify-content:space-between;gap:12px}.city-head h2{font-size:21px;margin:7px 0 15px}.city-head>span{font-size:11px;color:#8d9a8f;margin-bottom:15px}.city-list{display:grid;grid-auto-flow:column;grid-auto-columns:155px;gap:12px;overflow:auto}.city-list a{display:flex;flex-direction:column;gap:6px;font-size:13px}.city-list img{width:100%;height:105px;object-fit:cover;border-radius:9px}.city-list small{color:#8a978d;font-size:11px}.credits{font-size:10px;color:#9aa49c;line-height:1.7}.credits a{text-decoration:underline}@media(max-width:760px){.heading{align-items:start;flex-direction:column;gap:14px}.heading h1{font-size:25px}.location{max-width:100%}.discovery{display:flex;flex-direction:column-reverse;gap:12px}.results,.map-panel{width:100%}.map-panel{position:static}.map-panel :deep(.map-canvas){height:250px}.place-list{max-height:none}.city{margin-top:36px}.city-head>span{max-width:110px;text-align:right}.city-list{grid-auto-columns:138px}.city-list img{height:90px}}
</style>
