@REM
@REM Maven Wrapper Batch Script
@REM
@REM This script executes Maven or downloads it if not found.
@REM

@echo off
setlocal enabledelayedexpansion

if not "%MAVEN_SKIP_RC%" == "" goto skipRcPre
@REM check for pre script, once with legacy .bat ending and once with .cmd ending
if exist "%USERPROFILE%\mavenrc_pre.bat" (
    call "%USERPROFILE%\mavenrc_pre.bat" %*
)
if exist "%USERPROFILE%\mavenrc_pre.cmd" (
    call "%USERPROFILE%\mavenrc_pre.cmd" %*
)
:skipRcPre

@setlocal

set MAVEN_CMD_LINE_ARGS=%*

set MAVEN_PROJECTBASEDIR=%CD%

set WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar

set MAVEN_HOME=%MAVEN_PROJECTBASEDIR%\.mvn

if exist "%WRAPPER_JAR%" (
    echo Found Maven wrapper.
    set MAVEN_CMD=%MAVEN_HOME%\bin\mvn.cmd
) else (
    echo Maven wrapper not found. Using system Maven.
    for %%i in (mvn.cmd mvn.bat mvn) do (
        set "MAVEN_CMD=%%i"
        call "%%i" -v >nul 2>&1
        if !errorlevel! equ 0 goto foundMaven
    )
    echo Could not find Maven executable.
    exit /b 1
)

:foundMaven
%MAVEN_CMD% %MAVEN_CMD_LINE_ARGS%
exit /b %ERRORLEVEL%

