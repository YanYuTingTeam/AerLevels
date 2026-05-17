package com.aermini.aerlevels.placeholder;

import com.aermini.aerlevels.AerLevels;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
public class AerLevelsExpansion extends PlaceholderExpansion {
    private final AerLevels plugin;
    public AerLevelsExpansion(AerLevels plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "axp";
    }

    @Override
    public String getAuthor() {
        return "AerMini";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return "";
        }
        switch (params.toLowerCase()) {
            case "level":
                return String.valueOf(plugin.getMysqlManager().getPlayerLevel(player.getUniqueId()));
            case "xp_tonext":
                int level = plugin.getMysqlManager().getPlayerLevel(player.getUniqueId());
                if (level >= plugin.getConfigManager().getMainLevelMax()) {
                    return plugin.getConfigManager().getMainMaxFormat();
                }
                return String.valueOf((int) plugin.getMysqlManager().calculateXPNeeded(level));
            case "xp_next":
                level = plugin.getMysqlManager().getPlayerLevel(player.getUniqueId());
                if (level >= plugin.getConfigManager().getMainLevelMax()) {
                    return "0";
                }
                double needed = plugin.getMysqlManager().calculateXPNeeded(level);
                double current = plugin.getMysqlManager().getPlayerXP(player.getUniqueId());
                return String.valueOf((int) Math.max(0, needed - current));
            case "xp":
                return String.valueOf((int) plugin.getMysqlManager().getPlayerXP(player.getUniqueId()));
            case "level_next":
                level = plugin.getMysqlManager().getPlayerLevel(player.getUniqueId());
                if (level >= plugin.getConfigManager().getMainLevelMax()) {
                    return String.valueOf(level);
                }
                return String.valueOf(level + 1);
            case "level_last":
                level = plugin.getMysqlManager().getPlayerLevel(player.getUniqueId());
                if (level <= plugin.getConfigManager().getMainLevelMin()) {
                    return String.valueOf(level);
                }
                return String.valueOf(level - 1);
            default:
                return "";
        }
    }
}