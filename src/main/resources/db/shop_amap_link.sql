CREATE TABLE IF NOT EXISTS shop_amap_link (
  shop_id BIGINT UNSIGNED NOT NULL,
  amap_poi_id VARCHAR(32) NOT NULL,
  amap_name VARCHAR(255) NOT NULL,
  amap_address VARCHAR(255) NOT NULL,
  amap_longitude DECIMAL(10,6) NOT NULL,
  amap_latitude DECIMAL(10,6) NOT NULL,
  coordinate_system VARCHAR(16) NOT NULL,
  match_basis VARCHAR(255) NOT NULL,
  checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (shop_id),
  UNIQUE KEY uk_shop_amap_poi (amap_poi_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
