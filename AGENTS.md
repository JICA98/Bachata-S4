# Agent Instructions

## Android APK builds

Follow `documents/android-building.md` for Android runtime prerequisites and
build commands.

The Gradle build packages existing runtime assets but does not generate them.
Before `assembleDebug`, always build and package the runtime from the repository
root:

```bash
git submodule update --init --recursive --jobs 8
runtime/scripts/build-runtime-debian.sh
node runtime/tests/verify-runtime.mjs runtime/locks/components.lock.json
```

Then build the APK:

```bash
cd android/BachataS4
./gradlew test lintDebug assembleDebug
```

Before installing, verify the APK contains both managed-runtime assets:

```bash
unzip -l app/build/outputs/apk/debug/app-debug.apk \
  | grep -E 'assets/runtime/(manifest\.json|runtime\.zip)'
```

Do not install or publish an APK unless both entries are present. A successful
Gradle build alone does not prove that the managed runtime was packaged.
