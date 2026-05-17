package com.aermini.aerlevels.util;

import me.clip.placeholderapi.PlaceholderAPI;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import com.aermini.aerlevels.AerLevels;

public class PlaceholderUtil {
    private static AerLevels plugin;
    private static Economy econ = null;
    private static Permission perms = null;
    private static Chat chat = null;
    public static void init(AerLevels main) {
        plugin = main;
        setupEconomy();
        setupPermissions();
        setupChat();
    }

    public static String parsePlaceholders(Player player, String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String parsed = PlaceholderAPI.setPlaceholders(player, text);
        if (chat != null) {
            parsed = parsed.replace("%vault_prefix%", chat.getPlayerPrefix(player));
            parsed = parsed.replace("%vault_suffix%", chat.getPlayerSuffix(player));
        }
        return parsed.replace('&', '§');
    }

    private static boolean setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    private static boolean setupPermissions() {
        RegisteredServiceProvider<Permission> rsp = Bukkit.getServicesManager().getRegistration(Permission.class);
        if (rsp == null) {
            return false;
        }
        perms = rsp.getProvider();
        return perms != null;
    }

    private static boolean setupChat() {
        RegisteredServiceProvider<Chat> rsp = Bukkit.getServicesManager().getRegistration(Chat.class);
        if (rsp == null) {
            return false;
        }
        chat = rsp.getProvider();
        return chat != null;
    }

    public static Economy getEconomy() {
        return econ;
    }

    public static Permission getPermissions() {return perms;}

    public static Chat getChat() {return chat;}
}