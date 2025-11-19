-- Fix incorrect post counts in the database
-- This script will synchronize the posts_count field with actual post counts

USE socially_db;

-- Update all users' posts_count to match actual number of posts
UPDATE users u
SET posts_count = (
    SELECT COUNT(*)
    FROM posts p
    WHERE p.user_id = u.id
);

-- Verify the update
SELECT u.id, u.username, u.posts_count,
       (SELECT COUNT(*) FROM posts WHERE user_id = u.id) as actual_count
FROM users u
WHERE u.posts_count > 0 OR (SELECT COUNT(*) FROM posts WHERE user_id = u.id) > 0;

SELECT 'Post counts synchronized successfully!' AS Status;

