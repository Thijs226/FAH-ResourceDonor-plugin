#!/bin/bash

# FAH ResourceDonor Plugin Setup Script
# This script helps you configure the plugin for first use

echo "=================================================="
echo "     FAH ResourceDonor Plugin Setup"
echo "=================================================="
echo ""

# Check if config.yml exists
CONFIG_FILE="plugins/FAHResourceDonor/config.yml"

if [ ! -f "$CONFIG_FILE" ]; then
    echo "❌ Plugin not found! Make sure you have:"
    echo "   1. Installed the plugin JAR in plugins/ folder"
    echo "   2. Started the server once to generate config"
    echo ""
    exit 1
fi

echo "✅ Plugin configuration found at $CONFIG_FILE"
echo ""

# Check current token status
TOKEN_LINE=$(grep "token:" "$CONFIG_FILE")
if [[ "$TOKEN_LINE" == *'""'* ]]; then
    echo "⚠️  No FAH token configured!"
    echo ""
    echo "To get started:"
    echo "1. Visit: https://apps.foldingathome.org/getpasskey"
    echo "2. Enter your name and email to get a passkey"
    echo "3. Copy the ENTIRE passkey (32+ characters)"
    echo "4. Edit $CONFIG_FILE"
    echo "5. Replace token: \"\" with token: \"YOUR_PASSKEY_HERE\""
    echo "6. Run '/fah reload' in your server console"
    echo ""
    echo "Example:"
    echo "  token: \"a1b2c3d4e5f6789012345678901234ab\""
    echo ""
else
    echo "✅ Token appears to be configured"
    echo ""
fi

# Check debug mode
DEBUG_LINE=$(grep "debug:" "$CONFIG_FILE")
if [[ "$DEBUG_LINE" == *"false"* ]]; then
    echo "💡 Tip: Enable debug mode while setting up:"
    echo "   Change 'debug: false' to 'debug: true'"
    echo "   This shows detailed logs to help troubleshoot"
    echo ""
fi

echo "Quick Commands:"
echo "  /fah status     - Check if folding is working"
echo "  /fah diagnose   - Test your configuration"
echo "  /fah info       - Show settings and test connection"
echo "  /fah reload     - Reload config after changes"
echo ""

echo "Need help? Check TROUBLESHOOTING.md or run '/fah diagnose'"
echo "Stats will appear at: https://stats.foldingathome.org/donor/YourDonorName"
echo ""
echo "Happy folding! 🧬"