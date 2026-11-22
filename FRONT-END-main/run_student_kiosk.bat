@echo off
REM =========================================================
REM  A.K.L.A.T. — Student Kiosk (Standalone)
REM  Launches directly to the Student panel.
REM  No librarian login prompt. No staff access.
REM =========================================================

REM Use %~dp0 so this bat works from any folder (portable)
SET PROJ=%~dp0
IF "%PROJ:~-1%"=="\" SET PROJ=%PROJ:~0,-1%

SET JAVAFX=%PROJ%\lib\javafx-sdk-17\lib
SET PGJDBC=%PROJ%\lib\postgresql-42.7.4.jar
SET CAMLIBS=%PROJ%\lib\zxing-core-3.5.3.jar;%PROJ%\lib\zxing-javase-3.5.3.jar;%PROJ%\lib\webcam-capture-0.3.12.jar;%PROJ%\lib\bridj-0.7.0.jar;%PROJ%\lib\slf4j-api-1.7.36.jar;%PROJ%\lib\slf4j-simple-1.7.36.jar

REM Auto-detect JDK 17 from common install locations
SET JDK=C:\Program Files\Java\jdk-17\bin
IF NOT EXIST "%JDK%\javac.exe" SET JDK=C:\Program Files\Eclipse Adoptium\jdk-17.0.9.9-hotspot\bin
IF NOT EXIST "%JDK%\javac.exe" SET JDK=C:\Program Files\Microsoft\jdk-17.0.9.8-hotspot\bin
IF NOT EXIST "%JDK%\javac.exe" SET JDK=C:\Program Files\Java\jdk-17.0.9\bin
IF NOT EXIST "%JDK%\javac.exe" (
  echo [ERROR] JDK 17 not found. Please install JDK 17 from https://adoptium.net/
  pause
  exit /b 1
)

SET OUT=%PROJ%\out

cd /d "%PROJ%"

if not exist "%OUT%" mkdir "%OUT%"

REM Compile only if .class files are missing or outdated
if not exist "%OUT%\autolib\fx\AutoLibFX.class" goto compile
echo [INFO] Classes already compiled. Starting kiosk...
goto run

:compile
echo [1/2] Compiling A.K.L.A.T. JavaFX...

"%JDK%\javac.exe" ^
  --module-path "%JAVAFX%" ^
  --add-modules javafx.controls,javafx.fxml ^
  -cp "%PGJDBC%;%CAMLIBS%" ^
  -encoding UTF-8 ^
  -d "%OUT%" ^
  -sourcepath "." ^
  autolib\db\DBConnection.java ^
  autolib\fx\AutoLibFX.java ^
  autolib\fx\BatStateULogo.java ^
  autolib\fx\CameraBarcodeScannerFX.java ^
  autolib\fx\FXComponents.java ^
  autolib\fx\PortalScreenFX.java ^
  autolib\fx\LoginDialogFX.java ^
  autolib\fx\LibrarianPortalFX.java ^
  autolib\fx\StudentKioskFX.java ^
  autolib\fx\panels\DashboardFX.java ^
  autolib\fx\panels\BookCatalogFX.java ^
  autolib\fx\panels\StudentsFX.java ^
  autolib\fx\panels\CirculationFX.java ^
  autolib\fx\panels\TransactionsFX.java ^
  autolib\fx\panels\ReportsFX.java ^
  autolib\fx\panels\SettingsFX.java ^
  autolib\fx\kiosk\KioskDashboardFX.java ^
  autolib\fx\kiosk\KioskProfileCardFX.java ^
  autolib\fx\kiosk\KioskHistoryFX.java ^
  autolib\fx\kiosk\KioskProfileFX.java ^
  autolib\models\Book.java ^
  autolib\models\Student.java ^
  autolib\models\Transaction.java ^
  autolib\IsbnLookupService.java ^
  autolib\DataStore.java

IF %ERRORLEVEL% NEQ 0 (
  echo.
  echo [ERROR] Compilation FAILED. See errors above.
  pause
  exit /b 1
)

:run
echo [2/2] Starting A.K.L.A.T. Student Kiosk...
echo.

REM -Dautolib.kiosk=true tells AutoLibFX to skip the portal and go straight
REM to the Student Kiosk screen.
"%JDK%\java.exe" ^
  --module-path "%JAVAFX%" ^
  --add-modules javafx.controls,javafx.fxml ^
  -cp "%OUT%;%PGJDBC%;%CAMLIBS%" ^
  -Dautolib.kiosk=true ^
  autolib.fx.AutoLibFX

pause
