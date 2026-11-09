# Releasing to Google Play

App ID (permanent): `com.prathosh.hks`
App name: HarikathamruthaSaaraa Abhyaasaa

## 1. Upload key (already created, 2026-11-08)

`~/AndroidKeys/hks-upload.jks` is the key. `~/AndroidKeys/hks-upload.properties` holds its generated password, and the
build reads it from there. Upload key SHA-1 `D2:B2:90:E7:7D:C8:A5:5F:25:17:40:A1:0E:A7:E5:17:09:12:28:50` is registered in Firebase.

Back up the whole `~/AndroidKeys` folder somewhere private and off this Mac.
Never put these files in the project or send them to anyone.

## 2. Build the signed bundle

For every later release, first raise `versionCode` in `app/build.gradle.kts` (Play refuses a repeated number).

```zsh
./gradlew :app:bundleRelease
# test build: the release shrinker can break things debug builds never show, so install app-release.apk on the emulator
./gradlew :app:assembleRelease
jarsigner -verify app/build/outputs/bundle/release/app-release.aab
```

Upload: `app/build/outputs/bundle/release/app-release.aab`

## 3. Google Play Console

1. Create a developer account (one-time US$25). New personal accounts must run a closed test before production:
   check the current tester and duration requirements shown in the Console.
2. Create the app, keep **Play App Signing** on, upload the bundle to a testing track.
3. Complete App content: privacy policy URL (required: the app uses the microphone and accounts), Data safety,
   content rating, target audience, and the foreground service declaration (media playback, data sync).
4. Store listing: description, icon (512x512), feature graphic (1024x500), phone screenshots.

## 4. Firebase, after the first upload

Play re-signs the app with its own key, so Google and phone sign-in need that fingerprint.
Copy the SHA-1 and SHA-256 from Play Console > Test and release > App integrity > App signing, plus those of the
upload key, and add them to the Firebase app `com.prathosh.hks`.
Then download the config again and rebuild.

Before release:
- Turn on the Google provider in Firebase Authentication.
- Remove the test phone number from the Phone provider.
