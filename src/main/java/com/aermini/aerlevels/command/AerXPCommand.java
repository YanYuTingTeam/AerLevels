package com.aermini.aerlevels.command;

import com.aermini.aerlevels.AerLevels;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.UUID;

public class AerXPCommand implements CommandExecutor {
    private final AerLevels plugin;
    public AerXPCommand(AerLevels plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("aerlevels.admin")) {
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.getConfigManager().reloadConfig();
            sender.sendMessage(org.bukkit.ChatColor.GREEN + "插件配置已重载！");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§e§lAerLevels §r§7for §b§lYanYuTing §8| §r§fby. §dAerMini");
            return true;
        }

        if (args.length < 2) {
            sendHelp(sender);
            return true;
        }

        String playerName = args[0];
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage("§c玩家 " + playerName + " 不在线！");
            return true;
        }

        UUID uuid = target.getUniqueId();
        switch (args[1].toLowerCase()) {
            case "help":
                sendHelp(sender);
                break;
            case "addxp":
                if (args.length < 3) {
                    sender.sendMessage("§c/axp <玩家> addxp <经验值>");
                    return true;
                }
                try {
                    double xp = Double.parseDouble(args[2]);
                    plugin.getMysqlManager().addPlayerXP(uuid, xp);
                    String xpMsg = plugin.getConfigManager().getMessageGetXP();
                    xpMsg = xpMsg.replace("{xp}", String.valueOf(xp));
                    target.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', xpMsg));
                    sender.sendMessage("§a已给 " + playerName + " 添加 " + xp + " 经验");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c经验值类型错误");
                }
                break;

            case "addlevel":
                if (args.length < 3) {
                    sender.sendMessage("§c/axp <player> addlevel <level>");
                    return true;
                }
                try {
                    int level = Integer.parseInt(args[2]);
                    int currentLevel = plugin.getMysqlManager().getPlayerLevel(uuid);
                    plugin.getMysqlManager().setPlayerLevel(uuid, currentLevel + level);
                    sender.sendMessage("§a已给 " + playerName + " 添加 " + level + " 等级");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c等级类型错误");
                }
                break;

            case "setxp":
                if (args.length < 3) {
                    sender.sendMessage("§c/axp <player> setxp <xp>");
                    return true;
                }
                try {
                    double xp = Double.parseDouble(args[2]);
                    plugin.getMysqlManager().setPlayerXP(uuid, xp);
                    sender.sendMessage("§a已将 " + playerName + " 的经验设置为 " + xp);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c经验值类型错误");
                }
                break;

            case "setlevel":
                if (args.length < 3) {
                    sender.sendMessage("§c/axp <player> setlevel <level>");
                    return true;
                }
                try {
                    int level = Integer.parseInt(args[2]);
                    plugin.getMysqlManager().setPlayerLevel(uuid, level);
                    sender.sendMessage("§a已将 " + playerName + " 的等级设置为 " + level);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c等级类型错误");
                }
                break;

            case "resetxp":
                plugin.getMysqlManager().setPlayerXP(uuid, 0);
                sender.sendMessage("§a已重置 " + playerName + " 的经验");
                break;

            case "resetlevel":
                plugin.getMysqlManager().setPlayerLevel(uuid, plugin.getConfigManager().getMainLevelNormal());
                sender.sendMessage("§a已重置 " + playerName + " 的等级");
                break;

            case "openreward":
                new com.aermini.aerlevels.menu.RewardMenu(plugin).openMenu(target);
                sender.sendMessage(org.bukkit.ChatColor.GREEN + "§a已为 " + playerName + " 打开等级奖励菜单");
                break;
            case "removelevel":
                if (args.length < 3) {
                    sender.sendMessage("§c/axp <player> removelevel <level>");
                    return true;
                }
                try {
                    int level = Integer.parseInt(args[2]);
                    int currentLevel = plugin.getMysqlManager().getPlayerLevel(uuid);
                    int newLevel = Math.max(plugin.getConfigManager().getMainLevelMin(), currentLevel - level);
                    plugin.getMysqlManager().setPlayerLevel(uuid, newLevel);
                    sender.sendMessage("§a已将 " + playerName + " 的等级减少 " + level + " 级. 当前 " + newLevel);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c等级类型错误");
                }
                break;

            case "removexp":
                if (args.length < 3) {
                    sender.sendMessage("§c/axp <player> removexp <xp>");
                    return true;
                }
                try {
                    double xp = Double.parseDouble(args[2]);
                    double currentXP = plugin.getMysqlManager().getPlayerXP(uuid);
                    double newXP = Math.max(0, currentXP - xp);
                    plugin.getMysqlManager().setPlayerXP(uuid, newXP);
                    sender.sendMessage("§a已将 " + playerName + " 的经验减少 " + xp + ". 当前经验 " + (int) newXP);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c经验值类型错误");
                }
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(org.bukkit.ChatColor.YELLOW + "/axp <player> addxp <xp> - 加经验");
        sender.sendMessage(org.bukkit.ChatColor.YELLOW + "/axp <player> addlevel <level> - 加等级");
        sender.sendMessage(org.bukkit.ChatColor.YELLOW + "/axp <player> setxp <xp> - 设置经验");
        sender.sendMessage(org.bukkit.ChatColor.YELLOW + "/axp <player> setlevel <level> - 设置等级");
        sender.sendMessage(org.bukkit.ChatColor.YELLOW + "/axp <player> removelevel <level> - 减等级");
        sender.sendMessage(org.bukkit.ChatColor.YELLOW + "/axp <player> removexp <xp> - 减经验");
        sender.sendMessage(org.bukkit.ChatColor.YELLOW + "/axp <player> resetxp - 重置经验");
        sender.sendMessage(org.bukkit.ChatColor.YELLOW + "/axp <player> resetlevel - 重置等级");
        sender.sendMessage(org.bukkit.ChatColor.YELLOW + "/axp <player> openreward (/orw是为自己打开) - 打开奖励菜单");
        sender.sendMessage(org.bukkit.ChatColor.YELLOW + "/axp reload");
    }
}