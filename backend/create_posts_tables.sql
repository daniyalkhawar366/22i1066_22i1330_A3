-- Create missing tables for posts functionality

-- Table for post images
CREATE TABLE IF NOT EXISTS `post_images` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `post_id` VARCHAR(255) NOT NULL,
  `image_url` TEXT NOT NULL,
  `image_order` INT DEFAULT 0,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table for post likes
CREATE TABLE IF NOT EXISTS `post_likes` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `post_id` VARCHAR(255) NOT NULL,
  `user_id` VARCHAR(255) NOT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `unique_like` (`post_id`, `user_id`),
  INDEX `idx_post_id` (`post_id`),
  INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table for post comments
CREATE TABLE IF NOT EXISTS `post_comments` (
  `id` VARCHAR(255) PRIMARY KEY,
  `post_id` VARCHAR(255) NOT NULL,
  `user_id` VARCHAR(255) NOT NULL,
  `text` TEXT NOT NULL,
  `timestamp` BIGINT NOT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_post_id` (`post_id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_timestamp` (`timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Check if posts table exists and has correct columns
-- Add timestamp column if it doesn't exist
ALTER TABLE `posts`
ADD COLUMN IF NOT EXISTS `timestamp` BIGINT NOT NULL DEFAULT 0 AFTER `caption`,
ADD COLUMN IF NOT EXISTS `likes_count` INT DEFAULT 0 AFTER `timestamp`,
ADD COLUMN IF NOT EXISTS `comments_count` INT DEFAULT 0 AFTER `likes_count`;

-- Add indexes for better performance
ALTER TABLE `posts`
ADD INDEX IF NOT EXISTS `idx_user_id` (`user_id`),
ADD INDEX IF NOT EXISTS `idx_timestamp` (`timestamp`);

-- Display success message
SELECT 'All tables created successfully!' as message;

