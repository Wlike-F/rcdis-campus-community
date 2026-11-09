-- 把商铺坐标迁到「我的位置」周边（沈阳），并把店名里的杭州地点换成沈阳商圈
-- 中心点: 123.70256389816592, 41.45694740518100
-- 由 scripts/relocate_geo.py 生成；前 12 家在 GEORADIUS 5000m 内，后 2 家在半径外

-- 注意：tb_shop 的坐标只是数据源，Redis 里的 shop:geo:{typeId} 是检索用的 GEO 索引，
-- 两者必须同步。执行完本 SQL 后还要重建 GEO 索引，否则接口仍按老位置返回：
--   docker exec hmdp-redis7 redis-cli DEL shop:geo:1 shop:geo:2
--   docker exec hmdp-redis7 redis-cli GEOADD shop:geo:1 <x> <y> <id> ...   （见脚本生成的 geo_rebuild.redis）
--   或跑 HmDianPingApplicationTests#testLoadShopData 从数据库整批重建
-- 另外要删掉缓存里坐标已失效的商铺：redis-cli DEL cache:shop:<id>
START TRANSACTION;
UPDATE tb_shop SET x = 123.703867, y = 41.460592, name = '101茶餐厅(中街店)' WHERE id = 1;
UPDATE tb_shop SET x = 123.712368, y = 41.459335, name = '蔡馬洪涛烤肉·老北京铜锅涮羊肉(太原街店)' WHERE id = 2;
UPDATE tb_shop SET x = 123.713123, y = 41.450307, name = '新白鹿餐厅(沈阳万象城店)' WHERE id = 3;
UPDATE tb_shop SET x = 123.694560, y = 41.444084, name = 'Mamala(大悦城店)' WHERE id = 4;
UPDATE tb_shop SET x = 123.680938, y = 41.462846, name = '海底捞火锅(北行店)' WHERE id = 5;
UPDATE tb_shop SET x = 123.693299, y = 41.476025, name = '幸福里老北京涮锅(塔湾店)' WHERE id = 6;
UPDATE tb_shop SET x = 123.724685, y = 41.473526, name = '炉鱼(铁西万象汇店)' WHERE id = 7;
UPDATE tb_shop SET x = 123.714780, y = 41.431792, name = '浅草屋寿司(三好街店)' WHERE id = 8;
UPDATE tb_shop SET x = 123.664832, y = 41.446655, name = '羊老三羊蝎子牛仔排北派炭火锅(奥体中心店)' WHERE id = 9;
UPDATE tb_shop SET x = 123.746475, y = 41.451145, name = '开乐迪KTV(浑南店)' WHERE id = 10;
UPDATE tb_shop SET x = 123.719372, y = 41.491557, name = 'INLOVE KTV(龙之梦大都汇店)' WHERE id = 11;
UPDATE tb_shop SET x = 123.670801, y = 41.422951, name = '魅(沈河店)' WHERE id = 12;
UPDATE tb_shop SET x = 123.773150, y = 41.426405, name = '讴K拉量贩KTV(沈北道义店)' WHERE id = 13;
UPDATE tb_shop SET x = 123.606026, y = 41.498719, name = '星聚会KTV(于洪广场店)' WHERE id = 14;
COMMIT;
