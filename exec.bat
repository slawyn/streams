@echo off

set CMD=%1
set ANDROID_DIR=%~dp0%android
set WEB_DIR=%~dp0%web
set EMULATOR_DIR=%USERPROFILE%\AppData\Local\Android\Sdk\emulator
set AVD_DIR=%USERPROFILE%\.android\avd
set DEV_TV=192.168.0.102:5555
set DEV_EMU=emulator-5554
set APK=%ANDROID_DIR%\app\build\outputs\apk\debug\app-debug.apk
set PACKAGE=com.example.launcher

if "%CMD%"=="web" (
    pushd "%WEB_DIR%"
    python app.py
    popd
    exit /b
)
if "%CMD%"=="upload" (
    adb connect %DEV_TV%
    adb -s %DEV_TV% install -r %APK%
    exit /b
)
if "%CMD%"=="build" goto :build
if "%CMD%"=="test" (
    call :build
    adb -s %DEV_EMU% install -r %APK%
    adb -s %DEV_EMU% shell am start -n %PACKAGE%/.MainActivity
    @REM adb logcat
    exit /b
)
if "%CMD%"=="emulator wiped" (
    pushd "%AVD_DIR%"
    "%EMULATOR_DIR%\emulator.exe" -avd Television_720p -wipe-data
    popd
    exit /b
)
if "%CMD%"=="emulator" (
    pushd "%AVD_DIR%"
    "%EMULATOR_DIR%\emulator.exe" -avd Television_720p
    popd
    exit /b
)

:exit 
exit /b


:build
:: Copy .json
copy /Y "%WEB_DIR%\json\config.json" "%ANDROID_DIR%\app\src\main\assets\"

:: Build
rmdir /s /q "%ANDROID_DIR%\app\build\generated\res\buildnumber\values"
pushd "%ANDROID_DIR%"
call "%ANDROID_DIR%\gradlew.bat" preBuild
call "%ANDROID_DIR%\gradlew.bat" assembleDebug
popd
exit /b

@REM @echo off
@REM set SOURCE_VIDEO=
@REM set DEST_M3U8=video\output.m3u8
@REM ffmpeg -i %SOURCE_VIDEO% -map 0:v -map 0:a -c:v h264 -b:v 2000k -maxrate 2500k -bufsize 4000k -hls_time 10 -hls_playlist_type vod %DEST_M3U8%