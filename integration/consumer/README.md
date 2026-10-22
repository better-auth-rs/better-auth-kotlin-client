# Release consumers

This independent Gradle build verifies the published core, Redis, and Android artifacts. Dependencies resolve from JitPack, Maven Central, and Google Maven. The build does not use source substitution or a local Maven repository.

Set `ANDROID_HOME` to an Android SDK with platform 37 and accepted licenses. Run from the repository root with JDK 17:

```sh
./gradlew --no-daemon -p integration/consumer -PsdkVersion=v0.2.0 ktlintCheck verifyConsumers
```

Use the exact JitPack tag or commit for `sdkVersion`. The core and Redis consumers run without network requests to an authentication server. The core consumer rejects Android and Redis dependencies. The Redis consumer rejects Android dependencies and requires the core client and Lettuce. The Android consumer assembles an APK with minimum API 24. The build retains the dependencies' license metadata.

Run `ktlintFormat` with the same arguments to format the consumer sources and build files. The repository's Gradle wrapper and development environment supply the toolchain.
