<script setup lang="ts">
import { onMounted, ref } from 'vue'
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
const phone = ref('')
const code = ref('')
const sending = ref(false)
const logging = ref(false)
const phonePattern = /^1(?:[38][0-9]|4[579]|5[0-35-9]|66|7[0135678]|9[89])\d{8}$/
const nickname = ref('')
async function refreshUser(): Promise<void> {
  if (!sessionStorage.getItem('token')) { nickname.value = ''; return }
  try { const response = await http.get<ApiResult<{ nickName: string }>>('/user/me'); nickname.value = response.data.data.nickName }
  catch { nickname.value = '' }
}
onMounted(refreshUser)
window.addEventListener('rcdis:auth', () => { void refreshUser() })
async function sendCode(): Promise<void> {
  if (!phonePattern.test(phone.value)) { ElMessage.error('请输入后端支持的手机号'); return }
  sending.value = true
  try { await http.post('/user/code', null, { params: { phone: phone.value } }); ElMessage.success('验证码已生成，请查看后端日志') }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '验证码获取失败') }
  finally { sending.value = false }
}
async function login(): Promise<void> {
  if (!phonePattern.test(phone.value) || !/^\d{6}$/.test(code.value)) { ElMessage.error('请输入有效手机号和 6 位数字验证码'); return }
  logging.value = true
  try { const response = await http.post<ApiResult<string>>('/user/login', { phone: phone.value, code: code.value }); sessionStorage.setItem('token', response.data.data); await refreshUser(); window.dispatchEvent(new Event('rcdis:auth')); loginVisible.value = false; ElMessage.success('登录成功') }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '登录失败') }
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
      <p>手机号验证码登录，首次登录将自动注册。</p>
      <el-input v-model="phone" placeholder="请输入手机号" maxlength="11" />
      <div style="display:flex;gap:8px;margin-top:12px"><el-input v-model="code" placeholder="请输入验证码" maxlength="6" /><el-button :loading="sending" @click="sendCode">获取验证码</el-button></div>
      <p class="muted">演示环境的验证码请查看后端服务日志。</p>
      <template #footer><el-button type="primary" :loading="logging" @click="login">登录 / 注册</el-button></template>
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
.page-enter-active,.page-leave-active{transition:opacity .2s ease,transform .2s ease}.page-enter-from{opacity:0;transform:translateY(8px)}.page-leave-to{opacity:0;transform:translateY(-5px)}@media(prefers-reduced-motion:reduce){.page-enter-active,.page-leave-active{transition:none}}
</style>
