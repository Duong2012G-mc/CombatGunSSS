package dev.duong2012g.combatgun.manager;

import dev.duong2012g.combatgun.CombatGunSSSPlugin;
import dev.duong2012g.combatgun.data.GunData;
import dev.duong2012g.combatgun.data.RecipeData;
import dev.duong2012g.combatgun.data.RecoilData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GunManager {

    private static final String GUNS_FOLDER = "guns";
    private static final String MELEES_FOLDER = "melees";
    private static final List<String> BUILT_IN_GUN_IDS = List.of(
        "ak47","an94","aug","famas","g36","groza","kingfisher","m14","m4a1","parafal","scar","xm8",
        "bizon","cg15","mac10","mp40","mp5","p90","thompson","ump","vector","vss",
        "awm","kar98k","m24","m82b","vsk94",
        "m1014","m1887","m590","mag7","spas12","trogon",
        "desert_eagle","g18","m1873","m1917","m500","usp",
        "m107"
    );
    private static final List<String> BUILT_IN_MELEE_IDS = List.of(
        "bat","katana","knife","pan","parang","scythe"
    );

    private final CombatGunSSSPlugin plugin;
    private final Map<String, GunData> guns = new LinkedHashMap<>();
    private final Map<String, RecipeData> gunRecipes = new LinkedHashMap<>();
    private boolean worldWhitelistMode = false;
    private final List<String> worldFilterList = new ArrayList<>();

    public GunManager(CombatGunSSSPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public void loadGuns() {
        guns.clear();
        gunRecipes.clear();
        loadWorldConfig();

        // Load guns from guns/ folder
        File gunsDir = new File(plugin.getDataFolder(), GUNS_FOLDER);
        if (!gunsDir.exists()) {
            gunsDir.mkdirs();
        }
        saveDefaultGunFiles(gunsDir, BUILT_IN_GUN_IDS, GUNS_FOLDER);
        loadFromFolder(gunsDir, "gun");

        // Load melees from melees/ folder
        File meleesDir = new File(plugin.getDataFolder(), MELEES_FOLDER);
        if (!meleesDir.exists()) {
            meleesDir.mkdirs();
        }
        saveDefaultGunFiles(meleesDir, BUILT_IN_MELEE_IDS, MELEES_FOLDER);
        loadFromFolder(meleesDir, "melee");

        plugin.getLogger().info("Loaded " + guns.size() + " weapons (guns + melees).");
    }

    private void loadFromFolder(File folder, String type) {
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().warning("No " + type + " YAML files in " + folder.getPath());
            return;
        }

        List<File> sorted = new ArrayList<>(List.of(files));
        sorted.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        for (File file : sorted) {
            String id = file.getName().replaceAll("(?i)\\.yml$", "").toLowerCase(Locale.ROOT);
            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                // ✅ FIX: Validate critical fields before building GunData to catch
                // misconfigured guns early with a clear error message, rather than
                // letting them load silently and cause unexpected gameplay issues.
                List<String> validationErrors = validateGunConfig(id, cfg);
                if (!validationErrors.isEmpty()) {
                    plugin.getLogger().warning("Skipping " + type + " '" + id + "' — config validation failed:");
                    validationErrors.forEach(err -> plugin.getLogger().warning("  • " + err));
                    continue;
                }
                GunData gun = parseGun(id, cfg);
                guns.put(id, gun);
                if (gun.getRecipe() != null) {
                    gunRecipes.put(id, gun.getRecipe());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load " + type + " '" + id + "' (" + file.getName() + "): " + e.getMessage());
            }
        }
    }

    public boolean isAllowedInWorld(String worldName) {
        if (worldFilterList.isEmpty()) return true;
        boolean inList = worldFilterList.stream().anyMatch(w -> w.equalsIgnoreCase(worldName));
        return worldWhitelistMode ? inList : !inList;
    }

    private void loadWorldConfig() {
        worldFilterList.clear();
        org.bukkit.configuration.ConfigurationSection worlds = plugin.getConfig().getConfigurationSection("combatgun.worlds");
        if (worlds == null) return;
        worldWhitelistMode = "whitelist".equalsIgnoreCase(worlds.getString("mode", "blacklist"));
        List<String> list = worlds.getStringList("list");
        if (list != null) worldFilterList.addAll(list);
    }

    public GunData getGun(String id) {
        return id == null ? null : guns.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<GunData> getAllGuns() {
        return Collections.unmodifiableCollection(guns.values());
    }

    public Collection<RecipeData> getAllGunRecipes() {
        return Collections.unmodifiableCollection(gunRecipes.values());
    }

    public RecipeData getGunRecipe(String id) {
        return id == null ? null : gunRecipes.get(id.toLowerCase(Locale.ROOT));
    }

    public int getGunCount() {
        return guns.size();
    }

    public boolean exists(String id) {
        return id != null && guns.containsKey(id.toLowerCase(Locale.ROOT));
    }

    // ── Default extraction ─────────────────────────────────────────────────────

    /**
     * Copy bundled YAMLs from the JAR into the specified folder.
     * Never overwrites existing files so admins can edit them freely.
     */
    private void saveDefaultGunFiles(File destDir, List<String> ids, String resourceFolder) {
        for (String id : ids) {
            File dest = new File(destDir, id + ".yml");
            if (!dest.exists()) {
                String resourcePath = resourceFolder + "/" + id + ".yml";
                InputStream in = plugin.getResource(resourcePath);
                if (in != null) {
                    try {
                        plugin.saveResource(resourcePath, false);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Could not save default file: " + resourcePath);
                    }
                }
            }
        }
    }

    // ── Parsing ────────────────────────────────────────────────────────────────

    private GunData parseGun(String id, ConfigurationSection sec) {
        String name     = sec.getString("name", id.toUpperCase(Locale.ROOT));
        String category = sec.getString("category", "unknown");
        String ammoType = sec.getString("ammo_type", "none");
        int damage      = sec.getInt("damage", 10);
        double reload   = sec.getDouble("reload_time", 2.0);
        int mag         = sec.getInt("magazine_size", 30);
        double fireRate = sec.getDouble("fire_rate", 10.0);
        double hs       = sec.getDouble("headshot_multiplier", 1.5);
        String rarity   = sec.getString("rarity", "common");
        String sound    = sec.getString("sound", "ENTITY_FIREWORK_ROCKET_BLAST");
        int cmd         = sec.getInt("custom_model_data", 0);

        RecoilData recoil = new RecoilData(
            sec.getDouble("recoil.pitch",    defaultPitch(category, id)),
            sec.getDouble("recoil.yaw",      defaultYaw(category, id)),
            sec.getDouble("recoil.spread",   defaultSpread(category, id)),
            sec.getDouble("recoil.recovery", defaultRecovery(category, id))
        );

        RecipeData recipe = null;
        ConfigurationSection recipeIngr = sec.getConfigurationSection("recipe.ingredients");
        if (recipeIngr != null) {
            recipe = new RecipeData(
                id,
                name,
                RecipeData.RecipeKind.GUN,
                readIngredients(recipeIngr),
                1,
                sec.getString("recipe.station", "mechanical_crafting_table")
            );
        }

        return new GunData.Builder()
            .id(id).name(name).category(category).ammoType(ammoType)
            .damage(damage).reloadTime(reload).magazineSize(mag).fireRate(fireRate)
            .recoil(recoil).headshotMultiplier(hs)
            .rarity(rarity).sound(sound).customModelData(cmd).recipe(recipe)
            .range(                      sec.getDouble( "range",                        defaultRange(category, id)))
            .projectilesPerShot(         sec.getInt(    "projectiles_per_shot",         defaultProjectiles(category, id)))
            .projectileDamageMultiplier( sec.getDouble( "projectile_damage_multiplier", defaultProjectileDamageMultiplier(category, id)))
            .burstCount(                 sec.getInt(    "burst_count",                  defaultBurstCount(id)))
            .burstIntervalTicks(         sec.getLong(   "burst_interval_ticks",         defaultBurstInterval(id)))
            .blockPenetration(           sec.getDouble( "block_penetration",            defaultBlockPenetration(category, id)))
            .entityPenetration(          sec.getInt(    "entity_penetration",           defaultEntityPenetration(category, id)))
            .damageFalloffStart(         sec.getDouble( "damage_falloff_start",         defaultFalloffStart(category, id)))
            .minDamageMultiplier(        sec.getDouble( "min_damage_multiplier",        defaultMinDamageMultiplier(category, id)))
            .movementSpreadMultiplier(   sec.getDouble( "movement_spread_multiplier",   defaultMovementSpread(category, id)))
            .knockbackStrength(          sec.getDouble( "knockback",                    defaultKnockback(category, id)))
            .maxDurability(              sec.getInt(    "max_durability",               0))
            .scopeable(                  sec.getBoolean("scopeable",                    defaultScopeable(category, id)))
            .adsEnabled(                 sec.getBoolean("ads.enabled",              false))
            .adsSpreadMultiplier(        sec.getDouble( "ads.spread_multiplier",    0.35))
            .adsMovementPenalty(         sec.getDouble( "ads.movement_penalty",     0.6))
            .build();
    }

    /**
     * Validates critical fields in a gun config section.
     * Returns a list of human-readable error strings; empty list means valid.
     */
    private List<String> validateGunConfig(String id, YamlConfiguration cfg) {
        List<String> errors = new ArrayList<>();
        ConfigurationSection sec = cfg.getConfigurationSection(id);
        if (sec == null) sec = cfg; // flat structure fallback

        String name = sec.getString("name", "").trim();
        if (name.isEmpty()) errors.add("'name' is missing or blank");

        double damage = sec.getDouble("damage", -1);
        if (damage <= 0) errors.add("'damage' must be > 0 (got: " + damage + ")");

        double fireRate = sec.getDouble("fire_rate", -1);
        if (fireRate <= 0) errors.add("'fire_rate' must be > 0 (got: " + fireRate + ")");

        String category = sec.getString("category", "").trim().toLowerCase(Locale.ROOT);
        if (!"melee".equals(category)) {
            int mag = sec.getInt("magazine_size", -1);
            if (mag <= 0) errors.add("'magazine_size' must be > 0 for ranged weapons (got: " + mag + ")");

            double reloadTime = sec.getDouble("reload_time", -1);
            if (reloadTime <= 0) errors.add("'reload_time' must be > 0 (got: " + reloadTime + ")");

            double headshotMult = sec.getDouble("headshot_multiplier", 1.0);
            if (headshotMult < 1.0) errors.add("'headshot_multiplier' should be >= 1.0 (got: " + headshotMult + ")");
        }

        double range = sec.getDouble("range", -1);
        if (range != -1 && range <= 0) errors.add("'range' must be > 0 if set (got: " + range + ")");

        int burstCount = sec.getInt("burst_count", 1);
        if (burstCount < 1) errors.add("'burst_count' must be >= 1 (got: " + burstCount + ")");

        return errors;
    }

    private Map<String, Integer> readIngredients(ConfigurationSection sec) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String key : sec.getKeys(false)) {
            out.put(key.toLowerCase(Locale.ROOT), Math.max(1, sec.getInt(key, 1)));
        }
        return out;
    }

    // ── Category defaults ──────────────────────────────────────────────────────

    private boolean isHeavySniper(String id) {
        return "m107".equalsIgnoreCase(id) || "m82b".equalsIgnoreCase(id);
    }

    private String normalize(String category) {
        return category == null ? "unknown" : category.toLowerCase(Locale.ROOT);
    }

    private double defaultRange(String category, String id) {
        if (isHeavySniper(id)) return 140.0;
        return switch (normalize(category)) {
            case "snipers"  -> 120.0;
            case "shotguns" -> 26.0;
            case "smgs"     -> 48.0;
            case "pistols"  -> 42.0;
            case "melee"    -> 4.0;
            default         -> 72.0;
        };
    }

    private int defaultProjectiles(String category, String id) {
        if (!"shotguns".equals(normalize(category))) return 1;
        return "m1887".equalsIgnoreCase(id) ? 7 : 9;
    }

    private double defaultProjectileDamageMultiplier(String category, String id) {
        if (!"shotguns".equals(normalize(category))) return 1.0;
        return "m1887".equalsIgnoreCase(id) ? 0.23 : 0.18;
    }

    private int defaultBurstCount(String id) {
        if ("famas".equalsIgnoreCase(id)) return 3;
        if ("an94".equalsIgnoreCase(id))  return 2;
        return 1;
    }

    private long defaultBurstInterval(String id) {
        if ("famas".equalsIgnoreCase(id)) return 2L;
        if ("an94".equalsIgnoreCase(id))  return 1L;
        return 0L;
    }

    private double defaultBlockPenetration(String category, String id) {
        if (isHeavySniper(id)) return 2.6;
        return switch (normalize(category)) {
            case "snipers"        -> 1.5;
            case "assault_rifles" -> 0.8;
            case "smgs"           -> 0.45;
            case "pistols"        -> 0.55;
            case "shotguns"       -> 0.2;
            default               -> 0.0;
        };
    }

    private int defaultEntityPenetration(String category, String id) {
        if (isHeavySniper(id)) return 2;
        return "snipers".equals(normalize(category)) ? 1 : 0;
    }

    private double defaultFalloffStart(String category, String id) {
        if (isHeavySniper(id)) return 100.0;
        return switch (normalize(category)) {
            case "shotguns" -> 8.0;
            case "smgs"     -> 22.0;
            case "pistols"  -> 18.0;
            case "snipers"  -> 70.0;
            default         -> 36.0;
        };
    }

    private double defaultMinDamageMultiplier(String category, String id) {
        return switch (normalize(category)) {
            case "shotguns" -> 0.35;
            case "smgs"     -> 0.55;
            case "pistols"  -> 0.60;
            case "snipers"  -> 0.70;
            default         -> 0.65;
        };
    }

    private double defaultMovementSpread(String category, String id) {
        return switch (normalize(category)) {
            case "shotguns" -> 1.35;
            case "smgs"     -> 1.05;
            case "snipers"  -> 1.70;
            case "pistols"  -> 1.15;
            default         -> 1.25;
        };
    }

    private double defaultPitch(String category, String id) {
        if ("m107".equalsIgnoreCase(id)) return 2.9;
        if ("m82b".equalsIgnoreCase(id)) return 2.6;
        return switch (normalize(category)) {
            case "snipers"  -> 2.2;
            case "shotguns" -> 2.0;
            case "smgs"     -> 0.9;
            case "pistols"  -> 1.0;
            default         -> 1.3;
        };
    }

    private double defaultYaw(String category, String id) {
        return switch (normalize(category)) {
            case "shotguns" -> 0.55;
            case "snipers"  -> 0.38;
            case "smgs"     -> 0.24;
            case "pistols"  -> 0.28;
            default         -> 0.34;
        };
    }

    private double defaultSpread(String category, String id) {
        return switch (normalize(category)) {
            case "shotguns" -> 1.00;
            case "snipers"  -> 0.08;
            case "smgs"     -> 0.26;
            case "pistols"  -> 0.20;
            default         -> 0.18;
        };
    }

    private double defaultRecovery(String category, String id) {
        return switch (normalize(category)) {
            case "snipers"  -> 0.55;
            case "smgs"     -> 0.90;
            case "shotguns" -> 0.65;
            default         -> 0.80;
        };
    }

    private double defaultKnockback(String category, String id) {
        if (isHeavySniper(id)) return 0.9;
        return switch (normalize(category)) {
            case "snipers"        -> 0.6;
            case "shotguns"       -> 0.8;
            case "assault_rifles" -> 0.2;
            case "smgs"           -> 0.1;
            case "pistols"        -> 0.15;
            default               -> 0.0;
        };
    }

    private boolean defaultScopeable(String category, String id) {
        return "snipers".equals(normalize(category));
    }
}
