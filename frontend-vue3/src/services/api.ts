import axios from 'axios'
import type { ApiResult, AiChatRequest, Blog, CreateBlog, PointsSummary, Redemption, Reward, Shop, ShopType, User, Voucher } from '../types/api'

export const http = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL || '/api', timeout: 15000 })
http.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('token')
  if (token) config.headers.authorization = token
  return config
})

http.interceptors.response.use((response) => {
  const result = response.data as ApiResult<unknown>
  if (result && result.success === false) return Promise.reject(new Error(result.errorMsg || '请求失败'))
  return response
}, (error: unknown) => {
  if (axios.isAxiosError(error) && error.response?.status === 413) {
    return Promise.reject(new Error('图片过大，请选择 5MB 以内的图片'))
  }
  if (axios.isAxiosError(error) && error.response?.status === 401) {
    const hadToken = Boolean(sessionStorage.getItem('token'))
    sessionStorage.removeItem('token')
    if (hadToken) window.dispatchEvent(new Event('rcdis:auth'))
    return Promise.reject(new Error('登录已过期，请重新登录'))
  }
  return Promise.reject(error)
})

export interface CommunityComment { id: number; name: string; content: string; createTime: string }

export const communityApi = {
  async comments(blogId: number, current: number): Promise<{ items: CommunityComment[]; total: number }> {
    const response = await http.get<ApiResult<CommunityComment[]>>(`/blog-comments/list/${blogId}`, { params: { current } })
    return { items: response.data.data || [], total: response.data.total ?? 0 }
  },
  async createComment(blogId: number, content: string): Promise<void> { await http.post('/blog-comments', { blogId, content }) },
  async vouchers(id: number): Promise<Voucher[]> { return (await http.get<ApiResult<Voucher[]>>(`/voucher/list/${id}`)).data.data || [] },
  async blog(id: number): Promise<Blog> { return (await http.get<ApiResult<Blog>>(`/blog/${id}`)).data.data },
  async me(): Promise<User> { return (await http.get<ApiResult<User>>('/user/me')).data.data },
  async myBlogs(): Promise<Blog[]> { return (await http.get<ApiResult<Blog[]>>('/blog/of/me', { params: { current: 1 } })).data.data || [] },
  async sign(): Promise<PointsSummary> { return (await http.post<ApiResult<PointsSummary>>('/user/sign')).data.data },
  async points(): Promise<PointsSummary> { return (await http.get<ApiResult<PointsSummary>>('/user/points')).data.data },
  async rewards(): Promise<Reward[]> { return (await http.get<ApiResult<Reward[]>>('/rewards')).data.data || [] },
  async redemptions(): Promise<Redemption[]> { return (await http.get<ApiResult<Redemption[]>>('/rewards/mine')).data.data || [] },
  async redeem(rewardId: number, requestId: string): Promise<void> { await http.post('/rewards/redeem', { rewardId, requestId }) },
  async useTheme(code: string): Promise<void> { await http.post('/rewards/theme', { code }) },
  async signCount(): Promise<number> { return (await http.get<ApiResult<number>>('/user/sign/count')).data.data },
  async logout(): Promise<void> { await http.post('/user/logout') },
  async shopTypes(): Promise<ShopType[]> {
    const response = await http.get<ApiResult<ShopType[]>>('/shop-type/list')
    return response.data.data || []
  },
  async hotBlogs(current: number): Promise<Blog[]> {
    const response = await http.get<ApiResult<Blog[]>>('/blog/hot', { params: { current } })
    return response.data.data || []
  },
  async communityBlogs(current: number, topic: string): Promise<{ items: Blog[]; total: number }> {
    const response = await http.get<ApiResult<Blog[]>>('/blog/community', { params: topic ? { current, topic } : { current } })
    return { items: response.data.data || [], total: response.data.total ?? 0 }
  },
  async uploadBlogImage(file: File): Promise<string> {
    const form = new FormData()
    form.append('file', file)
    return (await http.post<ApiResult<string>>('/upload/blog', form)).data.data
  },
  async createBlog(blog: CreateBlog): Promise<number> {
    return (await http.post<ApiResult<number>>('/blog', blog)).data.data
  },
  async nearbyShops(typeId: number, current: number, x: number, y: number): Promise<Shop[]> {
    const response = await http.get<ApiResult<Shop[]>>('/shop/of/type', { params: { typeId, current, x, y, radius: 8000 } })
    return response.data.data || []
  },
  async searchShops(name: string): Promise<Shop[]> {
    const response = await http.get<ApiResult<Shop[]>>('/shop/of/name', { params: { name, current: 1 } })
    return response.data.data || []
  },
  async shop(id: number): Promise<Shop> {
    const response = await http.get<ApiResult<Shop>>(`/shop/${id}`)
    return response.data.data
  },
  async chat(body: AiChatRequest): Promise<string> {
    const response = await http.post<ApiResult<{ content: string }>>('/ai/chat', body, { timeout: 60000 })
    return response.data.data.content
  },
}
