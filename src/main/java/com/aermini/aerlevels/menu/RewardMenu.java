package com.aermini.aerlevels.menu;

import com.aermini.aerlevels.AerLevels;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class RewardMenu {
    private final AerLevels plugin;
    private final int menuSize;
    private final String menuName;
    private static final Map<UUID, Integer> playerPages = new HashMap<>();
    public RewardMenu(AerLevels plugin) {
        this.plugin = plugin;
        this.menuSize = plugin.getConfigManager().getConfig().getInt("rmenu.slot", 54);
        this.menuName = plugin.getConfigManager().getConfig().getString("rmenu.name", "等级奖励");
    }

    public void openMenu(Player player) {
        openMenu(player, 1);
    }
    
    public void openMenu(Player player, int page) {
        java.util.Set<Integer> configuredLevels = plugin.getConfigManager().getConfiguredRewardLevels();
        java.util.List<Integer> levelList = new java.util.ArrayList<>(configuredLevels);
        List<Integer> slots = plugin.getConfigManager().getConfig().getIntegerList("rmenu.mainreward.slot");
        int totalPages = (int) Math.ceil((double) levelList.size() / slots.size());
        if (totalPages == 0) totalPages = 1;
        page = Math.max(1, Math.min(page, totalPages));
        playerPages.put(player.getUniqueId(), page);
        Inventory inventory = Bukkit.createInventory(null, menuSize, menuName);
        fillBackground(inventory);
        addCloseButton(inventory);
        addPageButtons(inventory, page, totalPages);
        addRewardItems(inventory, player, page, levelList);
        player.openInventory(inventory);
    }
    
    public static int getCurrentPage(UUID uuid) {
        return playerPages.getOrDefault(uuid, 1);
    }
    
    public static void setPage(UUID uuid, int page) {
        playerPages.put(uuid, page);
    }

    private void fillBackground(Inventory inventory) {
        String itemName = plugin.getConfigManager().getConfig().getString("rmenu.fills.item", "GRAY");
        String displayName = plugin.getConfigManager().getConfig().getString("rmenu.fills.name", "&f");
        List<String> lore = plugin.getConfigManager().getConfig().getStringList("rmenu.fills.lore");
        ItemStack fillItem = getStainedGlassPane(itemName);
        ItemMeta meta = fillItem.getItemMeta();
        meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', displayName));
        List<String> coloredLore = new ArrayList<>();
        for (String line : lore) {
            coloredLore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', line));
        }
        meta.setLore(coloredLore);
        fillItem.setItemMeta(meta);
        java.util.Set<Integer> skipSlots = new java.util.HashSet<>();
        skipSlots.add(plugin.getConfigManager().getConfig().getInt("rmenu.close.slot", 8));
        skipSlots.addAll(plugin.getConfigManager().getConfig().getIntegerList("rmenu.mainreward.slot"));
        for (int i = 0; i < inventory.getSize(); i++) {
            if (!skipSlots.contains(i)) {
                inventory.setItem(i, fillItem);
            }
        }
    }

    private void addCloseButton(Inventory inventory) {
        int slot = plugin.getConfigManager().getConfig().getInt("rmenu.close.slot", 8);
        String itemName = plugin.getConfigManager().getConfig().getString("rmenu.close.item", "RED");
        String displayName = plugin.getConfigManager().getConfig().getString("rmenu.close.name", "&c&l关闭");
        List<String> lore = plugin.getConfigManager().getConfig().getStringList("rmenu.close.lore");
        ItemStack closeItem = getStainedGlassPane(itemName);
        ItemMeta meta = closeItem.getItemMeta();
        meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', displayName));
        
        List<String> coloredLore = new ArrayList<>();
        for (String line : lore) {
            coloredLore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', line));
        }
        meta.setLore(coloredLore);
        closeItem.setItemMeta(meta);
        inventory.setItem(slot, closeItem);
    }

    private void addPageButtons(Inventory inventory, int currentPage, int totalPages) {
        int lastSlot = plugin.getConfigManager().getConfig().getInt("rmenu.lastpage.slot", 39);
        String lastItemName = plugin.getConfigManager().getConfig().getString("rmenu.lastpage.item", "STONE_BUTTON");
        String lastDisplayName = plugin.getConfigManager().getConfig().getString("rmenu.lastpage.name", "上一页");
        List<String> lastLore = plugin.getConfigManager().getConfig().getStringList("rmenu.lastpage.lore");
        if (currentPage > 1) {
            ItemStack lastItem = new ItemStack(Material.valueOf(lastItemName));
            ItemMeta meta = lastItem.getItemMeta();
            String displayNameWithPage = lastDisplayName + "§0[PAGE:" + (currentPage - 1) + "]";
            meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', displayNameWithPage));
            List<String> coloredLore = new ArrayList<>();
            for (String line : lastLore) {
                coloredLore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(coloredLore);
            lastItem.setItemMeta(meta);
            inventory.setItem(lastSlot, lastItem);
        }

        int nextSlot = plugin.getConfigManager().getConfig().getInt("rmenu.nextpage.slot", 41);
        String nextItemName = plugin.getConfigManager().getConfig().getString("rmenu.nextpage.item", "STONE_BUTTON");
        String nextDisplayName = plugin.getConfigManager().getConfig().getString("rmenu.nextpage.name", "下一页");
        List<String> nextLore = plugin.getConfigManager().getConfig().getStringList("rmenu.nextpage.lore");
        if (currentPage < totalPages) {
            ItemStack nextItem = new ItemStack(Material.valueOf(nextItemName));
            ItemMeta meta = nextItem.getItemMeta();
            String displayNameWithPage = nextDisplayName + "§0[PAGE:" + (currentPage + 1) + "]";
            meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', displayNameWithPage));
            List<String> coloredLore = new ArrayList<>();
            for (String line : nextLore) {
                coloredLore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(coloredLore);
            nextItem.setItemMeta(meta);
            inventory.setItem(nextSlot, nextItem);
        }
    }

    private void addRewardItems(Inventory inventory, Player player, int page, java.util.List<Integer> levelList) {
        List<Integer> slots = plugin.getConfigManager().getConfig().getIntegerList("rmenu.mainreward.slot");
        int startIndex = (page - 1) * slots.size();
        String displayName = plugin.getConfigManager().getConfig().getString("rmenu.fills.name", "&f");
        for (int i = 0; i < slots.size(); i++) {
            int slot = slots.get(i);
            int levelIndex = startIndex + i;
            if (levelIndex >= levelList.size()) {
                ItemStack rewardItem = getStainedGlassPane("GRAY");
                ItemMeta remeta = rewardItem.getItemMeta();
                remeta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', displayName));
                rewardItem.setItemMeta(remeta);
                inventory.setItem(slot, rewardItem);
                continue;
            }
            
            int level = levelList.get(levelIndex);
            ItemStack rewardItem = createRewardItem(player, level);
            inventory.setItem(slot, rewardItem);
        }
    }
    private ItemStack createRewardItem(Player player, int level) {
        String defaultItem = plugin.getConfigManager().getConfig().getString("rmenu.mainreward.item", "MILK_BUCKET");
        String clickItem = plugin.getConfigManager().getConfig().getString("rmenu.mainreward.click", "BUCKET");
        String displayName = plugin.getConfigManager().getConfig().getString("rmenu.mainreward.name", "&7&l等级奖励&r&f-&a&llv{item_level}");
        List<String> defaultLore = plugin.getConfigManager().getConfig().getStringList("rmenu.mainreward.lore");
        boolean canClaim = plugin.getMysqlManager().getPlayerLevel(player.getUniqueId()) >= level 
                && !plugin.getMysqlManager().isRewardClaimed(player.getUniqueId(), level);
        boolean claimed = plugin.getMysqlManager().isRewardClaimed(player.getUniqueId(), level);
        ItemStack item;
        List<String> lore;
        boolean glow;
        if (canClaim) {
            String canClaimItem = plugin.getConfigManager().getConfig().getString("rmenu.mainreward.can_claim.item", defaultItem);
            glow = plugin.getConfigManager().getConfig().getBoolean("rmenu.mainreward.can_claim.glow", true);
            lore = plugin.getConfigManager().getConfig().getStringList("rmenu.mainreward.can_claim.lore");
            item = new ItemStack(Material.valueOf(canClaimItem));
        } else if (claimed) {
            String claimedItem = plugin.getConfigManager().getConfig().getString("rmenu.mainreward.claimed.item", clickItem);
            glow = plugin.getConfigManager().getConfig().getBoolean("rmenu.mainreward.claimed.glow", false);
            lore = plugin.getConfigManager().getConfig().getStringList("rmenu.mainreward.claimed.lore");
            item = new ItemStack(Material.valueOf(claimedItem));
        } else {
            glow = false;
            lore = defaultLore;
            item = new ItemStack(Material.valueOf(defaultItem));
        }
        
        ItemMeta meta = item.getItemMeta();
        displayName = displayName.replace("{item_level}", String.valueOf(level));
        meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', displayName));
        List<String> coloredLore = new ArrayList<>();
        String statusText = getStatusText(canClaim, claimed);
        for (String line : lore) {
            line = line.replace("{item_statu}", statusText)
                    .replace("{item_level}", String.valueOf(level));
            coloredLore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', line));
        }
        meta.setLore(coloredLore);
        if (glow) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        
        item.setItemMeta(meta);
        return item;
    }

    private String getStatusText(boolean canClaim, boolean claimed) {
        if (canClaim) {
            return plugin.getConfigManager().getConfig().getString("rmenu.statu.can_claim", "&a点击领取该奖励");
        } else if (claimed) {
            return plugin.getConfigManager().getConfig().getString("rmenu.statu.claimed", "&e已领取该奖励");
        } else {
            return plugin.getConfigManager().getConfig().getString("rmenu.statu.cannot_claim", "&c无法领取该奖励");
        }
    }

    private ItemStack getStainedGlassPane(String name) {
        Material material = Material.STAINED_GLASS_PANE;
        short data = 0;
        switch (name.toUpperCase()) {
            case "RED":
                data = 14;
                break;
            case "RED_STAINED_GLASS_PANE":
                data = 14;
                break;
            case "GRAY":
                data = 7;
                break;
            case "GRAY_STAINED_GLASS_PANE":
                data = 7;
                break;
            case "BLACK":
                data = 15;
                break;
            case "BLACK_STAINED_GLASS_PANE":
                data = 15;
                break;
            default:
                data = 0;
                break;
        }
        return new ItemStack(material, 1, data);
    }
}