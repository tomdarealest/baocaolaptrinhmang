@echo off
chcp 65001 >nul
echo ===================================================
echo     DANG KHOI DONG GIAO DIEN FTP CLIENT GUI...
echo ===================================================
if not exist "build\classes\Btapbaocao\FTPClientGUI.class" (
    echo [INFO] Dang bien dich ma nguon...
    javac -encoding UTF-8 -d build/classes src/Btapbaocao/*.java
)
start javaw -cp build/classes Btapbaocao.FTPClientGUI
