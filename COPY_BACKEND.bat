@echo off
echo ================================================
echo Copying Backend Files to XAMPP
echo ================================================
echo.

echo Copying messages.php...
copy /Y "C:\Users\pc\AndroidStudioProjects\22i1066_B_Socially\backend\api\messages.php" "C:\xampp\htdocs\backend\api\messages.php"
if %ERRORLEVEL% EQU 0 (
    echo [SUCCESS] messages.php copied!
) else (
    echo [ERROR] Failed to copy messages.php!
    pause
    exit /b 1
)

echo.
echo ================================================
echo Checking Database for last_seen Column
echo ================================================
echo.
echo IMPORTANT: You need to add last_seen column to users table!
echo.
echo Run this SQL command in phpMyAdmin or MySQL:
echo.
echo ALTER TABLE users ADD COLUMN last_seen TIMESTAMP NULL;
echo UPDATE users SET last_seen = NOW();
echo.
echo ================================================
echo.

pause

