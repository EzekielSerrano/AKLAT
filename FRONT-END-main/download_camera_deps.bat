@echo off
:: =====================================================================
::  A.K.L.A.T. — Download camera scanner dependencies
::  Run this ONCE from any folder with internet access.
::  Jars go into: FRONT-END-main\lib\
:: =====================================================================
SET PROJ=%~dp0
IF "%PROJ:~-1%"=="\" SET PROJ=%PROJ:~0,-1%
SET LIB=%PROJ%\lib

echo.
echo  Downloading ZXing Core (barcode decoder)...
curl.exe -L --fail --retry 3 -o "%LIB%\zxing-core-3.5.3.jar" "https://repo1.maven.org/maven2/com/google/zxing/core/3.5.3/core-3.5.3.jar"

echo.
echo  Downloading ZXing JavaSE (BufferedImage bridge)...
curl.exe -L --fail --retry 3 -o "%LIB%\zxing-javase-3.5.3.jar" "https://repo1.maven.org/maven2/com/google/zxing/javase/3.5.3/javase-3.5.3.jar"

echo.
echo  Downloading webcam-capture (Sarxos webcam library)...
curl.exe -L --fail --retry 3 -o "%LIB%\webcam-capture-0.3.12.jar" "https://repo1.maven.org/maven2/com/github/sarxos/webcam-capture/0.3.12/webcam-capture-0.3.12.jar"

echo.
echo  Downloading BridJ (native Windows camera driver)...
curl.exe -L --fail --retry 3 -o "%LIB%\bridj-0.7.0.jar" "https://repo1.maven.org/maven2/com/nativelibs4java/bridj/0.7.0/bridj-0.7.0.jar"

echo.
echo  Downloading SLF4J API (logging facade required by webcam-capture)...
curl.exe -L --fail --retry 3 -o "%LIB%\slf4j-api-1.7.36.jar" "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar"

echo.
echo  Downloading SLF4J Simple (logger implementation)...
curl.exe -L --fail --retry 3 -o "%LIB%\slf4j-simple-1.7.36.jar" "https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/1.7.36/slf4j-simple-1.7.36.jar"

echo.
echo  =========================================
echo   Done! Jars saved to:
echo   %LIB%
echo  =========================================
echo.
echo  Next: run  run_autolib_fx.bat  to build the app.
pause
