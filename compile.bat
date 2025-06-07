@echo off
echo 編譯Java GA任務調度程式...

:: 創建bin目錄
if not exist bin mkdir bin

:: 編譯所有Java檔案
javac -d bin src/*.java

if %errorlevel% == 0 (
    echo 編譯成功！
    echo 執行檔已放置在 bin/ 目錄中
) else (
    echo 編譯失敗，請檢查程式碼錯誤
)

pause 