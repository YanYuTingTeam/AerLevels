package com.aermini.aerlevels.util;

import com.aermini.aerlevels.AerLevels;
import org.bukkit.entity.Player;

public class ExperienceUtil {
    private static final AerLevels plugin = AerLevels.getInstance();
    public static double calculateXPToNextLevel(int level) {
        return plugin.getMysqlManager().calculateXPNeeded(level);
    }

    public static double calculateRemainingXP(int playerLevel, double playerXP) {
        if (playerLevel >= plugin.getConfigManager().getMainLevelMax()) {
            return 0.0;
        }
        double xpNeeded = calculateXPToNextLevel(playerLevel);
        return Math.max(0, xpNeeded - playerXP);
    }

    public static boolean canLevelUp(int playerLevel, double playerXP) {
        if (playerLevel >= plugin.getConfigManager().getMainLevelMax()) {
            return false;
        }
        return playerXP >= calculateXPToNextLevel(playerLevel);
    }

    public static void syncExpBar(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        if (!plugin.getConfigManager().isXPBarEnabled()) {
            return;
        }

        java.util.UUID uuid = player.getUniqueId();
        int level = plugin.getMysqlManager().getPlayerLevel(uuid);
        double currentXP = plugin.getMysqlManager().getPlayerXP(uuid);
        int maxLevel = plugin.getConfigManager().getMainLevelMax();
        float expProgress;
        if (level >= maxLevel) {
            expProgress = 1.0f;
        } else {
            double xpNeeded = calculateXPToNextLevel(level);
            if (xpNeeded > 0) {
                expProgress = (float) (currentXP / xpNeeded);
                expProgress = Math.max(0.0f, Math.min(1.0f, expProgress));
            } else {
                expProgress = 0.0f;
            }
        }

        player.setLevel(level);
        player.setExp(expProgress);
    }

    public static void syncExpBar(java.util.UUID uuid) {
        Player player = plugin.getServer().getPlayer(uuid);
        syncExpBar(player);
    }
}