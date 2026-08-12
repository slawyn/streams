@echo off

set CMD=%1
set APK=%CD%\android\app\build\outputs\apk\debug\app-debug.apk
set WEB_CONFIG_JSON=%CD%\web\json\config.json
set ANDROID=%CD%\android
set APP_CONFIG_JSON=%ANDROID%\app\src\main\assets\
set GRADLEW=%ANDROID%\gradlew.bat
set EMULATOR=%USERPROFILE%\AppData\Local\Android\Sdk\emulator
set AVD=%USERPROFILE%\.android\avd
set DEVICE=192.168.0.101:5555
if "%CMD%"=="web" (
    cd web
    python app.py
    exit /b
)

if "%CMD%"=="upload" (
    adb connect %DEVICE%
    adb -s %DEVICE% install -r %APK%
    exit /b
)

if "%CMD%"=="test" (
    copy %WEB_CONFIG_JSON% %APP_CONFIG_JSON%
    pushd %ANDROID%
    %GRADLEW% preBuild
    %GRADLEW% assembleDebug
    popd
    adb -s emulator-5554 install -r %APK%
    adb -s emulator-5554 shell am start -n com.example.launcher/.MainActivity
    exit /b
)

if "%CMD%"=="build" (
    copy %WEB_CONFIG_JSON% %APP_CONFIG_JSON%
    pushd %ANDROID%
    %GRADLEW% preBuild
    %GRADLEW% assembleDebug
    popd
    exit /b
)

if "%CMD%"=="emulator" (
    pushd %AVD%
    %EMULATOR%\emulator -avd Television_720p -wipe-data
    popd
)

@REM @echo off
@REM set SOURCE_VIDEO=
@REM set DEST_M3U8=video\output.m3u8
@REM ffmpeg -i %SOURCE_VIDEO% -map 0:v -map 0:a -c:v h264 -b:v 2000k -maxrate 2500k -bufsize 4000k -hls_time 10 -hls_playlist_type vod %DEST_M3U8%