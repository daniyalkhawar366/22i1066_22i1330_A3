@echo off
echo ================================================
echo Checking PHP Error Log
echo ================================================
echo.

set LOG_FILE=C:\xampp\apache\logs\error.log

if not exist "%LOG_FILE%" (
    echo Error log not found at: %LOG_FILE%
    echo.
    echo Try these locations:
    echo - C:\xampp\php\logs\php_error_log
    echo - C:\xampp\htdocs\backend\php_errors.log
    echo.
    pause
    exit /b 1
)

echo Last 50 lines related to getChatList:
echo ================================================
echo.

powershell -Command "Get-Content '%LOG_FILE%' -Tail 100 | Select-String -Pattern 'getChatList|Found.*chat_ids|Chat.*other user'"

echo.
echo ================================================
echo.
pause

