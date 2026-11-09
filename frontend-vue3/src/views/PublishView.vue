<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Picture, Close } from '@element-plus/icons-vue'
import { communityApi } from '../services/api'
import { communityTopics } from '../services/community'

interface SelectedImage { file: File; preview: string; uploadedPath: string }

const router = useRouter()
const title = ref('')
const content = ref('')
const topic = ref('')
const images = ref<SelectedImage[]>([])
const loggedIn = ref(Boolean(sessionStorage.getItem('token')))
const busy = ref(false)
const preview = ref(false)
const fileInput = ref<HTMLInputElement>()
const canPublish = computed(() => title.value.trim() && content.value.trim() && topic.value && !busy.value)

function updateAuth(): void { loggedIn.value = Boolean(sessionStorage.getItem('token')) }
function openLogin(): void { window.dispatchEvent(new Event('rcdis:login')) }
function selectImages(event: Event): void {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (files.length + images.value.length > 9) { ElMessage.error('最多添加 9 张图片'); return }
  if (files.some((file) => !['image/jpeg', 'image/png', 'image/gif'].includes(file.type) || file.size > 5 * 1024 * 1024)) {
    ElMessage.error('请选择 5MB 以内的 JPG、PNG 或 GIF 图片'); return
  }
  images.value.push(...files.map((file) => ({ file, preview: URL.createObjectURL(file), uploadedPath: '' })))
}
function removeImage(index: number): void {
  const image = images.value[index]
  if (!image) return
  URL.revokeObjectURL(image.preview)
  images.value.splice(index, 1)
}
async function publish(): Promise<void> {
  if (!loggedIn.value) { openLogin(); return }
  if (!canPublish.value) { ElMessage.error('请填写标题、正文并选择话题'); return }
  busy.value = true
  try {
    for (const image of images.value) {
      if (!image.uploadedPath) image.uploadedPath = await communityApi.uploadBlogImage(image.file)
    }
    const id = await communityApi.createBlog({ title: title.value.trim(), content: content.value.trim(), topic: topic.value, images: images.value.map((image) => image.uploadedPath).join(',') })
    ElMessage.success('发布成功')
    await router.push(`/blog/${id}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发布失败，请稍后重试')
  } finally { busy.value = false }
}
onMounted(() => { window.addEventListener('rcdis:auth', updateAuth); if (!loggedIn.value) openLogin() })
onUnmounted(() => { window.removeEventListener('rcdis:auth', updateAuth); images.value.forEach((image) => URL.revokeObjectURL(image.preview)) })
</script>

<template>
  <div class="publish-page">
    <RouterLink class="back" to="/">← 返回社区</RouterLink>
    <div class="publish-layout">
      <section class="editor">
        <div class="editor-heading"><div><span class="eyebrow">CAMPUS COMMUNITY</span><h1>分享校园新鲜事</h1><p>一条提问、一家小店，或是一次临时起意的邀约。</p></div><span class="editor-number">✳</span></div>
        <div v-if="!loggedIn" class="login-prompt">登录后就可以分享你的校园生活。<button @click="openLogin">去登录 →</button></div>
        <label class="field-label" for="post-title">标题 <span>{{title.length}} / 255</span></label>
        <input id="post-title" v-model="title" class="title-input" maxlength="255" placeholder="给帖子起个标题" :disabled="busy">
        <label class="field-label" for="post-content">正文 <span>{{content.length}} / 2048</span></label>
        <textarea id="post-content" v-model="content" class="content-input" maxlength="2048" placeholder="说说你的发现、问题或想法……" :disabled="busy"></textarea>
        <div class="field-label">选择话题 <span>必选</span></div>
        <div class="topics"><button v-for="item in communityTopics" :key="item" type="button" :class="{ active: topic === item }" :disabled="busy" @click="topic = item">{{item}}</button></div>
        <div class="field-label">添加图片 <span>最多 9 张，单张 5MB</span></div>
        <div class="image-picker"><div v-for="(image, index) in images" :key="image.preview" class="image-preview"><img :src="image.preview" alt="待发布图片"><button type="button" :disabled="busy" aria-label="移除图片" @click="removeImage(index)"><el-icon><Close /></el-icon></button></div><button v-if="images.length < 9" class="add-image" type="button" :disabled="busy" @click="fileInput?.click()"><el-icon><Picture /></el-icon><span>添加图片</span></button><input ref="fileInput" type="file" accept="image/jpeg,image/png,image/gif" multiple hidden @change="selectImages"></div>
        <div class="editor-actions"><span>请分享真实、友善的校园内容</span><div><el-button :disabled="busy" @click="preview = true">预览</el-button><el-button type="primary" :loading="busy" @click="publish">发布帖子</el-button></div></div>
      </section>
      <aside class="writing-note"><span class="eyebrow">A NOTE TO YOU</span><h2>你的日常，<br>也值得被看见。</h2><div class="note-line"></div><p>在这里提问、分享，找到和你一样热爱校园生活的人。</p><div class="note-item"><strong>01</strong><span>写清地点和细节，让分享更有用</span></div><div class="note-item"><strong>02</strong><span>友善交流，尊重彼此的不同</span></div><div class="note-item"><strong>03</strong><span>发布前检查个人信息和图片</span></div></aside>
    </div>
    <el-dialog v-model="preview" title="帖子预览" width="min(680px, 92vw)"><div class="preview-topic">{{topic || '未选择话题'}}</div><h2>{{title || '未填写标题'}}</h2><p class="preview-content">{{content || '还没有正文内容'}}</p><div class="preview-images"><img v-for="image in images" :key="image.preview" :src="image.preview" alt="帖子预览图片"></div></el-dialog>
  </div>
</template>

<style scoped>
.publish-page{max-width:1100px;margin:0 auto}.back{display:inline-block;color:#59715d;font-size:13px;margin:0 0 20px}.publish-layout{display:grid;grid-template-columns:minmax(0,1fr) 285px;gap:22px;align-items:start}.editor{background:#fff;padding:36px 40px}.editor-heading{display:flex;justify-content:space-between;padding-bottom:26px;border-bottom:1px solid #e9e9e1}.eyebrow{font-size:10px;letter-spacing:.13em;color:#899a88}.editor h1{font-size:30px;margin:12px 0 8px;color:#263a2d}.editor-heading p,.writing-note p{font-size:13px;color:#858d82;line-height:1.8;margin:0}.editor-number{font-size:35px;color:#a4b4a0}.field-label{display:flex;justify-content:space-between;margin:26px 0 11px;font-size:13px;font-weight:600;color:#36483a}.field-label span{font-size:11px;font-weight:400;color:#9aa196}.title-input,.content-input{display:block;width:100%;border:1px solid #e1e6de;background:#fbfcf9;padding:14px 15px;font:inherit;color:#28392c;outline:none;box-sizing:border-box;transition:border-color .2s}.title-input:focus,.content-input:focus{border-color:#77937b}.title-input{font-size:15px}.content-input{min-height:200px;resize:vertical;line-height:1.8;font-size:14px}.topics{display:flex;flex-wrap:wrap;gap:8px}.topics button{border:1px solid #dfe7de;background:#f8faf6;padding:8px 13px;font-size:12px;color:#6b7a6d;cursor:pointer}.topics button.active{background:#315744;color:#fff;border-color:#315744}.image-picker{display:flex;flex-wrap:wrap;gap:10px}.image-preview,.add-image{position:relative;width:92px;height:92px}.image-preview img{width:100%;height:100%;object-fit:cover}.image-preview button{position:absolute;right:3px;top:3px;background:#2f4037;color:#fff;border:0;width:22px;height:22px;cursor:pointer}.add-image{background:#f7f9f5;border:1px dashed #b9c9b9;color:#738875;display:flex;flex-direction:column;justify-content:center;align-items:center;gap:7px;font-size:11px;cursor:pointer}.add-image .el-icon{font-size:23px}.editor-actions{display:flex;justify-content:space-between;align-items:center;gap:15px;margin-top:34px;padding-top:22px;border-top:1px solid #e9e9e1}.editor-actions>span{font-size:11px;color:#9aa196}.editor-actions .el-button--primary{--el-color-primary:#315744}.writing-note{background:#e8ede5;padding:30px 26px;color:#365342}.writing-note h2{font-size:25px;line-height:1.5;font-weight:500;margin:17px 0}.note-line{width:34px;height:1px;background:#8fa48d;margin:22px 0}.writing-note p{color:#738678}.note-item{display:flex;gap:13px;margin-top:22px;color:#6d806f;font-size:12px;line-height:1.6}.note-item strong{font-size:10px;color:#9aab96}.login-prompt{margin-top:22px;background:#f0f4eb;padding:13px 15px;color:#5f7461;font-size:12px}.login-prompt button{border:0;background:none;color:#315744;cursor:pointer;margin-left:8px}.preview-topic{color:#66836b;font-size:12px}.preview-content{white-space:pre-wrap;line-height:1.8}.preview-images{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}.preview-images img{width:100%;aspect-ratio:1;object-fit:cover}@media(max-width:760px){.publish-layout{grid-template-columns:1fr}.editor{padding:24px 18px}.editor h1{font-size:25px}.writing-note{display:none}.editor-actions{align-items:flex-end}.editor-actions>span{max-width:110px}.image-preview,.add-image{width:76px;height:76px}}

.back{color:#315c4b}.editor{border:1px solid #e7e8e4;border-radius:14px}.editor-heading,.editor-actions{border-color:#eceeeb}.editor h1{color:#242825;letter-spacing:-.025em}.eyebrow{color:#78877c}.field-label{color:#34443a}.title-input,.content-input{background:#fff;border-color:#dce2dd;border-radius:8px}.title-input:focus,.content-input:focus{border-color:#668e79;box-shadow:0 0 0 2px #dfece2}.topics button{border-radius:20px;background:#fff;border-color:#dfe6e0;color:#617066}.topics button.active{background:#315c4b;border-color:#315c4b}.image-preview img,.add-image{border-radius:9px}.add-image{background:#f7faf7}.editor-actions .el-button--primary{--el-color-primary:#315c4b}.writing-note{background:#edf4ef;border-radius:14px;color:#315c4b}.login-prompt{background:#edf4ef;border-radius:8px}.login-prompt button{color:#315c4b}.preview-images img{border-radius:8px}
</style>
