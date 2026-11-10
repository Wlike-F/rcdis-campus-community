<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Calendar, EditPen, CollectionTag, Clock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { communityApi } from '../services/api'
import { blogDate, blogImage } from '../services/community'
import type { Blog, PointsSummary, User } from '../types/api'

interface Level { name: string; minimum: number; next: number | null; label: string }
const levels: Level[] = [
  { name: '初识邻里', minimum: 0, next: 100, label: '刚刚加入，故事正要开始' },
  { name: '校园熟邻', minimum: 100, next: 300, label: '你已是校园里的熟面孔' },
  { name: '生活探索家', minimum: 300, next: 800, label: '你的发现，让校园更有趣' },
  { name: '知邻伙伴', minimum: 800, next: null, label: '感谢一路分享与陪伴' },
]
const router = useRouter()
const user = ref<User>()
const blogs = ref<Blog[]>([])
const points = ref<PointsSummary>()
const count = ref(0)
const error = ref('')
const loading = ref(true)
const signing = ref(false)
const hasToken = ref(Boolean(sessionStorage.getItem('token')))
const editVisible = ref(false)
const editName = ref('')
const editIcon = ref('')
const savingProfile = ref(false)
const uploadingAvatar = ref(false)
function openEditor(): void { editName.value = user.value?.nickName || ''; editIcon.value = user.value?.icon || ''; editVisible.value = true }
async function uploadAvatar(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/gif'].includes(file.type) || file.size > 5 * 1024 * 1024) { ElMessage.error('请选择 5MB 以内的 JPG、PNG 或 GIF 图片'); input.value = ''; return }
  uploadingAvatar.value = true
  try { editIcon.value = await communityApi.uploadBlogImage(file) }
  catch (exception) { ElMessage.error(exception instanceof Error ? exception.message : '头像上传失败') }
  finally { uploadingAvatar.value = false; input.value = '' }
}
async function saveProfile(): Promise<void> {
  const name = editName.value.trim()
  if (!name || name.length > 32) { ElMessage.error('昵称须为 1 至 32 个字符'); return }
  if (savingProfile.value || uploadingAvatar.value) return
  savingProfile.value = true
  try { user.value = await communityApi.updateProfile(name, editIcon.value); editVisible.value = false; window.dispatchEvent(new Event('rcdis:auth')); ElMessage.success('资料已保存') }
  catch (exception) { ElMessage.error(exception instanceof Error ? exception.message : '资料保存失败') }
  finally { savingProfile.value = false }
}
const historyVisible = ref(false)
const rulesVisible = ref(false)
let latestLoad = 0
const level = computed(() => [...levels].reverse().find((item) => (points.value?.growth ?? 0) >= item.minimum)!)
const progress = computed(() => level.value.next === null ? 100 : Math.min(100, Math.round(((points.value?.growth ?? 0) - level.value.minimum) / (level.value.next - level.value.minimum) * 100)))

async function load(): Promise<void> {
  const request = ++latestLoad
  error.value = ''
  hasToken.value = Boolean(sessionStorage.getItem('token'))
  if (!hasToken.value) { user.value = undefined; points.value = undefined; loading.value = false; error.value = '登录后开启你的校园生活记录。'; return }
  loading.value = true
  try {
    const result = await Promise.all([communityApi.me(), communityApi.myBlogs(), communityApi.signCount(), communityApi.points()])
    if (request !== latestLoad) return
    user.value = result[0]; blogs.value = result[1]; count.value = result[2]; points.value = result[3]
  } catch {
    if (request !== latestLoad) return
    user.value = undefined; points.value = undefined
    error.value = sessionStorage.getItem('token') ? '个人信息加载失败，请检查网络后重试。' : '登录后开启你的校园生活记录。'
  } finally { if (request === latestLoad) loading.value = false }
}
async function sign(): Promise<void> {
  if (signing.value || points.value?.signedToday) return
  signing.value = true
  try { points.value = await communityApi.sign(); count.value = await communityApi.signCount(); ElMessage.success('签到成功，获得 10 积分') }
  catch (exception) { ElMessage.error(exception instanceof Error ? exception.message : '签到失败，请稍后重试'); await load() }
  finally { signing.value = false }
}
async function logout(): Promise<void> {
  try { await communityApi.logout(); sessionStorage.removeItem('token'); window.dispatchEvent(new Event('rcdis:auth')); await router.push('/') }
  catch { ElMessage.error('退出失败，请稍后重试') }
}
function openLogin(): void { window.dispatchEvent(new Event('rcdis:login')) }
onMounted(() => { void load(); window.addEventListener('rcdis:auth', load) })
onUnmounted(() => { latestLoad++; window.removeEventListener('rcdis:auth', load) })
</script>

<template>
  <main class="profile-page">
    <div v-if="error" class="profile-empty"><span class="empty-mark">R</span><h1>你好，校园新朋友</h1><p>{{error}}</p><el-button v-if="!hasToken" type="primary" @click="openLogin">登录 / 注册</el-button><el-button v-else @click="load">重新加载</el-button></div>
    <div v-else-if="loading && !user" class="profile-skeleton"><el-skeleton animated :rows="8" /></div>
    <template v-else-if="user && points">
      <section :class="['profile-hero', `theme-${points.theme}`]">
        <div class="identity"><div class="avatar"><img v-if="user.icon" :src="blogImage(user.icon)" alt="用户头像" @error="($event.target as HTMLImageElement).style.display='none'"><span v-else>{{(user.nickName || '邻').slice(0, 1)}}</span></div><div class="identity-copy"><span class="identity-kicker">MY CAMPUS LIFE</span><h1>{{user.nickName || '校园同学'}}</h1><span class="member-tag">✳ {{level.name}}</span></div><div class="identity-actions"><button class="edit-profile" @click="openEditor">编辑资料</button><button class="logout" @click="logout">退出登录</button></div></div>
        <div class="member-card"><div class="card-top"><div><span class="card-kicker">RCDIS · CAMPUS MEMBER</span><h2>{{level.name}}</h2><p>{{level.label}}</p></div><div class="card-emblem"><span>R</span><small>知邻</small></div></div><div class="level-row"><span>成长进度</span><span>{{points.growth}}<template v-if="level.next"> / {{level.next}}</template> 成长值</span></div><div class="progress-track"><div :style="{width: `${progress}%`}"></div></div><div class="card-bottom"><span>{{level.next ? `再获得 ${level.next - points.growth} 成长值，解锁下一级` : '已达到最高等级，继续记录你的校园生活'}}</span><button @click="rulesVisible = true">成长规则 →</button></div></div>
      </section>
      <div class="profile-content"><div class="main-column">
        <section class="asset-card"><div class="section-heading"><div><span class="section-kicker">YOUR CAMPUS ACCOUNT</span><h2>我的校园足迹</h2></div><button class="text-link" @click="historyVisible = true">积分明细 <el-icon><ArrowRight /></el-icon></button></div><div class="asset-grid"><button class="asset-item" @click="historyVisible = true"><strong>{{points.points}}</strong><span>我的积分</span><small>查看获取记录 →</small></button><div class="asset-item"><strong>{{count}}<i> 天</i></strong><span>本月连续签到</span><small>今天也来打个卡</small></div><div class="asset-item"><strong>{{blogs.length}}<i v-if="blogs.length === 10">+</i></strong><span>我的帖子</span><small>最近发布记录</small></div></div></section>
        <RouterLink class="reward-banner" to="/rewards"><div><span>POINTS MARKET</span><strong>把积分，换成一点心意</strong><small>兑换专属主题，装点你的校园主页</small></div><b>逛积分商城 →</b></RouterLink>
        <section class="tasks-card"><div class="section-heading"><div><span class="section-kicker">LITTLE DAILY HABITS</span><h2>今天，也积累一点快乐</h2><p>完成小任务，收集你的校园积分。</p></div><button class="text-link" @click="rulesVisible = true">积分规则 <el-icon><ArrowRight /></el-icon></button></div><div class="task-list"><div class="task-row"><div class="task-icon"><el-icon><Calendar /></el-icon></div><div class="task-copy"><strong>每日签到</strong><span>来社区打个招呼，记录每一天</span></div><div class="task-reward">+10 <small>积分</small></div><button class="task-action" :class="{done:points.signedToday}" :disabled="signing || points.signedToday" @click="sign">{{points.signedToday ? '今日已签到' : signing ? '签到中' : '签到'}}</button></div><div class="task-row"><div class="task-icon warm"><el-icon><EditPen /></el-icon></div><div class="task-copy"><strong>分享校园动态</strong><span>每天发布第一篇帖子即可获得</span></div><div class="task-reward">+20 <small>积分</small></div><span v-if="points.postedToday" class="task-action done">今日已完成</span><RouterLink v-else class="task-action outline" to="/publish">去分享</RouterLink></div></div></section>
        <section class="posts-card"><div class="section-heading"><div><span class="section-kicker">YOUR STORIES</span><h2>我的帖子</h2><p>那些值得记录的校园瞬间。</p></div><RouterLink class="text-link" to="/publish">写一篇 <el-icon><ArrowRight /></el-icon></RouterLink></div><div v-if="!blogs.length" class="posts-empty"><el-icon><CollectionTag /></el-icon><strong>这里还没有你的故事</strong><span>去分享第一条校园动态吧</span><RouterLink to="/publish">发布帖子 →</RouterLink></div><div v-else class="post-list"><RouterLink v-for="blog in blogs" :key="blog.id" class="post-row" :to="`/blog/${blog.id}`"><div class="post-copy"><span>{{blog.topic || '校园社区'}} <template v-if="blog.createTime">· {{blogDate(blog.createTime)}}</template></span><strong>{{blog.title}}</strong><small>查看帖子 →</small></div><img v-if="blog.images" :src="blogImage(blog.images.split(',')[0] || '')" alt="帖子图片" @error="($event.target as HTMLImageElement).style.display='none'"></RouterLink></div><p v-if="blogs.length === 10" class="list-note">展示最近 10 篇帖子</p></section>
      </div><aside class="side-column"><div class="side-card"><span class="section-kicker">A LITTLE EVERY DAY</span><h2>把日子过成<br>喜欢的样子。</h2><p>从一次签到、一条分享开始，慢慢留下属于你的校园记忆。</p><div class="side-art"><span>✳</span><span>RCDIS<br>CAMPUS</span></div></div><div class="side-note"><el-icon><Clock /></el-icon><span>积分和任务每天按北京时间更新</span></div></aside></div>
      <el-dialog v-model="editVisible" title="编辑个人资料" width="min(440px, 92vw)"><div class="profile-editor"><div class="editor-avatar"><img v-if="editIcon" :src="blogImage(editIcon)" alt="头像预览"><span v-else>{{(editName || '邻').slice(0, 1)}}</span></div><label class="avatar-upload">{{uploadingAvatar ? '上传中…' : '更换头像'}}<input type="file" accept="image/jpeg,image/png,image/gif" :disabled="uploadingAvatar" @change="uploadAvatar"></label><p>支持 JPG、PNG、GIF，最大 5MB</p><label for="profile-name">昵称</label><el-input id="profile-name" v-model="editName" maxlength="32" show-word-limit placeholder="请输入昵称" /></div><template #footer><el-button @click="editVisible = false">取消</el-button><el-button type="primary" :loading="savingProfile" :disabled="uploadingAvatar" @click="saveProfile">保存资料</el-button></template></el-dialog>
      <el-dialog v-model="historyVisible" title="积分明细" width="min(500px, 92vw)"><div class="history-total"><span>可用积分</span><strong>{{points.points}}</strong></div><div v-if="!points.records.length" class="history-empty">还没有积分记录，完成每日任务来积累吧。</div><div v-for="(record, index) in points.records" :key="`${record.action}-${record.businessDate}-${index}`" class="history-row"><div><strong>{{record.action === 'SIGN' ? '每日签到' : record.action === 'POST' ? '分享校园动态' : `兑换 · ${record.description}`}}</strong><span>{{record.businessDate}}</span></div><b>{{record.points > 0 ? '+' : ''}}{{record.points}}</b></div><p v-if="points.records.length === 20" class="list-note">展示最近 20 条记录</p></el-dialog>
      <el-dialog v-model="rulesVisible" title="积分与成长规则" width="min(500px, 92vw)"><div class="rules"><p>积分从功能上线后开始累计，历史签到和帖子不追溯补发。</p><p><strong>每日签到 +10 积分</strong><br>每天首次签到获得，每日一次。</p><p><strong>分享动态 +20 积分</strong><br>每天首次成功发布帖子获得，每日一次。</p><p><strong>成长等级</strong><br>0 成长值初识邻里、100 校园熟邻、300 生活探索家、800 知邻伙伴。获得积分同时增加成长值，兑换不影响等级。</p><p>积分可兑换校园主页装扮，每日任务按北京时间重置。</p></div></el-dialog>
    </template>
  </main>
</template>

<style scoped>
.theme-ginkgo{background:#f1e8d3}.theme-ginkgo .member-card{background:#6c5333}.theme-ginkgo .member-tag{background:#ead7a8;color:#735c34}.theme-spring{background:#e6f0e7}.theme-spring .member-card{background:#4c7562}.theme-night{background:#dfe5ec}.theme-night .member-card{background:#253750}.reward-banner{display:flex;align-items:center;justify-content:space-between;gap:18px;background:#e9f0e4;padding:23px 27px;color:#385c43;transition:background .2s}.reward-banner:hover{background:#dfeadc}.reward-banner div{display:flex;flex-direction:column;gap:6px}.reward-banner span{font-size:10px;letter-spacing:.13em;color:#91a58d}.reward-banner strong{font-size:18px;font-weight:600}.reward-banner small{font-size:11px;color:#829882}.reward-banner b{font-size:12px;font-weight:500;white-space:nowrap}@media(max-width:760px){.reward-banner{padding:19px 18px}.reward-banner strong{font-size:15px}.reward-banner b{font-size:11px}}
.profile-page{max-width:1120px;margin:auto;color:#253a2e}.profile-hero{background:#e6ece2;padding:32px 36px 0}.identity{display:flex;align-items:center;gap:15px;padding-bottom:25px}.avatar{width:62px;height:62px;display:grid;place-items:center;background:#f7f9f2;border:1px solid #d3dfd1;color:#3d684b;font:25px Georgia;border-radius:50%}.identity-copy{flex:1}.identity-kicker,.section-kicker,.card-kicker{font-size:10px;letter-spacing:.13em;color:#8ca08d}.identity h1{font-size:23px;margin:5px 0 8px}.member-tag{font-size:11px;color:#607f63;background:#d5e3d3;padding:4px 9px;border-radius:20px}.logout,.text-link{border:0;background:none;color:#758d78;font-size:12px;cursor:pointer}.member-card{position:relative;overflow:hidden;background:#284b3b;color:#f6f7ee;padding:28px 32px 22px;box-shadow:0 15px 35px rgba(38,72,52,.14)}.member-card:after{content:'';position:absolute;width:280px;height:280px;right:5%;top:-165px;border:1px solid rgba(239,246,225,.13);border-radius:50%;box-shadow:0 0 0 30px rgba(239,246,225,.025),0 0 0 65px rgba(239,246,225,.025)}.card-top,.level-row,.card-bottom{display:flex;justify-content:space-between;align-items:center;gap:15px;position:relative;z-index:1}.card-kicker{color:#b7cbb5}.card-top h2{font-size:27px;margin:10px 0 5px;font-weight:500}.card-top p{font-size:12px;color:#bdcfb9;margin:0}.card-emblem{width:86px;height:86px;border:1px solid #9cb59b;border-radius:50%;display:flex;align-items:center;justify-content:center;flex-direction:column;color:#dce9d6;font-family:Georgia,serif}.card-emblem span{font-size:39px;line-height:1}.card-emblem small{font:10px sans-serif;letter-spacing:.2em}.level-row{margin-top:22px;color:#cfdeca;font-size:11px}.progress-track{height:5px;background:#ffffff2b;margin-top:9px;border-radius:8px;overflow:hidden}.progress-track div{height:100%;background:#c3d8ab;transition:width .35s}.card-bottom{margin-top:11px;color:#b8cdb5;font-size:11px}.card-bottom button{border:0;background:none;color:#e0edda;font-size:11px;cursor:pointer;white-space:nowrap}.profile-content{display:grid;grid-template-columns:minmax(0,1fr) 250px;gap:22px;align-items:start;padding-top:24px}.main-column{display:grid;gap:20px}.asset-card,.tasks-card,.posts-card{background:#fff;padding:28px 30px}.section-heading{display:flex;justify-content:space-between;gap:16px;align-items:end}.section-heading h2{font-size:20px;margin:8px 0 0}.section-heading p{font-size:12px;color:#99a397;margin:8px 0 0}.text-link{display:flex;align-items:center;gap:3px;white-space:nowrap}.asset-grid{display:grid;grid-template-columns:repeat(3,1fr);margin-top:25px;background:#f5f8f2}.asset-item{text-align:left;display:flex;flex-direction:column;gap:7px;border:0;border-right:1px solid #e5ebe1;background:transparent;padding:20px 22px;color:inherit}.asset-item:last-child{border:0}.asset-item:first-child{cursor:pointer}.asset-item strong{font-size:28px;color:#365b41}.asset-item i{font-style:normal;font-size:12px;font-weight:400}.asset-item span{font-size:13px;color:#586d5a}.asset-item small{font-size:10px;color:#a2aea0}.task-list{margin-top:18px}.task-row{display:flex;gap:13px;align-items:center;padding:17px 0;border-bottom:1px solid #eff2ed}.task-row:last-child{border:0;padding-bottom:0}.task-icon{width:44px;height:44px;display:grid;place-items:center;background:#e9f1e6;color:#698d6e;font-size:20px;flex:none}.task-icon.warm{background:#f7eee4;color:#b88961}.task-copy{display:flex;flex-direction:column;gap:6px;min-width:0;flex:1}.task-copy strong{font-size:13px}.task-copy span{font-size:11px;color:#9ca69b;line-height:1.4}.task-reward{font-size:16px;font-weight:600;color:#c38c50;white-space:nowrap}.task-reward small{font-size:10px;font-weight:400}.task-action{background:#315a43;border:1px solid #315a43;color:#fff;font-size:11px;text-align:center;padding:8px 12px;white-space:nowrap;cursor:pointer}.task-action.outline{background:#fff;color:#426c4d}.task-action.done{background:#f2f5ef;color:#a1b0a0;border-color:#f2f5ef;cursor:default}.post-row{display:flex;justify-content:space-between;align-items:center;gap:14px;padding:18px 0;border-bottom:1px solid #eef1eb}.post-copy{min-width:0;display:flex;flex-direction:column;gap:7px}.post-copy span,.post-copy small,.list-note{color:#9ba99a;font-size:11px}.post-copy strong{font-size:14px;font-weight:500;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.post-row img{width:76px;height:62px;object-fit:cover}.posts-empty{display:flex;align-items:center;flex-direction:column;gap:9px;padding:32px;color:#a0aea0;font-size:12px}.posts-empty .el-icon{font-size:25px;color:#a9bea8}.posts-empty strong{font-size:13px;font-weight:500;color:#6d826e}.posts-empty a{color:#4e7756}.side-card{background:#e9eee5;padding:28px 25px;overflow:hidden}.side-card h2{font-size:24px;line-height:1.5;font-weight:500;margin:18px 0;color:#385744}.side-card p{font-size:12px;line-height:1.9;color:#839384}.side-art{margin:36px -10px -10px;padding-top:25px;border-top:1px solid #cbd8c8;display:flex;justify-content:space-between;align-items:end;color:#78967a}.side-art span:first-child{font-size:55px}.side-art span:last-child{text-align:right;font-size:10px;line-height:1.6;letter-spacing:.12em}.side-note{display:flex;gap:8px;align-items:center;color:#91a08f;font-size:11px;padding:18px 2px}.profile-empty,.profile-skeleton{background:#fff;padding:55px;text-align:center}.profile-empty h1{font-size:24px}.profile-empty p{color:#899888;font-size:13px}.profile-empty .el-button{--el-color-primary:#315a43;margin-top:15px}.empty-mark{display:inline-grid;place-items:center;width:52px;height:52px;background:#e9f0e6;border-radius:50%;color:#557960;font:28px Georgia}.history-total{display:flex;justify-content:space-between;align-items:center;background:#edf4eb;padding:18px;color:#68836c}.history-total strong{font-size:27px;color:#365d40}.history-empty{padding:32px 0;color:#98a597;text-align:center}.history-row{display:flex;justify-content:space-between;align-items:center;padding:15px 0;border-bottom:1px solid #eef1eb}.history-row div{display:flex;flex-direction:column;gap:5px}.history-row strong{font-size:13px;font-weight:500}.history-row span{font-size:11px;color:#a3aea0}.history-row b{color:#7c9c72}.rules{font-size:13px;color:#718074;line-height:1.8}.rules strong{color:#3f5f47}@media(max-width:760px){.profile-page{margin:-12px -16px 0}.profile-hero{padding:24px 18px 0}.avatar{width:52px;height:52px}.identity h1{font-size:20px}.member-card{padding:23px 21px 20px}.card-emblem{width:68px;height:68px}.card-emblem span{font-size:31px}.card-top h2{font-size:24px}.profile-content{display:block;padding:18px 14px 0}.main-column{gap:14px}.asset-card,.tasks-card,.posts-card{padding:22px 18px}.section-heading h2{font-size:18px}.asset-item{padding:16px 9px}.asset-item strong{font-size:23px}.asset-item small{font-size:9px}.task-row{gap:9px}.task-icon{width:38px;height:38px;font-size:17px}.task-reward{font-size:14px}.task-action{padding:8px}.side-column{display:none}.profile-empty,.profile-skeleton{margin:12px 16px;padding:48px 22px}}@media(prefers-reduced-motion:reduce){.progress-track div{transition:none}}

.profile-page{color:#25342a}.profile-hero{background:#edf4ef;border-radius:14px 14px 0 0}.member-card{border-radius:12px 12px 0 0;box-shadow:none}.asset-card,.tasks-card,.posts-card,.profile-empty,.profile-skeleton{border:1px solid #e7e8e4;border-radius:12px}.asset-grid{background:#f5f8f5;border-radius:9px;overflow:hidden}.asset-item strong{color:#315c4b}.task-icon{border-radius:10px}.task-action{border-radius:8px;background:#315c4b;border-color:#315c4b}.task-action.outline{color:#315c4b}.post-row img{border-radius:8px}.side-card,.reward-banner{border-radius:12px}.reward-banner{background:#edf4ef;color:#315c4b}.side-card{background:#edf4ef}.history-total{border-radius:9px}.profile-empty .el-button{--el-color-primary:#315c4b}@media(max-width:760px){.profile-hero{border-radius:0}.member-card{border-radius:12px 12px 0 0}}
.avatar{overflow:hidden}.avatar img{width:100%;height:100%;object-fit:cover}.identity-actions{display:flex;gap:12px;align-items:center}.edit-profile{border:1px solid #c8d9ca;border-radius:8px;background:#fff;color:#315c4b;padding:8px 11px;font-size:12px;cursor:pointer}.profile-editor{display:flex;flex-direction:column;align-items:flex-start;gap:12px}.editor-avatar{width:70px;height:70px;display:grid;place-items:center;border-radius:50%;overflow:hidden;background:#e5efe9;color:#315c4b;font-size:26px}.editor-avatar img{width:100%;height:100%;object-fit:cover}.avatar-upload{color:#315c4b;font-size:13px;cursor:pointer}.avatar-upload input{position:absolute;width:1px;height:1px;opacity:0}.profile-editor p{margin:0;color:#8a958c;font-size:11px}.profile-editor label[for]{margin-top:6px;font-size:13px;font-weight:600;color:#34473a}@media(max-width:760px){.identity-actions{flex-direction:column;gap:8px;align-items:flex-end}}
</style>
