package dev.duong2012g.combatgun.manager;

import dev.duong2012g.combatgun.CombatGunSSSPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Internationalization (i18n) manager for CombatGunSSS.
 * <p>
 * Loads player-facing messages from {@code lang/<code>.yml} inside the plugin data folder.
 * Falls back to the bundled English file if a key is missing in the configured language.
 *
 * <h3>Configuration</h3>
 * Set the active language in {@code config.yml}:
 * <pre>
 * combatgun:
 *   language: en    # en | vi | (any lang/*.yml filename without extension)
 * </pre>
 *
 * <h3>Adding a new language</h3>
 * Copy {@code lang/en.yml} to {@code lang/xx.yml} in the plugin data folder and
 * translate the values. The file is loaded automatically on the next {@code /gun reload}.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * String msg = plugin.getLangManager().get("gun.disabled_world");
 * player.sendActionBar(Component.text(msg, NamedTextColor.RED));
 * }</pre>
 *
 * Message values support {@code {0}}, {@code {1}} … placeholders:
 * <pre>{@code
 * // lang/en.yml:
 * //   gun.no_permission: "⛔ No permission to use {0}."
 * String msg = lang.format("gun.no_permission", gun.getName());
 * }</pre>
 */
public class LangManager {

    private final CombatGunSSSPlugin plugin;
    private final Map<String, String> messages = new HashMap<>();

    public LangManager(CombatGunSSSPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * (Re)loads the language file. Call once from {@code onEnable()} and again
     * from {@code /gun reload}.
     */
    public void load() {
        messages.clear();
        String langCode = plugin.getConfig()
            .getString("combatgun.language", "en")
            .toLowerCase();

        // 1. Load bundled English defaults first (always present in jar).
        loadFromJar("lang/en.yml");

        // 2. Save the configured language file to disk if it does not exist yet.
        String resourcePath = "lang/" + langCode + ".yml";
        File   diskFile     = new File(plugin.getDataFolder(), resourcePath);
        if (!diskFile.exists()) {
            if (plugin.getResource(resourcePath) != null) {
                plugin.saveResource(resourcePath, false);
            } else if (!langCode.equals("en")) {
                plugin.getLogger().warning("[LangManager] Language file not found: " + resourcePath
                    + ". Falling back to English.");
                return;
            }
        }

        // 3. Overlay with the configured language (overrides English defaults).
        if (!langCode.equals("en")) {
            loadFromDisk(diskFile, langCode);
        } else {
            // For "en", also load from disk so admins can customise messages.
            if (diskFile.exists()) loadFromDisk(diskFile, langCode);
        }

        plugin.getLogger().info("[LangManager] Loaded " + messages.size()
            + " messages (lang=" + langCode + ").");
    }

    /**
     * Returns the message for {@code key}, or a fallback string if the key is missing.
     *
     * @param key dot-separated key, e.g. {@code "gun.disabled_world"}
     * @return The translated message, never {@code null}.
     */
    public String get(String key) {
        return messages.getOrDefault(key, "§c[missing lang key: " + key + "]");
    }

    /**
     * Returns the message with {@code {0}}, {@code {1}} … placeholders replaced.
     *
     * @param key  dot-separated key
     * @param args replacement values in order
     */
    public String format(String key, Object... args) {
        String msg = get(key);
        for (int i = 0; i < args.length; i++) {
            msg = msg.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return msg;
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    /** Load from a bundled jar resource into the messages map. */
    private void loadFromJar(String resourcePath) {
        InputStream is = plugin.getResource(resourcePath);
        if (is == null) return;
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(
                new InputStreamReader(is, StandardCharsets.UTF_8));
            flatten("", cfg, messages);
        } catch (Exception e) {
            plugin.getLogger().warning("[LangManager] Failed to load bundled " + resourcePath + ": " + e.getMessage());
        }
    }

    /** Load from a file on disk, overriding existing keys. */
    private void loadFromDisk(File file, String langCode) {
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            flatten("", cfg, messages);
        } catch (Exception e) {
            plugin.getLogger().warning("[LangManager] Failed to load lang/" + langCode + ".yml: " + e.getMessage());
        }
    }

    /** Recursively flatten a YamlConfiguration into dot-separated key → string pairs. */
    private void flatten(String prefix, YamlConfiguration cfg, Map<String, String> out) {
        for (String key : cfg.getKeys(false)) {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            if (cfg.isConfigurationSection(key)) {
                // Recurse — but we need a sub-section, not a top-level cfg
                YamlConfiguration sub = new YamlConfiguration();
                var section = cfg.getConfigurationSection(key);
                if (section != null) {
                    for (String subKey : section.getKeys(true)) {
                        if (!section.isConfigurationSection(subKey)) {
                            out.put(fullKey + "." + subKey, String.valueOf(section.get(subKey)));
                        }
                    }
                }
            } else {
                out.put(fullKey, String.valueOf(cfg.get(key)));
            }
        }
    }
}
