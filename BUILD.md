# StopTime build note

This archive is the Android source project. It intentionally does not bundle the large Gradle distribution itself.

Use Android Studio with JDK 17, then open this `StopTime` directory. Android Studio can use the project's Gradle configuration and download the matching Gradle/Android components when needed.

The verified toolchain configuration in this project is:
- Android Gradle Plugin 9.4.0
- Gradle 9.6
- compileSdk 37
- targetSdk 37
- minSdk 23
- Java 17 source/target compatibility
