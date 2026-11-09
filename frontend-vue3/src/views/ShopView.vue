<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { communityApi } from '../services/api'
import type { Shop, Voucher } from '../types/api'
import { foodIllustration, isPublicPoi, shopSource } from '../services/shopSource'

const route = useRoute()
const shop = ref<Shop>()
const vouchers = ref<Voucher[]>([])
const error = ref('')
const voucherError = ref('')
async function loadVouchers(id: number): Promise<void> {
  voucherError.value = ''
  try { vouchers.value = await communityApi.vouchers(id) }
  catch { voucherError.value = '优惠券加载失败，请稍后重试。' }
}
async function load(): Promise<void> {
  error.value = ''
  const id = Number(route.params.id)
  if (!Number.isSafeInteger(id) || id <= 0) { error.value = '商户编号无效。'; return }
  try { shop.value = await communityApi.shop(id); if (!shop.value) { error.value = '商户不存在。'; return }; await loadVouchers(id) }
  catch { error.value = '无法加载商户详情，请稍后重试。' }
}
onMounted(load)
function picture(images: string): string { const value = images?.split(',')[0]; return value ? (value.startsWith('http') || value.startsWith('/imgs/') ? value : `/imgs/${value.replace(/^\//, '')}`) : '/city-guide.svg' }
</script>
<template><div class="detail"><RouterLink to="/nearby">← 返回附近</RouterLink><div v-if="error"><el-alert :title="error" type="error" :closable="false"/><el-button @click="load">重试</el-button></div><template v-else-if="shop"><img class="cover" :src="shop.images ? picture(shop.images) : foodIllustration(shop).image" :alt="shop.images ? shop.name : '示意图'" @error="($event.target as HTMLImageElement).src='/city-guide.svg'"><div class="detail-body"><p v-if="!shop.images" class="muted">{{ shop.typeId === 1 ? '菜品示意' : '城市插画' }} · 非本店实拍 <a v-if="foodIllustration(shop).source" :href="foodIllustration(shop).source" target="_blank" rel="noopener noreferrer">· {{ foodIllustration(shop).credit }}</a></p><p class="muted">校园生活 · 商户详情</p><h1>{{shop.name}}</h1><template v-if="isPublicPoi(shop)"><p>地址：{{shop.address}}</p><el-alert title="信息来自公开地图资料，坐标经过转换；营业状态和实际位置请出行前核实。" type="info" :closable="false"/><p><a :href="shopSource(shop)" target="_blank" rel="noopener noreferrer">查看数据来源 ↗</a></p></template><el-alert v-else title="当前为演示数据，地址、价格和评分尚未核实，请勿作为实际到店依据。" type="info" :closable="false"/><h2>商户优惠</h2><div v-if="voucherError"><el-alert :title="voucherError" type="warning" :closable="false"/><el-button @click="loadVouchers(shop.id)">重试</el-button></div><el-empty v-else-if="!vouchers.length" description="暂无优惠券"/><div v-for="voucher in vouchers" :key="voucher.id" class="voucher"><strong>{{voucher.title}}</strong><p>{{voucher.subTitle}}</p><span>演示价格 ¥{{(voucher.payValue / 100).toFixed(2)}}</span><p>{{voucher.rules}}</p></div></div></template><el-skeleton v-else animated :rows="6"/></div></template>
<style scoped>.detail{max-width:900px;margin:auto}.detail>a{display:block;color:#49634e;margin-bottom:20px}.cover{width:100%;height:320px;object-fit:cover;background:#e9ece3}.detail-body{background:#fff;padding:28px}.detail-body h1{font-size:30px}.detail-body p{color:#687068}.voucher{border:1px solid #e5e3db;padding:18px;margin:12px 0}.voucher span{color:#a85d37}@media(max-width:700px){.cover{height:220px}.detail-body{padding:20px}}
.detail>a{color:#315c4b}.cover{border-radius:14px 14px 0 0}.detail-body{border:1px solid #e7e8e4;border-top:0;border-radius:0 0 14px 14px;padding:30px}.detail-body h1{color:#242825;letter-spacing:-.025em}.detail-body p{color:#68746b}.voucher{border-color:#e3eae4;border-radius:10px;background:#fafcf9}.voucher span{color:#a85d37}@media(max-width:700px){.detail-body{padding:20px}}
</style>
