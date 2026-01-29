# Release Guide

This document explains how to create releases for the Viiibe Android app.

## Quick Release

```bash
# Create and push a release tag
git tag -a v1.0.0 -m "Release v1.0.0: Initial release"
git push origin v1.0.0
```

The GitHub Actions workflow will automatically:
1. Build the APK
2. Generate SHA256 hash
3. Create a GitHub Release with the APK attached

## Version Numbering

We use [Semantic Versioning](https://semver.org/):

- **MAJOR.MINOR.PATCH** (e.g., `1.2.3`)
- **MAJOR**: Breaking changes or major features
- **MINOR**: New features, backward compatible
- **PATCH**: Bug fixes, backward compatible

### Version Code Calculation

The version code is automatically calculated from the tag:
- Format: `MAJOR * 1000000 + MINOR * 1000 + PATCH`
- Example: `v1.2.3` -> `1002003`
- Example: `v2.0.0` -> `2000000`

This ensures the version code always increases with each release.

## Creating a Release

### 1. Prepare Your Changes

```bash
# Make sure you're on main branch and up to date
git checkout main
git pull origin main

# Ensure all tests pass
./gradlew test
./gradlew assembleDebug
```

### 2. Update Changelog (Optional)

If you maintain a CHANGELOG.md file, update it with the changes for this version.

### 3. Create an Annotated Tag

```bash
# Create tag with a message (recommended)
git tag -a v1.0.0 -m "Release v1.0.0

- Feature: Added new workout tracking
- Fix: Resolved crash on startup
- Improvement: Better performance"

# Or create a simple tag
git tag v1.0.0
```

### 4. Push the Tag

```bash
git push origin v1.0.0
```

### 5. Monitor the Build

1. Go to the repository's **Actions** tab
2. Watch the "Build and Release APK" workflow
3. Once complete, check the **Releases** page

## Pre-release Versions

For alpha, beta, or release candidates, use appropriate suffixes:

```bash
# Alpha release
git tag -a v1.0.0-alpha.1 -m "Alpha release for testing"

# Beta release
git tag -a v1.0.0-beta.1 -m "Beta release"

# Release candidate
git tag -a v1.0.0-rc.1 -m "Release candidate 1"
```

These will be automatically marked as "pre-release" on GitHub.

## Signing Configuration

### Overview

APK signing is **critical for security**. The signing key proves that the APK came from the official developer and hasn't been tampered with. Once published to Google Play, you can NEVER change the signing key.

### Security Requirements

- **NEVER** commit keystores, passwords, or signing credentials to version control
- Use **strong passwords** (20+ characters, mix of letters, numbers, symbols)
- Store the keystore and passwords in **separate secure locations**
- Create **encrypted backups** stored in multiple locations
- Use **4096-bit RSA** keys for maximum security

---

## 1. Generating a Production Keystore

### Recommended Command (Maximum Security)

```bash
keytool -genkeypair \
  -alias viiibe-release \
  -keyalg RSA \
  -keysize 4096 \
  -sigalg SHA512withRSA \
  -validity 10950 \
  -keystore viiibe-release.jks \
  -storetype JKS \
  -dname "CN=Viiibe App, OU=Mobile Development, O=Your Organization, L=City, ST=State, C=US"
```

### Parameter Explanation

| Parameter | Value | Reason |
|-----------|-------|--------|
| `-keyalg RSA` | RSA algorithm | Industry standard, widely supported |
| `-keysize 4096` | 4096 bits | Maximum security (2048 is minimum) |
| `-sigalg SHA512withRSA` | SHA-512 signature | Strongest hash algorithm |
| `-validity 10950` | 30 years | Google Play requires 25+ years validity |
| `-storetype JKS` | Java KeyStore | Standard Android format |

### Interactive Generation (Alternative)

```bash
keytool -genkeypair \
  -alias viiibe-release \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10950 \
  -keystore viiibe-release.jks
```

You'll be prompted for:
- Keystore password (use 20+ characters)
- Key password (can be same as keystore password)
- Your name, organization, location details

### Verify Your Keystore

```bash
# View keystore contents
keytool -list -v -keystore viiibe-release.jks

# Verify key algorithm and size
keytool -list -keystore viiibe-release.jks -alias viiibe-release -v | grep -E "(Algorithm|Key Size)"
```

---

## 2. Local Development Signing

### Option A: Using signing.properties (Recommended)

Create `signing.properties` in the project root (this file is git-ignored):

```properties
STORE_FILE=/absolute/path/to/viiibe-release.jks
STORE_PASSWORD=your_secure_store_password
KEY_ALIAS=viiibe-release
KEY_PASSWORD=your_secure_key_password
```

Then build normally:
```bash
./gradlew assembleRelease
```

### Option B: Using Environment Variables

```bash
export STORE_FILE=/path/to/viiibe-release.jks
export STORE_PASSWORD=your_store_password
export KEY_ALIAS=viiibe-release
export KEY_PASSWORD=your_key_password

./gradlew assembleRelease
```

### Option C: Using Gradle Command Line

```bash
./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file=/path/to/keystore.jks \
  -Pandroid.injected.signing.store.password=store_password \
  -Pandroid.injected.signing.key.alias=key_alias \
  -Pandroid.injected.signing.key.password=key_password
```

---

## 3. GitHub Actions CI/CD Signing

### Step 1: Encode Your Keystore

```bash
# On macOS/Linux
base64 -i viiibe-release.jks | tr -d '\n' > keystore_base64.txt

# On macOS (alternative)
base64 -i viiibe-release.jks -o keystore_base64.txt && tr -d '\n' < keystore_base64.txt > keystore_base64_clean.txt

# Verify the encoding (should output your keystore details)
base64 -d -i keystore_base64.txt > test.jks && keytool -list -keystore test.jks
rm test.jks  # Clean up test file
```

### Step 2: Configure GitHub Secrets

Go to: **Repository Settings > Secrets and variables > Actions > New repository secret**

| Secret Name | Value | Description |
|-------------|-------|-------------|
| `KEYSTORE_BASE64` | Contents of keystore_base64.txt | Base64-encoded keystore |
| `KEYSTORE_PASSWORD` | Your keystore password | Password to open the keystore |
| `KEY_ALIAS` | `viiibe-release` | Alias of the signing key |
| `KEY_PASSWORD` | Your key password | Password for the specific key |

### Step 3: Enable Signing in Workflow

Edit `.github/workflows/release.yml` and uncomment the signing configuration section (lines 73-91).

Change the build step from `assembleDebug` to use the signed release build.

### Step 4: Clean Up Local Files

```bash
# Delete the base64 encoded file - it contains your keystore!
rm keystore_base64.txt keystore_base64_clean.txt

# Move keystore to secure location outside repo
mv viiibe-release.jks ~/secure-location/
```

---

## 4. Security Best Practices

### Password Requirements

- **Minimum 20 characters** for production keystores
- Use a **password manager** to generate and store passwords
- Use **different passwords** for store and key if possible
- Example strong password format: `Xk9$mP2@nQ5#vL8&wR3!`

### Keystore Backup Strategy

1. **Primary backup**: Encrypted cloud storage (e.g., 1Password, Bitwarden)
2. **Secondary backup**: Encrypted USB drive in secure physical location
3. **Tertiary backup**: Printed QR code of base64-encoded keystore in safe deposit box

```bash
# Create encrypted backup
gpg --symmetric --cipher-algo AES256 viiibe-release.jks
# Creates viiibe-release.jks.gpg
```

### What to Store Securely

| Item | Where to Store | Notes |
|------|---------------|-------|
| Keystore file (.jks) | Password manager, encrypted backup | NEVER in git |
| Keystore password | Password manager | Separate from keystore |
| Key alias | Can be in documentation | Not sensitive |
| Key password | Password manager | Separate from keystore |
| Backup passphrase | Written, secure location | For GPG encrypted backup |

### If Your Keystore Is Compromised

1. **Immediately** notify Google Play (if published)
2. Generate a **new keystore** with new passwords
3. Request **key upgrade** through Google Play Console
4. Revoke access to any systems that had the old key
5. Audit git history for any accidental commits

---

## 5. Verifying APK Signatures

### For End Users

Users can verify the APK was signed with the official key:

```bash
# Using apksigner (Android SDK)
apksigner verify --print-certs app-release.apk

# Using keytool (JDK)
keytool -printcert -jarfile app-release.apk

# Using jarsigner (JDK)
jarsigner -verify -verbose -certs app-release.apk
```

### Expected Output

Look for:
- **SHA-256 certificate fingerprint** - should match the published fingerprint
- **Signature algorithm**: SHA512withRSA (or SHA256withRSA)
- **Key size**: 4096 bits (or 2048 minimum)
- **Validity period**: Should extend 25+ years

### Publishing Certificate Fingerprint

After generating your keystore, publish the certificate fingerprint so users can verify:

```bash
keytool -list -v -keystore viiibe-release.jks -alias viiibe-release | grep SHA256
```

Add this fingerprint to your:
- README.md
- Website security page
- App store listing

Example format:
```
Official APK Signing Certificate (SHA-256):
AB:CD:EF:12:34:56:78:90:AB:CD:EF:12:34:56:78:90:AB:CD:EF:12:34:56:78:90:AB:CD:EF:12:34:56:78:90
```

---

## 6. Google Play App Signing

If publishing to Google Play, consider using **Google Play App Signing**:

### Benefits
- Google manages your app signing key securely
- Enables key upgrade if your upload key is compromised
- Smaller APK sizes with optimized delivery

### Setup
1. Generate an **upload key** (can be different from app signing key)
2. Enroll in Google Play App Signing during first upload
3. Use upload key for signing APKs you submit
4. Google re-signs with the app signing key for distribution

### Upload Key Generation

```bash
keytool -genkeypair \
  -alias viiibe-upload \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10950 \
  -keystore viiibe-upload.jks
```

---

## 7. Troubleshooting

### "Keystore was tampered with, or password was incorrect"
- Verify you're using the correct password
- Check if the keystore file was corrupted (compare checksums with backup)

### "No key with alias found"
- List available aliases: `keytool -list -keystore your.jks`
- Verify the alias name matches exactly (case-sensitive)

### "The APK failed to install"
- Check if a debug version is installed (uninstall first)
- Verify the signature is valid: `apksigner verify app.apk`

### Build Fails with Signing Error
1. Verify `signing.properties` exists and has correct paths
2. Check environment variables are set correctly
3. Ensure keystore file path is absolute, not relative

## Verifying a Release

Users can verify the downloaded APK using the SHA256 hash:

```bash
# Linux/macOS
sha256sum viiibe-v1.0.0.apk
# or
shasum -a 256 viiibe-v1.0.0.apk

# Windows (PowerShell)
Get-FileHash viiibe-v1.0.0.apk -Algorithm SHA256
```

Compare the output with the hash in the release notes.

## Deleting a Tag (If Needed)

If you need to delete a tag and recreate it:

```bash
# Delete local tag
git tag -d v1.0.0

# Delete remote tag
git push origin --delete v1.0.0

# Create new tag
git tag -a v1.0.0 -m "Updated release message"
git push origin v1.0.0
```

**Note**: If a release was already created, you'll need to delete it manually from the GitHub Releases page first.

## Troubleshooting

### Build Fails

1. Check the Actions tab for error logs
2. Ensure `gradlew` has execute permissions
3. Verify all dependencies are correctly specified

### APK Not Signed

1. Ensure keystore secrets are correctly configured
2. Check that the keystore is properly base64-encoded
3. Verify key alias matches the one in secrets

### Version Code Conflicts

If Google Play rejects the APK due to version code:
1. Ensure you're incrementing versions properly
2. Check that no previous release used the same or higher version code

## Local Testing

To test the release build locally:

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing config)
./gradlew assembleRelease

# Find the APK
ls -la app/build/outputs/apk/
```
