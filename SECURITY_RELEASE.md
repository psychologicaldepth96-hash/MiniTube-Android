# MiniTube release signing and security

Security workflow: Actions > **Security audit**. It runs weekly and on every main-branch push. Reports are available in **MiniTube-security-audit** artifacts. A failed audit means inspect its findings before distributing.

Release workflow: Actions > **Signed release APK** > Run workflow. It requires four secrets under Settings > Secrets and variables > Actions:

- `MINITUBE_KEYSTORE_BASE64`: Base64 representation of your personal Android upload/release keystore.
- `MINITUBE_STORE_PASSWORD`: password for the keystore.
- `MINITUBE_KEY_ALIAS`: name of the signing key.
- `MINITUBE_KEY_PASSWORD`: password for that key.

Generate a keystore **on your own trusted computer**, never in a public workflow, and never commit it to GitHub. Store an offline backup securely. If you lose the signing key, updates to APK installs may become impossible. Do not send the keystore or passwords in a chat. Set repository visibility to Private if you do not want source code public.

The release process signs the built release APK with Android's apksigner, verifies the signature, and uploads `MiniTube-signed-release`. No release will be produced when secrets are absent. It does not upload to Google Play. Store policies and YouTube/media intellectual-property rules still apply.

Security limitations: automated dependency and secret scans do not detect every vulnerability, particularly those within native binaries or runtime-updated yt-dlp. Before external distribution, test installation, TLS behavior, update handling, background service, URI parsing, storage, and Android permissions on real devices. The project currently targets Android API 29, which should be assessed before publication.
