import type { Shop } from '../types/api'
import categoryPois from '../data/category-pois.json'

const sourcePages: Record<string, number> = {
  '椒盐水煮鱼': 150,
  '川汇老娃子火锅': 120,
  '小二狗肉馆': 240,
  '福锅记柴火铁锅炖': 990,
  '倪氏快餐': 960,
  '美味快餐': 900,
  '慧龙炭火烤肉店': 510,
  '锦绣江南西门烤串': 630,
  '香乐美食楼': 450,
  '肯德基': 990,
  '永和大王': 240,
  '蜀城春川味小吃楼': 330,
  '华莱士·全鸡汉堡': 690,
  '老倪肉串': 390,
  '娘仨卷饼': 900,
  '小红帽串吧': 900,
  '天天肥牛': 90,
  '小帽串店': 840,
  '沈家小厨': 810,
  '必胜客': 420,
}

export function isPublicPoi(shop: Shop): boolean {
  return shop.area === '公开POI·沈阳药科大学周边' && (shop.name in sourcePages || categoryPois.some((item) => item.name === shop.name && item.address === shop.address && item.typeId === shop.typeId))
}

export function shopSource(shop: Shop): string {
  const categoryPoi = categoryPois.find((item) => item.name === shop.name && item.address === shop.address && item.typeId === shop.typeId)
  if (categoryPoi) return categoryPoi.source
  const offset = sourcePages[shop.name]
  if (offset === undefined) throw new Error(`Missing source for shop: ${shop.name}`)
  return `https://www.poilist.cn/poi-list-%E7%BE%8E%E9%A3%9F-%E6%B2%88%E9%98%B3/${offset}/`
}

export function foodIllustration(shop: Shop): { image: string; source: string; credit: string } {
  if (shop.typeId !== 1) return { image: '/city-guide.svg', source: '', credit: '' }
  if (shop.name.includes('水煮鱼')) return { image: '/food/fish.jpg', source: 'https://commons.wikimedia.org/?curid=127972836', credit: 'N509FZ · CC BY-SA 4.0' }
  if (shop.name.includes('炖') || shop.name.includes('火锅') || shop.name.includes('肥牛')) return { image: '/food/stew.jpg', source: 'https://commons.wikimedia.org/?curid=159304337', credit: 'N509FZ · CC BY-SA 4.0' }
  if (shop.name.includes('串') || shop.name.includes('烤肉')) return { image: '/food/skewers.jpg', source: 'https://commons.wikimedia.org/?curid=6974076', credit: 'Kilroy238 · CC BY-SA 3.0' }
  return { image: '/food/meal.jpg', source: 'https://commons.wikimedia.org/?curid=86636125', credit: 'Pauloleong2002 · CC BY-SA 4.0' }
}
