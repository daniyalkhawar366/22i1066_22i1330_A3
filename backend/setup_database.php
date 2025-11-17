<?php
// Auto-setup script to create missing database tables
require_once __DIR__ . '/config/database.php';

$database = new Database();
$db = $database->getConnection();

echo "<!DOCTYPE html><html><head><title>Database Setup</title></head><body>";
echo "<h1>Database Setup for Socially App</h1>";
echo "<pre>";

// Check if post_likes table exists
$result = $db->query("SHOW TABLES LIKE 'post_likes'");
if ($result->num_rows == 0) {
    echo "❌ post_likes table does not exist. Creating...\n";

    $sql = "CREATE TABLE post_likes (
        id INT AUTO_INCREMENT PRIMARY KEY,
        post_id VARCHAR(100) NOT NULL,
        user_id VARCHAR(36) NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
        UNIQUE KEY unique_like (post_id, user_id),
        INDEX idx_post_id (post_id),
        INDEX idx_user_id (user_id)
    )";

    if ($db->query($sql)) {
        echo "✅ post_likes table created successfully!\n";
    } else {
        echo "❌ Error creating post_likes: " . $db->error . "\n";
    }
} else {
    echo "✅ post_likes table already exists\n";
}

// Check if post_comments table exists
$result = $db->query("SHOW TABLES LIKE 'post_comments'");
if ($result->num_rows == 0) {
    echo "❌ post_comments table does not exist. Creating...\n";

    $sql = "CREATE TABLE post_comments (
        id VARCHAR(100) PRIMARY KEY,
        post_id VARCHAR(100) NOT NULL,
        user_id VARCHAR(36) NOT NULL,
        text TEXT NOT NULL,
        timestamp BIGINT NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
        INDEX idx_post_timestamp (post_id, timestamp)
    )";

    if ($db->query($sql)) {
        echo "✅ post_comments table created successfully!\n";
    } else {
        echo "❌ Error creating post_comments: " . $db->error . "\n";
    }
} else {
    echo "✅ post_comments table already exists\n";
}

// Check if posts table has timestamp column
$result = $db->query("SHOW COLUMNS FROM posts LIKE 'timestamp'");
if ($result->num_rows == 0) {
    echo "❌ posts table missing timestamp column. Adding...\n";

    $sql = "ALTER TABLE posts ADD COLUMN timestamp BIGINT NOT NULL DEFAULT 0 AFTER comments_count";

    if ($db->query($sql)) {
        echo "✅ timestamp column added to posts table!\n";
    } else {
        echo "❌ Error adding timestamp column: " . $db->error . "\n";
    }
} else {
    echo "✅ posts table has timestamp column\n";
}

// Show all post-related tables
echo "\n--- Current Post Tables ---\n";
$result = $db->query("SHOW TABLES LIKE 'post%'");
while ($row = $result->fetch_array()) {
    echo "✓ " . $row[0] . "\n";
}

// Show sample data counts
echo "\n--- Data Counts ---\n";
$result = $db->query("SELECT COUNT(*) as count FROM posts");
$row = $result->fetch_assoc();
echo "Posts: " . $row['count'] . "\n";

$result = $db->query("SELECT COUNT(*) as count FROM post_images");
$row = $result->fetch_assoc();
echo "Post Images: " . $row['count'] . "\n";

if ($db->query("SHOW TABLES LIKE 'post_likes'")->num_rows > 0) {
    $result = $db->query("SELECT COUNT(*) as count FROM post_likes");
    $row = $result->fetch_assoc();
    echo "Post Likes: " . $row['count'] . "\n";
}

echo "\n✅ Database setup complete!\n";
echo "You can now close this page and try loading posts in your app.\n";
echo "</pre></body></html>";
?>

