<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { Compass, ChatDotRound, House, Plus, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { http } from './services/api'
import type { ApiResult } from './types/api'
import { campuses, selectedCampus, selectCampus } from './services/campus'

const route = useRoute()
const router = useRouter()
const query = ref('')
const loginVisible = ref(false)
const email = ref('')
const phone = ref('')
const password = ref('')
const loginMode = ref<'email' | 'password' | 'reset' | 'phone'>('email')
const code = ref('')
const sending = ref(false)
const clock = ref(Date.now())
const cooldownKey = computed(() => loginMode.value === 'phone' ? `rcdis:code:phone:${phone.value.trim()}` : `rcdis:code:email:${email.value.trim().toLowerCase()}`)
const cooldown = computed(() => Math.max(0, Math.ceil((Number(sessionStorage.getItem(cooldownKey.value) || 0) - clock.value) / 1000)))
let countdownTimer: number | undefined
function startCooldown(key: string): void { sessionStorage.setItem(key, String(Date.now() + 60000)); clock.value = Date.now() }

const logging = ref(false)
const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const nickname = ref('')
async function refreshUser(): Promise<void> {
  if (!sessionStorage.getItem('token')) { nickname.value = ''; return }
  try { const response = await http.get<ApiResult<{ nickName: string }>>('/user/me'); nickname.value = response.data.data.nickName }
  catch { nickname.value = '' }
}
onMounted(() => { void refreshUser(); countdownTimer = window.setInterval(() => { clock.value = Date.now() }, 1000) })
onUnmounted(() => { if (countdownTimer !== undefined) window.clearInterval(countdownTimer) })
window.addEventListener('rcdis:auth', () => { void refreshUser() })
async function sendCode(): Promise<void> {
  if (sending.value || cooldown.value > 0) return
  const requestKey = cooldownKey.value
  if (loginMode.value === 'phone') {
    if (!/^1\d{10}$/.test(phone.value)) { ElMessage.error('请输入有效手机号'); return }
    sending.value = true
    try { await http.post('/user/code', null, { params: { phone: phone.value } }); startCooldown(requestKey); ElMessage.success('验证码已生成，请查看后端日志') }
    catch (error) { ElMessage.error(error instanceof Error ? error.message : '验证码发送失败') }
    finally { sending.value = false }
    return
  }
  if (!emailPattern.test(email.value.trim())) { ElMessage.error('请输入有效的邮箱地址'); return }
  sending.value = true
  try { await http.post('/auth/email/code', { email: email.value.trim() }); startCooldown(requestKey); ElMessage.success('验证码已发送，请查收邮件') }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '验证码发送失败') }
  finally { sending.value = false }
}
async function login(): Promise<void> {
  if (loginMode.value === 'phone') {
    if (!/^1\d{10}$/.test(phone.value) || !/^\d{6}$/.test(code.value)) { ElMessage.error('请输入手机号和6位验证码'); return }
    logging.value = true
    try { const response = await http.post<ApiResult<string>>('/user/login', { phone: phone.value, code: code.value }); sessionStorage.setItem('token', response.data.data); code.value = ''; await refreshUser(); window.dispatchEvent(new Event('rcdis:auth')); loginVisible.value = false; ElMessage.success('登录成功') }
    catch (error) { ElMessage.error(error instanceof Error ? error.message : '登录失败') }
    finally { logging.value = false }
    return
  }
  if (!emailPattern.test(email.value.trim())) { ElMessage.error('请输入有效的邮箱地址'); return }
  if (loginMode.value !== 'password' && !/^\d{6}$/.test(code.value)) { ElMessage.error('请输入6位邮箱验证码'); return }
  if (loginMode.value !== 'email' && (password.value.length < 8 || password.value.length > 72)) { ElMessage.error('请输入8至72位密码'); return }
  logging.value = true
  const endpoint = loginMode.value === 'password' ? '/auth/password/login' : loginMode.value === 'reset' ? '/auth/password/reset' : '/auth/email/login'
  try {
    const response = await http.post<ApiResult<string>>(endpoint, { email: email.value.trim(), code: code.value, password: password.value })
    sessionStorage.setItem('token', response.data.data)
    password.value = ''; code.value = ''
    await refreshUser(); window.dispatchEvent(new Event('rcdis:auth')); loginVisible.value = false; ElMessage.success('登录成功')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '登录失败') }
  finally { logging.value = false }
}
function search(): void {
  if (query.value.trim()) void router.push({ path: '/nearby', query: { q: query.value.trim() } })
}
window.addEventListener('rcdis:login', () => { loginVisible.value = true })
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <RouterLink class="wordmark" to="/" aria-label="RCDIS 首页"><span class="wordmark-symbol">R</span><span>RCDIS</span></RouterLink>
      <nav class="desktop-nav" aria-label="主导航">
        <RouterLink to="/">校园社区</RouterLink><RouterLink to="/nearby">附近生活</RouterLink><RouterLink to="/assistant">AI 助手</RouterLink>
      </nav>
      <el-select :model-value="selectedCampus.id" class="campus-select" aria-label="选择校区" @change="selectCampus"><el-option v-for="campus in campuses" :key="campus.id" :label="campus.label" :value="campus.id" /></el-select>
      <div class="topbar-search"><el-input v-model="query" placeholder="搜索店铺、地点和灵感" @keyup.enter="search"><template #prefix><el-icon><Compass /></el-icon></template></el-input></div>
      <RouterLink v-if="nickname" class="login-button" to="/profile">{{nickname}}</RouterLink><el-button v-else class="login-button" round @click="loginVisible = true">登录 / 注册</el-button>
    </header>
    <main class="page-wrap"><RouterView v-slot="{ Component }"><Transition name="page" mode="out-in"><component :is="Component" /></Transition></RouterView></main>
    <el-dialog v-model="loginVisible" title="登录 RCDIS" width="min(420px, 92vw)">
      <div class="auth-tabs"><button type="button" :class="{active: loginMode === 'email'}" @click="loginMode = 'email'">邮箱验证码</button><button type="button" :class="{active: loginMode === 'password'}" @click="loginMode = 'password'">邮箱密码</button><button type="button" :class="{active: loginMode === 'phone'}" @click="loginMode = 'phone'">原手机号</button></div>
      <p class="auth-note">{{loginMode === 'phone' ? '原账号使用手机号验证码登录，验证码请查看后端日志。' : loginMode === 'reset' ? '验证邮箱后设置新密码，未注册邮箱将创建账号。' : loginMode === 'email' ? '首次使用邮箱登录将自动创建账号。' : '使用已设置密码的邮箱登录。'}}</p>
      <el-input v-if="loginMode !== 'phone'" v-model="email" type="email" placeholder="请输入邮箱地址" autocomplete="email" maxlength="190" />
      <el-input v-else v-model="phone" placeholder="请输入手机号" maxlength="11" />
      <div v-if="loginMode !== 'password'" class="auth-field"><el-input v-model="code" placeholder="请输入6位验证码" maxlength="6" /><el-button :loading="sending" :disabled="cooldown > 0" @click="sendCode">{{cooldown > 0 ? `${cooldown}秒后重试` : '获取验证码'}}</el-button></div>
      <div v-if="loginMode === 'password' || loginMode === 'reset'" class="auth-field"><el-input v-model="password" type="password" show-password placeholder="请输入8至72位密码" maxlength="72" /></div>
      <button v-if="loginMode === 'email' || loginMode === 'password'" type="button" class="auth-link" @click="loginMode = 'reset'">设置或重置密码</button><button v-else-if="loginMode === 'reset'" type="button" class="auth-link" @click="loginMode = 'password'">返回密码登录</button>
      <template #footer><el-button type="primary" :loading="logging" @click="login">{{loginMode === 'reset' ? '设置密码并登录' : '登录 / 注册'}}</el-button></template>
    </el-dialog>
    <nav class="mobile-nav" aria-label="底部导航">
      <RouterLink to="/" :class="{ selected: route.path === '/' }"><el-icon><House /></el-icon><span>社区</span></RouterLink>
      <RouterLink to="/nearby" :class="{ selected: route.path === '/nearby' }"><el-icon><Compass /></el-icon><span>附近</span></RouterLink>
      <RouterLink class="mobile-create" to="/publish" aria-label="发布帖子"><el-icon><Plus /></el-icon></RouterLink>
      <RouterLink to="/assistant" :class="{ selected: route.path === '/assistant' }"><el-icon><ChatDotRound /></el-icon><span>AI 助手</span></RouterLink>
      <RouterLink to="/profile"><el-icon><User /></el-icon><span>我的</span></RouterLink>
    </nav>
  </div>
</template>

<style>
.auth-tabs{display:flex;gap:20px;border-bottom:1px solid #e7e8e4}.auth-tabs button{border:0;border-bottom:2px solid transparent;background:none;color:#788179;padding:8px 0 12px;cursor:pointer}.auth-tabs button.active{color:#315c4b;border-color:#315c4b;font-weight:600}.auth-note{font-size:13px;color:#788179;line-height:1.6}.auth-field{display:flex;gap:8px;margin-top:12px}.auth-link{border:0;background:none;color:#315c4b;padding:14px 0 0;cursor:pointer;font-size:12px}

.page-enter-active,.page-leave-active{transition:opacity .2s ease,transform .2s ease}.page-enter-from{opacity:0;transform:translateY(8px)}.page-leave-to{opacity:0;transform:translateY(-5px)}@media(prefers-reduced-motion:reduce){.page-enter-active,.page-leave-active{transition:none}}
</style>
