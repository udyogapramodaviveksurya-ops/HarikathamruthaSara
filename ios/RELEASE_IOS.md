# Releasing to the App Store

Bundle ID (permanent after first upload): `com.prathosh.hks`, the same as Android.
Firebase app: `1:818646535112:ios:faef00cd201c92f21e4efd` (project `harikathamrutha-sara`).
Open `ios/HKS.xcodeproj` in Xcode. iOS 17 or later, iPhone and iPad.

## What only the account holder can do

### 1. Apple Developer account
Enrol the publisher at <https://developer.apple.com/programs/> (US$99/year). TechByte needs a D-U-N-S number to enrol as a company.

### 2. Certificates and capabilities
1. Xcode > Settings > Accounts: add the Apple ID.
2. Target HKS > Signing & Capabilities: tick *Automatically manage signing* and pick the team.
   Xcode registers the bundle ID with **Sign in with Apple** and **Push Notifications** (already in `HKS.entitlements`).

### 3. Firebase console
1. Authentication > Sign-in method > **Apple**: enable. No keys are needed for sign-in from the app itself.
2. Project settings > Cloud Messaging > iOS app: upload an **APNs Authentication Key** (.p8, created at developer.apple.com > Keys). Phone sign-in and push both need it.
3. Remove any test phone number before release.

### 4. Publish the updated privacy policy
`firebase/public/privacy.html` now mentions Sign in with Apple. Then:

```bash
cd firebase && firebase deploy --only hosting
```

### 5. Server: make pushes visible on iPhones
`HarikathamruthaSaraServer/tools/push_notify.py` sends data-only messages, which Android displays itself. iOS does not show those, so add an `apns` block with an alert, for example:

```python
"apns": {"payload": {"aps": {"alert": {"title": title, "body": body}, "sound": "default"}}}
```

Without this block iPhones receive "updates" pushes silently, and the app will still check for new content.

## Build and upload
For every later release raise *Version* and/or *Build* on the target's General tab.

1. Choose **Any iOS Device (arm64)**, then Product > Archive.
2. Organizer > Validate App, then Distribute App > App Store Connect.
3. Test the build through TestFlight on a real iPhone and iPad.

## App Store Connect
Copy the listing from `AppStore/metadata.md`, the screenshots from `AppStore/screenshots/`, and answer App Privacy from the table in that file.

## Test on a real device before submitting
The Simulator cannot prove these: microphone quality with Recite & check / Say it, push, phone (SMS) sign-in, Sign in with Apple, and background downloads with the phone locked.

## Keeping in step with Android
- Strings: `python3 ios/tools/port_strings.py` regenerates `HKS/Generated/` from the Kotlin tables.
- Letter/word clips and bundled text: copy `app/src/main/assets/` into `ios/Bundled/`.
- `HKS/Speech/Scoring.swift` must stay numerically identical to `speech/*.kt`.
- Progress, settings and the Firestore document use the same format on both platforms, so accounts move freely between them.
