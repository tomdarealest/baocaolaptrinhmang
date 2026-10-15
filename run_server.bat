@echo off
chcp 65001 >nul
echo ===================================================
echo     DANG KHOI DONG FTP SERVER (PORT 2121)...
echo ===================================================
if not exist "build\classes" (
    echo [INFO] Dang bien dich ma nguon...
    javac -encoding UTF-8 -d build/classes src/Btapbaocao/*.java
)
java -cp build/classes Btapbaocao.FTPServer
pause
