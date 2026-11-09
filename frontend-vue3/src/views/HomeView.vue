<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { ArrowRight, EditPen, Location, MagicStick } from '@element-plus/icons-vue'
import { communityApi } from '../services/api'
import type { Blog } from '../types/api'
import { selectedCampus } from '../services/campus'
import { blogDate, blogImage, blogText, communityTopics } from '../services/community'

const posts = ref<Blog[]>([])
const postTopic = ref('')
const postsLoading = ref(false)
const postsError = ref('')
const postPage = ref(1)
const failedPage = ref(1)
const hasMore = ref(false)
const postTotal = ref(0)
let postRequest = 0

async function loadPosts(topic: string, page: number): Promise<void> {
  const request = ++postRequest
  postTopic.value = topic
  if (page === 1) { posts.value = []; postTotal.value = 0; hasMore.value = false }
  postsLoading.value = true
  postsError.value = ''
  try {
    const result = await communityApi.communityBlogs(page, topic)
    if (request !== postRequest) return
    const seen = new Set(posts.value.map((post) => post.id))
    posts.value = page === 1 ? result.items : [...posts.value, ...result.items.filter((post) => !seen.has(post.id))]
    postPage.value = page
    postTotal.value = result.total
    hasMore.value = page * 10 < result.total
  } catch {
    if (request === postRequest) { failedPage.value = page; postsError.value = '社区动态加载失败，请稍后重试。' }
  } finally { if (request === postRequest) postsLoading.value = false }
}

onMounted(() => { void loadPosts('', 1) })
onUnmounted(() => { postRequest++ })
</script>

<template>
  <div class="community-home">
    <div class="community-layout">
      <section class="feed" aria-label="校园社区动态">
        <div class="feed-heading"><div><div class="campus-label"><el-icon><Location /></el-icon> 沈阳药科大学 · {{selectedCampus.label}}</div><h1>校园社区<span> / 最近发生</span></h1></div><RouterLink class="publish-button" to="/publish"><el-icon><EditPen /></el-icon> 发布帖子</RouterLink></div>
        <RouterLink class="compose" to="/publish"><span class="compose-avatar">R</span><span class="compose-placeholder">今天想和大家聊点什么？</span><span class="compose-action">写帖子 <el-icon><ArrowRight /></el-icon></span></RouterLink>
        <nav class="topic-tabs" aria-label="帖子话题"><button :class="{active: !postTopic}" :aria-pressed="!postTopic" @click="loadPosts('', 1)">全部动态</button><button v-for="item in communityTopics" :key="item" :class="{active: postTopic === item}" :aria-pressed="postTopic === item" @click="loadPosts(item, 1)">{{item}}</button></nav>
        <div v-if="postsLoading && !posts.length" class="feed-state">正在加载社区动态…</div>
        <div v-else-if="!posts.length && !postsError" class="feed-state"><strong>这里还很安静</strong><span>来分享第一条校园动态吧。</span><RouterLink to="/publish">发布帖子 →</RouterLink></div>
        <div v-if="posts.length" class="post-list"><RouterLink v-for="post in posts" :key="post.id" :to="`/blog/${post.id}`" class="post-row"><div class="post-avatar">{{(post.name || '邻').slice(0,1)}}</div><div class="post-main"><div class="post-meta"><strong>{{post.name || '校园同学'}}</strong><span v-if="post.createTime">{{blogDate(post.createTime)}}</span><span v-if="post.topic" class="post-topic">{{post.topic}}</span></div><h2>{{post.title}}</h2><p>{{blogText(post.content || '')}}</p><span class="post-read">阅读全文 <el-icon><ArrowRight /></el-icon></span></div><img v-if="post.images" class="post-thumb" :src="blogImage(post.images.split(',')[0] || '')" alt="帖子图片" @error="($event.target as HTMLImageElement).style.display='none'"></RouterLink></div>
        <div v-if="postsError" class="feed-error" role="alert">{{postsError}} <button @click="loadPosts(postTopic, failedPage)">重试</button></div>
        <button v-else-if="hasMore" class="load-posts" :disabled="postsLoading" @click="loadPosts(postTopic, postPage + 1)">{{postsLoading ? '加载中…' : `查看更多动态（已显示 ${posts.length} / ${postTotal}）`}}</button>
        <p v-else-if="posts.length" class="feed-end">已经看到这里了</p>
      </section>
      <aside class="community-aside" aria-label="校园生活入口">
        <div class="campus-card"><div class="campus-photo"><img src="/campus-gate.jpg" alt="沈阳药科大学文化路校本部大门"></div><div class="campus-copy"><span>在这里，遇见校园生活</span><strong>沈阳药科大学 · {{selectedCampus.label}}</strong><p>发现身边的故事，也分享你的日常。</p><RouterLink to="/publish">发布一条动态 <el-icon><ArrowRight /></el-icon></RouterLink><a class="photo-source" href="https://www.syphu.edu.cn/info/1065/4139.htm" target="_blank" rel="noopener noreferrer">图片来源：沈阳药科大学 ↗</a></div></div>
        <div class="aside-links"><RouterLink to="/nearby"><div><strong>探索附近</strong><span>商户、城市指南与生活去处</span></div><el-icon><ArrowRight /></el-icon></RouterLink><RouterLink to="/assistant"><div><strong>校园 AI 助手</strong><span>找店、查信息，问问助手</span></div><el-icon><MagicStick /></el-icon></RouterLink></div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.community-home{max-width:1120px;margin:0 auto;color:#37352f;font-family:Inter,-apple-system,BlinkMacSystemFont,"Segoe UI","Microsoft YaHei",sans-serif}
.community-layout{display:grid;grid-template-columns:minmax(0,1fr) 280px;gap:32px;align-items:start}
.feed{min-width:0;background:#fff;border:1px solid #e5e3df;border-radius:12px;overflow:hidden}
.feed-heading{display:flex;justify-content:space-between;align-items:end;gap:16px;padding:32px 32px 22px}
.campus-label{display:flex;align-items:center;gap:6px;color:#787671;font-size:12px}
.feed-heading h1{font-size:28px;line-height:1.25;letter-spacing:-.03em;margin:12px 0 0;color:#1a1a1a;font-weight:700}
.feed-heading h1 span{font-size:13px;color:#a4a097;font-weight:400;letter-spacing:0}
.publish-button{display:flex;align-items:center;gap:7px;background:#5645d4;color:#fff;border-radius:8px;padding:10px 15px;font-size:13px;font-weight:600;white-space:nowrap;transition:background .16s}
.publish-button:hover{background:#4534b3}
.compose{display:flex;align-items:center;gap:12px;margin:0 32px;padding:12px 14px;background:#f6f5f4;border:1px solid #ede9e4;border-radius:8px;color:#787671;font-size:14px;transition:background .16s,border-color .16s}
.compose:hover{background:#f0eeec;border-color:#d8d5d0}
.compose-avatar,.post-avatar{display:grid;place-items:center;flex:none;background:#e6e0f5;color:#5645a8;font-weight:600;border-radius:8px}
.compose-avatar{width:34px;height:34px;font-size:14px}.compose-placeholder{flex:1}.compose-action{display:flex;align-items:center;color:#5d5b54;font-size:12px;white-space:nowrap}
.topic-tabs{display:flex;gap:24px;overflow:auto;margin:18px 32px 0;border-bottom:1px solid #e5e3df;scrollbar-width:none}.topic-tabs::-webkit-scrollbar{display:none}
.topic-tabs button{white-space:nowrap;border:0;border-bottom:2px solid transparent;background:none;padding:12px 0;color:#787671;font-size:13px;cursor:pointer}.topic-tabs button:hover{color:#1a1a1a}.topic-tabs button.active{border-color:#37352f;color:#1a1a1a;font-weight:600}
.post-list{padding:0 32px}.post-row{display:flex;gap:14px;padding:22px 0;border-bottom:1px solid #ede9e4;transition:background .16s}.post-row:hover{background:#fafaf9}
.post-avatar{width:38px;height:38px;font-size:14px}.post-main{min-width:0;flex:1}.post-meta{display:flex;gap:9px;align-items:center;flex-wrap:wrap;color:#a4a097;font-size:12px}.post-meta strong{color:#5d5b54;font-size:13px;font-weight:600}.post-topic{padding:3px 7px;background:#f0eeec;border-radius:4px;color:#5d5b54}
.post-main h2{font-size:17px;line-height:1.5;color:#1a1a1a;margin:9px 0 6px;font-weight:600}.post-main p{display:-webkit-box;overflow:hidden;-webkit-line-clamp:2;-webkit-box-orient:vertical;white-space:pre-line;margin:0;color:#787671;font-size:14px;line-height:1.65}.post-read{display:flex;align-items:center;gap:3px;margin-top:12px;color:#787671;font-size:12px}.post-thumb{width:114px;height:86px;object-fit:cover;background:#f6f5f4;border-radius:8px;flex:none}
.feed-state{display:flex;min-height:200px;align-items:center;justify-content:center;flex-direction:column;gap:10px;color:#787671;font-size:13px}.feed-state strong{color:#37352f;font-weight:600}.feed-state a,.feed-error button{color:#5645d4}.feed-error{padding:18px 32px;color:#b42318;font-size:13px}.feed-error button{border:0;background:none;cursor:pointer}.load-posts{display:block;margin:22px auto 26px;padding:10px 18px;border:1px solid #e5e3df;border-radius:8px;background:#fff;color:#37352f;font-size:13px;cursor:pointer}.load-posts:hover{background:#f6f5f4}.load-posts:disabled{opacity:.6;cursor:wait}.feed-end{text-align:center;color:#a4a097;font-size:12px;padding:10px 0 20px}
.community-aside{display:grid;gap:16px;position:sticky;top:100px}.campus-card,.aside-links{background:#fff;border:1px solid #e5e3df;border-radius:12px;overflow:hidden}.campus-photo{height:145px;overflow:hidden;background:#f0eeec}.campus-photo img{width:100%;height:100%;object-fit:cover}.campus-copy{display:flex;flex-direction:column;align-items:flex-start;padding:20px}.campus-copy>span{font-size:12px;color:#787671}.campus-copy strong{font-size:15px;margin-top:8px;color:#1a1a1a}.campus-copy p{font-size:13px;line-height:1.65;color:#787671;margin:9px 0 14px}.campus-copy>a:not(.photo-source){display:flex;align-items:center;gap:4px;color:#5645d4;font-size:13px;font-weight:500}.campus-copy .photo-source{color:#a4a097;font-size:10px;margin-top:14px}.aside-links{padding:0 20px}.aside-links>a{display:flex;align-items:center;justify-content:space-between;padding:18px 0;color:#787671}.aside-links>a+a{border-top:1px solid #ede9e4}.aside-links div{display:flex;flex-direction:column;gap:6px}.aside-links strong{font-size:14px;color:#37352f;font-weight:600}.aside-links span{font-size:12px;color:#787671}.aside-links .el-icon{font-size:16px}.aside-links>a:hover strong{color:#5645d4}
.compose:focus-visible,.publish-button:focus-visible,.topic-tabs button:focus-visible,.post-row:focus-visible,.load-posts:focus-visible,.aside-links a:focus-visible{outline:2px solid #5645d4;outline-offset:2px}
@media(max-width:760px){.community-home{margin:-20px -16px 0}.community-layout{display:block}.community-aside{display:none}.feed{border:0;border-radius:0}.feed-heading{padding:22px 17px 17px}.feed-heading h1{font-size:23px;margin-top:9px}.feed-heading h1 span{font-size:11px}.publish-button{padding:9px 11px;font-size:12px}.compose{margin:0 17px;padding:10px 12px}.compose-avatar{width:30px;height:30px}.topic-tabs{gap:21px;margin:10px 17px 0}.post-list{padding:0 17px}.post-row{gap:10px;padding:19px 0}.post-avatar{width:32px;height:32px}.post-main h2{font-size:16px}.post-main p{font-size:13px}.post-thumb{width:76px;height:76px}.post-meta{gap:6px}.feed-end{padding-bottom:25px}}
@media(prefers-reduced-motion:reduce){.post-row,.compose,.publish-button{transition:none}}

.publish-button{background:#315c4b}.publish-button:hover{background:#254b3b}.compose-avatar,.post-avatar{background:#e5efe9;color:#315c4b}.campus-copy>a:not(.photo-source),.feed-state a,.feed-error button{color:#315c4b}.compose:focus-visible,.publish-button:focus-visible,.topic-tabs button:focus-visible,.post-row:focus-visible,.load-posts:focus-visible,.aside-links a:focus-visible{outline-color:#668e79}.aside-links>a:hover strong{color:#315c4b}
</style>
