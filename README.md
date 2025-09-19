# FAH ResourceDonor Plugin

A Minecraft plugin that donates your server's idle resources to [Folding@Home](https://foldingathome.org/) to help with medical research including cancer, Alzheimer's, and other diseases.

## 🚨 Troubleshooting "Running but not contributing" Issue

If your plugin says it's running but you don't see contributions in your FAH account, follow these steps:

### 1. Check Passkey Configuration
```yaml
# In config.yml
fah:
  passkey: "YOUR_PASSKEY_HERE"  # Must not be empty!
  token: ""                     # Optional, for advanced features
  team: ""                      # Leave empty to use fallback team
  fallback-team: "1067089"      # Used when team is not specified
  donor-name: "MinecraftServer"
```

**Get your passkey:** https://apps.foldingathome.org/getpasskey

### 2. Enable Debug Mode
```yaml
debug: true
```

Then restart the plugin or use `/fah reload`. Check your server console for detailed logs.

### 3. Verify Connection
Use `/fah info` in-game to test the connection to FAH servers.

### 4. Check Status
Use `/fah status` to see if work units are being processed.

### 5. Common Issues and Fixes

#### Issue: "Passkey not configured"
**Fix:** Add your passkey to config.yml and run `/fah reload`

#### Issue: "Connection failed" 
**Fix:** Check your server's internet connection and firewall settings

#### Issue: "Not processing work units"
**Fix:** The plugin will automatically request new work units. Check logs for errors.

#### Issue: "Stats not appearing in FAH account"
**Fix:** 
- Verify your passkey is correct
- Make sure your donor name is unique
- Check https://stats.foldingathome.org/donor/YourDonorName

## Installation

1. Download the plugin JAR file
2. Place it in your server's `plugins/` folder
3. Start your server
4. Edit `plugins/FAHResourceDonor/config.yml` with your FAH passkey
5. Run `/fah reload` or restart the server

## Commands

- `/fah status` - Show current folding status
- `/fah start` - Start folding (if stopped)
- `/fah stop` - Stop folding
- `/fah reload` - Reload configuration
- `/fah info` - Show configuration and test connection
- `/fah debug [on|off]` - Toggle debug mode
- `/fah stats` - Show folding statistics

## Configuration

```yaml
fah:
  # Your Folding@Home passkey (REQUIRED)
  passkey: ""
  
  # Your Folding@Home token (optional, for advanced features)
  token: ""
  
  # Team ID (optional, leave empty to use fallback team)
  team: ""
  
  # Fallback team (used when team is not specified)
  fallback-team: "1067089"
  
  # Your donor name (appears in FAH stats)
  donor-name: "MinecraftServer"
  
  # Status check interval in seconds
  check-interval: 300

# Enable detailed logging
debug: false
```

## How It Works

1. **Requests Work Units:** The plugin contacts FAH servers to get computational work
2. **Processes Research:** Uses server CPU to fold protein structures
3. **Reports Progress:** Sends completion data back to FAH
4. **Earns Points:** Contributions appear in your FAH account and team stats

## Requirements

- Minecraft server (Spigot/Paper)
- Java 21+
- Internet connection
- FAH passkey from https://apps.foldingathome.org/getpasskey

## Support

If you're still having issues:

1. Enable debug mode and check console logs
2. Verify your passkey works on the FAH website
3. Check your server's firewall allows outbound connections
4. Try a different donor name if yours might conflict

## Building

```bash
./gradlew build
```

The built JAR will be in `build/libs/FAHResourceDonor-1.0.0.jar`

## License

MIT License - See [LICENSE](LICENSE) file