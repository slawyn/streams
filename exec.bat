@echo off

set CMD=%1
set APK=%CD%\android\app\build\outputs\apk\debug\app-debug.apk
set WEB_CONFIG_JSON=%CD%\web\json\config.json
set APP_CONFIG_JSON=%CD%\android\app\src\main\assets\

if "%CMD%"=="web" (
    cd web
    python app.py
    exit /b
)

if "%CMD%"=="upload" (
    adb connect 192.168.0.103:5555
    adb install -r %APK%
    exit /b
)

if "%CMD%"=="copy" (
    copy %WEB_CONFIG_JSON% %APP_CONFIG_JSON%
    exit /b
)


@REM @echo off
@REM set SOURCE_VIDEO=
@REM set DEST_M3U8=video\output.m3u8
@REM ffmpeg -i %SOURCE_VIDEO% -map 0:v -map 0:a -c:v h264 -b:v 2000k -maxrate 2500k -bufsize 4000k -hls_time 10 -hls_playlist_type vod %DEST_M3U8%