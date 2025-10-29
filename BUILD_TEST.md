# Building and Testing Guide

## Prerequisites

- **Java 21 JDK** (required)
- **Git** (for cloning)
- **Internet connection** (for downloading dependencies)

## Quick Build

```bash
# Clone the repository
git clone https://github.com/Thijs226/FAH-ResourceDonor-plugin.git
cd FAH-ResourceDonor-plugin

# Make gradlew executable (Linux/Mac)
chmod +x ./gradlew

# Build the plugin
./gradlew clean shadowJar

# Windows users:
# gradlew.bat clean shadowJar
```

The compiled JAR will be at: `build/libs/FAHResourceDonor-1.0.0.jar`

## Testing the Build

### 1. Install on Test Server

```bash
# Copy to your test server
cp build/libs/FAHResourceDonor-1.0.0.jar /path/to/your/server/plugins/

# Start your test server
cd /path/to/your/server
java -jar spigot-1.21.jar
```

### 2. Accept License

When the server starts, you'll see:
```
[FAHResourceDonor] Please type exactly: I agree
```

Type in console:
```
I agree
```

### 3. Configure Account Token

Get your token from https://app.foldingathome.org and run:
```
/fah token <your-token> My-Test-Server
```

Expected output:
```
✓ Account token configured!
Machine name: My-Test-Server
🔄 Applying configuration and restarting FAH client...
✓ FAH client successfully linked to your account!
```

### 4. Verify Auto-Start Works

1. Stop your server normally
2. Start your server again
3. Check logs for:
   ```
   [FAHResourceDonor] Restoring FAH from previous session: X cores
   [FAHResourceDonor] Auto-resuming FAH with X cores
   ```
4. Run `/fah status` - should show FAH running

### 5. Test Single-Port Mode

1. Edit `config.yml`:
   ```yaml
   folding-at-home:
     ports:
       control-port: 0
       web-port: 0
       no-port-mode: "file-based"
   ```

2. Reload: `/fah reload`

3. Add players (or use `/fah cores 2`)

4. Check logs for:
   ```
   [FAHResourceDonor] Using CLI commands to avoid restart when possible
   [FAHResourceDonor] Successfully applied 2 cores via CLI (no restart needed)
   ```

## Test Checklist

### ✅ Core Functionality
- [ ] Plugin loads without errors
- [ ] License acceptance works
- [ ] FAH client installs (if not present)
- [ ] FAH client starts successfully

### ✅ Account Setup
- [ ] `/fah token` command works
- [ ] Token validation catches too-short tokens
- [ ] Token verification runs after setup
- [ ] FAH client links to your account

### ✅ Auto-Start Features
- [ ] FAH starts automatically on server boot
- [ ] State file created: `plugins/FAHResourceDonor/fah-state.properties`
- [ ] FAH resumes with previous core count after restart
- [ ] Auto-unpause happens after initialization

### ✅ Single-Port Mode
- [ ] Works with `control-port: 0`
- [ ] CLI commands used when possible
- [ ] Restart only happens when CLI fails
- [ ] Logs clearly explain what's happening

### ✅ Commands
- [ ] `/fah status` shows current state
- [ ] `/fah info` displays detailed information
- [ ] `/fah start` works when FAH is stopped
- [ ] `/fah cores X` changes core allocation
- [ ] `/fah help` shows updated help with auto-start info

### ✅ Configuration
- [ ] `auto-start` config section works
- [ ] `auto-resume: false` prevents auto-resume
- [ ] `startup-delay` adds delay before start
- [ ] Config changes persist after `/fah reload`

## Common Build Issues

### Issue: "Could not resolve org.spigotmc:spigot-api"

**Cause**: Network issues or Spigot repository unavailable

**Solution**:
```bash
# Try again later, or use a VPN if behind a firewall
./gradlew clean shadowJar --refresh-dependencies
```

### Issue: "Java version too low"

**Cause**: Need Java 21

**Solution**:
```bash
# Install Java 21
# Ubuntu/Debian:
sudo apt install openjdk-21-jdk

# macOS (with Homebrew):
brew install openjdk@21

# Verify:
java -version  # Should show version 21
```

### Issue: Permission denied on gradlew

**Cause**: gradlew not executable

**Solution**:
```bash
chmod +x ./gradlew
```

### Issue: Build succeeds but JAR not found

**Check**:
```bash
ls -la build/libs/
```

**Expected**: You should see `FAHResourceDonor-1.0.0.jar`

## Debugging Failed Tests

### Check FAH Client Logs

```bash
# On server:
cat plugins/FAHResourceDonor/folding-at-home/log.txt
```

### Check State File

```bash
# On server:
cat plugins/FAHResourceDonor/fah-state.properties
```

Should contain:
```
cores=4
running=true
last-update=1234567890
```

### Enable Debug Mode

In `config.yml`:
```yaml
debug: true
```

Reload and check console for detailed FAH output.

### Check Account Token

```bash
# In-game or console:
/fah token
```

Should show "Token is currently configured"

## Reporting Issues

When reporting issues, please include:

1. **Server Info**:
   - Minecraft version
   - Server software (Spigot/Paper/etc)
   - Java version: `java -version`

2. **Plugin Info**:
   - Plugin version
   - Config file (remove sensitive tokens!)

3. **Logs**:
   - Server startup logs
   - FAH client log: `plugins/FAHResourceDonor/folding-at-home/log.txt`
   - State file: `plugins/FAHResourceDonor/fah-state.properties`

4. **Issue Details**:
   - What you expected to happen
   - What actually happened
   - Steps to reproduce

## Getting Help

- **GitHub Issues**: https://github.com/Thijs226/FAH-ResourceDonor-plugin/issues
- **Folding@home Support**: https://foldingathome.org/support/
- **FAH v8 Documentation**: https://github.com/FoldingAtHome/fah-client-bastet

## Performance Testing

### Monitor Resource Usage

```bash
# On Linux:
top -p $(pgrep -f FAHClient)

# Check server TPS
# In-game: /tps (if you have essentials)
```

### Expected Behavior

- **TPS**: Should stay above 18.0 with players online
- **CPU**: FAH uses configured number of cores
- **Memory**: Minimal (FAH is separate process)

### Adjust if Needed

```yaml
allocation:
  dynamic:
    cores-per-player: 0.25  # Reduce if TPS drops
```

---

**Happy testing! 🧬**
