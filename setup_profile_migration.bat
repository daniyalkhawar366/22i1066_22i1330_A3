@echo off
echo ====================================
echo Profile Migration Setup Script
echo ====================================
echo.

echo This script will help you migrate the profile functionality to PHP backend.
echo.

echo Step 1: Database Migration
echo ---------------------------
echo You need to run the SQL migration file on your MySQL database.
echo.
echo Option A - Using MySQL Command Line:
echo   mysql -u root -p socially_db ^< backend\migration_profile_update.sql
echo.
echo Option B - Using phpMyAdmin:
echo   1. Open phpMyAdmin in your browser
echo   2. Select 'socially_db' database
echo   3. Go to SQL tab
echo   4. Open and copy contents of: backend\migration_profile_update.sql
echo   5. Paste and click 'Go'
echo.

echo Step 2: Verify Backend URL
echo ---------------------------
echo Open: app\src\main\java\com\example\a22i1066_b_socially\RetrofitClient.kt
echo Make sure BASE_URL matches your PHP backend server URL
echo Current: http://192.168.18.55/backend/api/
echo.

echo Step 3: Test the App
echo ---------------------
echo 1. Build and run the app
echo 2. Login with your account
echo 3. Navigate to My Profile
echo 4. Try editing your profile
echo 5. Try viewing another user's profile
echo 6. Try following/unfollowing
echo.

echo For detailed information, see: PROFILE_MIGRATION_README.md
echo.

pause

