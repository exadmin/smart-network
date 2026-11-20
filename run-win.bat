@echo off
setlocal
set "JAVA_HOME=C:\Development\Java\jdk-24.0.2"
set "JAVAFX_HOME=C:\Development\Java\javafx-sdk-25.0.1"
set "APP_JAR=target\smart-network-1.0.0.jar"

pushd "%~dp0" || exit /b 1
if not exist "%JAVA_HOME%\bin\javaw.exe" (
    echo JDK not found at "%JAVA_HOME%".
    goto failed
)
if not exist "%JAVAFX_HOME%\lib\javafx.controls.jar" (
    echo JavaFX SDK not found at "%JAVAFX_HOME%".
    goto failed
)

if not exist "%APP_JAR%" (
    where mvn.cmd >nul 2>&1
    if errorlevel 1 (
        if not exist "C:\Development\Java\apache-maven-3.9.12\bin\mvn.cmd" (
            echo Maven not found. Add mvn.cmd to PATH.
            goto failed
        )
        call "C:\Development\Java\apache-maven-3.9.12\bin\mvn.cmd" -B package
    ) else (
        call mvn.cmd -B package
    )
    if errorlevel 1 goto failed
)

start "" "%JAVA_HOME%\bin\javaw.exe" --enable-native-access=javafx.graphics --module-path "%JAVAFX_HOME%\lib" --add-modules javafx.controls -jar "%APP_JAR%"
if errorlevel 1 goto failed

popd
exit /b 0

:failed
echo Smart Network could not start.
pause
popd
exit /b 1
