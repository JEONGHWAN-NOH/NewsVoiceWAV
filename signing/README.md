# NewsVoice WAV — secure signing

The app uses a new package ID `com.newsvoice.tts.stable` so that it installs beside v5.27.

**Never check private signing files, passwords or base64 keystores into git.**
The public certificate SHA-256 is in `expected-cert-sha256.txt`.

To enable automated signed releases, set these **GitHub repository secrets** using
Settings → Secrets and variables → Actions (repository owner only):

- `SIGNING_KEYSTORE_B64`: base64 of the PKCS12 keystore bytes, without line breaks
- `NEWSVOICE_SIGNING_STORE_PASSWORD`: keystore password
- `NEWSVOICE_SIGNING_KEY_ALIAS`: `newsvoice-stable-v1`
- `NEWSVOICE_SIGNING_KEY_PASSWORD`: key password (same as store password in initial key)

The Release job blocks an APK whose signing certificate differs from the
public pinned fingerprint, or whose package ID is not
`com.newsvoice.tts.stable`. A build without secrets can generate a **review
unsigned APK**, but it does not publish it as the installable official release.
No private keys are uploaded to GitHub by the assistant.

Keep a separate encrypted offline backup of the .p12 keystore and its password;
do not share them in repository issues, PRs, chats with untrusted people or CI logs.

The current old v5.27 signing key is different and cannot migrate in place.
Do not remove the old app before verifying the new app's functionality.
