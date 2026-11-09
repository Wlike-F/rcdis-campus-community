export interface CityPlace {
  id: string
  name: string
  image: string
  source: string
  category: string
  address: string
  introduction: string
  highlights: string[]
  tips: string
}

export const cityPlaces: CityPlace[] = [
  { id: 'palace', name: '沈阳故宫博物院', image: '/city/palace.jpg', source: 'https://www.sypm.org.cn/', category: '历史文化', address: '沈河区沈阳路 171 号', introduction: '沈阳故宫是清代早期皇宫，如今作为博物院开放，是了解盛京历史与宫廷建筑的一站。', highlights: ['古建筑与宫廷历史', '适合安排半日参观', '中街商圈附近'], tips: '开放时间和预约方式以博物院当期公告为准。' },
  { id: 'mansion', name: '张学良旧居陈列馆', image: '/city/mansion.jpg', source: 'https://whly.ln.gov.cn/whly/wlzt/sjly/ajjq/2026031713582531639/index.shtml', category: '历史人文', address: '沈河区朝阳街少帅府巷 46 号', introduction: '张学良旧居位于沈阳方城一带，院落和建筑保存了东北近代历史的印记。', highlights: ['近代历史建筑', '院落与展览', '可与方城游览结合'], tips: '出行前请确认开放时间及预约要求。' },
  { id: 'qingnian', name: '青年公园', image: '/city/qingnian.jpg', source: 'https://www.amap.com/place/B00180JB3Q', category: '城市公园', address: '沈河区青年大街 111 号附近', introduction: '青年公园位于南运河沿线，水岸、树荫和步道为城市散步提供了舒展的空间。', highlights: ['湖畔散步', '临近青年大街', '适合休闲小憩'], tips: '公园设施与出入口信息请以现场为准。' },
  { id: 'nanhu', name: '南湖公园', image: '/city-guide.svg', source: 'https://zh.wikipedia.org/wiki/南湖公园_(沈阳)', category: '城市公园', address: '和平区文化路 1 号', introduction: '南湖公园是沈阳南运河带状公园的一部分，可以沿湖散步、感受城市绿地。', highlights: ['湖岸与绿地', '适合散步', '文化路沿线'], tips: '图片为城市插画，开放区域请以现场为准。' },
  { id: 'zhongjie', name: '中街', image: '/city/zhongjie.jpg', source: 'https://wlgd.shenyang.gov.cn/zwgk/fdzdgknr/jyta/rddbjy/202412/t20241201_4768506.html', category: '城市街区', address: '沈河区中街步行街', introduction: '中街是沈阳历史悠久的商业步行街，商铺与方城历史街巷相邻，适合边走边逛。', highlights: ['步行街与商圈', '方城历史街巷', '城市购物与美食'], tips: '节假日人流较多，具体店铺营业时间可能变化。' },
  { id: 'caita', name: '彩塔夜市', image: '/city-guide.svg', source: 'https://wlgd.shenyang.gov.cn/hdjl/zxft/202504/t20250416_4837575.html', category: '夜间生活', address: '沈河区彩电塔、青年公园附近', introduction: '彩塔夜市是沈阳具有代表性的夜间消费场景之一，适合感受城市的烟火气。', highlights: ['夜间街市', '小吃与散步', '彩电塔周边'], tips: '图片为城市插画，摊位与营业时间请以现场为准。' },
  { id: 'wuai', name: '五爱市场', image: '/city-guide.svg', source: 'https://wlgd.shenyang.gov.cn/zwgk/fdzdgknr/jyta/rddbjy/202412/t20241201_4768506.html', category: '城市商圈', address: '沈河区风雨坛街与热闹路附近', introduction: '五爱市场是沈阳知名商贸市场，周边汇集多种商品与城市生活服务。', highlights: ['传统商贸市场', '多品类购物', '沈河区城市生活'], tips: '图片为城市插画，各市场区域营业时间请提前核实。' },
]
