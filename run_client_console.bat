@echo off
chcp 65001 >nul
echo ===================================================
echo     DANG KHOI DONG FTP CLIENT DONG LENH (CLI)...
echo ===================================================
if not exist "build\classes\Btapbaocao\FTPClient.class" (
    echo [INFO] Dang bien dich ma nguon...
    javac -encoding UTF-8 -d build/classes src/Btapbaocao/*.java
)
java -cp build/classes Btapbaocao.FTPClient
pause
