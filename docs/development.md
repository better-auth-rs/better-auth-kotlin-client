# Development and releases

`devenv` provides JDK 17, Gradle, Node 24, Redis, Valkey, and Actionlint. Configure Android SDK platform 37 and build tools 37.0.0 through `ANDROID_HOME` or an ignored `local.properties`. An emulator or connected device is required for instrumented tests.

```sh
devenv shell
npm --prefix integration ci
./gradlew ktlintFormat
npm --prefix integration run format
check
npm --prefix integration test
REDIS_URL=redis://localhost:6379 VALKEY_URL=redis://localhost:6380 ./gradlew :redis:redisIntegrationTest
./gradlew :android:connectedDebugAndroidTest
```

`check` runs workflow lint, Kotlin lint, unit tests, Android lint, and the endpoint inventory verifier. The verifier compares 118 expected method/path contracts with HTTP requests observed in passing tests. The inventory is a coverage index, not a claim of exhaustive argument or server compatibility testing.

The Node fixture starts Better Auth 1.7.12 with an in-memory SQLite database and all supported plugin families. Verification messages stay in memory; the fixture does not send email or SMS. The fixture binds to loopback, supplies fresh test credentials, runs four integration flows, and closes the server after Gradle exits. Missing test configuration fails rather than reporting a skipped pass.

Redis integration tests require a reachable `REDIS_URL`. Set `VALKEY_URL` to verify the same operations on Valkey. Service failures fail the tests. Android instrumented tests exercise the actual Keystore and DataStore on the device.

## Release

All three modules share the version in `gradle.properties`. Work directly on `master`.

1. Update the version and documentation.
2. Run all relevant checks and inspect the publication artifacts.
3. Commit and push `master`.
4. Wait for both CI jobs to pass.
5. Tag the release commit with `vX.Y.Z` and push the tag.

Tag CI repeats the checks, builds all JitPack modules, validates their POMs and archive contents, compiles independent consumers, and creates the GitHub Release. No additional publishing secret is required for this public repository.

Published tags are immutable. Inspect a failed JitPack build log before retrying. A code or build configuration correction requires a new commit and version after publication. Commit SHA builds can verify JitPack before creating a release tag.

The `integration/consumer` build resolves published JitPack coordinates without `mavenLocal()`. Release CI runs `./gradlew -p integration/consumer -PsdkVersion=vX.Y.Z ktlintCheck verifyConsumers`. This catches transitive dependencies and Android manifest requirements that library compilation alone cannot verify.
