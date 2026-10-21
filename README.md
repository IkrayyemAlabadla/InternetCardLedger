# InternetCardLedger

Native Android ledger for internet-card sales. The application is Arabic-first,
RTL, offline-first, and stores its business data locally with Room.

## Release identity

- Application name: `دفتر كروت الإنترنت`
- Application ID: `com.alabadla.internetcardledger`
- Minimum Android version: API 24
- Version: `1.0.0` (`versionCode` 1)

## Build requirements

- Android Studio with Android SDK 37
- JDK 17 or newer (Android Studio's bundled JDK is recommended)
- No service credentials are required for normal offline operation

Keep `local.properties`, keystores, passwords, APKs, and exported backups out of
Git. They are intentionally ignored where applicable.

## Build and test

Windows:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
.\gradlew.bat lint
```

macOS/Linux:

```bash
./gradlew testDebugUnitTest connectedDebugAndroidTest assembleDebug assembleRelease lint
```

The release build is unsigned unless all four signing environment variables
described below are present. Release builds are explicitly non-debuggable.

## Install a debug build

With an emulator or device visible in `adb devices`:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.alabadla.internetcardledger/.MainActivity
```

`-r` preserves the existing application data when the installed build uses the
same application ID and signing key.

## Release signing

Create the keystore outside this repository. The command prompts securely for
passwords and does not place them in shell history:

```powershell
keytool -genkeypair -v -keystore C:\secure\internet-card-ledger-release.jks -alias internet-card-ledger -keyalg RSA -keysize 4096 -validity 10000
```

Set credentials only in the current shell or in a secure CI secret store:

```powershell
$env:ICL_RELEASE_STORE_FILE = 'C:\secure\internet-card-ledger-release.jks'
$env:ICL_RELEASE_KEY_ALIAS = 'internet-card-ledger'
$env:ICL_RELEASE_STORE_PASSWORD = Read-Host 'Keystore password' -MaskInput
$env:ICL_RELEASE_KEY_PASSWORD = Read-Host 'Key password' -MaskInput
.\gradlew.bat clean assembleRelease
```

Never commit the keystore or any of these values. Losing the release keystore
prevents publishing updates signed as the same application.

## Data and connectivity

Normal ledger use, Room storage, JSON backup/restore, and CSV export are fully
offline. Nearby synchronization uses Bluetooth and direct Wi-Fi capabilities;
the manifest does not request Internet access. The Android system file picker
can save to local storage or an installed document provider such as Google Drive.
Android automatic app-data backup is disabled so a restored phone cannot clone a
device synchronization identity; use the validated in-app JSON backup instead.

See [BACKUP_FORMAT.md](BACKUP_FORMAT.md) for the documented backup format and
[USER_GUIDE_AR.md](USER_GUIDE_AR.md) for the Arabic user guide.
