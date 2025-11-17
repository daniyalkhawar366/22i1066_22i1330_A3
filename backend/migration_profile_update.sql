-- Apply this migration to update the database for PHP backend profile functionality
-- Run this SQL script on your MySQL database

USE socially_db;

-- Add missing profile fields if they don't exist
ALTER TABLE users
ADD COLUMN IF NOT EXISTS title VARCHAR(255) DEFAULT '' AFTER bio,
ADD COLUMN IF NOT EXISTS threads_username VARCHAR(100) DEFAULT '' AFTER title;

-- Create follows table for follow/unfollow functionality
CREATE TABLE IF NOT EXISTS follows (
    id INT AUTO_INCREMENT PRIMARY KEY,
    follower_id VARCHAR(36) NOT NULL,
    following_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (follower_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (following_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY unique_follow (follower_id, following_id),
    INDEX idx_follower (follower_id),
    INDEX idx_following (following_id)
);

-- Verify the changes
DESCRIBE users;
DESCRIBE follows;

SELECT 'Migration completed successfully!' AS Status;

