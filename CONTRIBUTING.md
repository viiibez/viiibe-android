# Contributing to Viiibe

Thanks for your interest in contributing! This document provides guidelines for contributing to the project.

## Getting Started

1. **Fork the repository** on GitHub
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/viiibe.git
   cd viiibe
   ```
3. **Create a branch** from `develop`:
   ```bash
   git checkout develop
   git checkout -b feature/your-feature-name
   ```

## Development Setup

### Requirements
- JDK 17 (not newer)
- Android SDK with API 34
- Android Studio (recommended)

### Building
```bash
./gradlew assembleDebug
```

### Testing on Peloton
```bash
adb connect <PELOTON_IP>:<PORT>
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Making Changes

### Code Style
- **Kotlin** for all new code
- **Jetpack Compose** for UI components
- Follow existing patterns in the codebase
- Add comments for complex logic

### Commit Messages
- Use clear, descriptive commit messages
- Start with a verb: "Add", "Fix", "Update", "Remove"
- Keep the first line under 72 characters

### Pull Requests
1. Update your branch with the latest `develop`:
   ```bash
   git fetch origin
   git rebase origin/develop
   ```
2. Push your branch and create a PR against `develop`
3. Fill out the PR template
4. Wait for review

## What to Contribute

- **New Arcade Games** - Bike-controlled games
- **Bug Fixes** - Check the Issues tab
- **Hardware Support** - Other bike brands
- **Localization** - Translations
- **Documentation** - Improve guides

## Questions?

Open an issue with the "question" label.
