# SMS Sender (Android + GitHub Actions)

A starter Android project for `com.sms.sender`.

## Included
- First-launch SMS and phone-state permission request
- App-side active SIM selection
- Phone number/message fields and an explicit confirmation dialog before sending
- GitHub Actions workflow to build a debug APK and upload it as an artifact
- Firebase configuration supplied by the project owner

## Build APK on GitHub
1. Upload the contents of this folder to a GitHub repository (root directory).
2. Open **Actions** and run **Build Android APK**, or push to `main`/`master`.
3. Download the `sms-sender-debug-apk` artifact from the completed workflow.

## Firebase note
Firebase is configured as a project dependency, but no database listener is wired yet. To connect it, define the Realtime Database URL, path, and message schema (for example, fields for recipient and body), plus Firebase Authentication and database security rules. Do not make the database publicly writable.

## Important
This starter sends only after the user taps the button and confirms. It does not silently send Firebase-fed messages in the background. Android/device policies and carrier support may affect SMS permissions and SIM behavior. This is a source starter; it has not been tested on a physical device.
