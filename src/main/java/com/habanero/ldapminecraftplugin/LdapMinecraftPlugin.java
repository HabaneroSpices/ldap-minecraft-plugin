package com.habanero.ldapminecraftplugin;

import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.io.File;

public class LdapMinecraftPlugin extends JavaPlugin implements Listener {
    private LdapConnector ldapConnector;
    private boolean pluginEnabled = true;

    @Override
    public void onEnable() {
        // Save default config if it does not exist
        if (!new File(getDataFolder(), "config.yml").exists()) {
            saveDefaultConfig();
        }

        // Load configuration
        FileConfiguration config = getConfig();

        try {
            // Initialize LDAP connector
            ldapConnector = new LdapConnector(
                    config.getString("ldap.url"),
                    config.getString("ldap.baseDn"),
                    config.getString("ldap.userDn"),
                    config.getString("ldap.password"),
                    config.getString("ldap.userAttributeName"),
                    getLogger()
            );

            // Register event listeners
            getServer().getPluginManager().registerEvents(this, this);

            getLogger().info("LdapMinecraftPlugin has been enabled!");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize LdapMinecraftPlugin: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("LdapMinecraftPlugin has been disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ldapminecraft.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("ldapminecraft")) {
            if (args.length == 0) {
                sender.sendMessage("§6LDAP Minecraft Plugin Commands:");
                sender.sendMessage("§e/ldapminecraft enable §7- Enable LDAP checking");
                sender.sendMessage("§e/ldapminecraft disable §7- Disable LDAP checking");
                sender.sendMessage("§e/ldapminecraft status §7- Show current status");
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "enable":
                    pluginEnabled = true;
                    sender.sendMessage("§aLDAP checking has been enabled.");
                    getLogger().info("LDAP checking enabled by " + sender.getName());
                    break;
                case "disable":
                    pluginEnabled = false;
                    sender.sendMessage("§cLDAP checking has been disabled.");
                    getLogger().info("LDAP checking disabled by " + sender.getName());
                    break;
                case "status":
                    String status = pluginEnabled ? "§aEnabled" : "§cDisabled";
                    sender.sendMessage("§6LDAP checking is currently: " + status);
                    break;
                default:
                    sender.sendMessage("§cUnknown command. Use /ldapminecraft for help.");
            }
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        String username = event.getName();
        getLogger().info("Checking access for username: " + username);

        // If LDAP checking is disabled, allow all players
        if (!pluginEnabled) {
            getLogger().info("LDAP checking is disabled - allowing " + username);
            return;
        }

        // If built in whitelist is enabled, check there first
        if (getServer().hasWhitelist() && getServer().getOfflinePlayer(username).isWhitelisted()) {
            getLogger().info("Player " + username + " is whitelisted - allowing access");
            return;
        }

        // Check if the username exists in ldap directory
        getLogger().info("Checking LDAP for username: " + username);
        if (!ldapConnector.usernameExists(username)) {
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    getConfig().getString("messages.kickMessage", "Your username is not registered.")
            );
            getLogger().info("Player " + username + " was denied access - not found in LDAP");
        } else {
            // If the user was found, allow connection. To prevent whitelist kick behavior see "onPlayerLogin"
            event.setLoginResult(AsyncPlayerPreLoginEvent.Result.ALLOWED);
            getLogger().info("Player " + username + " verified successfully against LDAP");
        }
    }

    // If whitelist is enabled and the user isn't part of it but is allowed through ldap,
    // we must overwrite the whitelist decision, to prevent a kick.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        String username = event.getPlayer().getName();
        // If the player is going to be kicked by whitelist, and the user exists in the ldap directory, Allow connection
        if (event.getResult() == PlayerLoginEvent.Result.KICK_WHITELIST && ldapConnector.usernameExists(username)) {
            event.allow();
        }
    }
}
