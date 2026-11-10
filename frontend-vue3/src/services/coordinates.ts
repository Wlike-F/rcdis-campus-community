export function wgs84ToGcj02(longitude: number, latitude: number): [number, number] {
  if (longitude < 72.004 || longitude > 137.8347 || latitude < 0.8293 || latitude > 55.8271) return [longitude, latitude]
  const x = longitude - 105
  const y = latitude - 35
  const pi = Math.PI
  let latitudeOffset = -100 + 2 * x + 3 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
  latitudeOffset += (20 * Math.sin(6 * x * pi) + 20 * Math.sin(2 * x * pi)) * 2 / 3
  latitudeOffset += (20 * Math.sin(y * pi) + 40 * Math.sin(y / 3 * pi)) * 2 / 3
  latitudeOffset += (160 * Math.sin(y / 12 * pi) + 320 * Math.sin(y * pi / 30)) * 2 / 3
  let longitudeOffset = 300 + x + 2 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
  longitudeOffset += (20 * Math.sin(6 * x * pi) + 20 * Math.sin(2 * x * pi)) * 2 / 3
  longitudeOffset += (20 * Math.sin(x * pi) + 40 * Math.sin(x / 3 * pi)) * 2 / 3
  longitudeOffset += (150 * Math.sin(x / 12 * pi) + 300 * Math.sin(x / 30 * pi)) * 2 / 3
  const rad = latitude / 180 * pi
  const magic = 1 - 0.006693421622965943 * Math.sin(rad) ** 2
  const root = Math.sqrt(magic)
  const convertedLatitude = latitude + latitudeOffset * 180 / ((6378245 * (1 - 0.006693421622965943)) / (magic * root) * pi)
  const convertedLongitude = longitude + longitudeOffset * 180 / (6378245 / root * Math.cos(rad) * pi)
  return [convertedLongitude, convertedLatitude]
}
