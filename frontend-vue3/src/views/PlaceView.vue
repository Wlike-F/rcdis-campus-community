<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { communityApi } from '../services/api'
import type { MapPlace } from '../services/api'
const route = useRoute()
const router = useRouter()
const place = ref<MapPlace>()
const error = ref('')
const mapUrl = computed(() => `https://uri.amap.com/marker?position=${encodeURIComponent(place.value?.location || '')}&name=${encodeURIComponent(place.value?.name || '')}&src=rcdis`)
async function load(): Promise<void> {
  place.value = undefined; error.value = ''
  const id = String(route.params.id || '')
  if (!/^[A-Za-z0-9]{1,32}$/.test(id)) { error.value = '地点编号无效。'; return }
  try {
    const result = await communityApi.mapPlace(id)
    if (String(route.params.id) !== id) return
    if (result.shopId) { await router.replace(`/shop/${result.shopId}`); return }
    place.value = result
  } catch (exception) { error.value = exception instanceof Error ? exception.message : '地点信息加载失败，请重试。' }
}
onMounted(load)
watch(() => route.params.id, () => { void load() })
</script>
<template><div class="place-detail"><RouterLink class="back" to="/nearby">← 返回附近</RouterLink><div v-if="error" class="card"><p>{{error}}</p><button @click="load">重新加载</button></div><template v-else-if="place"><div class="card"><span class="tag">高德地点</span><h1>{{place.name}}</h1><p class="sub">地点信息由高德地图提供</p><div class="rule"></div><h2>到店信息</h2><p class="address">{{place.address}}</p><p class="note">位置、营业状态等可能发生变化，请出行前核实。</p><a v-if="place.location" class="primary" :href="mapUrl" target="_blank" rel="noopener noreferrer">在高德地图查看 ↗</a></div><div class="community"><strong>社区内容</strong><p>这个地点暂未关联社区商户，暂无可展示的社区帖子和优惠信息。</p><RouterLink to="/nearby">继续发现附近地点 →</RouterLink></div></template><div v-else class="card">正在加载地点信息…</div></div></template>
<style scoped>.place-detail{max-width:820px;margin:auto;color:#29382e}.back{display:inline-block;margin-bottom:20px;color:#587661;font-size:13px}.card,.community{background:#fff;border:1px solid #e7ece7;border-radius:14px;padding:32px}.tag{display:inline-block;background:#eef2ee;color:#66766b;border-radius:6px;padding:6px 9px;font-size:12px}h1{font-size:30px;margin:18px 0 8px;letter-spacing:-.03em}.sub,.note,.community p{color:#7f8f83;font-size:13px;line-height:1.8}.rule{height:1px;background:#edf0ed;margin:26px 0}h2{font-size:18px}.address{font-size:15px;font-weight:600}.primary{display:inline-block;margin-top:12px;padding:11px 15px;border-radius:8px;background:#315c4b;color:#fff;font-size:13px}.community{margin-top:16px;background:#edf5ef}.community strong{font-size:15px}.community a{font-size:12px;color:#315c4b}.card button{border:0;background:#315c4b;color:#fff;border-radius:8px;padding:9px 13px}@media(max-width:700px){.card,.community{padding:23px 20px}h1{font-size:24px}}</style>
