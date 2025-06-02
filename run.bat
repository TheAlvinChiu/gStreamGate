@echo off

REM Build the application
call gradlew clean build

REM Run the application
java -jar build/libs/gstreamgate-0.0.1-SNAPSHOT.jar