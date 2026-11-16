@echo off
title Build the Field Assistant APK
rem Run AFTER training has finished (the build needs memory). Needs Android Studio installed (it brings the JDK and Android SDK).
cd /d D:\OCDD\app\CropAdvisor
set "JAVA_HOME=%LOCALAPPDATA%\Programs\Android Studio\jbr"
if not exist "%JAVA_HOME%\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
call gradlew.bat assembleRelease
if not exist app\build\outputs\apk\release\app-release.apk (
  echo BUILD FAILED. Copy the red text above and send it to me.
  pause & exit /b 1
)
if not exist D:\OCDD\release mkdir D:\OCDD\release
copy /Y app\build\outputs\apk\release\app-release.apk D:\OCDD\release\FieldAssistant-1.0.apk >nul
echo.
echo DONE: D:\OCDD\release\FieldAssistant-1.0.apk
echo Upload it on GitHub: your repo - Releases - Draft a new release - attach this file.
pause
