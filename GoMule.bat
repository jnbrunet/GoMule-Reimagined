@echo off
rem Launches GoMule with the working directory set to this script's own folder.
rem Without this, Windows can launch a double-clicked .jar (or a shortcut without
rem "Start in" set) with the working directory pointing somewhere unrelated
rem (commonly C:\Windows\System32), and GoMule's data paths (d2111\, resources\)
rem are resolved relative to the working directory, so it would fail to find them.
rem Projects themselves no longer live here -- they're under %APPDATA%\GoMule-Reimagined --
rem so this is only about the data files now, not about creating a projects\ folder.
cd /d "%~dp0"
start "" javaw -jar GoMule.jar
