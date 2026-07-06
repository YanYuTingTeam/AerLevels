package com.aermini.aerlevels.database;

import com.aermini.aerlevels.AerLevels;
import com.aermini.aerlevels.util.ExperienceUtil;
import com.aermini.aerlevels.util.PlaceholderUtil;
import com.connorlinfoot.titleapi.TitleAPI;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.jexl3.MapContext;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 这个数据库是AI写的.
public class MySQLManager {
    private final AerLevels plugin;
    private HikariDataSource dataSource;
    private int heartbeatTaskId = -1;
    private String address;
    private String database;
    private String username;
    private String password;

    private static final ConcurrentHashMap<UUID, CachedPlayerData> playerCache = new ConcurrentHashMap<>();

    public static class CachedPlayerData {
        public volatile int level;
        public volatile double xp;
        public volatile Set<Integer> claimedRewards;
        public volatile boolean fullyLoaded;

        public CachedPlayerData(int level, double xp) {
            this.level = level;
            this.xp = xp;
            this.claimedRewards = new HashSet<>();
            this.fullyLoaded = false;
        }
    }

    private JexlEngine jexlEngine;
    private JexlExpression cachedJexlExpression;
    private String cachedFormulaStr;

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
        if (dataSource != null && !dataSource.isClosed()) {
            return;
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + address + "/" + database
                + "?useSSL=false&characterEncoding=utf8&serverTimezone=UTC");
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(10000);
        config.setIdleTimeout(300000);
        config.setMaxLifetime(600000);
        config.setPoolName("AerLevels-Pool");
        dataSource = new HikariDataSource(config);
        jexlEngine = new JexlBuilder().create();
        cachedJexlExpression = null;
        cachedFormulaStr = null;

        startHeartbeat();
    }

    public void disconnect() throws SQLException {
        stopHeartbeat();
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("MySQL 连接池已关闭");
        }
    }

    private void startHeartbeat() {
        if (heartbeatTaskId != -1) {
            return;
        }
        heartbeatTaskId = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT 1");
            } catch (Exception e) {
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

    public void createTables() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS aerlevels_data (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "player_name VARCHAR(16) NOT NULL," +
                    "level INT NOT NULL," +
                    "xp DOUBLE NOT NULL" +
                    ") CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;");

            stmt.execute("CREATE TABLE IF NOT EXISTS aerlevels_claimed (" +
                    "uuid VARCHAR(36) NOT NULL," +
                    "level INT NOT NULL," +
                    "PRIMARY KEY (uuid, level)" +
                    ") CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;");
            migrateClaimedRewards(conn);
        }
    }

    private void migrateClaimedRewards(Connection conn) {
        try {
            boolean hasOldColumn = false;
            try (ResultSet rs = conn.getMetaData().getColumns(null, null, "aerlevels_data", "claimed_rewards")) {
                hasOldColumn = rs.next();
            }
            if (!hasOldColumn) return;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT uuid, claimed_rewards FROM aerlevels_data " +
                         "WHERE claimed_rewards IS NOT NULL AND claimed_rewards != ''")) {
                while (rs.next()) {
                    String uuid = rs.getString("uuid");
                    String claimed = rs.getString("claimed_rewards");
                    if (claimed == null || claimed.isEmpty()) continue;

                    String[] parts = claimed.split(",");
                    try (PreparedStatement pstmt = conn.prepareStatement(
                            "INSERT IGNORE INTO aerlevels_claimed (uuid, level) VALUES (?, ?)")) {
                        for (String part : parts) {
                            part = part.trim();
                            if (part.isEmpty()) continue;
                            try {
                                int lvl = Integer.parseInt(part);
                                pstmt.setString(1, uuid);
                                pstmt.setInt(2, lvl);
                                pstmt.executeUpdate();
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                }
            }

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE aerlevels_data DROP COLUMN claimed_rewards");
            }
            plugin.getLogger().info("claimed_rewards 迁移完成，旧列已删除");
        } catch (SQLException e) {
            plugin.getLogger().warning("迁移 claimed_rewards 时出错（可手动处理）：" + e.getMessage());
        }
    }

    public void removePlayerCache(UUID uuid) {
        playerCache.remove(uuid);
    }

    public void initPlayerDataAsync(Player player, Runnable onComplete) {
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = dataSource.getConnection()) {
                // 不存在则插入（INSERT IGNORE 幂等）
                try (PreparedStatement checkStmt = conn.prepareStatement(
                        "SELECT 1 FROM aerlevels_data WHERE uuid = ?")) {
                    checkStmt.setString(1, uuid.toString());
                    if (!checkStmt.executeQuery().next()) {
                        int defaultLevel = plugin.getConfigManager().getMainLevelNormal();
                        try (PreparedStatement insertStmt = conn.prepareStatement(
                                "INSERT INTO aerlevels_data (uuid, player_name, level, xp) VALUES (?, ?, ?, ?)")) {
                            insertStmt.setString(1, uuid.toString());
                            insertStmt.setString(2, player.getName());
                            insertStmt.setInt(3, defaultLevel);
                            insertStmt.setDouble(4, 0.0);
                            insertStmt.executeUpdate();
                        }
                    }
                }

                loadPlayerDataToCache(conn, uuid);

                if (onComplete != null) {
                    Bukkit.getScheduler().runTask(plugin, onComplete);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("初始化玩家数据失败：" + e.getMessage());
            }
        });
    }

    private void loadPlayerDataToCache(Connection conn, UUID uuid) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT level, xp FROM aerlevels_data WHERE uuid = ?")) {
            pstmt.setString(1, uuid.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    CachedPlayerData data = new CachedPlayerData(
                            rs.getInt("level"),
                            rs.getDouble("xp")
                    );
                    data.claimedRewards = new HashSet<>();
                    // 一次性加载已领取奖励
                    try (PreparedStatement pstmt2 = conn.prepareStatement(
                            "SELECT level FROM aerlevels_claimed WHERE uuid = ?")) {
                        pstmt2.setString(1, uuid.toString());
                        try (ResultSet rs2 = pstmt2.executeQuery()) {
                            while (rs2.next()) {
                                data.claimedRewards.add(rs2.getInt("level"));
                            }
                        }
                    }
                    data.fullyLoaded = true;
                    playerCache.put(uuid, data);
                }
            }
        }
    }

    public boolean hasPlayerData(UUID uuid) {
        CachedPlayerData data = playerCache.get(uuid);
        if (data != null && data.fullyLoaded) {
            return true;
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT 1 FROM aerlevels_data WHERE uuid = ?")) {
            pstmt.setString(1, uuid.toString());
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            plugin.getLogger().severe("检查玩家数据失败：" + e.getMessage());
            return false;
        }
    }

    public int getPlayerLevel(UUID uuid) {
        CachedPlayerData data = playerCache.get(uuid);
        if (data != null) {
            return data.level;
        }
        // 缓存未命中
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
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

    public double getPlayerXP(UUID uuid) {
        CachedPlayerData data = playerCache.get(uuid);
        if (data != null) {
            return data.xp;
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
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

    public Set<Integer> getClaimedRewards(UUID uuid) {
        CachedPlayerData data = playerCache.get(uuid);
        if (data != null && data.claimedRewards != null) {
            return new HashSet<>(data.claimedRewards);
        }
        Set<Integer> claimed = new HashSet<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT level FROM aerlevels_claimed WHERE uuid = ?")) {
            pstmt.setString(1, uuid.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    claimed.add(rs.getInt("level"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取已领取奖励列表失败：" + e.getMessage());
        }
        if (data != null) {
            data.claimedRewards = claimed;
        }
        return claimed;
    }

    public void setPlayerLevel(UUID uuid, int level) {
        int maxLevel = plugin.getConfigManager().getMainLevelMax();
        int minLevel = plugin.getConfigManager().getMainLevelMin();
        level = Math.max(minLevel, Math.min(maxLevel, level));

        ensurePlayerDataAsync(uuid, "Unknown");

        CachedPlayerData data = playerCache.get(uuid);
        if (data != null) {
            data.level = level;
        }

        final int fLevel = level;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(
                         "UPDATE aerlevels_data SET level = ? WHERE uuid = ?")) {
                pstmt.setInt(1, fLevel);
                pstmt.setString(2, uuid.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("设置玩家等级失败：" + e.getMessage());
            }
        });

        ExperienceUtil.syncExpBar(uuid);
    }

    public void setPlayerXP(UUID uuid, double xp) {
        xp = Math.max(0, xp);
        ensurePlayerDataAsync(uuid, "Unknown");

        CachedPlayerData data = playerCache.get(uuid);
        if (data != null) {
            data.xp = xp;
        }

        final double fXP = xp;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(
                         "UPDATE aerlevels_data SET xp = ? WHERE uuid = ?")) {
                pstmt.setDouble(1, fXP);
                pstmt.setString(2, uuid.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("设置玩家经验失败：" + e.getMessage());
            }
        });

        ExperienceUtil.syncExpBar(uuid);
    }

    public void addPlayerXP(UUID uuid, double xp) {
        CachedPlayerData data = playerCache.get(uuid);
        if (data == null) {
            plugin.getLogger().warning("addPlayerXP: 玩家数据未加载到缓存，跳过");
            return;
        }

        int startLevel = data.level;
        double currentXP = data.xp;
        double newXP = currentXP + xp;
        int newLevel = startLevel;
        int maxLevel = plugin.getConfigManager().getMainLevelMax();
        boolean doRemove = plugin.getConfigManager().isDoRemoveXP();
        boolean doReset = plugin.getConfigManager().isDoResetXP();

        // 纯内存计算升级
        while (newXP >= calculateXPNeeded(newLevel) && newLevel < maxLevel) {
            double needed = calculateXPNeeded(newLevel);
            if (doRemove) {
                newXP -= needed;
            } else if (doReset) {
                newXP = plugin.getConfigManager().getResetXPValue();
            }
            newLevel++;
            if (newLevel >= maxLevel) {
                newXP = 0;
                break;
            }
        }

        data.level = newLevel;
        data.xp = newXP;
        final int fLevel = newLevel;
        final double fXP = newXP;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(
                         "UPDATE aerlevels_data SET level = ?, xp = ? WHERE uuid = ?")) {
                pstmt.setInt(1, fLevel);
                pstmt.setDouble(2, fXP);
                pstmt.setString(3, uuid.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("持久化玩家数据失败：" + e.getMessage());
            }
        });

        if (fLevel > startLevel) {
            sendLevelUpMessage(uuid, fLevel);
        }

        ExperienceUtil.syncExpBar(uuid);
    }

    private void ensurePlayerDataAsync(UUID uuid, String playerName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(
                         "INSERT IGNORE INTO aerlevels_data (uuid, player_name, level, xp) VALUES (?, ?, ?, ?)")) {
                int defaultLevel = plugin.getConfigManager().getMainLevelNormal();
                pstmt.setString(1, uuid.toString());
                pstmt.setString(2, playerName);
                pstmt.setInt(3, defaultLevel);
                pstmt.setDouble(4, 0.0);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("确保玩家数据存在失败：" + e.getMessage());
            }
        });
    }

    public void markRewardClaimed(UUID uuid, int level) {
        CachedPlayerData data = playerCache.get(uuid);
        if (data != null && data.claimedRewards != null) {
            data.claimedRewards.add(level);
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(
                         "INSERT IGNORE INTO aerlevels_claimed (uuid, level) VALUES (?, ?)")) {
                pstmt.setString(1, uuid.toString());
                pstmt.setInt(2, level);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("标记奖励已领取失败：" + e.getMessage());
            }
        });
    }

    public boolean isRewardClaimed(UUID uuid, int level) {
        CachedPlayerData data = playerCache.get(uuid);
        if (data != null && data.claimedRewards != null) {
            return data.claimedRewards.contains(level);
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT 1 FROM aerlevels_claimed WHERE uuid = ? AND level = ?")) {
            pstmt.setString(1, uuid.toString());
            pstmt.setInt(2, level);
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            plugin.getLogger().severe("检查奖励是否已领取失败：" + e.getMessage());
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
        try {
            // 公式未变则复用已编译的表达式
            if (!cleanedFormula.equals(cachedFormulaStr) || cachedJexlExpression == null) {
                cachedJexlExpression = jexlEngine.createExpression(cleanedFormula);
                cachedFormulaStr = cleanedFormula;
            }

            JexlContext context = new MapContext();
            context.set("level", level);
            Object result = cachedJexlExpression.evaluate(context);
            if (result instanceof Number) {
                return Math.max(0.0, ((Number) result).doubleValue());
            } else {
                plugin.getLogger().severe("经验公式计算结果不是数字！公式：" + rawFormula + " 等级：" + level);
                return 1000.0;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("计算升级经验失败！公式：" + rawFormula + " 等级：" + level + " 错误：" + e.getMessage());
            return 1000.0;
        }
    }

    public void clearJexlCache() {
        cachedJexlExpression = null;
        cachedFormulaStr = null;
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
        title = title != null ? title.replace('&', '\u00a7') : "";
        subtitle = subtitle != null ? subtitle.replace('&', '\u00a7') : "";
        rawMessage = rawMessage != null ? rawMessage.replace('&', '\u00a7') : "";
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
}