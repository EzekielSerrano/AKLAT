@echo off
title AKLAT Student Mobile API Server (Port 3000)
cd /d "%~dp0..\..\aklat_student_app\backend"
echo ===================================================
echo   A.K.L.A.T. - Student Mobile API Server
echo   Running on: http://192.168.2.2:3000
echo   Keep this window open while students use the app.
echo ===================================================
echo.
npm start
pause
