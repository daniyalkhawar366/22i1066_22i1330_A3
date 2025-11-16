# Story Migration to PHP Backend - Complete Guide

## ✅ What Has Been Completed

### Backend (PHP)
1. **Created `stories.php` API endpoint** at `C:\xampp\htdocs\backend\api\stories.php`
   - `GET stories.php?action=getActive` - Get all active (non-expired) stories
   - `GET stories.php?action=getUserStories&userId={id}` - Get stories for specific user
   - `POST stories.php?action=upload` - Upload new story (requires JWT auth)

2. **Updated database schema** in `database.sql`
   - Added `close_friends_only` column to stories table

### Android App
1. **Created Story Response Models** (`StoryResponse.kt`)
   - `StoryResponse` - API response wrapper
   - `StoryItem` - Individual story data
   - `UploadStoryRequest` - Story upload request
   - `UploadStoryResponse` - Upload response

2. **Updated ApiService.kt**
   - Added `getActiveStories()` - Fetch all active stories
   - Added `getUserStories()` - Fetch user's stories
   - Added `uploadStory()` - Upload new story

3. **Migrated UploadStoryActivity.kt**
   - Removed Firebase Authentication
   - Uses SessionManager for authentication
   - Profile picture loads from PHP backend
   - Story saves to PHP backend via API

4. **Migrated FYPActivity.kt**
   - `fetchAndDisplayStories()` - Loads stories from PHP backend
   - `addCurrentUserStory()` - Shows current user's story circle with "+" icon
   - Profile picture in bottom navigation loads from backend

## 📋 Step-by-Step Testing Guide

### Step 1: Database Setup
```sql
-- Run this in phpMyAdmin or MySQL Workbench
USE socially_db;

-- Verify stories table exists with all columns
DESCRIBE stories;

-- Should show: id, user_id, image_url, uploaded_at, expires_at, close_friends_only
```

### Step 2: Start Backend Server
1. Open XAMPP Control Panel
2. Start **Apache** and **MySQL**
3. Verify backend is running:
   - Open browser: `http://192.168.18.55/backend/api/auth.php?action=test`
   - Should see: `{"success":true,"message":"API is working"}`

### Step 3: Test Backend Endpoints

#### Test 1: Get Active Stories (Empty Initially)
```
Method: GET
URL: http://192.168.18.55/backend/api/stories.php?action=getActive
Headers: Authorization: Bearer YOUR_JWT_TOKEN

Expected Response:
{
  "success": true,
  "stories": []
}
```

#### Test 2: Upload Story (after uploading from app)
```
Method: POST
URL: http://192.168.18.55/backend/api/stories.php?action=upload
Headers: 
  - Authorization: Bearer YOUR_JWT_TOKEN
  - Content-Type: application/json
Body:
{
  "imageUrl": "https://res.cloudinary.com/.../story.jpg",
  "closeFriendsOnly": false
}

Expected Response:
{
  "success": true,
  "storyId": "story_abc123...",
  "uploadedAt": 1731715200000,
  "expiresAt": 1731801600000
}
```

### Step 4: Build and Run Android App
1. In Android Studio, click **Build → Clean Project**
2. Click **Build → Rebuild Project**
3. Run the app on your device/emulator

### Step 5: Test Story Functionality

#### A. View "Add Story" Button on FYP
1. Open the app and login
2. On the For You Page, you should see:
   - **Your story circle** (first position) with a **"+" icon**
   - Your profile picture inside the circle
   - Your username below

#### B. Add a New Story
1. Click on your story circle (the one with "+" icon)
2. Should open `AddStoryActivity`
3. Choose to:
   - **Pick from gallery** (left button)
   - **Take photo** (center button)
4. After selecting image, it navigates to `UploadStoryActivity`
5. You'll see:
   - Your image preview
   - Your profile picture avatar (loaded from backend)
   - Two buttons: "Your stories" and "Close Friends"

#### C. Upload Story
1. Click **"Your stories"** button
2. Progress bar shows upload progress (0-100%)
3. After successful upload:
   - Shows "Story uploaded!" toast
   - Automatically navigates back to FYP
4. On FYP, your story circle now shows:
   - **Ring around profile picture** (indicates active story)
   - **No "+" icon** (replaced by ring)

#### D. View Stories on FYP
1. After uploading, refresh the FYP (swipe down)
2. You should see:
   - Your own story (first position) - with ring
   - Other users' stories (if they uploaded any) - with rings
3. Click on your story circle → Opens `MyStoryActivity`
4. Click on other user's story → Opens `OtherUserStoryActivity`

## 🔧 Troubleshooting

### Issue 1: Stories Not Showing
**Check:**
- Backend API is running (test endpoint)
- JWT token is valid (check SessionManager)
- Stories are not expired (check `expires_at` in database)
- Network connection is working

**Fix:**
```kotlin
// Check Logcat for errors:
// Tag: UploadStoryActivity or FYPActivity
// Look for: "Failed to load stories" or "Error loading stories"
```

### Issue 2: Profile Picture Not Loading
**Check:**
- User has `profile_pic_url` in database
- URL is accessible (test in browser)
- Glide library is included

**Database Check:**
```sql
SELECT id, username, profile_pic_url FROM users WHERE id = 'YOUR_USER_ID';
```

### Issue 3: Story Upload Fails
**Common Causes:**
1. **Cloudinary upload failed**
   - Check Cloudinary credentials in UploadStoryActivity
   - Verify internet connection
   
2. **Backend save failed**
   - Check Logcat for API error response
   - Verify JWT token is valid
   - Check database permissions

**Debug:**
```
// Logcat filters:
- Tag: UploadStoryActivity
- Look for: "Cloudinary error" or "Failed to save story"
```

### Issue 4: "Add Story" Button Not Visible
**Check:**
- `R.id.addstory` exists in `foryou.xml` layout
- View is not hidden or overlapped
- `initializeViews()` is called in onCreate

**Fix:**
```kotlin
// In FYPActivity.kt, verify:
addStoryBtn = findViewById(R.id.addstory)
addStoryBtn.setOnClickListener {
    startActivity(Intent(this, AddStoryActivity::class.java))
}
```

## 📊 Database Verification

### Check Stories in Database
```sql
-- View all active stories
SELECT s.*, u.username 
FROM stories s 
JOIN users u ON s.user_id = u.id 
WHERE s.expires_at > UNIX_TIMESTAMP() * 1000
ORDER BY s.uploaded_at DESC;

-- View stories for specific user
SELECT * FROM stories 
WHERE user_id = 'YOUR_USER_ID'
ORDER BY uploaded_at DESC;

-- Delete expired stories (optional cleanup)
DELETE FROM stories 
WHERE expires_at < UNIX_TIMESTAMP() * 1000;
```

## 🔐 API Authentication

All story endpoints (except getActive for public viewing) require JWT authentication:

```kotlin
// In your activity:
val sessionManager = SessionManager(this)
val token = "Bearer ${sessionManager.getAuthToken()}"

// Pass to API:
RetrofitClient.instance.getActiveStories(token)
```

## 📱 UI Flow Summary

```
FYPActivity (For You Page)
    ↓ Click "+" story circle
AddStoryActivity (Pick/Take Photo)
    ↓ Image selected
UploadStoryActivity (Preview & Upload)
    ↓ Upload to Cloudinary
    ↓ Save to PHP Backend
    ↓ Success
FYPActivity (Story now shows with ring)
```

## ✨ Key Features Working

1. ✅ **Add story button** shows on FYP (your story circle with "+" icon)
2. ✅ **Profile picture** loads from PHP backend in:
   - Story circles on FYP
   - Bottom navigation bar
   - Upload story preview
3. ✅ **Username** displays below story circle
4. ✅ **Story upload** saves to PHP backend (24-hour expiration)
5. ✅ **Active stories** load from PHP backend
6. ✅ **Close Friends** option available
7. ✅ **Story grouping** by user (one circle per user)

## 🚀 Next Steps

After verifying stories work correctly, you can migrate:
1. **Posts Feed** - Replace Firebase Firestore with PHP backend
2. **Comments** - Migrate to PHP backend
3. **Likes** - Migrate to PHP backend
4. **User Search** - Already using PHP backend
5. **Messaging** - Last major Firebase component

## 📝 Notes

- Stories automatically expire after 24 hours (handled by backend)
- Cloudinary is still used for image hosting (same as before)
- JWT tokens expire based on backend configuration
- Multiple stories per user are supported (shown as one circle)
- Close Friends feature is database-ready (UI implementation pending)

---

**Migration Status:** Stories ✅ Complete | Posts ⏳ Pending | Messaging ⏳ Pending

