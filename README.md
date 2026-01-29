# Viiibe - Peloton Companion App

A free, open-source fitness gaming app for Peloton bikes. Play bike-controlled arcade games, compete in global multiplayer matches, and track your workouts.

## Download

**[Download Latest Release (v1.0.0-beta)](https://github.com/viiibez/viiibe-android/releases/latest)**

## Features

### Bike Arcade Games
8 games controlled entirely by pedaling - no touch input during gameplay!

| Input | Action |
|-------|--------|
| Cadence (RPM) | Speed / Movement |
| Power Output | Boost / Special |
| Resistance +8 | Jump / Action 1 |
| Resistance -8 | Slide / Action 2 |

**Games:**
- **Sprint Race** - Race against AI opponents
- **Power War** - Tug-of-war with sustained power
- **Rhythm Ride** - Match target cadences to the beat
- **Zombie Escape** - Outrun the zombie horde
- **Hill Climb** - Conquer mountain terrain
- **Power Surge** - Charge and blast targets
- **Music Speed** - Race to the beat
- **Paper Route** - Deliver newspapers by pedaling

### Global Multiplayer
- Real-time matches against players worldwide
- Server-relayed gameplay for fair competition
- Spectate live matches on [viiibe.xyz](https://viiibe.xyz)

### Real-time Metrics
- Cadence (RPM)
- Power output (watts)
- Resistance level
- Calories burned
- Total output (kJ)

### Web3 Wallet
- Built-in Avalanche wallet
- Track your stats on [viiibe.xyz](https://viiibe.xyz)

## Installation on Peloton

### Prerequisites
- Peloton Bike (original or Bike+)
- Computer with ADB installed
- WiFi connection (both devices on same network)

### Step 1: Enable Developer Mode on Peloton

1. On your Peloton, go to **Settings** > **Device Settings**
2. Tap **About tablet**
3. Tap **Build number** 7 times until you see "You are now a developer!"
4. Go back to **Device Settings** > **Developer options**
5. Enable **Wireless debugging**
6. Tap **Wireless debugging** to see the IP address and port

### Step 2: Install ADB on Your Computer

**macOS (Homebrew):**
```bash
brew install android-platform-tools
```

**Windows:**
Download from [Android SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools) and add to PATH.

**Linux:**
```bash
sudo apt install adb
```

### Step 3: Connect to Peloton via WiFi

```bash
# Pair with Peloton (first time only)
# On Peloton: Developer options > Wireless debugging > Pair device with pairing code
adb pair <IP>:<PAIRING_PORT>
# Enter the pairing code shown on Peloton

# Connect to Peloton
adb connect <IP>:<PORT>
# Example: adb connect 192.168.1.100:5555

# Verify connection
adb devices
# Should show your Peloton as "device"
```

### Step 4: Download and Install the App

```bash
# Download the latest APK
curl -LO https://github.com/viiibez/viiibe-android/releases/latest/download/viiibe-v1.0.0-beta.apk

# Verify the download (optional but recommended)
shasum -a 256 viiibe-v1.0.0-beta.apk
# Compare with SHA256 hash on release page

# Install on Peloton
adb install viiibe-v1.0.0-beta.apk
```

### Step 5: Launch the App

1. On Peloton, swipe up from the bottom to access the app drawer
2. Tap **Viiibe** to launch
3. The app will auto-connect to your bike's Bluetooth sensors
4. Start pedaling and enjoy!

## Verify APK Integrity

Each release includes a SHA256 hash file. Verify your download:

```bash
# macOS/Linux
shasum -a 256 viiibe-v1.0.0-beta.apk

# Windows PowerShell
Get-FileHash viiibe-v1.0.0-beta.apk -Algorithm SHA256
```

Compare the output with the hash on the [releases page](https://github.com/viiibez/viiibe-android/releases).

## Building from Source

### Requirements
- JDK 17
- Android SDK with API 34
- Gradle 8.x

### Build Commands

```bash
# Clone the repository
git clone https://github.com/viiibez/viiibe-android.git
cd viiibe-android

# Create local.properties with your SDK path
echo "sdk.dir=/path/to/your/Android/sdk" > local.properties

# Build debug APK
./gradlew assembleDebug

# Install on connected device
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Troubleshooting

### "adb: command not found"
Make sure ADB is installed and in your PATH.

### Peloton not showing in `adb devices`
1. Check both devices are on the same WiFi network
2. Verify wireless debugging is enabled
3. Try pairing again with `adb pair`

### Bike sensors not connecting
1. Ensure Bluetooth is enabled on Peloton
2. Close any other apps that might be using Bluetooth
3. Try restarting the Peloton

### Metrics showing zero
- Start pedaling! Sensors only report when the bike is in motion
- Check the Bluetooth connection indicator in the app

### App crashes on launch
1. Uninstall and reinstall: `adb uninstall com.viiibe.app && adb install viiibe-v1.0.0-beta.apk`
2. Make sure you're using the latest release

## Links

- **Website:** [viiibe.xyz](https://viiibe.xyz)
- **Releases:** [GitHub Releases](https://github.com/viiibez/viiibe-android/releases)
- **Issues:** [Report a Bug](https://github.com/viiibez/viiibe-android/issues)

## Legal Disclaimer

This app is not affiliated with, endorsed by, or connected to Peloton Interactive, Inc. "Peloton" is a trademark of Peloton Interactive, Inc.

This app only accesses standard Bluetooth fitness protocols that the bike publicly broadcasts. It does not bypass any security measures or access proprietary systems.

Use at your own risk.

## License

MIT License - See [LICENSE](LICENSE) file for details.

---

**Ride free!**
