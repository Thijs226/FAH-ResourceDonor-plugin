# FAH Resource Donor Plugin

A Minecraft server plugin that donates unused CPU resources to [Folding@home](https://foldingathome.org/) to support medical research into diseases like cancer, Alzheimer's, and COVID-19.

## ✨ Key Features

### 🚀 Auto-Start & Auto-Resume
- **Automatic startup**: FAH client starts automatically when your server boots
- **State persistence**: Work resumes automatically after server restarts
- **Health monitoring**: Periodic checks ensure FAH keeps running
- **Zero manual intervention**: No need to manually start FAH on the website after restarts

### 🔒 Single-Port Optimized
- **File-based control**: Works perfectly on shared hosting with only one port
- **Smart CPU allocation**: Dynamically adjusts based on player count
- **CLI commands**: Uses FAH CLI to change cores without restart when possible
- **Auto-port detection**: Finds available ports automatically

### 🔗 Easy Account Setup
- **Account token support**: Link server to your F@H account with one command
- **Token validation**: Automatic verification after setup
- **Passkey support**: Earn bonus points for your contributions
- **Traditional setup**: Username/team/passkey also supported

### 🎮 Player-Friendly
- **Dynamic resource allocation**: More players = fewer cores for FAH
- **Minimal server impact**: Reserves cores for Minecraft
- **Transparent operation**: Players can see contribution stats
- **Voting system**: Let players vote on which diseases to research

## 📦 Quick Setup

### 1. Install the Plugin

1. Download the latest `FAHResourceDonor-X.X.X.jar` from releases
2. Place it in your server's `plugins/` folder
3. Start your server
4. Type `I agree` in console to accept the FAH license

### 2. Configure Your Account (Choose One Method)

#### Option A: Account Token (Recommended for v8)

Get your account token from [https://app.foldingathome.org](https://app.foldingathome.org):
1. Log in to your F@H account
2. Go to account settings
3. Generate a new token
4. Run in your server:

```
/fah token <your-account-token> My-Minecraft-Server
```

The plugin will:
- ✅ Configure FAH with your token
- ✅ Restart FAH client
- ✅ Verify the connection
- ✅ Enable auto-start on server restart

#### Option B: Traditional Setup

```
/fah setup <username> <team-id> <passkey>
```

Get your passkey at: https://apps.foldingathome.org/getpasskey

### 3. Verify It's Working

```
/fah status
```

You should see:
- ✅ FAH client running
- ✅ Work unit being processed
- ✅ Progress percentage

## 🎯 Single-Port Configuration

If you're on shared hosting (Apex, Shockbyte, etc.) with only one port, the default config is already optimized:

```yaml
folding-at-home:
  ports:
    control-port: 0        # Disabled - uses file-based control
    web-port: 0            # Disabled - not needed
    no-port-mode: "file-based"  # Optimized for single-port
```

The plugin will:
- ✅ Use CLI commands to change CPU cores without restart (when possible)
- ✅ Auto-detect available ports for internal communication
- ✅ Persist state across restarts
- ✅ Work perfectly with shared hosting restrictions

## 🔧 Configuration

### Auto-Start Settings

```yaml
folding-at-home:
  auto-start:
    enabled: true          # Auto-start FAH on server boot
    auto-resume: true      # Resume work after restart
    startup-delay: 30      # Seconds to wait before starting
```

### Account Configuration

```yaml
folding-at-home:
  account:
    # Option 1: Account Token (easiest)
    account-token: "your-token-here"
    machine-name: "Minecraft-Server"
    
    # Option 2: Traditional
    username: "YourUsername"
    team-id: "0"
    passkey: "your-32-char-passkey"
```

### Resource Allocation

```yaml
server:
  total-cores: 8           # Auto-detected by default
  reserved-cores: 1        # Always reserved for Minecraft

allocation:
  mode: "dynamic"          # Adjusts based on player count
  dynamic:
    cores-per-player: 0.5  # Cores needed per player
    min-cores-for-minecraft: 1
    max-cores-for-fah: 7
```

## 📋 Commands

### Quick Setup
- `/fah token <token>` - Link with account token (easiest)
- `/fah setup <user> <team> <passkey>` - Traditional setup
- `/fah passkey <passkey>` - Add passkey for bonus points

### Status & Info
- `/fah status` - Current status and progress
- `/fah info` - Detailed information
- `/fah stats` - Your contribution statistics
- `/fah account info` - View account details

### Admin Controls
- `/fah start` - Manually start (auto-starts by default)
- `/fah stop` - Stop FAH client
- `/fah pause` - Pause folding
- `/fah resume` - Resume folding
- `/fah cores <number>` - Manually set core count
- `/fah reload` - Reload configuration

### Environment
- `/fah environment` - Show detected environment
- `/fah platform` - Platform-specific info
- `/fah optimize` - Apply optimizations

## 🐛 Troubleshooting

### FAH Not Starting After Restart?

1. **Check token/passkey is configured:**
   ```
   /fah token
   ```
   If not set, configure it.

2. **Check logs:**
   Look for "Auto-resuming FAH with X cores" in console

3. **Manually start:**
   ```
   /fah start
   ```

4. **Verify state file:**
   Check `plugins/FAHResourceDonor/fah-state.properties` exists

### CPU Cores Not Changing on Single-Port Server?

This is normal behavior:
- Plugin tries CLI commands first (no restart)
- If CLI fails 3 times, it restarts FAH (brief interruption)
- This preserves work units when possible
- Check logs for "Successfully applied X cores via CLI"

### Token Not Working?

1. **Verify token length:**
   - Should be 32+ characters
   - Copy the entire token from F@H dashboard

2. **Check verification:**
   After `/fah token`, you should see:
   - "✓ FAH client successfully linked to your account!"

3. **Generate new token:**
   - Old tokens may expire
   - Generate fresh token in F@H dashboard

## 🏗️ Building from Source

```bash
git clone https://github.com/Thijs226/FAH-ResourceDonor-plugin.git
cd FAH-ResourceDonor-plugin
chmod +x ./gradlew
./gradlew clean shadowJar
```

Output: `build/libs/FAHResourceDonor-X.X.X.jar`

## 🤝 Contributing

Contributions are welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## 📄 License

This plugin is licensed under the MIT License. See LICENSE file for details.

The Folding@home client is licensed under GPL-3.0. By using this plugin, you agree to the F@H client license.

## 🔗 Links

- **Folding@home**: https://foldingathome.org/
- **Get Passkey**: https://apps.foldingathome.org/getpasskey
- **F@H Dashboard**: https://app.foldingathome.org/
- **Team Stats**: https://stats.foldingathome.org/teams

## ⭐ Support the Project

If this plugin helps your server contribute to medical research, please:
- ⭐ Star this repository
- 🐛 Report bugs and issues
- 💡 Suggest improvements
- 📢 Share with other server owners

## 🙏 Credits

- **Thijs226** - Original plugin author
- **Folding@home** - Stanford University's distributed computing project
- **Spigot/Bukkit** - Minecraft server API

---

**Together, we're folding for a cure! 🧬**
