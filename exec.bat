@echo off
cd server
python app.py

@REM @echo off
@REM set SOURCE_VIDEO=
@REM set DEST_M3U8=video\output.m3u8
@REM ffmpeg -i %SOURCE_VIDEO% -map 0:v -map 0:a -c:v h264 -b:v 2000k -maxrate 2500k -bufsize 4000k -hls_time 10 -hls_playlist_type vod %DEST_M3U8%