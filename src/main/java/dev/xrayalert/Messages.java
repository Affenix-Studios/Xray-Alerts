package dev.xrayalert;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class Messages {

    private final XRayAlert plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private YamlConfiguration lang = new YamlConfiguration();
    private YamlConfiguration fallback = new YamlConfiguration();

    public Messages(XRayAlert plugin) {
        this.plugin = plugin;
    }

    public void load(String code) {
        code = code.toLowerCase(Locale.ROOT).trim();
        File file = new File(plugin.getDataFolder(), "lang/" + code + ".yml");
        if (!file.exists()) {
            plugin.getLogger().warning("Language file lang/" + code + ".yml not found, falling back to en.");
            file = new File(plugin.getDataFolder(), "lang/en.yml");
        }
        lang = YamlConfiguration.loadConfiguration(file);

        try (InputStream in = plugin.getResource("lang/en.yml")) {
            if (in != null) {
                fallback = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not load fallback language: " + ex.getMessage());
        }
    }

    private String raw(String key) {
        String s = lang.getString(key);
        if (s == null) {
            s = fallback.getString(key, key);
        }
        return s;
    }

    public Component get(String key, TagResolver... resolvers) {
        return mm.deserialize(raw(key), resolvers);
    }

    public String oreName(String key) {
        String name = lang.getString("ores." + key);
        if (name == null) {
            name = fallback.getString("ores." + key);
        }
        return name != null ? name : prettify(key);
    }

    private static String prettify(String key) {
        StringBuilder sb = new StringBuilder();
        for (String part : key.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
