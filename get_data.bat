@echo off
REM Downloads IP102 pest data, unpacks everything (incl. nested zips), deletes zips, audits all raw datasets.
REM Only touches D:\OCDD\data\raw\ip102, D:\OCDD\data\raw\_dl and writes D:\OCDD\data\raw_audit.txt
cd /d D:\OCDD
python fetch_data.py
echo.
echo Exit code %ERRORLEVEL%   (0 = done)
pause
