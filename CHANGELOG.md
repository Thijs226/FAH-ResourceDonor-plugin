# Changelog

All notable changes to the FAH Resource Donor plugin will be documented in this file.

## [Unreleased] - Enhanced Auto-Start & Single-Port Support

### 🚀 Added

#### Auto-Start & State Persistence
- **Automatic startup**: FAH client now starts automatically when server boots
- **State persistence**: Added `fah-state.properties` to save/restore FAH state across restarts
- **Auto-resume**: FAH automatically resumes work after server restarts without manual intervention
- **Periodic health checks**: Added 60-second interval checks to ensure FAH stays running
- **Auto-restart on failure**: FAH automatically restarts if it stops unexpectedly

#### Account Token Support
- **Enhanced token setup**: Improved `/fah token` command with validation
- **Token length validation**: Warns if token appears too short
- **Automatic verification**: Token application is verified after configuration
- **Better feedback**: Clear success/failure messages with troubleshooting tips
- **Machine name support**: Multi-word machine names now supported

#### Configuration Enhancements
- **Auto-start settings**: New `folding-at-home.auto-start` configuration section
  - `enabled`: Auto-start on server boot (default: true)
  - `auto-resume`: Resume work after restart (default: true)
  - `startup-delay`: Configurable delay before starting (default: 30s)
- **Power settings**: FAH config now includes `power='full'` and `on-idle='false'`
- **Unpaused by default**: FAH slots configured with `paused='false'`

#### File-Based Mode Improvements
- **Smart CLI usage**: Tries CLI commands before restarting FAH
- **Work unit preservation**: Avoids restarts when possible to preserve current work
- **Better logging**: Clear messages about when restart is/isn't needed
- **Success tracking**: Logs "Successfully applied X cores via CLI (no restart needed)"

### 🔧 Changed

#### FAH Client Management
- **Enhanced startup**: Added `--chdir` flag to ensure proper working directory
- **Better initialization**: 10-second delay before auto-unpause to ensure FAH is ready
- **Improved restart logic**: More intelligent restart decisions in file-based mode
- **Config persistence**: Core count persisted whenever changed

#### Command Improvements
- **`/fah start`**: Now uses FAHManager.forceStart() for better reliability
- **`/fah token`**: Enhanced with validation, verification, and better instructions
- **Help messages**: Updated to clarify auto-start behavior
- **Status checks**: Commands now verify FAH is actually running after operations

#### Logging Enhancements
- **Auto-start messages**: Clear logs when FAH auto-starts on boot
- **Resume messages**: Logs when auto-resuming with saved core count
- **File-based mode**: Detailed logs about CLI vs restart decisions
- **Token verification**: Logs success/failure of token application

### 📚 Documentation

#### New Files
- **README.md**: Comprehensive setup and usage guide
  - Quick setup instructions
  - Single-port configuration guide
  - Troubleshooting section
  - Command reference
  - Building from source

#### Updated Files
- **config.yml**: Added auto-start section with clear documentation
- **In-game help**: Updated `/fah` command help to mention auto-start
- **Command descriptions**: Clarified that FAH auto-starts by default

### 🐛 Fixed

#### Auto-Start Issues
- **✅ Fixed**: FAH not auto-starting after server restart
- **✅ Fixed**: Manual intervention required on FAH website after restart
- **✅ Fixed**: State not persisted across restarts
- **✅ Fixed**: FAH pausing and not resuming automatically

#### Single-Port Server Issues
- **✅ Improved**: CPU core changes now use CLI when possible
- **✅ Improved**: Better handling of file-based mode failures
- **✅ Improved**: Clearer logging about when restart is needed

#### Token Configuration Issues
- **✅ Improved**: Better validation of account tokens
- **✅ Improved**: Verification after token setup
- **✅ Improved**: Clearer error messages

### 🔒 Security

- Token validation added to prevent obviously incorrect tokens
- Existing security measures maintained (localhost-only control)

### 📦 Dependencies

- No dependency changes
- Still uses Spigot API 1.21-R0.1-SNAPSHOT
- Java 21 required

### 🎯 Migration Guide

#### From Previous Versions

1. **No breaking changes**: All existing configurations still work
2. **New features auto-enabled**: Auto-start is enabled by default
3. **Optional config updates**: Can add `auto-start` section for customization
4. **Token setup recommended**: Consider using account tokens for easier setup

#### Configuration Changes (Optional)

Add to your `config.yml`:

```yaml
folding-at-home:
  auto-start:
    enabled: true
    auto-resume: true
    startup-delay: 30
```

### 🙏 Credits

- Original issues and feature requests from server operators
- FAH v8 API research and documentation
- Community feedback on single-port server limitations

---

## [Previous Versions]

_(Previous changelog entries not included in this update)_
