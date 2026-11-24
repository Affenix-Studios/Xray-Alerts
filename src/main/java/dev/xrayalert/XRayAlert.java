package dev.xrayalert;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class XRayAlert extends JavaPlugin implements TabExecutor {

    private Messages messages;
    private final Set<String> oreKeys = new HashSet<>();
    private final Set<String> ignoredWorlds = new HashSet<>();
    private final Set<UUID> muted = new HashSet<>();

    private boolean veinMode;
    private int veinMaxSize;
    private long veinTrackingMillis;
    private boolean notifyConsole;
    private boolean showLocation;
    private boolean clickableTeleport;
    private boolean ignoreCreative;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        for (String code : new String[]{"en", "de"}) {
            File f = new File(getDataFolder(), "lang/" + code + ".yml");
            if (!f.exists()) {
                saveResource("lang/" + code + ".yml", false);
            }
        }

        messages = new Messages(this);
        loadSettings();

        BlockListener listener = new BlockListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        getServer().getScheduler().runTaskTimer(this, listener::cleanup, 1200L, 1200L);

        PluginCommand cmd = getCommand("xrayalert");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
    }

    private void loadSettings() {
        reloadConfig();
        FileConfiguration c = getConfig();

        messages.load(c.getString("language", "en"));

        veinMode = !"SINGLE".equalsIgnoreCase(c.getString("mode", "VEIN"));
        veinMaxSize = Math.max(1, c.getInt("vein.max-size", 64));
        veinTrackingMillis = Math.max(1, c.getInt("vein.tracking-seconds", 120)) * 1000L;
        notifyConsole = c.getBoolean("notify.console", true);
        showLocation = c.getBoolean("notify.show-location", true);
        clickableTeleport = c.getBoolean("notify.clickable-teleport", true);
        ignoreCreative = c.getBoolean("ignore.creative", true);

        ignoredWorlds.clear();
        for (String w : c.getStringList("ignore.worlds")) {
            ignoredWorlds.add(w.toLowerCase(Locale.ROOT));
        }

        oreKeys.clear();
        for (String name : c.getStringList("ores")) {
            Material m = Material.matchMaterial(name.trim());
            if (m == null) {
                getLogger().warning("Unknown material in config 'ores': " + name);
                continue;
            }
            oreKeys.add(normalize(m));
        }
        getLogger().info("Watching " + oreKeys.size() + " ore type(s), mode " + (veinMode ? "VEIN" : "SINGLE") + ".");
    }

    private static String normalize(Material m) {
        return m.name().replace("DEEPSLATE_", "");
    }

    public String oreKey(Material m) {
        String key = normalize(m);
        return oreKeys.contains(key) ? key : null;
    }

    public boolean veinMode() { return veinMode; }
    public int veinMaxSize() { return veinMaxSize; }
    public long veinTrackingMillis() { return veinTrackingMillis; }
    public boolean ignoreCreative() { return ignoreCreative; }
    public boolean isIgnoredWorld(String world) { return ignoredWorlds.contains(world.toLowerCase(Locale.ROOT)); }

    public void alert(Player miner, Block block, String key, int amount) {
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        String world = block.getWorld().getName();

        TagResolver res = TagResolver.resolver(
                Placeholder.unparsed("player", miner.getName()),
                Placeholder.unparsed("ore", messages.oreName(key)),
                Placeholder.unparsed("amount", Integer.toString(amount)),
                Placeholder.unparsed("world", world),
                Placeholder.unparsed("x", Integer.toString(x)),
                Placeholder.unparsed("y", Integer.toString(y)),
                Placeholder.unparsed("z", Integer.toString(z)));

        Component msg = messages.get(amount > 1 ? "alert-vein" : "alert-single", res);
        if (showLocation) {
            msg = msg.append(messages.get("location", res));
        }
        if (clickableTeleport) {
            String cmd = "/execute in " + block.getWorld().getKey().asString()
                    + " run tp @s " + x + " " + y + " " + z;
            msg = msg.clickEvent(ClickEvent.runCommand(cmd))
                    .hoverEvent(HoverEvent.showText(messages.get("hover-teleport", res)));
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("xrayalert.notify") && !muted.contains(p.getUniqueId())) {
                p.sendMessage(msg);
            }
        }
        if (notifyConsole) {
            Bukkit.getConsoleSender().sendMessage(msg);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(messages.get("usage"));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                if (!sender.hasPermission("xrayalert.admin")) {
                    sender.sendMessage(messages.get("no-permission"));
                    return true;
                }
                loadSettings();
                sender.sendMessage(messages.get("reloaded"));
            }
            case "toggle" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(messages.get("player-only"));
                    return true;
                }
                if (!player.hasPermission("xrayalert.notify")) {
                    sender.sendMessage(messages.get("no-permission"));
                    return true;
                }
                if (muted.remove(player.getUniqueId())) {
                    player.sendMessage(messages.get("toggle-on"));
                } else {
                    muted.add(player.getUniqueId());
                    player.sendMessage(messages.get("toggle-off"));
                }
            }
            default -> sender.sendMessage(messages.get("usage"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String s : new String[]{"reload", "toggle"}) {
                if (s.startsWith(prefix)) out.add(s);
            }
        }
        return out;
    }
}
