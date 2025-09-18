package com.thijs226.fahdonor.display;

import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import com.thijs226.fahdonor.FAHClientManager.FoldingCause;

public class ResearchInfoDisplay {
    
    public static void showResearchInfo(Player player, FoldingCause cause) {
        player.sendMessage(ChatColor.GOLD + "════════════════════════════");
        
        switch (cause) {
            case COVID_19:
                player.sendMessage(ChatColor.RED + "🦠 COVID-19 Research");
                player.sendMessage(ChatColor.WHITE + "Helping understand:");
                player.sendMessage(ChatColor.GRAY + "• How the virus infects cells");
                player.sendMessage(ChatColor.GRAY + "• Potential drug targets");
                player.sendMessage(ChatColor.GRAY + "• Vaccine development");
                player.sendMessage(ChatColor.AQUA + "Impact: " + ChatColor.WHITE + "Pandemic response");
                break;
                
            case CANCER:
                player.sendMessage(ChatColor.LIGHT_PURPLE + "🎗 Cancer Research");
                player.sendMessage(ChatColor.WHITE + "Working on:");
                player.sendMessage(ChatColor.GRAY + "• Protein misfolding in cancer");
                player.sendMessage(ChatColor.GRAY + "• Drug resistance mechanisms");
                player.sendMessage(ChatColor.GRAY + "• Targeted therapies");
                player.sendMessage(ChatColor.AQUA + "Impact: " + ChatColor.WHITE + "Better treatments");
                break;
                
            case ALZHEIMERS:
                player.sendMessage(ChatColor.YELLOW + "🧠 Alzheimer's Research");
                player.sendMessage(ChatColor.WHITE + "Studying:");
                player.sendMessage(ChatColor.GRAY + "• Amyloid beta aggregation");
                player.sendMessage(ChatColor.GRAY + "• Tau protein tangles");
                player.sendMessage(ChatColor.GRAY + "• Early detection markers");
                player.sendMessage(ChatColor.AQUA + "Impact: " + ChatColor.WHITE + "Memory preservation");
                break;
                
            default:
                player.sendMessage(ChatColor.GREEN + "✅ All Research Areas");
                player.sendMessage(ChatColor.WHITE + "Contributing to:");
                player.sendMessage(ChatColor.GRAY + "• Whatever needs it most");
                player.sendMessage(ChatColor.GRAY + "• Balanced approach");
                player.sendMessage(ChatColor.GRAY + "• Flexible allocation");
                player.sendMessage(ChatColor.AQUA + "Impact: " + ChatColor.WHITE + "Broad support");
                break;
        }
        
        player.sendMessage(ChatColor.GOLD + "════════════════════════════");
        player.sendMessage(ChatColor.GRAY + "Learn more: " + 
                          ChatColor.AQUA + ChatColor.UNDERLINE + 
                          "https://foldingathome.org/diseases/");
    }
}