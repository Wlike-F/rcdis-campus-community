CREATE TABLE IF NOT EXISTS community_reward (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(255) NOT NULL,
    price INT UNSIGNED NOT NULL,
    stock INT UNSIGNED NOT NULL,
    active TINYINT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reward_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS community_redemption (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    reward_id BIGINT UNSIGNED NOT NULL,
    request_id VARCHAR(36) NOT NULL,
    cost INT UNSIGNED NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_redemption_request (user_id, request_id),
    UNIQUE KEY uk_redemption_reward (user_id, reward_id),
    KEY idx_redemption_user_time (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS community_profile_theme (
    user_id BIGINT UNSIGNED NOT NULL,
    theme_code VARCHAR(32) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO community_reward (code, name, description, price, stock) VALUES
('ginkgo', '银杏来信', '一抹银杏金，为你的校园主页留住秋日光影。', 50, 1000),
('spring', '春日新芽', '柔和的春日绿，让主页多一点生长的气息。', 100, 1000),
('night', '星夜自习室', '沉静的夜空色，陪伴每一个认真生活的夜晚。', 300, 1000);
