export const communityTopics = ['校园问答', '周边探店', '闲置互助', '活动搭子', '随便聊聊'] as const

export function blogImage(path: string): string {
  if (/^https?:\/\//.test(path) || path.startsWith('/imgs/') || path.startsWith('/city/') || path.startsWith('/food/') || path === '/campus-gate.jpg' || path === '/city-guide.svg') return path
  return `/imgs/${path.replace(/^\//, '')}`
}

export function blogText(content: string): string {
  return content.replace(/<br\s*\/?\s*>/gi, '\n').replace(/<[^>]*>/g, '')
}

export function blogDate(value: string | undefined): string {
  return value ? value.replace('T', ' ').slice(0, 16) : ''
}
