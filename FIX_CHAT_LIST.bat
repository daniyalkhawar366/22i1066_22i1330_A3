@echo off
echo ================================================
echo FIXING CHAT LIST - Copying Backend File
echo ================================================
echo.

set SOURCE="C:\Users\pc\AndroidStudioProjects\22i1066_B_Socially\backend\api\messages.php"
set DEST="C:\xampp\htdocs\backend\api\messages.php"

echo Source: %SOURCE%
echo Destination: %DEST%
echo.

if not exist %SOURCE% (
    echo [ERROR] Source file not found!
    pause
    exit /b 1
)

if not exist "C:\xampp\htdocs\backend\api\" (
    echo [ERROR] XAMPP backend folder not found!
    echo Please check if XAMPP is installed and folder exists.
    pause
    exit /b 1
)

echo Copying messages.php to XAMPP...
copy /Y %SOURCE% %DEST%

if %ERRORLEVEL% EQU 0 (
    echo [SUCCESS] Backend file copied!
    echo.

    echo Verifying file sizes...
    for %%A in (%SOURCE%) do set SOURCE_SIZE=%%~zA
    for %%A in (%DEST%) do set DEST_SIZE=%%~zA

    echo Source size: %SOURCE_SIZE% bytes
    echo Dest size:   %DEST_SIZE% bytes
    echo.

    if "%SOURCE_SIZE%"=="%DEST_SIZE%" (
        echo [SUCCESS] File sizes match - copy verified!
    ) else (
        echo [WARNING] File sizes don't match - copy may have failed!
    )

    echo.
    echo Chat list should now show latest messages correctly.
    echo.
) else (
    echo [ERROR] Failed to copy file!
    echo Error code: %ERRORLEVEL%
    echo.
    pause
    exit /b 1
)

echo ================================================
echo Database Setup Reminder
echo ================================================
echo.
echo Make sure your users table has last_seen column:
echo.
echo   ALTER TABLE users ADD COLUMN last_seen TIMESTAMP NULL;
echo   UPDATE users SET last_seen = NOW();
echo.
echo ================================================
echo.
echo Backend file updated! Now:
echo 1. Check file sizes above match
echo 2. Restart your app
echo 3. Test chat list
echo.
pause

