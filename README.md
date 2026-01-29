# Viiibe - Open Source Peloton Companion App

A free, open-source fitness companion app for Peloton bikes. Track your rides, play bike-controlled arcade games, and mint workout achievements as NFTs - all without a subscription.

## Features

### Real-time Metrics Tracking
- Cadence (RPM)
- Resistance level
- Power output (watts)
- Speed (mph/km/h)
- Heart rate (with compatible monitor)
- Total output (kJ)
- Calories burned
- Distance

### Bike Arcade
6 games controlled entirely by pedaling - no touch input during gameplay!

| Input | Action |
|-------|--------|
| Cadence (RPM) | Speed / Movement |
| Power Burst | Boost / Special |
| Resistance +10 | Jump / Action 1 |
| Resistance -10 | Slide / Action 2 |

**Games:**
- **Sprint Race** - Race against AI opponents
- **Power War** - Tug-of-war with sustained power
- **Rhythm Ride** - Match target cadences to the beat
- **Zombie Escape** - Outrun the zombie horde
- **Hill Climb** - Conquer mountain terrain
- **Power Surge** - Charge and blast targets

### Avalanche Wallet
- Generate or import your wallet
- Send and receive AVAX
- PIN protection with auto-lock
- Mint workout achievements as on-chain NFTs
- QR code for easy receiving

> Your private keys are encrypted and stored locally. They are never transmitted externally.

### Workout History
- Complete ride history stored locally
- Detailed per-ride statistics
- Progress tracking over time

### Video Workouts
- Browse and play free cycling workout videos
- Categories: HIIT, Endurance, Climb, Intervals, Scenic
- Multiple difficulty levels

## Installation on Peloton

### Prerequisites
- Peloton Bike (original or Bike+)
- Computer with ADB installed
- USB cable or WiFi connection

### Step 1: Enable Developer Mode

1. On your Peloton, go to **Settings** > **Device Settings**
2. Tap **About tablet**
3. Tap **Build number** 7 times to enable Developer Options
4. Go back to **Device Settings** > **Developer options**
5. Enable **USB debugging** or **Wireless debugging**

### Step 2: Connect via ADB

**USB Connection:**
```bash
# Connect USB cable from computer to Peloton
adb devices
# You should see your Peloton listed
```

**WiFi Connection:**
```bash
# On Peloton: Developer options > Wireless debugging > Pair device
adb pair <IP>:<PAIRING_PORT>
# Enter pairing code

adb connect <IP>:<PORT>
```

### Step 3: Install the App

**From Release APK:**
```bash
adb install viiibe-release.apk
```

**Build from Source:**
```bash
git clone https://github.com/avaxjesus/viiibe.git
cd viiibe
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Step 4: Launch

1. Swipe up from the bottom to access the app drawer
2. Tap **FreeSpin** to launch
3. The app will auto-connect to your bike's Bluetooth sensors

## Building from Source

### Requirements
- **JDK 17** (not newer)
- **Android SDK** with API 34
- **Gradle 8.x**

### Commands
```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test
```

### Project Structure
```
app/src/main/java/com/freespin/app/
├── arcade/              # Bike-controlled games
│   ├── data/            # Game models and state
│   ├── engine/          # Game engine, AI, input processing
│   └── ui/              # Game screens and renderers
├── blockchain/          # Avalanche wallet integration
├── bluetooth/           # Peloton sensor communication
├── data/                # Database and models
├── ui/                  # Main app screens
└── overlay/             # Floating metrics overlay
```

## Contributing

Contributions are welcome! Here's how to get started:

### Getting Started

1. **Fork the repository** on GitHub

2. **Clone your fork:**
   ```bash
   git clone https://github.com/YOUR_USERNAME/viiibe.git
   cd viiibe
   ```

3. **Create a feature branch:**
   ```bash
   git checkout -b feature/your-feature-name
   ```

4. **Make your changes** and test on a Peloton or Android emulator

5. **Commit your changes:**
   ```bash
   git add .
   git commit -m "Add your feature description"
   ```

6. **Push to your fork:**
   ```bash
   git push origin feature/your-feature-name
   ```

7. **Open a Pull Request** on GitHub

### Code Guidelines

- **Kotlin** for all new code
- **Jetpack Compose** for UI components
- Follow existing code style and patterns
- Add comments for complex logic
- Test on actual Peloton hardware when possible

### Ideas for Contribution

- **New Arcade Games** - Add more bike-controlled games
- **Workout Programs** - Structured training plans
- **Social Features** - Leaderboards, challenges
- **Hardware Support** - Other bike brands
- **Localization** - Translations
- **Bug Fixes** - Check the Issues tab

### Reporting Issues

1. Check if the issue already exists
2. Include your Peloton model and app version
3. Describe steps to reproduce
4. Include error messages or screenshots

## Troubleshooting

### Bike not connecting
- Ensure Bluetooth is enabled
- Make sure no other app is connected to the bike
- Try restarting the bike

### Metrics showing zero
- Pedal the bike to generate data
- Check Bluetooth connection is established

### App crashes on launch
- Check that all permissions are granted
- Clear app data and try again
- Reinstall the APK

## Legal Disclaimer

This app is not affiliated with, endorsed by, or connected to Peloton Interactive, Inc. "Peloton" is a trademark of Peloton Interactive, Inc.

This app only accesses standard Bluetooth fitness protocols that the bike publicly broadcasts. It does not bypass any security measures or access proprietary systems.

Use at your own risk. The developers are not responsible for any damage to your equipment.

## License

MIT License - See LICENSE file for details

---

**Ride free!**
