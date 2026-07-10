@rem Gradle startup script for Windows
@if "%DEBUG%"=="" @echo off
set JAVA_HOME=%JAVA_HOME%
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
"%JAVA_HOME%\bin\java.exe" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
