ALTER TABLE tb_blog ADD COLUMN topic VARCHAR(32) DEFAULT NULL AFTER title;
CREATE INDEX idx_blog_topic_time ON tb_blog (topic, create_time, id);
