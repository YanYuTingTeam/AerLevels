package com.aermini.aerlevels.command;

import com.aermini.aerlevels.AerLevels;
import com.aermini.aerlevels.menu.RewardMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class OpenRewardCommand implements CommandExecutor {

    private final AerLevels plugin;

    public OpenRewardCommand(AerLevels plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!plugin.getConfigManager().isMenuEnabled()) return true;
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以打开等级奖励菜单");
            return true;
        }

        Player player = (Player) sender;
        new RewardMenu(plugin).openMenu(player);
        return true;
    }
}