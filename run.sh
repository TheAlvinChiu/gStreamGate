#!/bin/bash

# Build the application
./gradlew clean build

# Run the application
java -jar build/libs/gstreamgate-0.0.1-SNAPSHOT.jar