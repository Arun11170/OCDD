@echo off
rem Phone connected by USB, USB debugging on. Copies the test photos to Pictures/CropSamples.
set "ADB=adb"
where adb >nul 2>nul
if errorlevel 1 (
  if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
  if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
  if exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" set "ADB=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
)
"%ADB%" version >nul 2>nul
if errorlevel 1 (
  echo adb not found. In Android Studio: Settings ^> Languages ^& Frameworks ^> Android SDK, copy the "Android SDK Location",
  echo then edit this file: set ADB=^<that folder^>\platform-tools\adb.exe
  pause
  exit /b 1
)
"%ADB%" devices
"%ADB%" shell mkdir -p /sdcard/Pictures/CropSamples
for %%f in ("%~dp0*.jpg") do (
  "%ADB%" push "%%f" /sdcard/Pictures/CropSamples/
  "%ADB%" shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/CropSamples/%%~nxf >nul
)
echo Done. Open the app, tap Gallery, then the CropSamples folder.
pause
