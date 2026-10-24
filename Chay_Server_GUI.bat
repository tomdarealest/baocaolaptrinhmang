@echo off
chcp 65001 > nul
title FTP Server GUI - Báo Cáo Lập Trình Mạng
echo Đang khởi động FTP Server GUI...
java -cp build/classes Btapbaocao.FTPServerGUI
pause
