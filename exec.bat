@echo off
setlocal enabledelayedexpansion

:: --- Configuration: Paths and Variables ---
set "CMD=%~1"
set "ANDROID_DIR=%~dp0android"
set "WEB_DIR=%~dp0web"
set "EMULATOR_DIR=%USERPROFILE%\AppData\Local\Android\Sdk\emulator"
set "AVD_DIR=%USERPROFILE%\.android\avd"

:: --- Configuration: Devices and Package ---
set "DEV_TV=192.168.0.102:5555"
set "DEV_EMU=emulator-5554"
set "APK=%ANDROID_DIR%\app\build\outputs\apk\debug\app-debug.apk"
set "PACKAGE=com.example.launcher"

:: --- Command Routing Block ---
if "%CMD%"=="web"            goto :cmd_web
if "%CMD%"=="upload"         goto :cmd_upload
if "%CMD%"=="test"           goto :cmd_test
if "%CMD%"=="emulator wiped" goto :cmd_emu_wiped
if "%CMD%"=="emulator"       goto :cmd_emu_normal

echo Unknown command: "%CMD%"
goto :exit


:: --- Command Handlers ---

:cmd_web
    pushd "%WEB_DIR%"
    python app.py
    popd
    goto :exit

:cmd_upload
    :: Clean generated build number resources before building
    set "BUILD_NUM_DIR=%ANDROID_DIR%\app\build\generated\res\buildnumber\values"
    if exist "%BUILD_NUM_DIR%" rmdir /s /q "%BUILD_NUM_DIR%"
    
    :: Always copy the latest configuration before running the build
    call :task_copy_config

    :: Trigger the Gradle build process
    pushd "%ANDROID_DIR%"
    call ".\gradlew.bat" preBuildRelease
    call ".\gradlew.bat" assembleDebug
    popd
    adb connect %DEV_TV%
    adb -s %DEV_TV% install -r "%APK%"
    goto :exit

:cmd_test
    call :task_copy_config
    
    :: Trigger the Gradle build process
    pushd "%ANDROID_DIR%"
    call ".\gradlew.bat" preBuildDebug
    call ".\gradlew.bat" assembleDebug
    popd

    adb -s %DEV_EMU% install -r "%APK%"
    adb -s %DEV_EMU% shell am start -n %PACKAGE%/.MainActivity
    @REM adb logcat
    goto :exit

:cmd_emu_wiped
    pushd "%AVD_DIR%"
    "%EMULATOR_DIR%\emulator.exe" -avd Television_720p -wipe-data
    popd
    goto :exit

:cmd_emu_normal
    pushd "%AVD_DIR%"
    "%EMULATOR_DIR%\emulator.exe" -avd Television_720p
    popd
    goto :exit


:: --- Subroutines (Internal Tasks) ---

:task_copy_config
    :: Copy the configuration JSON into the Android assets folder
    copy /Y "%WEB_DIR%\json\config.json" "%ANDROID_DIR%\app\src\main\assets\"
    exit /b


:exit 
exit /b

:: ==============================================================================
:: FFMPEG TEMPLATE FOR REFERENCE:
:: @echo off
:: set "SOURCE_VIDEO="
:: set "DEST_M3U8=video\output.m3u8"
:: ffmpeg -i %SOURCE_VIDEO% -map 0:v -map 0:a -c:v h264 -b:v 2000k -maxrate 2500k -bufsize 4000k -hls_time 10 -hls_playlist_type vod %DEST_M3U8%
:: ==============================================================================
