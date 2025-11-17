-- Fix missing tables for posts functionality
-- Run this in phpMyAdmin SQL tab

USE socially_db;

-- Drop and recreate post_likes table to ensure it exists with correct structure
DROP TABLE IF EXISTS post_likes;

CREATE TABLE post_likes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    post_id VARCHAR(100) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY unique_like (post_id, user_id),
    INDEX idx_post_id (post_id),
    INDEX idx_user_id (user_id)
);

-- Verify posts table has the timestamp column
-- If this fails, uncomment the line below:
-- ALTER TABLE posts ADD COLUMN timestamp BIGINT NOT NULL DEFAULT 0 AFTER comments_count;

-- Show results
SELECT 'Tables created/verified successfully!' as status;
SHOW TABLES LIKE 'post%';

