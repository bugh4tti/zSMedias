package com.zs.medias;

import com.zs.medias.commands.AnuncioCommand;
import com.zs.medias.commands.CreadoresCommand;
import com.zs.medias.commands.MediaCommand;
import com.zs.medias.commands.MediaTypeCommand;
import com.zs.medias.listeners.PlayerListener;
import com.zs.medias.managers.AnnounceManager;
import com.zs.medias.managers.CooldownManager;
import com.zs.medias.managers.CreatorManager;
import com.zs.medias.managers.CreatorRewards;
import com.zs.medias.managers.DataManager;
import com.zs.medias.managers.DiscordManager;
import com.zs.medias.managers.HistoryManager;
import com.zs.medias.managers.LiveManager;
import com.zs.medias.managers.LogManager;
import com.zs.medias.managers.MessageManager;
import com.zs.medias.menu.ConfigMenu;
import com.zs.medias.menu.CreadoresMenu;
import com.zs.medias.placeholders.MediaPlaceholders;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

public class ZSMedias extends JavaPlugin {

    private static ZSMedias instance;

    private MessageManager messages;
    private CreatorManager creators;
    private DataManager data;
    private CooldownManager cooldowns;
    private HistoryManager history;
    private LogManager logs;
    private DiscordManager discord;
    private AnnounceManager announcer;
    private LiveManager live;
    private CreatorRewards rewards;
    private CreadoresMenu creadoresMenu;
    private ConfigMenu configMenu;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        messages = new MessageManager(this);
        messages.load();

        creators = new CreatorManager(this);
        creators.load();

        data = new DataManager(this);
        data.load();

        cooldowns = new CooldownManager(this);
        cooldowns.load();

        history = new HistoryManager(this);
        history.load();

        logs = new LogManager(this);
        discord = new DiscordManager(this);
        announcer = new AnnounceManager(this);
        live = new LiveManager(this);
        rewards = new CreatorRewards(this);

        creadoresMenu = new CreadoresMenu(this);
        configMenu = new ConfigMenu(this);

        getServer().getPluginManager().registerEvents(creadoresMenu, this);
        getServer().getPluginManager().registerEvents(configMenu, this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        register("media", new MediaCommand(this));
        register("directo", new MediaTypeCommand(this, "directo"));
        register("video", new MediaTypeCommand(this, "video"));
        register("anuncio", new AnuncioCommand(this));
        register("creadores", new CreadoresCommand(this));

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new MediaPlaceholders(this).register();
            getLogger().info("PlaceholderAPI detectado: placeholders registrados.");
        }

        // Entrega los hitos que ya se hayan alcanzado (por ejemplo, si se agregaron hitos nuevos)
        rewards.checkAll();

        getLogger().info("zSMedias v" + getDescription().getVersion() + " habilitado.");
    }

    @Override
    public void onDisable() {
        if (live != null) {
            live.shutdown();
        }
        if (data != null) {
            data.save();
        }
        if (cooldowns != null) {
            cooldowns.save();
        }
        getLogger().info("zSMedias deshabilitado.");
    }

    private void register(String name, CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("El comando '" + name + "' no está en plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        if (executor instanceof TabCompleter completer) {
            command.setTabCompleter(completer);
        }
    }

    /** Recarga config.yml, el idioma y creadores.yml. */
    public void reloadAll() {
        reloadConfig();
        messages.load();
        creators.load();
        rewards.checkAll();
        creadoresMenu.refreshOpen();
    }

    public static ZSMedias getInstance() {
        return instance;
    }

    public MessageManager getMessages() {
        return messages;
    }

    public CreatorManager getCreators() {
        return creators;
    }

    public DataManager getData() {
        return data;
    }

    public CooldownManager getCooldowns() {
        return cooldowns;
    }

    public HistoryManager getHistory() {
        return history;
    }

    public LogManager getLogs() {
        return logs;
    }

    public DiscordManager getDiscord() {
        return discord;
    }

    public AnnounceManager getAnnouncer() {
        return announcer;
    }

    public LiveManager getLive() {
        return live;
    }

    public CreatorRewards getRewards() {
        return rewards;
    }

    public CreadoresMenu getCreadoresMenu() {
        return creadoresMenu;
    }

    public ConfigMenu getConfigMenu() {
        return configMenu;
    }
}
