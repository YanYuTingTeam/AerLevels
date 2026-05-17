package com.aermini.aerlevels.database;

import com.aermini.aerlevels.AerLevels;
import com.aermini.aerlevels.util.ExperienceUtil;
import com.connorlinfoot.titleapi.TitleAPI;
import org.apache.commons.jexl3.JexlEngine;
import com.aermini.aerlevels.util.PlaceholderUtil;
import org.bukkit.entity.Player;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.jexl3.MapContext;
import java.sql.*;
import java.util.UUID;

// 这个数据库是AI写的.
public class MySQLManager {
    private final AerLevels plugin;
    private Connection connection;
    private int heartbeatTaskId = -1;
    private String address;
    private String database;
    private String username;
    private String password;
    public MySQLManager(AerLevels plugin) {
        this.plugin = plugin;
        loadDatabaseConfig();
    }

    private void loadDatabaseConfig() {
        address = plugin.getConfigManager().getConfig().getString("database.address", "127.0.0.1:3306");
        database = plugin.getConfigManager().getConfig().getString("database.database", "minecraft");
        username = plugin.getConfigManager().getConfig().getString("database.username", "root");
        password = plugin.getConfigManager().getConfig().getString("database.password", "");
    }

    public void connect() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return;
        }
        String url = "jdbc:mysql://" + address + "/" + database + "?useSSL=false&characterEncoding=utf8&serverTimezone=UTC&autoReconnect=true&maxReconnects=3&connectTimeout=10000";
        connection = DriverManager.getConnection(url, username, password);
        startHeartbeat();
    }

    public void disconnect() throws SQLException {
        stopHeartbeat();
        if (connection != null && !connection.isClosed()) {
            connection.close();
            plugin.getLogger().info("MySQL 数据库连接已断开");
        }
    }

    private void startHeartbeat() {
        if (heartbeatTaskId != -1) {
            return;
        }

        heartbeatTaskId = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                if (connection != null && !connection.isClosed()) {
                    try (Statement stmt = connection.createStatement()) {
                        stmt.execute("SELECT 1");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("数据库心跳失败：" + e.getMessage());
            }
        }, 20L * 60 * 5, 20L * 60 * 5).getTaskId();
    }

    private void stopHeartbeat() {
        if (heartbeatTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(heartbeatTaskId);
            heartbeatTaskId = -1;
        }
    }

    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            plugin.getLogger().warning("数据库连接已断开，正在重新连接...");
            connect();
        }
        try {
            if (!connection.isValid(3)) {
                plugin.getLogger().warning("数据库连接无效，正在重新连接...");
                connection.close();
                connect();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("数据库连接验证失败，正在重新连接..." + e.getMessage());
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException ignored) {
            }
            connect();
        }

        return connection;
    }

    public void createTables() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS aerlevels_data (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "player_name VARCHAR(16) NOT NULL," +
                "level INT NOT NULL," +
                "xp DOUBLE NOT NULL," +
                "claimed_rewards TEXT" +
                ") CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;";
        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute(sql);
        }
    }

    public void initPlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        if (!hasPlayerData(uuid)) {
            int defaultLevel = plugin.getConfigManager().getMainLevelNormal();
            try (PreparedStatement pstmt = getConnection().prepareStatement(
                    "INSERT INTO aerlevels_data (uuid, player_name, level, xp, claimed_rewards) VALUES (?, ?, ?, ?, ?)")) {
                pstmt.setString(1, uuid.toString());
                pstmt.setString(2, player.getName());
                pstmt.setInt(3, defaultLevel);
                pstmt.setDouble(4, 0.0);
                pstmt.setString(5, "");
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("初始化玩家数据失败：" + e.getMessage());
            }
        }
    }

    public boolean hasPlayerData(UUID uuid) {
        try (PreparedStatement pstmt = getConnection().prepareStatement(
                "SELECT 1 FROM aerlevels_data WHERE uuid = ?")) {
            pstmt.setString(1, uuid.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("检查玩家数据失败：" + e.getMessage());
            return false;
        }
    }

    public int getPlayerLevel(UUID uuid) {
        try (PreparedStatement pstmt = getConnection().prepareStatement(
                "SELECT level FROM aerlevels_data WHERE uuid = ?")) {
            pstmt.setString(1, uuid.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("level");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取玩家等级失败：" + e.getMessage());
        }
        return plugin.getConfigManager().getMainLevelMin();
    }

    public void setPlayerLevel(UUID uuid, int level) {
        if (!hasPlayerData(uuid)) {
            int defaultLevel = plugin.getConfigManager().getMainLevelNormal();
            try (PreparedStatement pstmt = getConnection().prepareStatement(
                    "INSERT INTO aerlevels_data (uuid, player_name, level, xp, claimed_rewards) VALUES (?, ?, ?, ?, ?)")) {
                pstmt.setString(1, uuid.toString());
                pstmt.setString(2, "Unknown");
                pstmt.setInt(3, defaultLevel);
                pstmt.setDouble(4, 0.0);
                pstmt.setString(5, "");
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("初始化玩家数据失败：" + e.getMessage());
                return;
            }
        }

        int maxLevel = plugin.getConfigManager().getMainLevelMax();
        int minLevel = plugin.getConfigManager().getMainLevelMin();
        level = Math.max(minLevel, Math.min(maxLevel, level));
        try (PreparedStatement pstmt = getConnection().prepareStatement(
                "UPDATE aerlevels_data SET level = ? WHERE uuid = ?")) {
            pstmt.setInt(1, level);
            pstmt.setString(2, uuid.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("设置玩家等级失败：" + e.getMessage());
        }
        ExperienceUtil.syncExpBar(uuid);
    }

    public double getPlayerXP(UUID uuid) {
        try (PreparedStatement pstmt = getConnection().prepareStatement(
                "SELECT xp FROM aerlevels_data WHERE uuid = ?")) {
            pstmt.setString(1, uuid.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("xp");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取玩家经验失败：" + e.getMessage());
        }
        return 0.0;
    }

    public void setPlayerXP(UUID uuid, double xp) {
        if (!hasPlayerData(uuid)) {
            int defaultLevel = plugin.getConfigManager().getMainLevelNormal();
            try (PreparedStatement pstmt = getConnection().prepareStatement(
                    "INSERT INTO aerlevels_data (uuid, player_name, level, xp, claimed_rewards) VALUES (?, ?, ?, ?, ?)")) {
                pstmt.setString(1, uuid.toString());
                pstmt.setString(2, "Unknown");
                pstmt.setInt(3, defaultLevel);
                pstmt.setDouble(4, 0.0);
                pstmt.setString(5, "");
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("初始化玩家数据失败：" + e.getMessage());
                return;
            }
        }

        xp = Math.max(0, xp);
        try (PreparedStatement pstmt = getConnection().prepareStatement(
                "UPDATE aerlevels_data SET xp = ? WHERE uuid = ?")) {
            pstmt.setDouble(1, xp);
            pstmt.setString(2, uuid.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("设置玩家经验失败：" + e.getMessage());
        }

        ExperienceUtil.syncExpBar(uuid);
    }

    public void addPlayerXP(UUID uuid, double xp) {
        int startLevel = getPlayerLevel(uuid);
        double currentXP = getPlayerXP(uuid);
        setPlayerXP(uuid, currentXP + xp);
        while (checkLevelUp(uuid)) {
        }
        int finalLevel = getPlayerLevel(uuid);
        if (finalLevel > startLevel) {
            sendLevelUpMessage(uuid, finalLevel);
        }
        ExperienceUtil.syncExpBar(uuid);
    }

    private boolean checkLevelUp(UUID uuid) {
        int currentLevel = getPlayerLevel(uuid);
        double currentXP = getPlayerXP(uuid);
        double xpNeeded = calculateXPNeeded(currentLevel);
        if (currentXP >= xpNeeded && currentLevel < plugin.getConfigManager().getMainLevelMax()) {
            setPlayerLevel(uuid, currentLevel + 1);
            boolean doRemove = plugin.getConfigManager().isDoRemoveXP();
            boolean doReset = plugin.getConfigManager().isDoResetXP();
            if (doRemove) {
                setPlayerXP(uuid, currentXP - xpNeeded);
            } else if (doReset) {
                setPlayerXP(uuid, plugin.getConfigManager().getResetXPValue());
            }
            return true;
        }
        return false;
    }

    public double calculateXPNeeded(int level) {
        String rawFormula = plugin.getConfigManager().getLevelUpXPFormula();
        if (rawFormula == null || rawFormula.trim().isEmpty()) {
            plugin.getLogger().severe("升级经验公式配置为空！使用默认值 1000.0");
            return 1000.0;
        }

        String cleanedFormula = rawFormula.replaceAll("\\{(\\w+)\\}", "$1");
        JexlEngine jexl = new JexlBuilder().create();
        try {
            JexlContext context = new MapContext();
            context.set("level", level);
            JexlExpression expression = jexl.createExpression(cleanedFormula);
            Object result = expression.evaluate(context);
            if (result instanceof Number) {
                double xpNeeded = ((Number) result).doubleValue();
                return Math.max(0.0, xpNeeded);
            } else {
                plugin.getLogger().severe("经验公式计算结果不是数字！公式：" + rawFormula + " 等级：" + level);
                return 1000.0;
            }

        } catch (Exception e) {
            plugin.getLogger().severe("计算升级经验失败！公式：" + rawFormula + " 等级：" + level + " 错误：" + e.getMessage());
            e.printStackTrace();
            return 1000.0;
        }
    }

    private void sendLevelUpMessage(UUID uuid, int newLevel) {
        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            return;
        }

        double currentXP = getPlayerXP(uuid);
        double xpToNext = calculateXPNeeded(newLevel);
        double xpNext = Math.max(0, xpToNext - currentXP);
        int levelNext = (newLevel < plugin.getConfigManager().getMainLevelMax()) ? newLevel + 1 : newLevel;
        int levelLast = (newLevel > plugin.getConfigManager().getMainLevelMin()) ? newLevel - 1 : newLevel;
        String title = plugin.getConfig().getString("levelup.title", "");
        String subtitle = plugin.getConfig().getString("levelup.subtitle", "");
        String rawMessage = plugin.getConfig().getString("levelup.raw", "");
        title = PlaceholderUtil.parsePlaceholders(player, parseLevelUpPlaceholders(player, newLevel, currentXP, xpToNext, xpNext, levelNext, levelLast, title));
        subtitle = PlaceholderUtil.parsePlaceholders(player, parseLevelUpPlaceholders(player, newLevel, currentXP, xpToNext, xpNext, levelNext, levelLast, subtitle));
        rawMessage = PlaceholderUtil.parsePlaceholders(player, parseLevelUpPlaceholders(player, newLevel, currentXP, xpToNext, xpNext, levelNext, levelLast, rawMessage));
        title = title != null ? title.replace('&', '§') : "";
        subtitle = subtitle != null ? subtitle.replace('&', '§') : "";
        rawMessage = rawMessage != null ? rawMessage.replace('&', '§') : "";
        if (title != null && !title.equals("none") && subtitle != null && !subtitle.equals("none")) {
            TitleAPI.sendTitle(player, 20, 40, 20, title, subtitle);
        }

        if (rawMessage != null && !rawMessage.isEmpty()) {
            player.sendMessage(rawMessage);
        }
    }

    private String parseLevelUpPlaceholders(Player player, int level, double xp, double xpToNext, double xpNext, int levelNext, int levelLast, String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        text = text.replace("{player}", player.getName());
        text = text.replace("{prefix}", plugin.getConfigManager().getConfig().getString("levelup.prefix", ""));
        text = text.replace("{suffix}", plugin.getConfigManager().getConfig().getString("levelup.suffix", ""));
        text = text.replace("{level}", String.valueOf(level));
        text = text.replace("{level_next}", String.valueOf(levelNext));
        text = text.replace("{level_last}", String.valueOf(levelLast));
        text = text.replace("{xp}", String.valueOf((int) xp));
        text = text.replace("{xp_tonext}", String.valueOf((int) xpToNext));
        text = text.replace("{xp_next}", String.valueOf((int) xpNext));
        if (level >= plugin.getConfigManager().getMainLevelMax()) {
            String maxFormat = plugin.getConfigManager().getMainMaxFormat();
            text = text.replace("{xp_tonext}", maxFormat);
            text = text.replace("{xp_next}", maxFormat);
        }
        return text;
    }

    public void markRewardClaimed(UUID uuid, int level) {
        try (PreparedStatement pstmt = getConnection().prepareStatement(
                "UPDATE aerlevels_data SET claimed_rewards = CONCAT(IFNULL(claimed_rewards, ''), ?, ',') WHERE uuid = ?")) {
            pstmt.setString(1, String.valueOf(level));
            pstmt.setString(2, uuid.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("标记奖励已领取失败：" + e.getMessage());
        }
    }

    public boolean isRewardClaimed(UUID uuid, int level) {
        try (PreparedStatement pstmt = getConnection().prepareStatement(
                "SELECT claimed_rewards FROM aerlevels_data WHERE uuid = ?")) {
            pstmt.setString(1, uuid.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String claimed = rs.getString("claimed_rewards");
                    return claimed != null && (claimed.contains("," + level + ",")
                            || claimed.startsWith(level + ",")
                            || claimed.endsWith("," + level));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("检查奖励是否已领取失败：" + e.getMessage());
        }
        return false;
    }
}