CREATE TABLE IF NOT EXISTS community_points_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    action VARCHAR(16) NOT NULL,
    business_date DATE NOT NULL,
    points INT UNSIGNED NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_points_daily (user_id, action, business_date),
    KEY idx_points_user_time (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
