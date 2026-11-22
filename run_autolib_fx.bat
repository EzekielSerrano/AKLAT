@echo off
SET PROJ=c:\Users\JENNIFER CANTUJA\Downloads\FRONT-END-main\FRONT-END-main
SET JAVAFX=%PROJ%\lib\javafx-sdk-17\lib
SET PGJDBC=%PROJ%\lib\postgresql-42.7.4.jar
SET CAMLIBS=%PROJ%\lib\zxing-core-3.5.3.jar;%PROJ%\lib\zxing-javase-3.5.3.jar;%PROJ%\lib\webcam-capture-0.3.12.jar;%PROJ%\lib\bridj-0.7.0.jar;%PROJ%\lib\slf4j-api-1.7.36.jar;%PROJ%\lib\slf4j-simple-1.7.36.jar
SET JDK=C:\Program Files\Java\jdk-17\bin
SET OUT=%PROJ%\out

echo =========================================
echo  A.K.L.A.T. FX - Build and Run
echo  Automated Key Library Analytics & Tracking
echo =========================================

cd /d "%PROJ%"

if not exist "%OUT%" mkdir "%OUT%"

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

echo [2/2] Starting A.K.L.A.T. FX...
echo.

"%JDK%\java.exe" ^
  --module-path "%JAVAFX%" ^
  --add-modules javafx.controls,javafx.fxml ^
  -cp "%OUT%;%PGJDBC%;%CAMLIBS%" ^
  autolib.fx.AutoLibFX

pause
