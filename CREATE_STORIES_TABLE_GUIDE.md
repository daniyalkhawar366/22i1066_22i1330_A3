# CRITICAL FIX: Create Stories Table

## The Problem
Your database is missing the `stories` table, which is why story uploads are failing with 500 errors.

## Solution: Create the Stories Table

### Method 1: Using phpMyAdmin (RECOMMENDED)

1. **Open phpMyAdmin:**
   - Go to: `http://localhost/phpmyadmin/`

2. **Select Database:**
   - Click on `socially_db` in the left sidebar

3. **Run SQL:**
   - Click on the "SQL" tab at the top
   - Copy and paste this SQL code:

```sql
CREATE TABLE `stories` (
    `id` VARCHAR(100) PRIMARY KEY,
    `user_id` VARCHAR(36) NOT NULL,
    `image_url` TEXT NOT NULL,
    `uploaded_at` BIGINT NOT NULL,
    `expires_at` BIGINT NOT NULL,
    `close_friends_only` TINYINT(1) DEFAULT 0,
    FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE,
    INDEX `idx_expires` (`user_id`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

4. **Click "Go" button** at the bottom right

5. **Verify:**
   - Click "socially_db" in left sidebar
   - You should now see `stories` table listed along with `chats`, `sessions`, `users`, and `messages`

### Method 2: Using SQL File (Alternative)

I've created a SQL file for you at:
`C:\xampp\htdocs\backend\create_stories_table.sql`

In phpMyAdmin:
1. Select `socially_db` database
2. Click "Import" tab
3. Choose the file: `create_stories_table.sql`
4. Click "Go"

## What This Table Does

- **id**: Unique story identifier
- **user_id**: Links to the user who created the story
- **image_url**: URL of the uploaded story image (from Cloudinary)
- **uploaded_at**: Timestamp when story was uploaded (milliseconds)
- **expires_at**: When story expires (24 hours after upload)
- **close_friends_only**: Whether story is for close friends only

## After Creating the Table

1. **Restart Apache** in XAMPP (optional but recommended)
2. **Test story upload:**
   - Open your app
   - Click camera button or your story circle
   - Take/select a photo
   - Upload story
   - Should now work without 500 error!

## Verify It Worked

Run this SQL query in phpMyAdmin to check:
```sql
SHOW TABLES FROM socially_db;
```

You should see:
- chats
- messages
- sessions
- **stories** ← NEW!
- users

---

**Once the table is created, story uploads will work perfectly!** 🎉

