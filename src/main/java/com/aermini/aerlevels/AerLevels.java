package com.aermini.aerlevels;

import com.aermini.aerlevels.command.AerXPCommand;
import com.aermini.aerlevels.command.OpenRewardCommand;
import com.aermini.aerlevels.config.ConfigManager;
import com.aermini.aerlevels.database.MySQLManager;
import com.aermini.aerlevels.listener.PlayerListener;
import com.aermini.aerlevels.placeholder.AerLevelsExpansion;
import org.bukkit.plugin.java.JavaPlugin;
import java.sql.SQLException;

public final class AerLevels extends JavaPlugin {
    private static AerLevels instance;
    private ConfigManager configManager;
    private MySQLManager mysqlManager;

    @Override
    public void onEnable() {
        instance = this;
        configManager = new ConfigManager(this);
        configManager.loadConfig();
        try {
            mysqlManager = new MySQLManager(this);
            mysqlManager.connect();
            mysqlManager.createTables();
        } catch (SQLException e) {
            getLogger().severe("数据库连接失败\n" + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        getCommand("axp").setExecutor(new AerXPCommand(this));
        getCommand("aerlevels").setExecutor(new AerXPCommand(this));
        getCommand("alevel").setExecutor(new AerXPCommand(this));
        getCommand("aerlevel").setExecutor(new AerXPCommand(this));
        getCommand("orw").setExecutor(new OpenRewardCommand(this));
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) { new AerLevelsExpansion(this).register(); }
        getLogger().info("AerLevels 插件已成功启用");
    }

    @Override
    public void onDisable() {
        if (mysqlManager != null) {
            try {
                mysqlManager.disconnect();
            } catch (SQLException e) {
                getLogger().severe("数据库断开连接失败\n" + e.getMessage());
            }
        }
        getLogger().info("AerLevels 插件已禁用");
    }

    public static AerLevels getInstance() {return instance;}
    public ConfigManager getConfigManager() {return configManager;}
    public MySQLManager getMysqlManager() {return mysqlManager;}
}