package com.thijs226.fahdonor.gui;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

/**
 * Advanced GUI system for displaying information and interactive menus
 */
public abstract class BaseGUI implements Listener {
    
    protected final Plugin plugin;
    protected final Player player;
    protected final String title;
    protected final int size;
    protected Inventory inventory;
    
    public BaseGUI(Plugin plugin, Player player, String title, int rows) {
        this.plugin = plugin;
        this.player = player;
        this.title = ChatColor.translateAlternateColorCodes('&', title);
        this.size = rows * 9;
        this.inventory = Bukkit.createInventory(null, size, this.title);
    }
    
    /**
     * Opens the GUI for the player
     */
    public void open() {
        buildInventory();
        player.openInventory(inventory);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    /**
     * Closes the GUI
     */
    public void close() {
        player.closeInventory();
        HandlerList.unregisterAll(this);
    }
    
    /**
     * Builds the inventory contents - implement in subclasses
     */
    protected abstract void buildInventory();
    
    /**
     * Handles click events - implement in subclasses
     */
    protected abstract void handleClick(InventoryClickEvent event);
    
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) {
            return;
        }
        
        event.setCancelled(true);
        
        if (event.getWhoClicked().equals(player)) {
            handleClick(event);
        }
    }
    
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory) && event.getPlayer().equals(player)) {
            HandlerList.unregisterAll(this);
        }
    }
    
    /**
     * Creates an item with name and lore
     */
    protected ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            
            if (lore.length > 0) {
                List<String> loreList = new ArrayList<>();
                for (String line : lore) {
                    loreList.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(loreList);
            }
            
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Creates an item with custom amount
     */
    protected ItemStack createItem(Material material, int amount, String name, String... lore) {
        ItemStack item = createItem(material, name, lore);
        item.setAmount(Math.max(1, Math.min(64, amount)));
        return item;
    }
    
    /**
     * Fills border with a material
     */
    protected void fillBorder(Material material) {
        ItemStack borderItem = new ItemStack(material);
        ItemMeta meta = borderItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            borderItem.setItemMeta(meta);
        }
        
        int rows = size / 9;
        
        // Top and bottom rows
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, borderItem);
            inventory.setItem((rows - 1) * 9 + i, borderItem);
        }
        
        // Left and right columns
        for (int i = 1; i < rows - 1; i++) {
            inventory.setItem(i * 9, borderItem);
            inventory.setItem(i * 9 + 8, borderItem);
        }
    }
    
    /**
     * Adds navigation buttons for pagination
     */
    protected void addNavigationButtons(int page, int maxPages, Runnable previousAction, Runnable nextAction) {
        if (page > 0) {
            ItemStack prevButton = createItem(Material.ARROW, 
                "&e← Previous Page",
                "&7Page " + page + " of " + (maxPages + 1));
            inventory.setItem(size - 9, prevButton);
        }
        
        if (page < maxPages) {
            ItemStack nextButton = createItem(Material.ARROW, 
                "&eNext Page →",
                "&7Page " + (page + 2) + " of " + (maxPages + 1));
            inventory.setItem(size - 1, nextButton);
        }
        
        // Close button in center
        ItemStack closeButton = createItem(Material.BARRIER, 
            "&cClose",
            "&7Click to close this menu");
        inventory.setItem(size - 5, closeButton);
    }
}
