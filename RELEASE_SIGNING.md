# FRAMEFORGE release signing

FRAMEFORGE must never use the Android debug keystore to sign a production release.

## Local signing

Generate a private keystore on your own computer:

```bash
keytool -genkeypair -v -keystore frameforge-release.jks -alias frameforge -keyalg RSA -keysize 2048 -validity 10000
```

Keep the keystore and passwords in a private backup. Do not commit them to Git or paste them into chat.

Before running `./gradlew :app:assembleRelease`, provide these environment variables in your local build environment:

- `FRAMEFORGE_RELEASE_STORE_FILE`: absolute path to the keystore file
- `FRAMEFORGE_RELEASE_STORE_PASSWORD`
- `FRAMEFORGE_RELEASE_KEY_ALIAS`
- `FRAMEFORGE_RELEASE_KEY_PASSWORD`

If all four variables are present, Gradle signs the release APK with that keystore. If any are missing, Gradle intentionally creates `app-release-unsigned.apk`.

## GitHub Actions signing

To produce a signed release artifact in GitHub Actions, add these repository Actions secrets:

- `FRAMEFORGE_KEYSTORE_BASE64`: base64-encoded contents of the private keystore
- `FRAMEFORGE_KEYSTORE_PASSWORD`
- `FRAMEFORGE_KEY_ALIAS`
- `FRAMEFORGE_KEY_PASSWORD`

The workflow decodes the keystore into the runner's temporary directory and passes the signing values to Gradle through the job environment. The keystore is not written into the repository workspace or uploaded as an artifact.

Without those secrets, CI still builds, validates, and uploads an unsigned release APK. The artifact name explicitly includes `unsigned`; it must not be distributed as a production app.
