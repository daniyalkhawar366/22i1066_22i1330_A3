# Posts Implementation Complete Guide

## Summary of Changes

This document outlines all changes made to implement the three requested features:

### 1. Stories Scrolling Fix ✓
**Problem**: Stories were fixed at the top and didn't scroll with the page.

**Solution**: Wrapped both stories and posts in a NestedScrollView within the SwipeRefreshLayout.

**Files Modified**:
- `app/src/main/res/layout/foryou.xml`: Changed layout structure to nest HorizontalScrollView and RecyclerView inside NestedScrollView

### 2. Auto-Login Implementation ✓
**Problem**: Users had to login every time they opened the app.

**Solution**: Added session check in MainActivity to automatically navigate to FYPActivity if user is already logged in.

**Files Modified**:
- `app/src/main/java/com/example/a22i1066_b_socially/MainActivity.kt`: Added SessionManager check in onCreate()

### 3. Posts Backend Implementation ✓
**Problem**: Posts were stored in Firebase/Cloudinary. Need to migrate to backend database.

**Solution**: Created complete backend API for posts with database tables and updated Android app to use the API.

#### Backend Changes:

**Files Created**:
- `api/posts.php`: Complete REST API for posts management

**Files Modified**:
- `database.sql`: Added tables for:
  - `posts`: Main posts table
  - `post_images`: Multiple images per post
  - `post_likes`: Like tracking
  - `post_comments`: Comments with user info

#### Android App Changes:

**Files Modified**:
- `app/src/main/java/com/example/a22i1066_b_socially/ApiService.kt`:
  - Added post API endpoints (create, getFeed, getUserPosts, toggleLike, addComment, getComments, deletePost)
  - Added data classes (CreatePostRequest, PostItem, ToggleLikeRequest, CommentItem, etc.)

- `app/src/main/java/com/example/a22i1066_b_socially/Post.kt`:
  - Changed timestamp from Timestamp to Long
  - Changed likesCount and commentsCount from Long to Int

- `app/src/main/java/com/example/a22i1066_b_socially/FYPActivity.kt`:
  - Replaced Firebase post loading with backend API call
  - Replaced Firebase like handling with backend API call
  - Cleaned up unused Firebase imports and methods

- `app/src/main/java/com/example/a22i1066_b_socially/AddPostDetailsActivity.kt`:
  - Replaced Firebase post creation with backend API call
  - Uses backend to store post data after Cloudinary upload

- `app/src/main/java/com/example/a22i1066_b_socially/CommentsActivity.kt`:
  - Replaced Firebase comment loading with backend API call
  - Replaced Firebase comment posting with backend API call

- `app/src/main/java/com/example/a22i1066_b_socially/PostAdapter.kt`:
  - Updated updatePost() to use Int instead of Long for likes count

## Features Implemented

### Posts Features:
✓ Multiple images per post
✓ Captions
✓ Like/Unlike functionality
✓ Comments with user info
✓ Post deletion
✓ User-specific post retrieval
✓ Feed generation
✓ Like count tracking
✓ Comment count tracking

## Database Schema

### posts table
- id (VARCHAR PRIMARY KEY)
- user_id (VARCHAR FOREIGN KEY)
- caption (TEXT)
- likes_count (INT)
- comments_count (INT)
- timestamp (BIGINT)

### post_images table
- id (AUTO INCREMENT)
- post_id (VARCHAR FOREIGN KEY)
- image_url (TEXT)
- image_order (INT)

### post_likes table
- id (AUTO INCREMENT)
- post_id (VARCHAR FOREIGN KEY)
- user_id (VARCHAR FOREIGN KEY)
- UNIQUE constraint on (post_id, user_id)

### post_comments table
- id (VARCHAR PRIMARY KEY)
- post_id (VARCHAR FOREIGN KEY)
- user_id (VARCHAR FOREIGN KEY)
- text (TEXT)
- timestamp (BIGINT)

## API Endpoints

### POST /api/posts.php?action=create
Creates a new post with images and caption.

### GET /api/posts.php?action=getFeed
Retrieves posts feed with pagination support.

### GET /api/posts.php?action=getUserPosts
Retrieves posts for a specific user.

### POST /api/posts.php?action=toggleLike
Toggles like status for a post.

### POST /api/posts.php?action=addComment
Adds a comment to a post.

### GET /api/posts.php?action=getComments
Retrieves all comments for a post.

### POST /api/posts.php?action=deletePost
Deletes a post (owner only).

## Next Steps

1. Run the SQL script to create the new database tables:
   ```sql
   mysql -u your_user -p socially_db < database.sql
   ```

2. Make sure the API files are deployed to your server.

3. Build and run the Android app.

4. Test all features:
   - Auto-login on app restart
   - Stories scrolling with page
   - Create post with multiple images
   - Like/unlike posts
   - Add comments
   - View posts on profile

## Notes

- Images are still uploaded to Cloudinary (as before)
- Post metadata is now stored in MySQL backend
- All post operations now go through the backend API
- Session management handles authentication
- The app maintains the same UI/UX as before

