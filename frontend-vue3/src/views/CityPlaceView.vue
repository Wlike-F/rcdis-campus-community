<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowRight, Location } from '@element-plus/icons-vue'
import { cityPlaces } from '../services/cityGuide'

const route = useRoute()
const place = computed(() => cityPlaces.find((item) => item.id === route.params.id))
</script>

<template>
  <article v-if="place" class="place-page">
    <RouterLink class="back-link" to="/">← 返回城市指南</RouterLink>
    <div class="place-hero">
      <div class="place-cover"><img :src="place.image" :alt="place.image.endsWith('.svg') ? '沈阳城市插画' : place.name"></div>
      <div class="place-summary"><span class="place-type">{{ place.category }} / 沈阳</span><h1>{{ place.name }}</h1><p class="place-intro">{{ place.introduction }}</p><div class="place-location"><el-icon><Location /></el-icon><span>{{ place.address }}</span></div><a :href="place.source" target="_blank" rel="noopener noreferrer" class="source-link">查看资料来源 <el-icon><ArrowRight /></el-icon></a></div>
    </div>
    <div class="place-content"><section><span class="eyebrow">ABOUT THIS PLACE</span><h2>在这里，发现沈阳</h2><p>{{ place.introduction }}</p><div class="highlights"><div v-for="(item, index) in place.highlights" :key="item"><span>0{{ index + 1 }}</span><strong>{{ item }}</strong></div></div></section><aside><span class="eyebrow">BEFORE YOU GO</span><h2>出行小贴士</h2><p>{{ place.tips }}</p><p>城市指南地点不代表距离学校的步行距离。</p><RouterLink to="/assistant">问问校园助手 →</RouterLink></aside></div>
  </article>
  <div v-else class="not-found"><h1>没有找到这个地点</h1><RouterLink to="/">返回首页</RouterLink></div>
</template>

<style scoped>
.place-page{max-width:1120px;margin:auto}.back-link{display:inline-block;margin:0 0 22px;color:#58715b;font-size:13px}.place-hero{display:grid;grid-template-columns:1.2fr 1fr;background:#e9ece3;min-height:410px}.place-cover{min-height:410px;overflow:hidden}.place-cover img{width:100%;height:100%;object-fit:cover;transition:transform .7s ease}.place-cover:hover img{transform:scale(1.035)}.place-summary{display:flex;flex-direction:column;justify-content:center;align-items:flex-start;padding:48px}.place-type,.eyebrow{font-size:11px;letter-spacing:.12em;color:#728377}.place-summary h1{font-size:clamp(30px,3vw,44px);line-height:1.25;margin:18px 0}.place-intro,.place-content p{line-height:1.9;color:#6d776e;font-size:14px}.place-location{display:flex;gap:8px;align-items:center;margin-top:22px;color:#46634d;font-size:13px}.source-link{display:flex;align-items:center;gap:7px;margin-top:32px;color:#fff;background:#284d3d;padding:12px 17px;font-size:12px}.place-content{display:grid;grid-template-columns:2fr 1fr;gap:70px;padding:56px 0}.place-content h2{font-size:23px;margin:12px 0 20px}.highlights{display:grid;grid-template-columns:repeat(3,1fr);border-top:1px solid #dcded4;margin-top:28px}.highlights div{display:flex;flex-direction:column;gap:12px;padding:20px 12px 0 0}.highlights span{color:#93a290;font-size:11px}.highlights strong{font-size:13px;font-weight:500}.place-content aside{background:#eef0e8;padding:27px}.place-content aside h2{font-size:18px}.place-content aside a{display:inline-block;margin-top:12px;color:#365c45;font-size:13px}.not-found{padding:80px 0;text-align:center}.not-found a{color:#365c45}@media(max-width:700px){.place-hero,.place-content{grid-template-columns:1fr}.place-cover{min-height:240px;height:240px}.place-summary{padding:28px 24px}.place-content{gap:28px;padding:38px 0}.highlights{gap:10px}.highlights strong{font-size:12px}}@media(prefers-reduced-motion:reduce){.place-cover img{transition:none}}

.back-link{color:#315c4b}.place-hero{background:#f0f5f1;border-radius:16px;overflow:hidden}.place-cover img{display:block}.place-type,.eyebrow{color:#738278}.place-summary h1{color:#242825;letter-spacing:-.025em}.place-intro,.place-content p{color:#626f66}.place-location{color:#315c4b}.source-link{border-radius:8px;background:#315c4b}.place-content aside{background:#edf4ef;border-radius:12px}.place-content aside a{color:#315c4b}.highlights{border-color:#e1e8e2}.highlights span{color:#74857a}
</style>
