# Droid Launcher — clean-room starter (0.1.0)

This is a small Android/Kotlin project created independently from an Android app skeleton. It does **not** copy Amethyst/PojavLauncher source code.

## Included

- Fetches Mojang's official version manifest over HTTPS, checks the source host, limits the response to 5 MiB, and caches the manifest privately for offline viewing.
- Saves a selected version as local preference (selection is metadata only; no game files are installed).
- Saves a low-memory RAM preference from 512–1024 MB in 64 MB steps (not applied to a game until engine integration exists).
- Stores server bookmarks locally and validates hostname/port formatting.
- Imports .jar files through Android's document picker into private app storage, with a 64 MiB limit and basic ZIP/JAR structure checks. Imported files are staged, not installed into a game profile; there is no malware scanner.
- Opens a third-party skin-preview page using a username after basic validation.
- Account section explains Microsoft OAuth is not implemented and never asks for a Microsoft password.
- Play section clearly reports that the game engine is not connected.
- JVM unit tests for RAM bounds, server addresses, username validation, and version-manifest parsing.
- GitHub Actions workflow to run tests and build a debug APK.

## Important limitation

This is a **prototype, not yet a playable Minecraft Java launcher**. It does not include the Android Java runtime, renderer/native input libraries, complete library/native/assets resolution, secure Microsoft OAuth and ownership checks, in-app skin rendering, server ping/join, or direct game-process launching. Do not interpret a successful APK build as proof Minecraft will run.

A real from-scratch Java launcher requires a compatible Java-on-Android runtime and native graphics/input/audio layers; it is a larger project than an Android screen/app. Implement those only after this prototype has a passing CI build.

## Build a debug APK

The cloud workflow uses JDK 17, Android SDK platform 35/build-tools 35.0.0, and Gradle 8.9. To build on a compatible computer with those tools installed, run from the repository root:

```bash
gradle --no-daemon testDebugUnitTest assembleDebug
```

The APK is produced at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

On your Android 9 phone with 2 GB RAM or less, use cloud build rather than trying to compile the Android project locally. In GitHub, open **Actions → Clean-room Android launcher build → Run workflow**. When a run succeeds, download the `droid-launcher-from-scratch-debug-apk` artifact. Install the APK by downloading it on the phone, opening the file, and allowing your browser/file manager to install unknown apps if Android requests that permission.

## Next development stages

1. Get the debug build and unit tests passing.
2. Choose a legally reusable Java-on-Android engine and review all dependency licences.
3. Add secure official OAuth/entitlement validation without collecting passwords.
4. Integrate runtime selection, rule-aware libraries/native files, assets, and process launch.
5. Test real game startup, mod-loader compatibility, graphics, thermals, and memory use on the target device.
