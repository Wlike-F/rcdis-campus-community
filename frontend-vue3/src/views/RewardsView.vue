<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowRight, Lock } from '@element-plus/icons-vue'
import { communityApi } from '../services/api'
import type { PointsSummary, Redemption, Reward } from '../types/api'

const rewards = ref<Reward[]>([])
const redemptions = ref<Redemption[]>([])
const points = ref<PointsSummary>()
const loading = ref(true)
const error = ref('')
const loggedIn = ref(Boolean(sessionStorage.getItem('token')))
const selected = ref<Reward>()
const confirming = ref(false)
const applying = ref('')
const pending = ref<{ rewardId: number; requestId: string }>()
let requestSequence = 0

async function load(): Promise<void> {
  const sequence = ++requestSequence
  loggedIn.value = Boolean(sessionStorage.getItem('token'))
  if (!loggedIn.value) { points.value = undefined; rewards.value = []; redemptions.value = []; loading.value = false; error.value = ''; return }
  loading.value = true; error.value = ''
  try {
    const [summary, catalog, history] = await Promise.all([communityApi.points(), communityApi.rewards(), communityApi.redemptions()])
    if (sequence !== requestSequence) return
    points.value = summary; rewards.value = catalog; redemptions.value = history
  } catch (exception) {
    if (sequence === requestSequence) error.value = exception instanceof Error ? exception.message : '积分商城暂时无法加载'
  } finally { if (sequence === requestSequence) loading.value = false }
}
function openLogin(): void { window.dispatchEvent(new Event('rcdis:login')) }
function redemptionDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '时间不可用'
  return new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(date)
}
function selectReward(reward: Reward): void {
  selected.value = reward
  if (pending.value?.rewardId !== reward.id) pending.value = { rewardId: reward.id, requestId: crypto.randomUUID() }
}
async function redeem(): Promise<void> {
  if (!selected.value || !pending.value || confirming.value) return
  confirming.value = true
  try {
    await communityApi.redeem(selected.value.id, pending.value.requestId)
    selected.value = undefined; pending.value = undefined
    ElMessage.success('兑换成功，装扮已放入我的兑换')
    await load()
  } catch (exception) { ElMessage.error(exception instanceof Error ? exception.message : '兑换失败，请稍后重试') }
  finally { confirming.value = false }
}
async function useTheme(code: string): Promise<void> {
  if (applying.value) return
  applying.value = code
  try { await communityApi.useTheme(code); ElMessage.success('主页主题已更新'); await load() }
  catch (exception) { ElMessage.error(exception instanceof Error ? exception.message : '主题切换失败，请稍后重试') }
  finally { applying.value = '' }
}
onMounted(() => { void load(); window.addEventListener('rcdis:auth', load) })
onUnmounted(() => { requestSequence++; window.removeEventListener('rcdis:auth', load) })
</script>

<template>
  <main class="rewards-page">
    <RouterLink class="back" to="/profile">← 返回个人主页</RouterLink>
    <div v-if="!loggedIn" class="state"><el-icon><Lock /></el-icon><h1>登录后探索积分商城</h1><p>积累校园生活里的小小心意，换一份喜欢的装扮。</p><el-button type="primary" @click="openLogin">登录 / 注册</el-button></div>
    <div v-else-if="error" class="state"><h1>暂时无法加载商城</h1><p>{{error}}</p><el-button @click="load">重新加载</el-button></div>
    <div v-else-if="loading && !points" class="state"><el-skeleton animated :rows="6" /></div>
    <template v-else-if="points">
      <section class="market-hero"><div><span class="eyebrow">RCDIS POINTS MARKET</span><h1>把日常，<br>换成喜欢的模样。</h1><p>每一次签到和分享，都能为你的校园生活添一点色彩。</p><a href="#market-items">探索校园装扮 <el-icon><ArrowRight /></el-icon></a></div><div class="points-ticket"><span>我的可用积分</span><strong>{{points.points}}</strong><small>成长值 {{points.growth}} · 兑换不影响等级</small><RouterLink to="/profile">去完成每日任务 →</RouterLink><div class="ticket-star">✳</div></div></section>
      <section id="market-items" class="market-section"><div class="section-head"><div><span class="eyebrow">PICK A LITTLE JOY</span><h2>校园装扮</h2><p>一份主题，装点你的个人主页。</p></div><span class="section-note">数字权益 · 每款限兑一次</span></div>
        <div v-if="!rewards.length" class="empty">暂时没有可兑换的装扮。</div><div v-else class="reward-grid"><article v-for="reward in rewards" :key="reward.id" class="reward-card"><div :class="['reward-art', reward.code]"><span class="art-label">CAMPUS COLLECTION</span><div class="art-orbit"></div><div class="art-symbol">{{reward.code === 'ginkgo' ? '✳' : reward.code === 'spring' ? '✿' : '✦'}}</div><div class="art-caption"><span>RCDIS</span><strong>{{reward.name}}</strong></div><span class="art-no">{{reward.code === 'ginkgo' ? '01' : reward.code === 'spring' ? '02' : '03'}}</span></div><div class="reward-copy"><span class="reward-type">主页主题</span><h3>{{reward.name}}</h3><p>{{reward.description}}</p><div class="reward-bottom"><div><strong>{{reward.price}}</strong><span> 积分</span><small>剩余 {{reward.stock}} 份</small></div><button v-if="reward.owned" class="owned" @click="useTheme(reward.code)" :disabled="Boolean(applying) || points.theme === reward.code">{{points.theme === reward.code ? '使用中' : applying === reward.code ? '切换中' : '使用主题'}}</button><button v-else :disabled="reward.stock === 0 || points.points < reward.price" @click="selectReward(reward)">{{reward.stock === 0 ? '已兑完' : points.points < reward.price ? '积分不足' : '兑换'}}</button></div></div></article></div>
      </section>
      <section class="history-section"><div class="section-head"><div><span class="eyebrow">YOUR COLLECTION</span><h2>我的兑换</h2><p>收藏的装扮会一直留在这里。</p></div><button v-if="points.theme !== 'forest'" class="reset-theme" :disabled="Boolean(applying)" @click="useTheme('forest')">恢复默认主题</button></div><div v-if="!redemptions.length" class="empty">还没有兑换记录，攒够积分后挑选喜欢的主题吧。</div><div v-else class="history-list"><div v-for="item in redemptions" :key="item.id" class="history-row"><div :class="['history-symbol',item.code]">✳</div><div class="history-copy"><strong>{{item.name}}</strong><span>{{redemptionDate(item.createdAt)}} · 消耗 {{item.cost}} 积分</span></div><button :disabled="Boolean(applying) || points.theme === item.code" @click="useTheme(item.code)">{{points.theme === item.code ? '使用中' : '使用主题'}}</button></div></div></section>
      <el-dialog :model-value="Boolean(selected)" title="确认兑换" width="min(440px, 92vw)" :close-on-click-modal="!confirming" :show-close="!confirming" @update:model-value="selected = undefined"><template v-if="selected"><div class="confirm-copy"><span>主页主题</span><h3>{{selected.name}}</h3><p>兑换将消耗 <strong>{{selected.price}} 积分</strong>，当前可用 {{points.points}} 积分。</p><small>兑换后可在“我的兑换”中切换使用，每款装扮限兑一次。</small></div></template><template #footer><el-button :disabled="confirming" @click="selected = undefined">再想想</el-button><el-button type="primary" :loading="confirming" @click="redeem">确认兑换</el-button></template></el-dialog>
    </template>
  </main>
</template>

<style scoped>
.rewards-page{max-width:1120px;margin:auto;color:#293d30}.back{display:inline-block;color:#6d846f;font-size:13px;margin:0 0 20px}.state{background:#fff;padding:70px 25px;text-align:center;color:#859487}.state h1{font-size:23px;color:#36513b}.state p{font-size:13px;line-height:1.8}.state .el-icon{font-size:26px}.state .el-button,.confirm-copy~.el-button{--el-color-primary:#315a43}.market-hero{display:grid;grid-template-columns:1fr .7fr;align-items:center;gap:40px;background:#e7eee3;padding:45px 50px}.eyebrow{color:#8b9e8a;font-size:10px;letter-spacing:.13em}.market-hero h1{font-size:clamp(34px,4vw,48px);line-height:1.28;font-weight:600;letter-spacing:-.04em;margin:18px 0}.market-hero p,.section-head p{font-size:13px;color:#829181;line-height:1.8}.market-hero a{display:inline-flex;align-items:center;gap:5px;margin-top:14px;color:#42664a;font-size:12px}.points-ticket{position:relative;overflow:hidden;background:#305542;color:#fff;padding:27px;min-height:175px;display:flex;flex-direction:column;align-items:flex-start;gap:8px}.points-ticket>span{font-size:12px;color:#c0d2be}.points-ticket strong{font-size:45px;line-height:1.2}.points-ticket small{font-size:11px;color:#b7cdb6}.points-ticket a{color:#e1efdb;margin-top:14px}.ticket-star{position:absolute;right:25px;top:22px;font-size:75px;color:#aac5a433}.market-section,.history-section{padding-top:52px}.section-head{display:flex;justify-content:space-between;align-items:end;gap:15px;margin-bottom:20px}.section-head h2{font-size:23px;margin:10px 0 5px}.section-head p{margin:0}.section-note{font-size:11px;color:#9da99b;white-space:nowrap}.reward-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:18px}.reward-card{background:#fff;min-width:0}.reward-art{position:relative;overflow:hidden;aspect-ratio:1.55;color:#fff;background:#7c6950;padding:20px}.reward-art.ginkgo,.history-symbol.ginkgo{background:#ab8d56}.reward-art.spring,.history-symbol.spring{background:#769a7c}.reward-art.night,.history-symbol.night{background:#405877}.art-label,.art-no{position:absolute;font-size:9px;letter-spacing:.12em;opacity:.7}.art-no{right:20px;top:20px}.art-orbit{position:absolute;width:200px;height:200px;border:1px solid #ffffff42;border-radius:50%;right:-15px;top:-55px;box-shadow:0 0 0 25px #ffffff0d,0 0 0 54px #ffffff0a}.art-symbol{position:absolute;right:22%;top:18%;font-size:80px;color:#fff8e5aa;font-family:Georgia,serif}.art-caption{position:absolute;bottom:20px;left:20px;display:flex;flex-direction:column;gap:4px}.art-caption span{font-size:10px;letter-spacing:.13em}.art-caption strong{font-size:19px;font-weight:500}.reward-copy{padding:20px}.reward-type{font-size:11px;color:#90a38c}.reward-copy h3{font-size:18px;margin:9px 0}.reward-copy p{font-size:12px;line-height:1.8;color:#9aa699;min-height:44px}.reward-bottom{display:flex;align-items:end;justify-content:space-between;gap:8px;margin-top:20px}.reward-bottom strong{font-size:24px;color:#436448}.reward-bottom span,.reward-bottom small{font-size:11px;color:#99a596}.reward-bottom small{display:block;margin-top:5px}.reward-bottom button,.history-row button{border:1px solid #3f694b;background:#3f694b;color:#fff;font-size:12px;padding:9px 14px;cursor:pointer;white-space:nowrap}.reward-bottom button.owned,.history-row button{background:#fff;color:#4f7655}.reward-bottom button:disabled,.history-row button:disabled{border-color:#edf1e9;background:#f3f6f0;color:#aab5a7;cursor:default}.history-section{padding-bottom:20px}.empty{padding:45px;text-align:center;background:#fff;color:#a2aea0;font-size:13px}.history-list{background:#fff;padding:0 22px}.history-row{display:flex;gap:14px;align-items:center;padding:17px 0;border-bottom:1px solid #edf0eb}.history-row:last-child{border:0}.history-symbol{width:45px;height:45px;display:grid;place-items:center;color:#fff;font-size:23px}.history-copy{display:flex;flex-direction:column;gap:6px;flex:1}.history-copy strong{font-size:14px;font-weight:500}.history-copy span{font-size:11px;color:#a0aca0}.reset-theme{border:0;background:none;color:#68876c;font-size:12px;cursor:pointer}.confirm-copy>span{font-size:11px;color:#879c86}.confirm-copy h3{font-size:22px;color:#36563c}.confirm-copy p{color:#6b806d}.confirm-copy p strong{color:#bb8950}.confirm-copy small{line-height:1.8;color:#9aa999}@media(max-width:760px){.market-hero{grid-template-columns:1fr;gap:22px;padding:28px 22px}.market-hero h1{font-size:34px}.points-ticket{min-height:140px;padding:21px}.points-ticket strong{font-size:38px}.market-section,.history-section{padding-top:36px}.reward-grid{grid-template-columns:1fr;gap:13px}.reward-card{display:grid;grid-template-columns:42% 1fr}.reward-art{aspect-ratio:auto;min-height:180px}.art-symbol{font-size:55px;right:15%;top:22%}.art-caption strong{font-size:15px}.reward-copy{padding:17px 14px}.reward-copy p{min-height:0;margin-bottom:0}.reward-bottom{margin-top:12px}.reward-bottom strong{font-size:20px}.reward-bottom button{padding:8px 10px}.section-head h2{font-size:20px}.section-note{font-size:10px}.history-copy span{font-size:10px}}

.back{color:#315c4b}.state,.empty,.history-list{border:1px solid #e7e8e4;border-radius:12px}.market-hero{background:#edf4ef;border-radius:16px}.market-hero h1{color:#242825}.market-hero p,.section-head p{color:#6f7c71}.points-ticket{background:#315c4b;border-radius:12px}.reward-card{border:1px solid #e7e8e4;border-radius:12px;overflow:hidden}.reward-copy h3{color:#25342a}.reward-copy p{color:#717e73}.reward-bottom strong{color:#315c4b}.reward-bottom button,.history-row button{border-radius:8px;background:#315c4b;border-color:#315c4b}.reward-bottom button.owned,.history-row button{color:#315c4b;background:#fff}.history-symbol{border-radius:9px}.state .el-button,.confirm-copy~.el-button{--el-color-primary:#315c4b}
</style>
