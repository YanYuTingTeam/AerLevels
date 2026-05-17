package com.aermini.aerlevels.listener;

import com.aermini.aerlevels.AerLevels;
import com.aermini.aerlevels.util.ExperienceUtil;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class PlayerListener implements Listener {
    private final AerLevels plugin;
    public PlayerListener(AerLevels plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        plugin.getMysqlManager().initPlayerData(player);
        ExperienceUtil.syncExpBar(player);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        Inventory inventory = e.getInventory();
        ItemStack clickedItem = e.getCurrentItem();
        if (!inventory.getName().equals(plugin.getConfigManager().getConfig().getString("rmenu.name", "等级奖励"))) {
            return;
        }

        e.setCancelled(true);
        if ((clickedItem == null || clickedItem.getType() == Material.AIR) || handlePageButtonClick(player, e.getSlot(), clickedItem)) {
            return;
        }

        if (e.getSlot() == plugin.getConfigManager().getConfig().getInt("rmenu.close.slot", 8)) {
            player.closeInventory();
            return;
        }

        handleRewardClick(player, e.getSlot(), clickedItem, inventory);
    }

    private boolean handlePageButtonClick(Player player, int slot, ItemStack item) {
        int lastPageSlot = plugin.getConfigManager().getConfig().getInt("rmenu.lastpage.slot", 39);
        int nextPageSlot = plugin.getConfigManager().getConfig().getInt("rmenu.nextpage.slot", 41);
        if (slot != lastPageSlot && slot != nextPageSlot) {
            return false;
        }

        if (item.getItemMeta() != null && item.getItemMeta().getDisplayName() != null) {
            String displayName = item.getItemMeta().getDisplayName();
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[PAGE:(\\d+)\\]");
            java.util.regex.Matcher matcher = pattern.matcher(displayName);
            if (matcher.find()) {
                try {
                    int targetPage = Integer.parseInt(matcher.group(1));
                    new com.aermini.aerlevels.menu.RewardMenu(plugin).openMenu(player, targetPage);
                    return true;
                } catch (NumberFormatException ex) {
                    plugin.getLogger().warning("解析页码失败 " + ex.getMessage());
                }
            }
        }
        return false;
    }

    private void handleRewardClick(Player player, int slot, ItemStack item, Inventory inventory) {
        if (item == null || item.getItemMeta() == null || item.getItemMeta().getDisplayName() == null) {
            return;
        }

        String itemName = item.getItemMeta().getDisplayName();
        int targetLevel = -1;
        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d+");
            java.util.regex.Matcher matcher = pattern.matcher(itemName);
            java.util.List<Integer> numbers = new java.util.ArrayList<>();
            while (matcher.find()) {
                numbers.add(Integer.parseInt(matcher.group()));
            }

            if (!numbers.isEmpty()) {
                targetLevel = numbers.get(numbers.size() - 1);
            }

            if (targetLevel == -1) {
                plugin.getLogger().warning("从物品名称中无法解析等级 " + itemName);
                return;
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("解析奖励等级失败 " + ex.getMessage());
            ex.printStackTrace();
            return;
        }

        String defaultItemName = plugin.getConfigManager().getConfig().getString("rmenu.mainreward.item", "MILK_BUCKET");
        String clickItemName = plugin.getConfigManager().getConfig().getString("rmenu.mainreward.click", "BUCKET");
        boolean isDefaultType = item.getType() == Material.valueOf(defaultItemName);
        int playerLevel = plugin.getMysqlManager().getPlayerLevel(player.getUniqueId());
        boolean isClaimed = plugin.getMysqlManager().isRewardClaimed(player.getUniqueId(), targetLevel);
        try {
            ItemStack newItem = new ItemStack(Material.valueOf(clickItemName));
            ItemMeta oldMeta = item.getItemMeta();
            ItemMeta newMeta = newItem.getItemMeta();
            newMeta.setDisplayName(oldMeta.getDisplayName());
            List<String> newLore = new ArrayList<>();
            String statusText = plugin.getConfigManager().getConfig().getString("rmenu.statu.claimed", "&e已领取该奖励");
            if (oldMeta.getLore() != null) {
                for (String line : oldMeta.getLore()) {
                    if (line.contains("点击领取") || line.contains("无法领取")) {newLore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', statusText));
                    } else {newLore.add(line);}
                }
            }
            newMeta.setLore(newLore);
            newMeta.removeEnchant(org.bukkit.enchantments.Enchantment.DURABILITY);
            newItem.setItemMeta(newMeta);
            inventory.setItem(slot, newItem);
        } catch (Exception ex) {
            plugin.getLogger().warning("更新物品失败 -> " + ex.getMessage());
            ex.printStackTrace();
        }
        if (isDefaultType && playerLevel < targetLevel) {
            String failMessage = plugin.getConfigManager().getMessageFail();
            player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', failMessage));
        }
        if (isClaimed){return;}
        if (playerLevel < targetLevel) {return;} else {
            try {executeRewardCommands(player, targetLevel);} catch (Exception ex) {
                plugin.getLogger().severe("执行命令失败 -> " + ex.getMessage());
                ex.printStackTrace();
                return;
            }
            try {plugin.getMysqlManager().markRewardClaimed(player.getUniqueId(), targetLevel);} catch (Exception ex) {
                plugin.getLogger().severe("标记领取失败 -> " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    private void executeRewardCommands(Player player, int level) {
        if (!plugin.getConfigManager().getConfig().isConfigurationSection("reward")) {
            plugin.getLogger().warning("奖励配置不存在！");
            return;
        }

        for (String rewardKey : plugin.getConfigManager().getConfig().getConfigurationSection("reward").getKeys(false)) {
            String path = "reward." + rewardKey;
            java.util.List<Integer> rewardLevels = plugin.getConfigManager().getConfig().getIntegerList(path + ".level");
            boolean isMatch = plugin.getConfigManager().isLevelMatch(rewardLevels, level);
            if (!isMatch) {
                continue;
            }

            java.util.List<String> commands = plugin.getConfigManager().getConfig().getStringList(path + ".cmd");
            for (String cmd : commands) {
                executeCommand(player, cmd);
            }
        }
    }

    private void executeCommand(Player player, String cmd) {
        cmd = cmd.replace("{player}", player.getName())
                .replace("{level}", String.valueOf(plugin.getMysqlManager().getPlayerLevel(player.getUniqueId())));
        if (cmd.startsWith("[COMMAND_]")) {
            String actualCmd = cmd.substring("[COMMAND_]".length()).trim();
            player.performCommand(actualCmd);
        } else if (cmd.startsWith("[CMD_]")) {
            String actualCmd = cmd.substring("[CMD_]".length()).trim();
            player.performCommand(actualCmd);
        } else if (cmd.startsWith("[OPCMD_]")) {
            String actualCmd = cmd.substring("[OPCMD_]".length()).trim();
            boolean isOp = player.isOp();
            player.setOp(true);
            player.performCommand(actualCmd);
            player.setOp(isOp);
        } else if (cmd.startsWith("[CONSOLE_]")) {
            String actualCmd = cmd.substring("[CONSOLE_]".length()).trim();
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), actualCmd);
        } else {
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd);
        }
    }
}