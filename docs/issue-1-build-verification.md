# Issue #1 — Build `assembleDebug`, Module Structure & `.gitignore` Verification

## Project Information

- **Project:** ChiliLeaf AI — Chili Leaf Disease Classification
- **Issue:** #1 — `[Build] assembleDebug sukses + struktur modul + .gitignore`
- **Audit Round 2 Fix Date:** 28 October 2026
- **Platform:** Native Android Application (Kotlin, AGP 9.1.1, Gradle 9.3.1)

## Root Cause (Reopened Audit Round 2)

During the Round 2 production audit (22 October 2026), Issue #1 was reopened because:
1. `git ls-files -s android-app/gradlew` still reported file mode `100644` (non-executable) instead of `100755`, causing `./gradlew assembleDebug` to fail with `Permission denied` on a fresh clone on POSIX/Linux/macOS/Git Bash environments.
2. `.gitattributes` was missing at the repository root to enforce `eol=lf` on `gradlew` and `eol=crlf` on `gradlew.bat` across platforms.

## Fixes Applied

1. **Executable Permission on `android-app/gradlew` (`100755`):**
   - Executed `git update-index --chmod=+x android-app/gradlew`.
   - Verified index mode via `git ls-files -s android-app/gradlew`:
     ```text
     100755 ef07e0162b183eb9d19a2c9ba7035c283af9f8dd 0	android-app/gradlew
     ```

2. **Added `.gitattributes`:**
   - Enforces `text eol=lf` for `gradlew`, `**/gradlew`, and `*.sh` so fresh clones on any OS preserve POSIX LF line endings without `^M` interpreter errors.
   - Enforces `text eol=crlf` for `gradlew.bat`, `**/gradlew.bat`, and `*.bat`.
   - Marks binary files (`*.jar`, `*.aar`, `*.apk`, `*.aab`, `*.tflite`, images) as `binary`.

3. **Hardened `.gitignore` (`/.gitignore`, `android-app/.gitignore`, `android-app/app/.gitignore`):**
   - Ignores `build/`, `**/build/`, `.gradle/`, `**/.gradle/`, `local.properties`, `**/local.properties`, `.idea/`, `.kotlin/`.
   - Ignores generated packages and binaries: `*.apk`, `*.aab`, `*.ap_`, `*.dex`, `output-metadata.json`, `*.jks`, `*.keystore`.
   - Prevents foreign `*.jar` and `*.aar` files from being committed while explicitly whitelisting `!android-app/gradle/wrapper/gradle-wrapper.jar`.

4. **Module Structure & Dependency Audit:**
   - Single clean `:app` module configured via Gradle Version Catalog (`android-app/gradle/libs.versions.toml`).
   - Verified no foreign `.jar` or `.aar` files are tracked in Git (`android-app/gradle/wrapper/gradle-wrapper.jar` is the only `.jar` in version control).

## Verification Results

### 1. File Mode & Line Endings (`git ls-files`)

```text
$ git ls-files -s android-app/gradlew
100755 ef07e0162b183eb9d19a2c9ba7035c283af9f8dd 0	android-app/gradlew

$ git ls-files --eol android-app/gradlew android-app/gradlew.bat .gitattributes .gitignore
i/lf    w/lf    attr/text eol=lf      	.gitattributes
i/lf    w/lf    attr/text eol=lf      	.gitignore
i/lf    w/lf    attr/text eol=lf      	android-app/gradlew
i/lf    w/crlf  attr/text eol=crlf    	android-app/gradlew.bat
```

### 2. `./gradlew --version` Verification

```text
$ cd android-app && ./gradlew --version

------------------------------------------------------------
Gradle 9.3.1
------------------------------------------------------------

Build time:    2026-01-29 14:15:01 UTC
Revision:      44f4e8d3122ee6e7cbf5a248d7e20b4ca666bda3

Kotlin:        2.2.21
Groovy:        4.0.29
Ant:           Apache Ant(TM) version 1.10.15 compiled on August 25 2024
Launcher JVM:  25 (Oracle Corporation 25+37-LTS-3491)
Daemon JVM:    Compatible with Java 21, any vendor, nativeImageCapable=false (from gradle/gradle-daemon-jvm.properties)
OS:            Windows 11 10.0 amd64
```

### 3. `./gradlew assembleDebug` & Clean Working Tree Verification

```text
$ cd android-app && ./gradlew assembleDebug
...
> Task :app:packageDebug
> Task :app:createDebugApkListingFileRedirect
> Task :app:assembleDebug

BUILD SUCCESSFUL in 1m 26s
35 actionable tasks: 7 executed, 28 up-to-date
```

- **Generated APK:** `android-app/app/build/outputs/apk/debug/app-debug.apk` (43,071,747 bytes)
- **Repository Artifact Hygiene:** All build outputs (`build/`, `.gradle/`, `local.properties`, `app-debug.apk`) remain ignored by Git.

Closes #1
