@echo off
setlocal
cd /d "%~dp0"
where java >nul 2>&1
if errorlevel 1 goto missingjdk
where javac >nul 2>&1
if errorlevel 1 goto missingjdk
call mvnw.cmd -B -ntp verify
if errorlevel 1 goto failed
java -Djava.net.preferIPv4Stack=true -jar target/blog-0.0.1-SNAPSHOT.jar %*
if errorlevel 1 goto failed
exit /b 0

:missingjdk
echo Java 21 JDK is required. Install it and add its bin directory to PATH.
goto failed

:failed
echo Startup failed. Read the error above. Your data has not been reset.
pause
exit /b 1
