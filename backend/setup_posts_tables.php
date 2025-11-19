<?php
// Script to automatically create missing database tables
require_once __DIR__ . '/api/config.php';

echo "<pre>";
echo "Creating missing database tables...\n\n";

try {
    $db = getDB();

    // Create post_images table
    echo "Creating post_images table...\n";
    $sql = "CREATE TABLE IF NOT EXISTS `post_images` (
      `id` INT AUTO_INCREMENT PRIMARY KEY,
      `post_id` VARCHAR(255) NOT NULL,
      `image_url` TEXT NOT NULL,
      `image_order` INT DEFAULT 0,
      `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      INDEX `idx_post_id` (`post_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    $db->exec($sql);
    echo "✓ post_images table created\n\n";

    // Create post_likes table
    echo "Creating post_likes table...\n";
    $sql = "CREATE TABLE IF NOT EXISTS `post_likes` (
      `id` INT AUTO_INCREMENT PRIMARY KEY,
      `post_id` VARCHAR(255) NOT NULL,
      `user_id` VARCHAR(255) NOT NULL,
      `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      UNIQUE KEY `unique_like` (`post_id`, `user_id`),
      INDEX `idx_post_id` (`post_id`),
      INDEX `idx_user_id` (`user_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    $db->exec($sql);
    echo "✓ post_likes table created\n\n";

    // Create post_comments table
    echo "Creating post_comments table...\n";
    $sql = "CREATE TABLE IF NOT EXISTS `post_comments` (
      `id` VARCHAR(255) PRIMARY KEY,
      `post_id` VARCHAR(255) NOT NULL,
      `user_id` VARCHAR(255) NOT NULL,
      `text` TEXT NOT NULL,
      `timestamp` BIGINT NOT NULL,
      `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      INDEX `idx_post_id` (`post_id`),
      INDEX `idx_user_id` (`user_id`),
      INDEX `idx_timestamp` (`timestamp`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    $db->exec($sql);
    echo "✓ post_comments table created\n\n";

    // Update posts table - add missing columns if they don't exist
    echo "Updating posts table...\n";

    // Check existing columns
    $stmt = $db->query("SHOW COLUMNS FROM posts");
    $columns = $stmt->fetchAll(PDO::FETCH_COLUMN);

    if (!in_array('timestamp', $columns)) {
        $db->exec("ALTER TABLE `posts` ADD COLUMN `timestamp` BIGINT NOT NULL DEFAULT 0 AFTER `caption`");
        echo "✓ Added timestamp column\n";
    }

    if (!in_array('likes_count', $columns)) {
        $db->exec("ALTER TABLE `posts` ADD COLUMN `likes_count` INT DEFAULT 0 AFTER `timestamp`");
        echo "✓ Added likes_count column\n";
    }

    if (!in_array('comments_count', $columns)) {
        $db->exec("ALTER TABLE `posts` ADD COLUMN `comments_count` INT DEFAULT 0 AFTER `likes_count`");
        echo "✓ Added comments_count column\n";
    }

    // Add indexes
    try {
        $db->exec("ALTER TABLE `posts` ADD INDEX `idx_user_id` (`user_id`)");
        echo "✓ Added user_id index\n";
    } catch (PDOException $e) {
        if (strpos($e->getMessage(), 'Duplicate key name') === false) {
            throw $e;
        }
    }

    try {
        $db->exec("ALTER TABLE `posts` ADD INDEX `idx_timestamp` (`timestamp`)");
        echo "✓ Added timestamp index\n";
    } catch (PDOException $e) {
        if (strpos($e->getMessage(), 'Duplicate key name') === false) {
            throw $e;
        }
    }

    echo "\n";
    echo "========================================\n";
    echo "✓ ALL TABLES CREATED SUCCESSFULLY!\n";
    echo "========================================\n";
    echo "\nYou can now use your app. Try loading the feed again!\n";

} catch (PDOException $e) {
    echo "\n❌ ERROR: " . $e->getMessage() . "\n";
    echo "\nPlease check your database configuration.\n";
}

echo "</pre>";

