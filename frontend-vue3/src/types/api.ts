export interface ApiResult<T> {
  success: boolean
  errorMsg: string | null
  data: T
  total: number | null
}

export interface ShopType {
  id: number
  name: string
  icon: string
}

export interface Shop {
  id: number
  name: string
  typeId: number
  images: string
  area: string
  address: string
  avgPrice: number
  score: number
  comments: number
  distance?: number
  openHours?: string
}

export interface Blog {
  id: number
  title: string
  images: string
  liked: number
  name: string
  icon: string
  isLike: boolean
  content?: string
  topic?: string
  comments?: number
  createTime?: string
}

export interface CreateBlog { title: string; content: string; topic: string; images: string }

export interface Voucher { id: number; title: string; subTitle?: string; rules?: string; payValue: number; actualValue: number; type: number; stock?: number; beginTime?: string; endTime?: string }
export interface User { id: number; nickName: string; icon?: string }

export interface PointsRecord { action: 'SIGN' | 'POST' | 'REDEEM'; points: number; businessDate: string; description: string }
export interface PointsSummary { points: number; growth: number; theme: string; signedToday: boolean; postedToday: boolean; records: PointsRecord[] }
export interface Reward { id: number; code: string; name: string; description: string; price: number; stock: number; owned: number | boolean }
export interface Redemption { id: number; code: string; name: string; description: string; cost: number; createdAt: string }

export interface AiMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface AiChatRequest {
  conversationId: string
  location: { x: number; y: number }
  messages: AiMessage[]
}
