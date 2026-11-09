<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { ArrowUp, Compass, MagicStick } from '@element-plus/icons-vue'
import type { AiMessage, AiChatRequest } from '../types/api'
import { selectedCampus, currentLocation, currentLocationLabel, setCurrentLocation } from '../services/campus'

const conversationId = ref(`rcdis-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`)
const locationPoint = currentLocation
const locationStatus = currentLocationLabel
const messages = ref<AiMessage[]>([{ role: 'assistant', content: '你好，我是 RCDIS 校园助手。可以帮你找沈阳药科大学附近的店、优惠券和社区内容。你今天想发现什么？' }])
const input = ref('')
const loading = ref(false)
const streamError = ref('')
const needsLogin = ref(false)
const window = globalThis.window
const Event = globalThis.Event
const thread = ref<HTMLElement>()
interface ShopCard { id: number; name: string; distance?: number }
const shopCards = ref<Record<number, ShopCard[]>>({})
const suggestions = ['附近适合自习的咖啡馆', '查一下校园周边的优惠券', '今晚想吃点热乎的，有什么推荐？']
let activeToken = sessionStorage.getItem('token')
let requestController: AbortController | undefined
function resetForAccount(): void {
  const token = sessionStorage.getItem('token')
  if (token === activeToken) return
  activeToken = token
  requestController?.abort()
  conversationId.value = `rcdis-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  messages.value = [{ role: 'assistant', content: '你好，我是 RCDIS 校园助手。告诉我你今天想发现什么。' }]
  shopCards.value = {}
  streamError.value = ''
  needsLogin.value = false
}
onMounted(() => window.addEventListener('rcdis:auth', resetForAccount))
onUnmounted(() => { window.removeEventListener('rcdis:auth', resetForAccount); requestController?.abort() })

function locate(): void {
  if (!navigator.geolocation) { locationStatus.value = '当前浏览器不支持定位'; return }
  navigator.geolocation.getCurrentPosition((position) => {
    setCurrentLocation(position.coords.longitude, position.coords.latitude)
  }, () => { locationStatus.value = `定位失败，继续使用上次位置（${locationStatus.value}）` }, { enableHighAccuracy: false, timeout: 5000, maximumAge: 600000 })
}

function parseFrames(buffer: string): { frames: Array<{ event: string; data: string }>; rest: string } {
  const parts = buffer.split(/\r?\n\r?\n/)
  const rest = parts.pop() || ''
  const frames = parts.map((part) => {
    const lines = part.split(/\r?\n/)
    const event = lines.find((line) => line.startsWith('event:'))?.slice(6).trim() || 'message'
    const data = lines.filter((line) => line.startsWith('data:')).map((line) => line.slice(5).replace(/^ /, '')).join('\n')
    return { event, data }
  })
  return { frames, rest }
}

async function send(text = input.value): Promise<void> {
  const content = text.trim()
  if (!content || loading.value) return
  if (content.length > 4000) { streamError.value = '问题最多支持 4000 个字符，请精简后再发送。'; return }
  messages.value.push({ role: 'user', content }, { role: 'assistant', content: '' })
  input.value = ''; loading.value = true; streamError.value = ''; needsLogin.value = false
  await scrollToBottom()
  const assistantIndex = messages.value.length - 1
  const controller = new AbortController()
  requestController = controller
  try {
    const token = sessionStorage.getItem('token') || ''
    const body: AiChatRequest = { conversationId: conversationId.value, location: locationPoint.value, messages: [{ role: 'user', content }] }
    const response = await fetch(`${import.meta.env.VITE_API_BASE_URL || '/api'}/ai/chat/stream`, { method: 'POST', headers: { 'Content-Type': 'application/json', authorization: token }, body: JSON.stringify(body), signal: controller.signal })
    if (response.status === 401) { needsLogin.value = true; throw new Error('请先登录，再使用校园 AI 助手。') }
    if (response.status === 429) throw new Error('当前请求较多，请稍等片刻再试。')
    if (!response.ok || !response.body) throw new Error(`连接 AI 服务失败（${response.status}），请稍后重试。`)
    if (!(response.headers.get('content-type') || '').includes('text/event-stream')) throw new Error('AI 服务响应格式异常，请稍后重试。')
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let completed = false
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const parsed = parseFrames(buffer); buffer = parsed.rest
      for (const frame of parsed.frames) {
        if (frame.event === 'delta') messages.value[assistantIndex]!.content += frame.data
        if (frame.event === 'tool_start') messages.value[assistantIndex]!.content += `\n正在查询：${toolLabel(frame.data)}…\n`
        if (frame.event === 'tool_result') messages.value[assistantIndex]!.content += `${toolLabel(frame.data)}查询完成，正在整理。\n`
        if (frame.event === 'shops') {
          const parsed: unknown = JSON.parse(frame.data)
          if (Array.isArray(parsed)) {
            const cards = parsed.filter((item): item is ShopCard => typeof item === 'object' && item !== null && Number.isSafeInteger(item.id) && typeof item.name === 'string')
            shopCards.value[assistantIndex] = [...(shopCards.value[assistantIndex] || []), ...cards]
          }
        }
        if (frame.event === 'error') throw new Error(frame.data || 'AI 请求失败')
        if (frame.event === 'done') completed = true
      }
      await scrollToBottom()
    }
    if (!completed) throw new Error('回答中断，请稍后重试。')
  } catch (error) {
    if (controller.signal.aborted) return
    streamError.value = error instanceof Error ? error.message : 'AI 服务暂时不可用'
    if (!input.value) input.value = content
    if (!messages.value[assistantIndex]!.content) messages.value[assistantIndex]!.content = '暂时没有接上服务，请稍后再试。'
  } finally { if (requestController === controller) { requestController = undefined; loading.value = false }; await scrollToBottom() }
}

function toolLabel(name: string): string {
  const labels: Record<string, string> = { query_shop_types: '校园周边分类', query_nearby_shops: '附近地点', query_shop: '地点详情', query_vouchers: '优惠信息', query_hot_blogs: '社区内容', query_follow_feed: '关注动态' }
  return labels[name] || '社区数据'
}

async function scrollToBottom(): Promise<void> {
  await nextTick()
  if (thread.value) thread.value.scrollTop = thread.value.scrollHeight
}

</script>

<template>
  <div class="ai-view">
    <aside class="ai-aside"><div class="section-kicker">RCDIS INTELLIGENCE</div><h1>校园生活，<br><span>问我就好。</span></h1><p>从沈阳药科大学出发，发现周边生活。</p><div class="aside-rule"></div><div class="capability"><el-icon><Compass /></el-icon><div><strong>附近发现</strong><span>按位置找店和生活服务</span></div></div><div class="capability"><el-icon><MagicStick /></el-icon><div><strong>社区信息</strong><span>从商户、优惠与帖子中查找</span></div></div><div class="location-status"><span class="location-dot"></span>{{locationStatus}}<button @click="locate">更新位置</button></div></aside>
    <section class="chat-panel"><header class="chat-header"><div class="assistant-avatar">R</div><div><strong>RCDIS 校园助手</strong><span>沈阳药科大学周边</span></div><el-tag size="small" effect="plain">SenseNova</el-tag></header>
      <div ref="thread" class="chat-thread"><div v-for="(message,index) in messages" :key="index" :class="['chat-message',message.role]"><div v-if="message.role==='assistant'" class="message-avatar">R</div><div class="message-content">{{message.content}}<span v-if="loading && index===messages.length-1" class="cursor"></span><div v-if="shopCards[index]?.length" class="recommendations"><RouterLink v-for="shop in shopCards[index]" :key="shop.id" :to="`/shop/${shop.id}`"><strong>{{shop.name}}</strong><small>公开地图资料 · 营业状态待核实 · 查看详情 →</small></RouterLink></div></div></div><div v-if="!loading && messages.length===1" class="suggestions"><span>可以试试</span><button v-for="item in suggestions" :key="item" @click="send(item)">{{item}} <el-icon><ArrowUp /></el-icon></button></div></div>
      <div v-if="streamError" class="inline-error">{{streamError}} <a v-if="needsLogin" href="#" @click.prevent="window.dispatchEvent(new Event('rcdis:login'))">前往登录</a></div><form class="composer" @submit.prevent="send()"><el-input v-model="input" type="textarea" :autosize="{minRows:1,maxRows:4}" resize="none" placeholder="说说你想找什么…" :disabled="loading" @keydown.enter.exact.prevent="send()"/><el-button circle type="primary" native-type="submit" :disabled="loading || !input.trim()"><el-icon><ArrowUp /></el-icon></el-button></form><div class="chat-footnote">AI 提供的地点和优惠信息以平台实时数据为准</div>
    </section>
  </div>
</template>

<style scoped>
.recommendations{display:grid;gap:8px;margin-top:12px}.recommendations a{display:flex;flex-direction:column;gap:3px;padding:12px;background:#fff;border:1px solid #e5e3db;color:#264b3c}.recommendations small{color:#7b8177}.recommendations a:focus-visible{outline:2px solid #264b3c}
.ai-view{display:grid;grid-template-columns:.72fr 1.28fr;min-height:calc(100vh - 190px);background:#fbfaf7;border:1px solid var(--line)}.ai-aside{padding:42px 34px;background:#e9ece3;display:flex;flex-direction:column}.ai-aside h1{font-size:36px;line-height:1.3;letter-spacing:-.05em;margin:20px 0 10px;font-weight:600}.ai-aside h1 span{color:#56745b}.ai-aside>p{font-size:13px;color:#73796f;line-height:1.8}.aside-rule{height:1px;background:#d3d8cd;margin:25px 0}.capability{display:flex;gap:12px;margin:0 0 22px;color:#49634e}.capability>.el-icon{font-size:19px;margin-top:2px}.capability div{display:flex;flex-direction:column;gap:5px}.capability strong{font-size:13px;font-weight:600;color:#303c32}.capability span{font-size:11px;color:#80857c}.location-status{display:flex;align-items:center;gap:7px;margin-top:auto;padding-top:20px;font-size:11px;color:#66705f}.location-dot{height:7px;width:7px;background:#66845f;border-radius:50%}.location-status button{margin-left:auto;background:none;border:0;text-decoration:underline;color:#66705f;font-size:11px;cursor:pointer}.chat-panel{min-height:580px;display:flex;flex-direction:column}.chat-header{height:72px;border-bottom:1px solid var(--line);display:flex;align-items:center;padding:0 24px;gap:12px}.assistant-avatar,.message-avatar{width:34px;height:34px;border:1px solid #59765d;color:#47634d;display:grid;place-items:center;font-family:Georgia,serif;font-size:20px}.chat-header div:nth-child(2){display:flex;flex-direction:column;gap:4px;flex:1}.chat-header strong{font-size:13px}.chat-header span{font-size:11px;color:#888b82}.chat-thread{flex:1;overflow:auto;padding:24px;max-height:calc(100vh - 390px);min-height:380px}.chat-message{display:flex;gap:10px;align-items:flex-start;margin-bottom:22px}.message-avatar{width:27px;height:27px;font-size:16px;flex:none}.message-content{white-space:pre-wrap;line-height:1.8;font-size:14px;max-width:90%;background:#f0f1ea;padding:12px 15px;color:#303931}.chat-message.user{justify-content:flex-end}.chat-message.user .message-content{background:#264b3c;color:#fff}.cursor{display:inline-block;width:1px;height:15px;background:#536e56;vertical-align:middle;animation:blink 1s step-end infinite}@keyframes blink{50%{opacity:0}}.suggestions{display:flex;flex-direction:column;align-items:flex-start;gap:9px;margin:35px 0 0 38px}.suggestions>span{font-size:11px;color:#898c82;margin-bottom:3px}.suggestions button{border:1px solid #e1e1d9;background:transparent;padding:9px 12px;color:#586258;font-size:12px;cursor:pointer}.suggestions button .el-icon{transform:rotate(45deg);margin-left:10px}.composer{display:flex;align-items:end;gap:12px;padding:13px 16px;margin:0 20px;border:1px solid #deded5;background:#fff}.composer .el-textarea{flex:1}.composer :deep(.el-textarea__inner){box-shadow:none;background:transparent;padding:7px 2px;line-height:1.6}.composer .el-button{--el-color-primary:#264b3c;width:38px;height:38px}.chat-footnote{text-align:center;color:#a2a49c;font-size:10px;padding:11px 0 14px}.inline-error{margin:0 20px 8px;color:#a14e3a;font-size:12px}@media(max-width:760px){.ai-view{grid-template-columns:1fr;border:0;background:transparent}.ai-aside{padding:20px;background:#e9ece3}.ai-aside h1{font-size:27px;margin:10px 0 5px}.ai-aside>p,.aside-rule,.capability{display:none}.location-status{margin-top:10px;padding-top:10px}.chat-panel{margin-top:12px;background:#fbfaf7;min-height:calc(100vh - 260px)}.chat-thread{min-height:330px;max-height:none;padding:17px}.chat-header{padding:0 16px}.composer{margin:0 12px}}

.ai-view{background:#fff;border-color:#e7e8e4;border-radius:14px;overflow:hidden}.ai-aside{background:#edf4ef}.ai-aside h1{color:#242825;font-size:32px}.ai-aside h1 span{color:#315c4b}.capability{color:#315c4b}.capability strong{color:#28342d}.capability span,.ai-aside>p{color:#6f7971}.chat-header{background:#fff}.assistant-avatar,.message-avatar{border:0;border-radius:9px;background:#e5efe9;color:#315c4b}.chat-thread{background:#fff}.message-content{border-radius:4px 14px 14px 14px;background:#f3f5f2;color:#303a32}.chat-message.user .message-content{background:#315c4b;border-radius:14px 4px 14px 14px}.suggestions button{border-radius:9px;background:#fff;border-color:#e0e6e1;color:#45564b}.suggestions button:hover{background:#f2f7f3}.composer{border-color:#dce4de;border-radius:12px;box-shadow:0 3px 14px #203b2b0a}.composer .el-button{--el-color-primary:#315c4b}.recommendations a{border-radius:9px;border-color:#e3e9e4;color:#315c4b}@media(max-width:760px){.ai-view{border-radius:0;background:transparent}.ai-aside{border-radius:12px;background:#edf4ef}.chat-panel{background:#fff;border:1px solid #e7e8e4;border-radius:12px;overflow:hidden}}
</style>
