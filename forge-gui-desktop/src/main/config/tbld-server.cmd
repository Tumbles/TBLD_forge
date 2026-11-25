@echo off

pushd %~dp0

java -version 1>nul 2>nul || (
   echo no java installed
   popd
   pause
   exit /b 2
)
for /f tokens^=2^ delims^=.-_^+^" %%j in ('java -fullversion 2^>^&1') do set "jver=%%j"

if %jver% LEQ 16 (
   echo unsupported java
   popd
   pause
   exit /b 2
)

java $mandatory.java.args$ -jar $project.build.finalName$ tbld %*
popd
pause
