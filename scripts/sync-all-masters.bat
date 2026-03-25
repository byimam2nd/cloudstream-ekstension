@echo off
REM ========================================
REM Sync All Masters - Windows Batch Version
REM ========================================

setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "ROOT_DIR=%SCRIPT_DIR:~0,-1%"

echo ========================================
echo Sync All Master Files
echo ========================================
echo.

REM List of Master files to sync (OPTIMIZED v3.0)
set "MASTER_FILES[0]=MasterExtractors.kt:SyncExtractors.kt"
set "MASTER_FILES[1]=MasterUtils.kt:SyncUtils.kt"
set "MASTER_FILES[2]=MasterCaches.kt:SyncCaches.kt"
set "MASTER_FILES[3]=MasterMonitors.kt:SyncMonitors.kt"
set "MASTER_FILES[4]=MasterHttpClientFactory.kt:SyncHttpClientFactory.kt"
set "MASTER_FILES[5]=MasterCompiledRegexPatterns.kt:SyncCompiledRegexPatterns.kt"

set "MASTER_COUNT=6"

echo Master files to sync:
for /L %%i in (0,1,%MASTER_COUNT%) do (
    for /f "tokens=1,2 delims=:" %%a in ("!MASTER_FILES[%%i]!") do (
        echo    - %%a ^> %%b
    )
)
echo.

REM Find all modules with build.gradle.kts
echo Detecting active modules...
set "MODULES="
for %%d in ("%ROOT_DIR%\*") do (
    if exist "%%d\build.gradle.kts" (
        set "MODULE_NAME=%%~nxd"
        if not "!MODULE_NAME!"=="gradle" (
            set "MODULES=!MODULES! !MODULE_NAME!"
        )
    )
)

echo Found modules:%MODULES%
echo.

REM Sync to each module
for %%m in (%MODULES%) do (
    echo Processing %%m...
    
    set "MODULE_DIR=%ROOT_DIR%\%%m"
    set "SRC_DIR=!MODULE_DIR!\src\main\kotlin\com"
    
    if not exist "!SRC_DIR!" (
        echo    Error: Source directory not found: !SRC_DIR!
        goto :continue
    )
    
    REM Get folder name in com/
    for /d %%f in ("!SRC_DIR!\*") do set "FOLDER=%%~nxf"
    
    set "DEST_DIR=!SRC_DIR!\!FOLDER!\generated_sync"
    if not exist "!DEST_DIR!" mkdir "!DEST_DIR!"
    
    echo    Destination: !DEST_DIR!
    
    REM Copy each master file
    for /L %%i in (0,1,%MASTER_COUNT%) do (
        for /f "tokens=1,2 delims=:" %%a in ("!MASTER_FILES[%%i]!") do (
            set "MASTER_SOURCE=%ROOT_DIR%\master\%%a"
            set "DEST_FILE=!DEST_DIR!\%%b"
            
            if exist "!MASTER_SOURCE!" (
                REM Create header and replace package
                (
                    echo // ========================================
                    echo // AUTO-GENERATED - DO NOT EDIT MANUALLY
                    echo // Synced from master/%%a
                    echo // File: %%b
                    echo // ========================================
                    echo package com.!FOLDER!.generated_sync
                    echo.
                    findstr /v "^package " "!MASTER_SOURCE!" ^| findstr /v "^import master\."
                    
                    REM Add replaced imports
                    for /f "delims=" %%l in ('findstr /c:"import master\." "!MASTER_SOURCE!"') do (
                        set "IMPORT_LINE=%%l"
                        set "IMPORT_LINE=!IMPORT_LINE:import master.=import com.!FOLDER!.generated_sync.!"
                        echo !IMPORT_LINE!
                    )
                ) > "!DEST_FILE!"
                
                echo    Synced: %%b
            )
        )
    )
    
    echo.
    
    :continue
)

echo ========================================
echo Sync completed!
echo ========================================
