@echo off
chcp 65001 > nul
title FTP Client GUI - Báo Cáo Lập Trình Mạng
echo Đang khởi động FTP Client GUI...
java -cp build/classes Btapbaocao.FTPClientGUI
pause
