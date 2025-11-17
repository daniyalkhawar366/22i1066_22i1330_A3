# Profile Functionality Migration to PHP Backend

## Overview
This migration converts the My Profile functionality from Firebase Firestore to PHP backend, ensuring all features work exactly the same way.

## Changes Made

### Backend Changes

#### 1. Enhanced `backend/api/user.php`
Added complete profile functionality including:
- **GET profile** - Retrieve complete user profile with all fields
  - Returns: id, username, displayName, firstName, lastName, profilePicUrl, bio, title, threadsUsername, website, email, followersCount, followingCount, postsCount, isFollowing
  
- **POST follow** - Follow a user
  - Endpoint: `user.php?action=follow`
  - Updates follower/following counts
  
- **POST unfollow** - Unfollow a user
  - Endpoint: `user.php?action=unfollow`
  - Updates follower/following counts
  
- **GET checkFollow** - Check if current user follows another user
  - Endpoint: `user.php?action=checkFollow`
  
- **POST updateProfile** - Update user profile
  - Endpoint: `user.php?action=updateProfile`
  - Supports updating: displayName, firstName, lastName, bio, title, threadsUsername, website, profilePicUrl

#### 2. Database Schema Updates
Created migration file: `backend/migration_profile_update.sql`
- Added `title` field to users table
- Added `threads_username` field to users table
- Created `follows` table for managing follow relationships
  - Tracks follower_id and following_id
  - Prevents duplicate follows with unique constraint
  - Properly indexed for performance

### Android App Changes

#### 1. EditProfileActivity.kt
**Migrated from Firebase to PHP Backend:**
- Replaced Firestore data loading with API call to `getUserProfile()`
- Replaced Firestore updates with API call to `updateProfile()`
- Still uses Cloudinary for image uploads (as before)
- Maintains exact same UI/UX behavior

**Key Changes:**
- Added `loadProfileFromBackend()` method using Retrofit
- Replaced `performFirestoreUpdate()` with `performBackendUpdate()` using PHP API
- Added proper error handling and loading states
- Uses SessionManager for authentication token

#### 2. MyProfileActivity.kt
**Already Using PHP Backend:**
- ✅ Profile loading via `getUserProfile()` API
- ✅ Posts loading via `getUserPosts()` API
- ⚠️ Still uses Firebase Firestore for highlights (as intended)
- Properly displays all profile fields

#### 3. ProfileActivity.kt
**Already Using PHP Backend:**
- ✅ Profile loading via `getUserProfile()` API
- ✅ Follow/unfollow via `followUser()`/`unfollowUser()` API
- ✅ Posts loading via `getUserPosts()` API
- ⚠️ Still uses Firebase Realtime Database for online status (as intended)
- ⚠️ Still uses Firebase Firestore for highlights (as intended)
- ⚠️ Still uses Firebase Firestore for follow notifications (as intended)

## Database Migration Steps

### Step 1: Backup Your Database
```sql
mysqldump -u root -p socially_db > socially_db_backup.sql
```

### Step 2: Apply the Migration
Run the migration SQL file:
```bash
mysql -u root -p socially_db < backend/migration_profile_update.sql
```

Or using phpMyAdmin:
1. Open phpMyAdmin
2. Select `socially_db` database
3. Go to SQL tab
4. Copy and paste contents of `migration_profile_update.sql`
5. Click "Go" to execute

### Step 3: Verify Migration
```sql
USE socially_db;
DESCRIBE users;
DESCRIBE follows;
```

You should see:
- `title` and `threads_username` columns in users table
- `follows` table with follower_id, following_id columns

## Features Working with PHP Backend

### ✅ Fully Migrated to PHP Backend:
1. **View My Profile**
   - Display name, username, bio, title, website
   - Profile picture
   - Posts count, followers count, following count
   - User's posts grid

2. **Edit Profile**
   - Update display name (first name + last name)
   - Update username
   - Update website
   - Update bio
   - Change profile picture (via Cloudinary)
   - All changes saved to PHP backend database

3. **View Other User Profiles**
   - All profile information
   - Follow/Unfollow functionality
   - Follow status tracking
   - Follower/Following counts update in real-time

4. **Follow System**
   - Follow users
   - Unfollow users
   - Check follow status
   - Update counts automatically
   - Prevent self-follow
   - Prevent duplicate follows

### ⚠️ Still Using Firebase (By Design):
1. **Highlights** - Still stored in Firestore (to be migrated later if needed)
2. **Online Status** - Still uses Firebase Realtime Database for presence
3. **Follow Notifications** - Still creates notification requests in Firestore

## API Endpoints Used

### Profile Endpoints
```
GET  user.php?action=profile&userId={id}&currentUserId={currentId}
GET  user.php?action=checkFollow&targetUserId={id}
POST user.php?action=follow         (Body: {targetUserId: string})
POST user.php?action=unfollow       (Body: {targetUserId: string})
POST user.php?action=updateProfile  (Body: UpdateProfileRequest)
```

### Posts Endpoints
```
GET posts.php?action=getUserPosts&userId={id}
```

## Testing Checklist

### My Profile (MyProfileActivity)
- [ ] Profile loads correctly with all fields
- [ ] Profile picture displays correctly
- [ ] Counts display correctly (posts, followers, following)
- [ ] Pull to refresh works
- [ ] Click Edit Profile opens EditProfileActivity
- [ ] Posts grid displays user's posts
- [ ] Click on post opens PostDetailActivity

### Edit Profile (EditProfileActivity)
- [ ] Current profile data loads correctly
- [ ] Can edit display name
- [ ] Can edit username
- [ ] Can edit website
- [ ] Can edit bio
- [ ] Can change profile picture
- [ ] Changes save successfully
- [ ] Success message displays
- [ ] Returns to previous screen after save
- [ ] Profile updates are immediately visible

### View Other Profile (ProfileActivity)
- [ ] Profile loads correctly
- [ ] Follow button shows correct state
- [ ] Can follow user
- [ ] Can unfollow user
- [ ] Follower count updates after follow/unfollow
- [ ] Cannot follow yourself
- [ ] Posts grid displays user's posts
- [ ] Message button opens chat
- [ ] Online indicator works (if user is online)

## Troubleshooting

### Profile Not Loading
1. Check backend URL in RetrofitClient.kt matches your server
2. Verify database has all required fields (run migration)
3. Check PHP error logs: `backend/php_errors.log`
4. Check Android Logcat for network errors

### Profile Update Fails
1. Verify authentication token is valid
2. Check that user is logged in
3. Verify database connection in PHP
4. Check that all fields in UpdateProfileRequest are optional

### Follow/Unfollow Not Working
1. Verify `follows` table exists in database
2. Check that foreign keys are properly set up
3. Verify authentication token in request header
4. Check PHP error logs for SQL errors

## Notes

- Profile pictures are still uploaded to Cloudinary (not stored in PHP backend)
- Authentication still uses Firebase Auth for getting user ID
- Session tokens are managed via SessionManager
- All profile data is now stored in MySQL database
- Highlights remain in Firebase Firestore for now
- Online presence remains in Firebase Realtime Database

## Next Steps (Optional Future Enhancements)

1. Migrate highlights to PHP backend
2. Create PHP-based presence system to replace Firebase Realtime DB
3. Create PHP-based notification system to replace Firebase Firestore
4. Add profile picture upload to PHP backend (optional alternative to Cloudinary)
5. Add username availability check during edit
6. Add profile view count tracking
7. Add last active timestamp

