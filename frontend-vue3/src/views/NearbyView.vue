<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Location, Search, ArrowRight } from '@element-plus/icons-vue'
import { communityApi } from '../services/api'
import type { Shop, ShopType } from '../types/api'
import { currentLocation, currentLocationLabel, selectedCampus, setCurrentLocation } from '../services/campus'
import { foodIllustration, isPublicPoi } from '../services/shopSource'
import { cityPlaces } from '../services/cityGuide'

const route = useRoute()
const types = ref<ShopType[]>([])
const shops = ref<Shop[]>([])
const selectedType = ref(0)
const query = ref(String(route.query.q || ''))
const loading = ref(false)
const error = ref('')
const verifiedLeads = [
  { name: '老妈麻辣烫（文化路店）', address: '文化路 113–115 号', source: 'https://www.dianping.com/shop/552814/photos/album/p3' },
  { name: '壹个咖啡馆', address: '文化路 78 号甲 2', source: 'https://shen.fang.anjuke.com/loupan/446916/fujin-0106.html' },
  { name: '满宝馄饨（陆军总院店）', address: '文化路 103 号附近', source: 'https://cn.tripadvisor.com/Restaurant_Review-g297454-d3466307-Reviews-Man_Bao_Wonton_LuJun_ZongYuan-Shenyang_Liaoning.html' },
]
let latestRequest = 0
const center = currentLocation
const locationLabel = currentLocationLabel
watch(currentLocation, () => { if (types.value.length) void load() })
function updateLocation(): void {
  if (!navigator.geolocation) { locationLabel.value = '当前浏览器不支持定位'; return }
  navigator.geolocation.getCurrentPosition((position) => {
    setCurrentLocation(position.coords.longitude, position.coords.latitude)
  }, () => { locationLabel.value = '定位失败，请检查浏览器定位权限' }, { timeout: 10000 })
}
const currentLabel = computed(() => types.value.find((type) => type.id === selectedType.value)?.name || '全部生活')
function imageUrl(path: string): string {
  const first = path?.split(',')[0]
  if (!first) return '/city-guide.svg'
  if (/^https?:\/\//.test(first)) return first
  if (first.startsWith('/imgs/')) return first
  return `/imgs${first.startsWith('/') ? '' : '/'}${first}`
}

async function load(): Promise<void> {
  const requestId = ++latestRequest
  loading.value = true; error.value = ''
  try {
    let result: Shop[]
    if (query.value.trim()) {
      result = await communityApi.searchShops(query.value.trim())
    } else if (selectedType.value === 0) {
      const pages = await Promise.all(types.value.flatMap((type) => (type.id === 1 ? [1, 2, 3, 4] : [1]).map((page) => communityApi.nearbyShops(type.id, page, center.value.x, center.value.y))))
      result = pages.flat().sort((left, right) => (left.distance ?? Infinity) - (right.distance ?? Infinity))
    } else {
      const pageNumbers = selectedType.value === 1 ? [1, 2, 3, 4] : [1]
      const pages = await Promise.all(pageNumbers.map((page) => communityApi.nearbyShops(selectedType.value, page, center.value.x, center.value.y)))
      result = pages.flat()
    }
    if (requestId === latestRequest) shops.value = result.filter(isPublicPoi)
  } catch {
    if (requestId === latestRequest) { shops.value = []; error.value = '附近生活数据暂时不可用，请确认后端服务已启动。' }
  } finally { if (requestId === latestRequest) loading.value = false }
}

onMounted(async () => {
  try {
    types.value = await communityApi.shopTypes()
    const typeId = Number(route.query.typeId)
    selectedType.value = Number.isFinite(typeId) ? typeId : types.value[0]?.id || 0
    await load()
  } catch { error.value = '无法读取商户分类，请稍后重试。' }
})
watch(() => route.query.q, (value) => { query.value = String(value || ''); if (types.value.length) load() })
watch(() => route.query.typeId, (value) => {
  const id = Number(value)
  selectedType.value = Number.isSafeInteger(id) && id > 0 ? id : 0
  query.value = ''
  if (types.value.length) void load()
})
</script>

<template>
  <div class="nearby-view">
    <div class="nearby-heading"><div><div class="section-kicker">CAMPUS LIFE, AROUND YOU</div><h1 class="section-title">在沈阳药科大学附近</h1><p>从熟悉的校园出发，找到下一处想去的地方。</p></div><button class="location-pill" @click="updateLocation"><el-icon><Location /></el-icon> {{ locationLabel }} · 更新位置</button></div>
    <section class="city-guide" aria-label="沈阳城市指南"><div class="city-heading"><div><span class="section-kicker">CITY GUIDE</span><h2>发现沈阳的七个去处</h2></div><span>城市指南 · 不代表校园步行距离</span></div><div class="city-cards"><RouterLink v-for="place in cityPlaces" :key="place.id" class="city-card" :to="`/city/${place.id}`"><img :src="place.image" :alt="place.image === '/city-guide.svg' ? '沈阳城市指南插画' : place.name"><div><strong>{{place.name}}</strong><span>{{place.category}} · 查看详情 →</span></div></RouterLink></div><p class="photo-credit">图片：<a href="https://commons.wikimedia.org/wiki/File:Mukden_Palace_drone_view_5.jpg" target="_blank" rel="noopener noreferrer">故宫</a>、<a href="https://commons.wikimedia.org/wiki/File:Zhongjie_drone_view_3.jpg" target="_blank" rel="noopener noreferrer">中街</a> © Techyan，<a href="https://creativecommons.org/licenses/by-sa/4.0/" target="_blank" rel="noopener noreferrer">CC BY-SA 4.0</a>；<a href="https://commons.wikimedia.org/?curid=154244625" target="_blank" rel="noopener noreferrer">张学良旧居</a>：ScareCriterion12；<a href="https://commons.wikimedia.org/?curid=155034959" target="_blank" rel="noopener noreferrer">青年公园</a>：E2568，均为 <a href="https://creativecommons.org/publicdomain/zero/1.0/" target="_blank" rel="noopener noreferrer">CC0</a>。其余为城市插画。</p></section>
    <section v-if="selectedCampus.id === 'shenyang'" class="local-leads"><strong>校本部周边线索</strong><p>依据公开页面整理，营业状态和地址可能变化，请出行前核实。</p><a v-for="lead in verifiedLeads" :key="lead.name" :href="lead.source" target="_blank" rel="noopener noreferrer">{{lead.name}} · {{lead.address}} ↗</a></section>
    <div class="search-row"><el-input v-model="query" size="large" placeholder="搜咖啡、食堂、书店…" @keyup.enter="load"><template #prefix><el-icon><Search /></el-icon></template></el-input><el-button type="primary" size="large" @click="load">搜索</el-button></div>
    <div class="type-tabs"><button :class="{active:selectedType===0}" @click="selectedType=0;query='';load()">全部</button><button v-for="type in types" :key="type.id" :class="{active:selectedType===type.id}" @click="selectedType=type.id;query='';load()">{{type.name}}</button></div>
    <div class="result-heading"><h2>{{ currentLabel }} <small>公开地图资料，营业状态请事先核实</small></h2><span>位置为近似值</span></div>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" show-icon />
    <div v-else-if="loading" class="shop-grid"><el-skeleton v-for="i in 4" :key="i" animated><template #template><el-skeleton-item variant="image" style="height:200px"/><el-skeleton-item variant="h3" style="margin-top:12px"/></template></el-skeleton></div>
    <div v-else class="shop-grid"><RouterLink v-for="shop in shops" :key="shop.id" :to="`/shop/${shop.id}`" class="shop-card"><div class="shop-image"><img :src="shop.images ? imageUrl(shop.images) : foodIllustration(shop).image" :alt="shop.images ? shop.name : '示意图'" @error="($event.target as HTMLImageElement).src='/city-guide.svg'"><span v-if="!shop.images" class="distance">{{ shop.typeId === 1 ? '菜品示意' : '城市插画' }} · 非本店实拍</span></div><div class="shop-copy"><div class="shop-name-row"><h3>{{shop.name}}</h3></div><p>{{shop.address}}</p><div class="shop-foot"><span>{{ shop.distance != null ? `直线约 ${Math.round(shop.distance)} 米` : '公开地图资料' }}</span><span>查看详情 →</span></div></div></RouterLink><el-empty v-if="!shops.length" description="暂时没有找到合适的地点" /></div>
    <RouterLink class="assistant-link" to="/assistant">不知道去哪？让 AI 按你的预算和时间推荐 <el-icon><ArrowRight /></el-icon></RouterLink>
  </div>
</template>

<style scoped>
.city-guide{margin:0 0 32px}.city-heading{display:flex;align-items:end;justify-content:space-between;gap:15px;margin-bottom:14px}.city-heading h2{font-size:20px;margin:7px 0 0}.city-heading>span{font-size:11px;color:#929c8f}.city-cards{display:grid;grid-auto-flow:column;grid-auto-columns:minmax(155px,1fr);gap:13px;overflow-x:auto;padding-bottom:8px;scroll-snap-type:x proximity}.city-card{min-width:0;background:#fbfaf7;scroll-snap-align:start}.city-card img{display:block;width:100%;height:120px;object-fit:cover;background:#e9ece3;transition:opacity .2s}.city-card:hover img{opacity:.84}.city-card div{display:flex;flex-direction:column;gap:6px;padding:11px 12px}.city-card strong{font-size:13px;color:#3c5141;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.city-card span{font-size:10px;color:#95a093}.photo-credit{font-size:10px;color:#a0aa9c;line-height:1.7;margin:9px 0 0}.photo-credit a{text-decoration:underline;text-underline-offset:2px}@media(max-width:700px){.city-guide{margin-bottom:25px}.city-heading{align-items:start;flex-direction:column;gap:5px}.city-heading h2{font-size:18px}.city-heading>span{font-size:10px}.city-cards{grid-auto-columns:142px;margin-right:-16px}.city-card img{height:96px}}
.local-leads{background:#edf0e8;padding:18px 20px;margin:0 0 20px;color:#36543d}.local-leads p{font-size:12px;color:#717b70}.local-leads a{display:inline-block;margin:5px 18px 5px 0;font-size:13px;text-decoration:underline;text-underline-offset:3px}
.nearby-heading{display:flex;justify-content:space-between;align-items:end;margin-bottom:25px}.nearby-heading p{margin:9px 0 0;color:#85877f;font-size:14px}.location-pill{display:flex;align-items:center;gap:7px;font-size:12px;background:#e9ece3;color:#486451;padding:10px 13px}.search-row{display:flex;gap:10px;max-width:700px}.search-row .el-input{flex:1}.search-row .el-input__wrapper{box-shadow:0 0 0 1px #e3e1d9 inset}.search-row .el-button{--el-color-primary:#264b3c}.type-tabs{display:flex;gap:10px;overflow:auto;padding:24px 0 20px;border-bottom:1px solid var(--line)}.type-tabs button{white-space:nowrap;padding:8px 14px;border:1px solid transparent;background:transparent;color:#757970;cursor:pointer}.type-tabs button.active{border-color:#264b3c;background:#264b3c;color:#fff}.result-heading{display:flex;justify-content:space-between;align-items:center;margin:25px 0 18px}.result-heading h2{font-size:19px;margin:0;font-weight:600}.result-heading small{font-size:12px;color:#85877f;font-weight:400;margin-left:8px}.result-heading>span{font-size:12px;color:#85877f}.shop-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:20px}.shop-card{background:#fbfaf7}.shop-image{height:220px;position:relative;overflow:hidden;background:#e6e5de}.shop-image img{width:100%;height:100%;object-fit:cover}.distance{position:absolute;left:12px;bottom:12px;background:rgba(251,250,247,.92);padding:6px 9px;font-size:11px;color:#264b3c}.shop-copy{padding:15px 17px}.shop-name-row{display:flex;justify-content:space-between;align-items:center}.shop-name-row h3{margin:0;font-size:16px;font-weight:600}.score{font-size:12px;color:#a96d35}.shop-copy p{margin:8px 0;color:#85877f;font-size:12px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.shop-foot{display:flex;justify-content:space-between;color:#687068;font-size:12px;margin-top:13px}.assistant-link{display:flex;justify-content:center;align-items:center;gap:8px;color:#486451;padding:24px;font-size:13px}@media(max-width:700px){.nearby-heading{align-items:flex-start;gap:15px;flex-direction:column}.location-pill{padding:8px 10px}.shop-grid{grid-template-columns:1fr}.shop-image{height:210px}.search-row .el-button{padding:0 15px}}

.nearby-heading h1{color:#242825;letter-spacing:-.025em}.location-pill{border:1px solid #e3e9e4;border-radius:24px;background:#f0f5f1;color:#315c4b}.city-card{background:#fff;border:1px solid #e9ebe8;border-radius:12px;overflow:hidden}.city-card strong{color:#252c27}.city-card span,.city-heading>span{color:#737b75}.city-card img{height:120px}.local-leads{background:#edf4ef;border-radius:12px}.type-tabs{gap:8px}.type-tabs button{border-radius:22px;color:#626b64}.type-tabs button.active{background:#315c4b;border-color:#315c4b}.search-row .el-button{--el-color-primary:#315c4b}.shop-grid{gap:22px}.shop-card{background:#fff;border:1px solid #ebedea;border-radius:14px;overflow:hidden;transition:box-shadow .18s,transform .18s}.shop-card:hover{box-shadow:0 8px 24px #253e3012;transform:translateY(-2px)}.shop-image{border-radius:12px 12px 0 0}.shop-copy{padding:17px 18px 19px}.shop-name-row h3{color:#252c27}.shop-copy p,.shop-foot{color:#737b75}.distance{border-radius:6px;color:#315c4b}.assistant-link{color:#315c4b}@media(prefers-reduced-motion:reduce){.shop-card{transition:none}}
</style>
