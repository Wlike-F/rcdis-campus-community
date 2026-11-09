import { ref } from 'vue'

export interface Campus { id: string; label: string; x: number; y: number; description: string }
export const campuses: Campus[] = [
  { id: 'benxi', label: '本溪南校区', x: 123.70256389816592, y: 41.456947405181005, description: '本溪高新区华佗大街 26 号（演示坐标）' },
  { id: 'shenyang', label: '沈阳校本部', x: 123.44611, y: 41.76944, description: '沈阳市沈河区文化路 103 号（校园近似坐标）' },
]
const saved = localStorage.getItem('rcdis-campus')
export const selectedCampus = ref<Campus>(campuses.find((campus) => campus.id === saved) || campuses.find((campus) => campus.id === 'shenyang')!)
export const currentLocation = ref({ x: selectedCampus.value.x, y: selectedCampus.value.y })
export const currentLocationLabel = ref(selectedCampus.value.description)
export function selectCampus(id: string): void {
  const campus = campuses.find((item) => item.id === id)
  if (!campus) throw new Error('Unknown campus')
  selectedCampus.value = campus
  currentLocation.value = { x: campus.x, y: campus.y }
  currentLocationLabel.value = campus.description
  localStorage.setItem('rcdis-campus', id)
}
export function setCurrentLocation(x: number, y: number): void {
  currentLocation.value = { x, y }
  const shenyang = campuses.find((campus) => campus.id === 'shenyang')!
  const latitudeDelta = (y - shenyang.y) * Math.PI / 180
  const longitudeDelta = (x - shenyang.x) * Math.PI / 180
  const latitude = y * Math.PI / 180
  const campusLatitude = shenyang.y * Math.PI / 180
  const a = Math.sin(latitudeDelta / 2) ** 2 + Math.cos(latitude) * Math.cos(campusLatitude) * Math.sin(longitudeDelta / 2) ** 2
  const distanceMeters = 12742000 * Math.asin(Math.min(1, Math.sqrt(a)))
  if (distanceMeters <= 800) {
    selectedCampus.value = shenyang
    localStorage.setItem('rcdis-campus', shenyang.id)
    currentLocationLabel.value = '当前位置：沈阳药科大学校本部附近'
  } else {
    currentLocationLabel.value = '当前位置（学校名称未确认）'
  }
}
