# FAH ResourceDonor Plugin - Troubleshooting Guide

## 🚨 Plugin Says "Running" But Not Contributing to Research

This is the most common issue. Here's how to fix it:

### Step 1: Check Your Passkey
Your Folding@Home passkey is crucial for crediting your work.

1. **Get a passkey:** Visit https://apps.foldingathome.org/getpasskey
2. **Copy the ENTIRE passkey** (32+ characters, letters and numbers only)
3. **Add to config.yml:**
   ```yaml
   fah:
     passkey: "YOUR_FULL_PASSKEY_HERE"
   ```
4. **Reload:** `/fah reload` or restart server

### Step 2: Enable Debug Mode
```yaml
debug: true
```

This will show detailed logs in your console explaining what's happening.

### Step 3: Run Diagnostics
Use the command: `/fah diagnose`

This will test:
- ✓ Token validity
- ✓ Internet connection
- ✓ FAH server connectivity
- ✓ Donor name uniqueness
- ✓ Team verification

### Step 4: Check Common Issues

#### Issue: "Token not configured"
**Solution:** Add your passkey to config.yml and reload

#### Issue: "Failed to initialize FAH client"
**Solutions:**
- Check internet connection
- Verify firewall allows outbound connections
- Try restarting the plugin

#### Issue: "Not processing work units"
**Solutions:**
- Wait 5-10 minutes for work assignment
- Check if FAH servers are online
- Restart the plugin with `/fah stop` then `/fah start`

#### Issue: "Connection timeout"
**Solutions:**
- Check firewall settings
- Verify your server has internet access
- Try different DNS servers

#### Issue: "Work not appearing in FAH account"
**Solutions:**
- Verify your passkey is correct
- Make sure donor name is unique
- Check https://stats.foldingathome.org/donor/YourDonorName
- It can take 30+ minutes for stats to update

## Advanced Troubleshooting

### Check Plugin Status
```
/fah status
```

Should show:
- Status: Running
- Work Unit: Processing work unit: WU_xxxxx
- Progress: X%
- Processing: Yes

### View Detailed Info
```
/fah info
```

This tests connection and shows configuration.

### Monitor Progress
```
/fah stats
```

Shows points earned and links to online stats.

### Server Console Logs
With debug enabled, look for:
```
[FAH] Successfully connected to Folding@Home servers!
[FAH] Received work unit: WU_12345
[FAH] Progress reported: 10%
[FAH] Work unit WU_12345 completed! Points earned: 150
```

### Network Requirements
The plugin needs outbound access to:
- `foldingathome.org` (port 443)
- `stats.foldingathome.org` (port 443)
- `api.foldingathome.org` (port 443)
- `assign.foldingathome.org` (port 8080)

### Configuration Validation

#### Valid Token Format
- 32+ characters
- Only letters (a-f) and numbers (0-9)
- Example: `a1b2c3d4e5f6789012345678901234ab`

#### Valid Team ID
- Numbers only
- Use "0" for no team
- Check team exists: https://stats.foldingathome.org/teams

#### Unique Donor Name
- Should be unique globally
- Avoid common names like "MinecraftServer"
- Try "YourName_MC" or "ServerName_FAH"

## FAQ

### Q: How long before I see results?
A: Work units take 30 minutes to several hours. Stats update every 30+ minutes.

### Q: How much CPU does it use?
A: Configurable, defaults to idle priority. Won't impact gameplay.

### Q: Can I join a team?
A: Yes! Set the team ID in config.yml. Find teams at https://stats.foldingathome.org/teams

### Q: Is it safe?
A: Yes! Folding@Home is a legitimate Stanford University research project.

### Q: Why is my donor name taken?
A: Try adding "_MC" or numbers to make it unique.

## Still Having Issues?

1. **Enable debug mode** and check console logs
2. **Run `/fah diagnose`** to identify problems
3. **Verify your passkey** works on the FAH website
4. **Check firewall settings** for outbound connections
5. **Try a different donor name** if yours conflicts

## Getting Help

Include this information when asking for help:
- Plugin version
- Server software (Spigot/Paper) and version
- Output of `/fah diagnose`
- Console logs with debug enabled
- Your configuration (without the passkey)