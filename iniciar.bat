@echo off
cd /d "%~dp0"
where javac >nul 2>nul
if errorlevel 1 (
 echo Java JDK 17 ou superior nao encontrado. Instale um JDK e tente novamente.
 pause
 exit /b 1
)
if not exist build mkdir build
javac -encoding UTF-8 -d build src\FazendaAmigao.java
if errorlevel 1 (echo Erro ao compilar o Java & pause & exit /b 1)
java -cp build FazendaAmigao
pause
