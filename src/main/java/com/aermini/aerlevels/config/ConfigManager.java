package com.aermini.aerlevels.config;

import com.aermini.aerlevels.AerLevels;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ConfigManager {
    private final AerLevels plugin;
    private File configFile;
    private FileConfiguration config;
    public ConfigManager(AerLevels plugin) {
        this.plugin = plugin;
        saveDefaultConfig();
    }

    public void loadConfig() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "config.yml");
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        InputStream defConfigStream = plugin.getResource("config.yml");
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defConfigStream, StandardCharsets.UTF_8));
            config.setDefaults(defConfig);
        }
    }

    public void saveDefaultConfig() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "config.yml");
        }
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
    }

    public void reloadConfig() {
        loadConfig();
    }

    public FileConfiguration getConfig() {
        if (config == null) {
            loadConfig();
        }
        return config;
    }

    public void saveConfig() {
        if (config == null || configFile == null) {
            return;
        }
        try {
            getConfig().save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("保存配置文件失败：" + e.getMessage());
        }
    }

    public int getMainLevelMin() {
        return getConfig().getInt("main.level_min", 1);
    }

    public int getMainLevelMax() {
        return getConfig().getInt("main.level_max", 100);
    }

    public String getMainMaxFormat() {
        return getConfig().getString("main.max_format", "MAX");
    }

    public int getMainLevelNormal() {
        String raw = getConfig().getString("main.level_normal", "1");
        if (raw.contains("{_main.level_min}")) {
            return getMainLevelMin();
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public String getLevelUpXPFormula() {
        return getConfig().getString("levelup.xp", "1250+750 *({level}-1)");
    }

    public String getLevelUpRawMessage() {
        return getConfig().getString("levelup.raw", "");
    }

    public boolean isDoResetXP() {
        return getConfig().getBoolean("levelup.doreset", true);
    }

    public int getResetXPValue() {
        return getConfig().getInt("levelup.resetxp", 0);
    }

    public boolean isDoRemoveXP() {
        return getConfig().getBoolean("levelup.doremove", true);
    }

    public boolean isLevelMatch(java.util.List<Integer> rewardLevels, int targetLevel) {
        if (rewardLevels == null || rewardLevels.isEmpty()) {
            return false;
        }
        int maxLevel = getMainLevelMax();
        if (rewardLevels.contains(targetLevel)) {
            return true;
        }

        for (int level : rewardLevels) {
            if (level == -1) {
                return true;
            } else if (level <= -2) {
                int excludeCount = Math.abs(level) - 1;
                int excludeStart = maxLevel - excludeCount + 1;
                if (targetLevel < excludeStart) {
                    return true;
                }
            }
        }
        return false;
    }

    public java.util.Set<Integer> getConfiguredRewardLevels() {
        java.util.Set<Integer> result = new java.util.TreeSet<>();
        if (!getConfig().isConfigurationSection("reward")) {
            return result;
        }

        int minLevel = getMainLevelMin();
        int maxLevel = getMainLevelMax();
        for (String rewardKey : getConfig().getConfigurationSection("reward").getKeys(false)) {
            String path = "reward." + rewardKey;
            java.util.List<Integer> rewardLevels = getConfig().getIntegerList(path + ".level");
            for (int level : rewardLevels) {
                if (level == -1) {
                    for (int i = minLevel; i <= maxLevel; i++) {
                        result.add(i);
                    }
                } else if (level <= -2) {
                    int excludeCount = Math.abs(level) - 1;
                    int excludeStart = maxLevel - excludeCount + 1;
                    for (int i = minLevel; i < excludeStart; i++) {
                        result.add(i);
                    }
                } else if (level >= minLevel && level <= maxLevel) {
                    result.add(level);
                }
            }
        }
        return result;
    }

    public String getMessageFail() {
        return getConfig().getString("message.fail", "&c等级不足,无法领取该奖励.");
    }

    public String getMessageGetXP() {
        return getConfig().getString("message.getxp", "&7恭喜你获得了&a{xp}&7点经验值!");
    }
}