<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { communityApi } from '../services/api'
import type { CommunityComment } from '../services/api'
import type { Blog } from '../types/api'
import { blogDate, blogImage, blogText } from '../services/community'
const route = useRoute()
const blog = ref<Blog>()
const error = ref('')
const comments = ref<CommunityComment[]>([])
const commentTotal = ref(0)
const commentPage = ref(1)
const commentText = ref('')
const commentError = ref('')
const commentLoading = ref(false)
const commentSending = ref(false)
let commentRequest = 0
async function loadComments(page: number): Promise<void> {
  const id = Number(route.params.id)
  if (!Number.isSafeInteger(id) || id <= 0) return
  const request = ++commentRequest
  commentLoading.value = true
  commentError.value = ''
  try {
    const result = await communityApi.comments(id, page)
    if (request !== commentRequest) return
    comments.value = page === 1 ? result.items : [...comments.value, ...result.items]
    commentTotal.value = result.total
    commentPage.value = page
  } catch (exception) {
    if (request === commentRequest) commentError.value = exception instanceof Error ? exception.message : '评论加载失败，请重试。'
  } finally { if (request === commentRequest) commentLoading.value = false }
}
async function sendComment(): Promise<void> {
  if (!isLoggedIn.value) { openLogin(); return }
  const content = commentText.value.trim()
  if (!content || commentSending.value) return
  const id = Number(route.params.id)
  commentSending.value = true
  commentError.value = ''
  try {
    await communityApi.createComment(id, content)
    if (Number(route.params.id) === id) { commentText.value = ''; await loadComments(1) }
  } catch (exception) { commentError.value = exception instanceof Error ? exception.message : '评论发布失败，请重试。' }
  finally { commentSending.value = false }
}
const isLoggedIn = ref(Boolean(sessionStorage.getItem('token')))
function openLogin(): void { window.dispatchEvent(new Event('rcdis:login')) }
async function load(): Promise<void> {
  error.value = ''
  const id = Number(route.params.id)
  if (!Number.isSafeInteger(id) || id <= 0) { error.value = '帖子编号无效。'; return }
  try { blog.value = await communityApi.blog(id); if (!blog.value) error.value = '帖子不存在。' }
  catch (exception) { error.value = exception instanceof Error ? exception.message : '帖子加载失败，请稍后重试。' }
}
function refreshAuth(): void { isLoggedIn.value = Boolean(sessionStorage.getItem('token')) }
onMounted(() => { void load(); void loadComments(1); window.addEventListener('rcdis:auth', refreshAuth) })
onUnmounted(() => { commentRequest++; window.removeEventListener('rcdis:auth', refreshAuth) })
watch(() => route.params.id, () => { blog.value = undefined; comments.value = []; commentText.value = ''; void load(); void loadComments(1) })
</script>
<template><article class="blog-detail"><RouterLink class="back" to="/">← 返回社区</RouterLink><div v-if="error"><el-alert :title="error" type="warning" :closable="false"/><el-button @click="load">重试</el-button><el-button v-if="!isLoggedIn" @click="openLogin">登录</el-button></div><template v-else-if="blog"><div class="topic">{{blog.topic || '校园社区'}}</div><h1>{{blog.title}}</h1><div class="author"><span class="avatar">{{(blog.name || '邻').slice(0,1)}}</span><div><strong>{{blog.name || '校园同学'}}</strong><small>{{blogDate(blog.createTime)}}</small></div></div><p class="content">{{blogText(blog.content || '')}}</p><div class="images"><img v-for="image in (blog.images || '').split(',').filter(Boolean)" :key="image" :src="blogImage(image)" alt="帖子图片" @error="($event.target as HTMLImageElement).src='/city-guide.svg'"></div><div class="end-note">分享让校园生活更有温度 <RouterLink to="/publish">写一篇帖子 →</RouterLink></div><section class="comments" aria-label="评论区"><div class="comments-heading"><div><h2>评论 <span>{{commentTotal}}</span></h2><p>一起聊聊这条校园动态</p></div></div><form class="comment-form" @submit.prevent="sendComment"><textarea v-model="commentText" maxlength="255" rows="3" placeholder="写下你的想法，友善交流…" aria-label="评论内容"></textarea><div class="comment-actions"><span>{{commentText.length}} / 255</span><button type="submit" :disabled="commentSending || (isLoggedIn && !commentText.trim())">{{commentSending ? '发布中…' : isLoggedIn ? '发表评论' : '登录后评论'}}</button></div></form><p v-if="commentError" class="comment-error" role="alert">{{commentError}} <button type="button" @click="loadComments(commentPage)">重试加载</button></p><div v-if="commentLoading && !comments.length" class="comment-state">正在加载评论…</div><div v-else-if="!comments.length && !commentError" class="comment-state">还没有评论，来分享你的想法吧。</div><div v-else class="comment-list"><div v-for="item in comments" :key="item.id" class="comment-item"><span class="comment-avatar">{{(item.name || '邻').slice(0,1)}}</span><div><div class="comment-meta"><strong>{{item.name}}</strong><time>{{blogDate(item.createTime)}}</time></div><p>{{item.content}}</p></div></div></div><button v-if="comments.length < commentTotal && !commentError" class="comment-more" :disabled="commentLoading" @click="loadComments(commentPage + 1)">{{commentLoading ? '加载中…' : '查看更多评论'}}</button></section></template><el-skeleton v-else animated :rows="6"/></article></template>
<style scoped>.blog-detail{max-width:820px;margin:auto;background:#fff;padding:36px 42px}.back{display:inline-block;color:#69826b;font-size:13px;margin-bottom:35px}.topic{color:#638568;background:#eef4eb;display:table;font-size:11px;padding:6px 9px}.blog-detail h1{font-size:30px;line-height:1.4;color:#283d2d;margin:16px 0 20px}.author{display:flex;gap:10px;align-items:center;border-bottom:1px solid #edf0e9;padding-bottom:22px}.avatar{width:36px;height:36px;display:grid;place-items:center;background:#e1ebde;color:#557358}.author div{display:flex;flex-direction:column;gap:4px}.author strong{font-size:12px;color:#4a5e4d;font-weight:500}.author small{font-size:11px;color:#a1aa9e}.images{display:grid;grid-template-columns:repeat(2,1fr);gap:12px;margin:24px 0}.images img{width:100%;max-height:420px;object-fit:cover}.content{white-space:pre-wrap;line-height:2;color:#48544a;margin:28px 0}.end-note{border-top:1px solid #edf0e9;padding-top:20px;margin-top:35px;color:#9ca89a;font-size:12px;display:flex;justify-content:space-between}.end-note a{color:#496f51}@media(max-width:700px){.blog-detail{padding:23px 18px}.blog-detail h1{font-size:24px}.back{margin-bottom:25px}.images img{max-height:200px}}
.blog-detail{border:1px solid #e7e8e4;border-radius:14px}.back,.end-note a{color:#315c4b}.topic{border-radius:6px;background:#e8f1eb;color:#426c52}.blog-detail h1{color:#242825;letter-spacing:-.025em}.avatar{border-radius:10px;background:#e5efe9;color:#315c4b}.author{border-color:#eceeeb}.author strong{color:#414c44}.author small{color:#7d867f}.content{color:#39433c;line-height:1.9}.images img{border-radius:10px}.end-note{border-color:#eceeeb;color:#7d867f}@media(max-width:700px){.blog-detail{border-radius:12px}}

.comments{border-top:1px solid #eceeeb;margin-top:28px;padding-top:28px}.comments-heading h2{font-size:19px;margin:0;color:#242825}.comments-heading h2 span{font-size:13px;font-weight:400;color:#79847c;margin-left:5px}.comments-heading p{font-size:12px;color:#7d867f;margin:8px 0 18px}.comment-form{border:1px solid #e3e9e4;border-radius:10px;background:#f9fbf9;padding:12px}.comment-form:focus-within{border-color:#8aab97}.comment-form textarea{display:block;width:100%;resize:vertical;min-height:65px;border:0;outline:0;background:transparent;font:inherit;font-size:14px;line-height:1.6;color:#263b2e}.comment-actions{display:flex;align-items:center;justify-content:space-between;margin-top:8px}.comment-actions span{font-size:11px;color:#929c94}.comment-actions button{background:#315c4b;border:0;border-radius:7px;color:#fff;padding:9px 14px;cursor:pointer;font-size:12px}.comment-actions button:disabled{opacity:.5;cursor:default}.comment-item{display:flex;gap:12px;padding:19px 0;border-bottom:1px solid #f0f2ef}.comment-avatar{display:grid;place-items:center;width:34px;height:34px;flex:none;border-radius:9px;background:#e5efe9;color:#315c4b;font-size:13px}.comment-item>div{min-width:0;flex:1}.comment-meta{display:flex;align-items:center;justify-content:space-between;gap:12px;font-size:12px;color:#8a948c}.comment-meta strong{color:#46594b}.comment-item p{white-space:pre-wrap;overflow-wrap:anywhere;font-size:14px;line-height:1.7;color:#34463a;margin:8px 0 0}.comment-state{text-align:center;padding:35px 0;color:#929c94;font-size:13px}.comment-error{font-size:12px;color:#ad4f40}.comment-error button{border:0;background:none;color:#315c4b;cursor:pointer}.comment-more{display:block;margin:20px auto 0;background:#fff;border:1px solid #dce5dd;border-radius:8px;padding:9px 16px;color:#315c4b;cursor:pointer}@media(max-width:700px){.comments{margin-top:24px;padding-top:24px}}
</style>
