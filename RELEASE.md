# GitHub release setup

DroidDesk GitHub releases are signed ARM64 APKs. The release workflow creates a
draft pre-release so a generated APK cannot become public before device and
compliance review.

## One-time signing setup

Generate a dedicated Android release keystore on a trusted machine. Android
Studio's **Build > Generate Signed Bundle / APK > Create new** flow is the
preferred interactive method. Keep the keystore and passwords out of the
repository and back them up securely. Losing the key prevents Android from
accepting future DroidDesk updates over an installed release.

In the GitHub repository:

1. Open **Settings > Environments** and create or select `release`.
2. Add a required reviewer if the repository plan supports environment
   protection.
3. Open **Settings > Secrets and variables > Actions**.
4. Add the following repository or `release` environment secrets:

   - `DROIDDESK_KEYSTORE_BASE64`: base64-encoded contents of the `.jks` file;
   - `DROIDDESK_STORE_PASSWORD`: keystore password;
   - `DROIDDESK_KEY_ALIAS`: signing-key alias; and
   - `DROIDDESK_KEY_PASSWORD`: signing-key password.

On Linux, create the value for `DROIDDESK_KEYSTORE_BASE64` without modifying
the keystore:

```bash
base64 -w 0 /secure/path/droiddesk-release.jks
```

Do not paste the keystore or any password into issues, commits, workflow files,
release notes, or chat.

## Build a draft release

1. Update `version:` in `app/pubspec.yaml` and the changelog.
2. Commit the qualified source to `main` and wait for CI to pass.
3. Create a strict SemVer tag matching the pubspec version, such as `v0.2.0`.
4. Push the tag.
5. Inspect the Release workflow and download its retained artifact.

The workflow:

- checks out and verifies the exact tag;
- runs repository, Flutter, and Kotlin tests;
- decodes the keystore only inside the ephemeral Actions runner;
- builds only `android-arm64`;
- rejects an Android debug certificate;
- rejects APKs containing any ABI other than `arm64-v8a`;
- verifies `versionName` and a positive `versionCode`;
- creates SHA-256, certificate, and metadata files; and
- creates a draft GitHub pre-release.

## Publish

Install the draft APK on the target ARM64 devices and complete the Runtime
Verification checklist in `README.md`. Complete the public-distribution
requirements in `COMPLIANCE.md`. Only then edit the draft on GitHub, confirm
the APK checksum and signing-certificate digest, and publish it.

GitHub automatically provides the workflow repository token; a personal access
token is not required for release creation.
