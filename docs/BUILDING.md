# Building LinkiGram

This guide builds the standalone LinkiGram APK from a clean checkout.

## Toolchain

- JDK 17
- Android SDK 36
- Android Build Tools 36.0.0
- Android NDK 26.3.11579264
- CMake 3.22.1
- Python 3.11 for Chaquopy build tasks

Android Studio may install the Android SDK, NDK and CMake components. The repository includes the Gradle wrapper, so a separate Gradle installation is not required.

## Checkout

Clone with submodules:

```bash
git clone --recursive https://github.com/timaa130704/LinkiGram.git
cd LinkiGram
```

If the repository was cloned without `--recursive`, initialize the submodules separately:

```bash
git submodule update --init --recursive
```

## Local configuration

Copy the public template:

```bash
cp private.properties.example private.properties
```

At minimum, set these values:

```properties
TELEGRAM_API_ID=123456
TELEGRAM_API_HASH=your_api_hash
```

Obtain credentials from [my.telegram.org](https://my.telegram.org). Do not commit `private.properties`, service configuration files or signing material.

Optional blank values are supported for integrations which are not required by a local development build. Release signing can be configured through `private.properties` or equivalent environment variables.

## Build variants

Build the standalone APK containing both `arm64-v8a` and `armeabi-v7a`:

```bash
./gradlew :TMessagesProj_AppStandalone:assembleAfatStandalone
```

Build ARM64 only for faster local iteration:

```bash
./gradlew :TMessagesProj_AppStandalone:assembleAfatStandalone -PngArm64Only
```

The default output is:

```text
TMessagesProj_AppStandalone/build/outputs/apk/afat/standalone/app.apk
```

## Native hook runtime

The repository pins Pine as a Git submodule. LinkiGram-specific changes are stored in `patches/pine-nimarkogram.patch`, while the matching ARM binaries used by the application are tracked in `TMessagesProj/jni`.

To inspect the patch against a clean Pine checkout:

```bash
git -C third_party/pine apply --check ../../patches/pine-nimarkogram.patch
```

## tg-ws-linki bypass engine

The in-process bypass engine is the `tgwsproxy` Rust crate, linked into
`libtmessages` through `TMessagesProj/jni/tgwsbridge.cpp`. The Android build does **not**
compile it with Gradle: the archives are checked in as build inputs and imported as a
static library from `TMessagesProj/jni/prebuild/lib/<abi>/`.

| ABI | Archive |
| --- | --- |
| `arm64-v8a` | `TMessagesProj/jni/prebuild/lib/arm64-v8a/libtgwsproxy.a` |
| `armeabi-v7a` | `TMessagesProj/jni/prebuild/lib/armeabi-v7a/libtgwsproxy.a` |

`x86` and `x86_64` are intentionally absent. Those ABIs fall back to the in-house relay
route at runtime, so an emulator build still links.

### Regenerating the archives

The crate must be built as a `staticlib` with `panic = "abort"` in `[profile.release]`.
With a Rust toolchain that has the Android targets installed:

```bash
rustup target add aarch64-linux-android armv7-linux-androideabi
cargo install cargo-ndk

cargo ndk -t arm64-v8a -t armeabi-v7a -o ./prebuild/lib build --release
```

Replace the two files above with the output, and set the NDK API level used by the build
(see `ANDROID_PLATFORM` / `ndkVersion` in the Gradle configuration).

Both ARM archives are untracked build inputs. If they are missing, CMake configuration
fails with an unresolved `tgwsproxy` target rather than silently producing an APK without
the bypass engine.

### Why the linker flag is required

`libtlottie.a` and `libtgwsproxy.a` are separate Rust builds, so each one ships its own
copy of the compiler runtime (`core`, `alloc`, panic handlers, memcopy). NDK CMake links
with `-Wl,--allow-multiple-definition`, which keeps the first definition and discards the
rest. Merging the two archives into a single object archive does not deduplicate them,
because the duplicate symbols live in different object files.

## Troubleshooting

- Confirm that all submodules are initialized before diagnosing native-linker failures.
- Confirm that Gradle runs on JDK 17 rather than a system-default JDK.
- Install the exact NDK and CMake versions declared above when CMake configuration fails.
- Remove only generated module build directories when a stale local build cache causes inconsistent output; do not delete source or local signing files.
