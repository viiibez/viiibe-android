# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability, please report it responsibly:

1. **Do NOT open a public issue**
2. Email the maintainers directly or use GitHub's private vulnerability reporting
3. Include detailed steps to reproduce the vulnerability
4. Allow reasonable time for a fix before public disclosure

## Security Considerations

### Wallet Security
- Private keys are encrypted using Android's EncryptedSharedPreferences
- Keys are stored locally on device only - never transmitted
- PIN protection with auto-lock feature available
- Users should backup their private keys securely

### Bluetooth
- App connects to standard Bluetooth FTMS services
- No authentication bypass or proprietary protocol exploitation

### Data Storage
- All workout data stored locally in encrypted Room database
- No data transmitted to external servers

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.x.x   | :white_check_mark: |

## Best Practices for Users

1. Set up PIN protection for wallet
2. Backup private keys securely offline
3. Keep the app updated
4. Only install from trusted sources
