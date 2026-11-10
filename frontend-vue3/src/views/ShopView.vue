<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, ArrowRight, Location, Link, ChatDotRound, Ticket } from '@element-plus/icons-vue'
import { communityApi } from '../services/api'
import type { MapShopLink } from '../services/api'
import type { Shop, ShopType, Voucher } from '../types/api'
import { foodIllustration, isPublicPoi, shopSource } from '../services/shopSource'

const route = useRoute()
const shop = ref<Shop>()
const types = ref<ShopType[]>([])
const vouchers = ref<Voucher[]>([])
const mapLink = ref<MapShopLink | null>(null)
const posts = ref<{ id: number; title: string; createTime: string }[]>([])
const postsError = ref('')
const error = ref('')
const voucherError = ref('')
const voucherLoading = ref(false)
const loggedIn = ref(Boolean(sessionStorage.getItem('token')))
const claiming = ref<number | null>(null)
const claimed = ref<Record<number, boolean>>({})
const claimMessage = ref('')
function refreshAuth(): void { loggedIn.value = Boolean(sessionStorage.getItem('token')); if (shop.value) void loadVouchers(shop.value.id) }
async function claimVoucher(voucherId: number): Promise<void> {
  if (!loggedIn.value) { window.dispatchEvent(new Event('rcdis:login')); return }
  if (claiming.value !== null) return
  claiming.value = voucherId; claimMessage.value = ''
  try {
    await communityApi.claimVoucher(voucherId)
    for (let attempt = 0; attempt < 6; attempt++) {
      if (await communityApi.voucherClaimed(voucherId)) {
        claimed.value = { ...claimed.value, [voucherId]: true }
        claimMessage.value = '领取成功，每人限领一次。'
        return
      }
      await new Promise<void>((resolve) => window.setTimeout(resolve, 1000))
    }
    claimMessage.value = '领取请求已提交，订单正在处理，请稍后刷新查看结果。'
  } catch (exception) { claimMessage.value = exception instanceof Error ? exception.message : '领取失败，请稍后重试。' }
  finally { claiming.value = null }
}
const imageFailed = ref(false)
let requestSequence = 0
const category = computed(() => types.value.find((type) => type.id === shop.value?.typeId)?.name || '校园周边')
const publicPoi = computed(() => Boolean(shop.value && isPublicPoi(shop.value)))
const mapUrl = computed(() => mapLink.value ? `https://uri.amap.com/marker?position=${mapLink.value.longitude},${mapLink.value.latitude}&name=${encodeURIComponent(mapLink.value.name)}&src=rcdis` : `https://map.baidu.com/search/${encodeURIComponent(`沈阳 ${shop.value?.address || ''} ${shop.value?.name || ''}`)}`)
const cover = computed(() => {
  if (!shop.value) return ''
  if (imageFailed.value) return '/city-guide.svg'
  const value = shop.value.images?.split(',')[0]
  if (!value) return foodIllustration(shop.value).image
  return value.startsWith('http') || value.startsWith('/imgs/') ? value : `/imgs/${value.replace(/^\//, '')}`
})
const coverLabel = computed(() => imageFailed.value ? '城市插画 · 图片暂不可用' : shop.value?.images ? '商户图片 · 来源待核实' : shop.value?.typeId === 1 ? '菜品示意 · 非本店实拍' : '城市插画 · 非本店实拍')
async function loadVouchers(id: number): Promise<void> {
  voucherLoading.value = true; voucherError.value = ''
  try {
    const result = await communityApi.vouchers(id)
    if (shop.value?.id !== id) return
    vouchers.value = result
    claimed.value = {}
    if (loggedIn.value) {
      const statuses = await Promise.all(result.filter((item) => item.type === 1).map(async (item) => ({ id: item.id, claimed: await communityApi.voucherClaimed(item.id) })))
      if (shop.value?.id === id) claimed.value = Object.fromEntries(statuses.map((item) => [item.id, item.claimed]))
    }
  }
  catch { voucherError.value = '优惠信息加载失败，请稍后重试。' }
  finally { voucherLoading.value = false }
}
async function load(): Promise<void> {
  const sequence = ++requestSequence
  error.value = ''; shop.value = undefined; vouchers.value = []; imageFailed.value = false
  const id = Number(route.params.id)
  if (!Number.isSafeInteger(id) || id <= 0) { error.value = '商户编号无效。'; return }
  try {
    const result = await communityApi.shop(id)
    if (sequence !== requestSequence) return
    if (!result) { error.value = '商户不存在。'; return }
    shop.value = result
    void loadVouchers(id)
    mapLink.value = null
    void communityApi.mapShop(id).then((result) => { if (sequence === requestSequence) mapLink.value = result }).catch(() => { if (sequence === requestSequence) mapLink.value = null })
    posts.value = []; postsError.value = ''
    try { const result = await communityApi.shopPosts(id); if (sequence === requestSequence) posts.value = result }
    catch { if (sequence === requestSequence) postsError.value = '社区帖子加载失败，请稍后重试。' }
  } catch { if (sequence === requestSequence) error.value = '无法加载商户详情，请稍后重试。' }
}
onMounted(() => { window.addEventListener('rcdis:auth', refreshAuth); void load(); void communityApi.shopTypes().then((result) => { types.value = result }).catch(() => { types.value = [] }) })
onUnmounted(() => window.removeEventListener('rcdis:auth', refreshAuth))
watch(() => route.params.id, () => { claimMessage.value = ''; void load() })
</script>

<template>
  <div class="detail">
    <RouterLink class="back" to="/nearby"><el-icon><ArrowLeft /></el-icon> 返回附近</RouterLink>
    <div v-if="error" class="state"><el-alert :title="error" type="error" :closable="false"/><el-button @click="load">重试</el-button></div>
    <template v-else-if="shop">
      <div class="page-heading"><div><div class="heading-meta"><span>{{category}}</span><span>沈阳 · 校园周边</span></div><h1>{{shop.name}}</h1><p>{{publicPoi ? '从校园出发，发现身边的生活' : '校园周边商户信息'}}</p></div><a v-if="publicPoi" class="source-top" :href="shopSource(shop)" target="_blank" rel="noopener noreferrer">查看资料来源 <el-icon><Link /></el-icon></a></div>
      <div class="hero"><img :src="cover" :alt="coverLabel" @error="imageFailed = true"><span class="image-label">{{coverLabel}}</span><a v-if="!shop.images && !imageFailed && foodIllustration(shop).source" class="image-credit" :href="foodIllustration(shop).source" target="_blank" rel="noopener noreferrer">图片：{{foodIllustration(shop).credit}}</a></div>
      <div class="layout"><main class="content">
        <section class="info section"><div class="section-heading"><div><span class="kicker">LOCATION</span><h2>到店信息</h2></div><el-icon><Location /></el-icon></div><div class="address-row"><span class="address-icon"><el-icon><Location /></el-icon></span><div><strong>{{publicPoi ? shop.address : '地址暂未核实'}}</strong><p>{{publicPoi ? '沈阳 · 实际位置以地图及商家信息为准' : '演示商户，暂不提供到店指引'}}</p></div></div><div v-if="publicPoi" class="actions"><a class="primary-action" :href="mapUrl" target="_blank" rel="noopener noreferrer">{{mapLink ? '在高德地图查看' : '在地图中搜索'}} <el-icon><ArrowRight /></el-icon></a><a class="plain-action" :href="shopSource(shop)" target="_blank" rel="noopener noreferrer">查看地点来源</a></div><p class="notice">{{publicPoi ? '信息来自公开地图资料，位置为近似值，营业状态请出行前核实。' : '当前为演示数据，地址、价格及评分尚未核实。'}}</p></section>
        <section class="section offers"><div class="section-heading"><div><span class="kicker">OFFERS</span><h2>优惠信息</h2><p>看看这里有没有可以使用的优惠</p></div><el-icon><Ticket /></el-icon></div><div v-if="voucherLoading" class="empty">正在加载优惠信息…</div><div v-else-if="voucherError" class="empty">{{voucherError}} <button @click="loadVouchers(shop.id)">重试</button></div><div v-else-if="!vouchers.length" class="empty"><span class="empty-icon"><el-icon><Ticket /></el-icon></span><strong>暂无优惠信息</strong><span>附近的好去处，不止优惠这一种惊喜。</span></div><div v-else class="voucher-list"><div v-for="voucher in vouchers" :key="voucher.id" class="voucher"><div><span class="voucher-label">演示优惠</span><h3>{{voucher.title}}</h3><p v-if="voucher.subTitle">{{voucher.subTitle}}</p><p v-if="voucher.rules" class="rules">{{voucher.rules}}</p></div><div class="voucher-price"><small>抵扣金额</small><strong>¥{{(voucher.actualValue / 100).toFixed(2)}}</strong><button v-if="voucher.type === 1" class="claim-button" :disabled="Boolean(claimed[voucher.id]) || claiming !== null" @click="claimVoucher(voucher.id)">{{claimed[voucher.id] ? '已领取' : claiming === voucher.id ? '处理中…' : loggedIn ? '免费领取' : '登录领取'}}</button></div></div><p v-if="claimMessage" class="claim-message" role="status">{{claimMessage}}</p><p class="notice">优惠券为演示数据，仅用于项目功能展示，不代表商户真实优惠。</p></div></section>
        <section class="section"><div class="section-heading"><div><span class="kicker">COMMUNITY</span><h2>同学们的分享</h2><p>与这家商户关联的社区帖子</p></div></div><p v-if="postsError" class="notice" role="alert">{{postsError}}</p><div v-else-if="!posts.length" class="empty">暂无关联帖子</div><div v-else class="shop-posts"><RouterLink v-for="post in posts" :key="post.id" :to="`/blog/${post.id}`">{{post.title}} <span>查看帖子 →</span></RouterLink></div></section>
      </main><aside class="sidebar"><div class="aside-card"><span class="aside-icon"><el-icon><ChatDotRound /></el-icon></span><h2>还想了解更多？</h2><p>问问校园助手，发现更多周边生活灵感。</p><RouterLink to="/assistant">问问 AI 助手 <el-icon><ArrowRight /></el-icon></RouterLink></div><div class="aside-note"><strong>出发前的小提醒</strong><p>商户信息可能发生变化，建议出行前确认地址和营业状态。</p></div></aside></div>
    </template><div v-else class="state"><el-skeleton animated :rows="8" /></div>
  </div>
</template>

<style scoped>
.detail{max-width:1060px;margin:auto;color:#29352d}.back{display:inline-flex;align-items:center;gap:5px;color:#61796a;font-size:13px;margin-bottom:24px}.back:hover{color:#315c4b}.state{background:#fff;border-radius:14px;padding:28px}.state .el-button{margin-top:16px}.page-heading{display:flex;align-items:end;justify-content:space-between;gap:20px;margin-bottom:22px}.heading-meta{display:flex;align-items:center;gap:10px;color:#79877c;font-size:12px}.heading-meta span:first-child{background:#e8f1eb;color:#315c4b;border-radius:6px;padding:5px 9px}.page-heading h1{font-size:32px;letter-spacing:-.035em;line-height:1.25;margin:14px 0 8px;color:#202b23}.page-heading p{color:#869088;margin:0;font-size:13px}.source-top{display:flex;align-items:center;gap:6px;color:#66796c;font-size:12px;white-space:nowrap}.hero{position:relative;height:350px;border-radius:14px;overflow:hidden;background:#e8eee9}.hero img{display:block;width:100%;height:100%;object-fit:cover}.image-label,.image-credit{position:absolute;bottom:14px;color:#fff;background:#202c25b8;border-radius:6px;padding:6px 9px;font-size:11px}.image-label{left:14px}.image-credit{right:14px}.layout{display:grid;grid-template-columns:minmax(0,1fr) 270px;gap:22px;margin-top:22px;align-items:start}.content{display:grid;gap:18px}.section,.aside-card,.aside-note{background:#fff;border:1px solid #e7eae6;border-radius:14px}.section{padding:28px 30px}.section-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:15px}.section-heading>.el-icon{font-size:22px;color:#9bb5a2}.kicker{color:#90a297;font-size:10px;letter-spacing:.1em}.section-heading h2{font-size:20px;margin:7px 0 0;color:#26372b}.section-heading p{font-size:12px;color:#8a958d;margin:8px 0 0}.address-row{display:flex;align-items:flex-start;gap:13px;margin-top:24px}.address-icon,.aside-icon,.empty-icon{display:grid;place-items:center;background:#edf5ef;color:#527b5f;border-radius:10px;flex:none}.address-icon{width:39px;height:39px;font-size:19px}.address-row strong{font-size:15px;font-weight:600;line-height:1.5}.address-row p{font-size:12px;color:#87938a;margin:6px 0 0;line-height:1.6}.actions{display:flex;align-items:center;gap:20px;margin-top:22px}.primary-action{display:inline-flex;align-items:center;gap:8px;color:#fff;background:#315c4b;border-radius:8px;padding:11px 15px;font-size:13px}.plain-action{font-size:12px;color:#557861}.notice{font-size:11px;color:#909b92;line-height:1.7;margin:20px 0 0}.empty{min-height:135px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px;color:#96a099;font-size:12px}.empty strong{color:#5f7063;font-size:13px}.empty-icon{width:38px;height:38px;font-size:19px}.empty button{border:0;background:none;color:#315c4b;cursor:pointer}.voucher-list{margin-top:20px}.voucher{display:flex;justify-content:space-between;gap:15px;border:1px solid #e5ede6;border-radius:10px;padding:18px;background:#f8fbf8}.voucher+.voucher{margin-top:10px}.voucher-label{font-size:10px;color:#69856f}.voucher h3{font-size:15px;margin:8px 0}.voucher p{font-size:12px;color:#7c8a7e;line-height:1.6;margin:6px 0}.rules{white-space:pre-line}.voucher-price{text-align:right;white-space:nowrap}.voucher-price small{display:block;color:#9aa59b;font-size:10px}.voucher-price strong{display:block;color:#bd7955;font-size:21px;margin-top:8px}.sidebar{display:grid;gap:14px}.aside-card{background:#edf5ef;padding:25px}.aside-icon{width:36px;height:36px;background:#dcebe0;font-size:18px}.aside-card h2{font-size:16px;margin:18px 0 8px}.aside-card p,.aside-note p{font-size:12px;color:#7d8d80;line-height:1.8;margin:0}.aside-card a{display:inline-flex;align-items:center;gap:5px;color:#315c4b;font-size:12px;margin-top:18px}.aside-note{padding:21px 23px}.aside-note strong{font-size:13px}.aside-note p{margin-top:9px}@media(max-width:700px){.back{margin-bottom:18px}.page-heading{align-items:start}.page-heading h1{font-size:25px;margin-top:12px}.source-top{font-size:11px;margin-top:5px}.hero{height:235px;border-radius:12px}.image-credit{font-size:9px;max-width:46%;text-align:right}.layout{display:flex;flex-direction:column;gap:14px;margin-top:14px}.content,.sidebar{width:100%}.content{gap:14px}.section{padding:23px 20px}.sidebar{gap:12px}.aside-card{padding:21px}.aside-note{padding:18px 20px}.section-heading h2{font-size:18px}}
.claim-button{display:block;margin:10px 0 0 auto;padding:8px 12px;background:#315c4b;color:#fff;border:0;border-radius:7px;font-size:12px;cursor:pointer}.claim-button:disabled{background:#e5ebe5;color:#89998c;cursor:default}.claim-message{font-size:12px;color:#315c4b;line-height:1.6;margin:14px 0 0}
.shop-posts{margin-top:16px}.shop-posts a{display:flex;justify-content:space-between;gap:12px;padding:14px 0;border-bottom:1px solid #edf1ed;font-size:13px}.shop-posts span{font-size:11px;color:#64836c;white-space:nowrap}
</style>
