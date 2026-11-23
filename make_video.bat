@echo off
rem Usage: make_video.bat phone_recording.mp4   -> demo_1080p.mp4 (1920x1080, 16:9, MP4, max 2:00)
rem Needs ffmpeg (winget install Gyan.FFmpeg). Phone video is portrait, so it is placed on a 1080p 16:9 canvas with a blurred background.
if "%~1"=="" (echo give the recording file & exit /b 1)
ffmpeg -y -i "%~1" -t 120 -filter_complex "[0:v]split[a][b];[a]scale=1920:1080:force_original_aspect_ratio=increase,crop=1920:1080,boxblur=30[bg];[b]scale=-2:1080[fg];[bg][fg]overlay=(W-w)/2:0,format=yuv420p" -c:v libx264 -crf 20 -c:a aac -b:a 160k demo_1080p.mp4
echo done: demo_1080p.mp4
