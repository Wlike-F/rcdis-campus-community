ALTER TABLE tb_blog ADD COLUMN community_visible TINYINT NOT NULL DEFAULT 1;
UPDATE tb_blog SET community_visible = 0
WHERE id IN (4, 5, 6, 7) AND user_id IN (1, 2) AND create_time < '2023-01-01';
CREATE INDEX idx_blog_community_time ON tb_blog (community_visible, create_time, id);
CREATE INDEX idx_blog_community_topic_time ON tb_blog (community_visible, topic, create_time, id);
