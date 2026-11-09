#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
把 tb_shop 的经纬度从杭州整体迁到「我的位置」周边，并生成可直接执行的 SQL 与 GEO 重建命令。

坐标换算（局部平面近似，误差对 10km 内可忽略）：
  1 度纬度 ≈ 111.32 km
  1 度经度 ≈ 111.32 * cos(lat) km
"""
import math

# 我的位置（东经, 北纬）
MY_LON = 123.70256389816592
MY_LAT = 41.456947405181005

M_PER_DEG_LAT = 111320.0
M_PER_DEG_LON = 111320.0 * math.cos(math.radians(MY_LAT))

# (商铺id, 类型id, 距我的位置的直线距离米, 方位角：0=正北，顺时针, 新店名)
# 前 12 家落在 5km 检索半径内，后 2 家刻意放到半径外，用来验证过滤确实生效
#
# 店名：品牌保留，括号里的地点换成沈阳真实存在的商圈/地标。
# 但坐标是本脚本按「距离+方位角」环形生成的假数据，并不代表这家店真开在该商圈。
SHOPS = [
    (1,  1, 420,   15,  "101茶餐厅(中街店)"),
    (2,  1, 860,   72,  "蔡馬洪涛烤肉·老北京铜锅涮羊肉(太原街店)"),
    (3,  1, 1150,  130, "新白鹿餐厅(沈阳万象城店)"),
    (4,  1, 1580,  205, "Mamala(大悦城店)"),
    (5,  1, 1920,  290, "海底捞火锅(北行店)"),
    (6,  1, 2260,  340, "幸福里老北京涮锅(塔湾店)"),
    (7,  1, 2610,  45,  "炉鱼(铁西万象汇店)"),
    (8,  1, 2980,  160, "浅草屋寿司(三好街店)"),
    (9,  1, 3350,  250, "羊老三羊蝎子牛仔排北派炭火锅(奥体中心店)"),
    (10, 2, 3720,  100, "开乐迪KTV(浑南店)"),
    (11, 2, 4100,  20,  "INLOVE KTV(龙之梦大都汇店)"),
    (12, 2, 4620,  215, "魅(沈河店)"),
    (13, 2, 6800,  120, "讴K拉量贩KTV(沈北道义店)"),    # 超出 5km，应被过滤
    (14, 2, 9300,  300, "星聚会KTV(于洪广场店)"),       # 超出 5km，应被过滤
]


def offset(distance_m, bearing_deg):
    """距离 + 方位角 -> (经度增量, 纬度增量)"""
    theta = math.radians(bearing_deg)
    north = distance_m * math.cos(theta)
    east = distance_m * math.sin(theta)
    return east / M_PER_DEG_LON, north / M_PER_DEG_LAT


def haversine(lon1, lat1, lon2, lat2):
    """反向校验：用标准球面公式算回距离，确认落点没写歪"""
    r = 6371000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = p2 - p1
    dl = math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


rows = []
for sid, tid, dist, bearing, name in SHOPS:
    dl, db = offset(dist, bearing)
    lon = round(MY_LON + dl, 6)
    lat = round(MY_LAT + db, 6)
    actual = haversine(MY_LON, MY_LAT, lon, lat)
    rows.append((sid, tid, name, lon, lat, dist, actual))

# ---------- 1. 可复用的 SQL 迁移文件 ----------
lines = [
    "-- 把商铺坐标迁到「我的位置」周边（沈阳），并把店名里的杭州地点换成沈阳商圈",
    "-- 中心点: %.14f, %.14f" % (MY_LON, MY_LAT),
    "-- 由 scripts/relocate_geo.py 生成；前 12 家在 GEORADIUS 5000m 内，后 2 家在半径外",
    "",
    "-- 注意：tb_shop 的坐标只是数据源，Redis 里的 shop:geo:{typeId} 是检索用的 GEO 索引，",
    "-- 两者必须同步。执行完本 SQL 后还要重建 GEO 索引，否则接口仍按老位置返回：",
    "--   docker exec hmdp-redis7 redis-cli DEL shop:geo:1 shop:geo:2",
    "--   docker exec hmdp-redis7 redis-cli GEOADD shop:geo:1 <x> <y> <id> ...   （见脚本生成的 geo_rebuild.redis）",
    "--   或跑 HmDianPingApplicationTests#testLoadShopData 从数据库整批重建",
    "-- 另外要删掉缓存里坐标已失效的商铺：redis-cli DEL cache:shop:<id>",
    "START TRANSACTION;",
]
for sid, tid, name, lon, lat, dist, actual in rows:
    lines.append("UPDATE tb_shop SET x = %.6f, y = %.6f, name = '%s' WHERE id = %d;" % (lon, lat, name, sid))
lines.append("COMMIT;")
lines.append("")

with open("D:/hm/hm-dianping/src/main/resources/db/geo_relocate_shenyang.sql", "w", encoding="utf-8") as f:
    f.write("\n".join(lines))

# ---------- 2. GEO 重建命令（redis-cli 可直接管道输入）----------
geo = ["DEL shop:geo:1", "DEL shop:geo:2"]
for sid, tid, name, lon, lat, dist, actual in rows:
    geo.append("GEOADD shop:geo:%d %.6f %.6f %d" % (tid, lon, lat, sid))
# 检索半径内应有的成员数
inside = sum(1 for r in rows if r[5] <= 5000)
geo.append("# expect %d members within 5000m" % inside)

with open("C:/Users/19577/AppData/Local/Temp/geo_rebuild.redis", "w", encoding="utf-8") as f:
    f.write("\n".join(geo))

print("商铺总数=%d，5km 内=%d，5km 外=%d" % (len(rows), inside, len(rows) - inside))
print("%-4s %-6s %-11s %-12s %9s %9s %s" % ("id", "type", "lon", "lat", "目标m", "实测m", "新店名"))
for sid, tid, name, lon, lat, dist, actual in rows:
    flag = "" if dist <= 5000 else "  <- 超出半径"
    print("%-4d %-6d %-11.6f %-12.6f %9.0f %9.1f %s%s" % (sid, tid, lon, lat, dist, actual, name, flag))
