@echo off
echo 執行GA任務調度程式...
echo.

:: 檢查是否已編譯
if not exist bin\Main.class (
    echo 請先執行 compile.bat 編譯程式
    pause
    exit /b
)

:: 切換到程式目錄並執行
cd bin
java Main
cd ..

echo.
echo 程式執行完畢
pause 