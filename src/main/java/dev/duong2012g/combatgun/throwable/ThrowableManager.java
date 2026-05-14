package dev.duong2012g.combatgun.throwable;

import dev.duong2012g.combatgun.CombatGunSSSPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Manages throwable items (grenades, smoke, flashbang) for CombatGunSSS.
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li>Player right-clicks while holding a throwable item → {@link #handleThrow(Player, ItemStack)}
 *       is called from the listener.</li>
 *   <li>A Snowball projectile is spawned in the player's look direction.</li>
 *   <li>The thrown projectile UUID is registered. When it lands
 *       ({@code ProjectileHitEvent}) the listener calls {@link #onProjectileLand(Projectile)}.</li>
 *   <li>The appropriate explosion effect fires at the impact location.</li>
 * </ol>
 *
 * <h3>Config ({@code config.yml})</h3>
 * <pre>
 * combatgun:
 *   throwables:
 *     frag_grenade:
 *       type: FRAG
 *       display_name: "Frag Grenade"
 *       fuse_ticks: 60
 *       explosion_radius: 5.0
 *       explosion_damage: 8.0
 *       custom_model_data: 9001
 *     smoke_grenade:
 *       type: SMOKE
 *       display_name: "Smoke Grenade"
 *       fuse_ticks: 20
 *       duration_ticks: 200
 *       cloud_radius: 4.0
 *       custom_model_data: 9002
 *     flashbang:
 *       type: FLASHBANG
 *       display_name: "Flashbang"
 *       fuse_ticks: 20
 *       effect_radius: 6.0
 *       blind_ticks: 100
 *       deaf_ticks: 60
 *       custom_model_data: 9003
 * </pre>
 */
public class ThrowableManager {

    private static final String PDC_TYPE_VALUE = "throwable";
    private static final String PDC_ID_KEY     = "throwable_id";

    private final CombatGunSSSPlugin plugin;
    private final NamespacedKey      keyItemType;
    private final NamespacedKey      keyThrowableId;

    /** Tracks live projectiles: projectile UUID → throwable config ID */
    private final Map<UUID, String> liveProjectiles = new HashMap<>();
    /** Tracks throwable config definitions */
    private final Map<String, ThrowableConfig> registry = new LinkedHashMap<>();

    public ThrowableManager(CombatGunSSSPlugin plugin) {
        this.plugin         = plugin;
        this.keyItemType    = new NamespacedKey(plugin, "custom_item_type");
        this.keyThrowableId = new NamespacedKey(plugin, PDC_ID_KEY);
    }

    // ── Config record ─────────────────────────────────────────────────────────

    public record ThrowableConfig(
        String          id,
        ThrowableType   type,
        String          displayName,
        int             customModelData,
        int             fuseTicks,
        double          explosionRadius,
        double          explosionDamage,
        int             durationTicks,
        double          cloudRadius,
        double          effectRadius,
        int             blindTicks,
        int             deafTicks
    ) {}

    // ── Loading ───────────────────────────────────────────────────────────────

    public void load() {
        registry.clear();
        var sec = plugin.getConfig().getConfigurationSection("combatgun.throwables");
        if (sec == null) return;

        for (String id : sec.getKeys(false)) {
            var a = sec.getConfigurationSection(id);
            if (a == null) continue;
            try {
                ThrowableType type = ThrowableType.valueOf(
                    a.getString("type", "FRAG").toUpperCase());
                registry.put(id.toLowerCase(), new ThrowableConfig(
                    id.toLowerCase(), type,
                    a.getString("display_name", id),
                    a.getInt("custom_model_data", 0),
                    a.getInt("fuse_ticks", 60),
                    a.getDouble("explosion_radius", 5.0),
                    a.getDouble("explosion_damage", 8.0),
                    a.getInt("duration_ticks", 200),
                    a.getDouble("cloud_radius", 4.0),
                    a.getDouble("effect_radius", 6.0),
                    a.getInt("blind_ticks", 100),
                    a.getInt("deaf_ticks", 60)
                ));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[ThrowableManager] Unknown type for '" + id + "'");
            }
        }
        plugin.getLogger().info("[ThrowableManager] Loaded " + registry.size() + " throwable(s).");
    }

    public boolean exists(String id) { return registry.containsKey(id.toLowerCase()); }
    public Collection<ThrowableConfig> getAll() { return registry.values(); }
    public boolean isTracked(UUID uid) { return liveProjectiles.containsKey(uid); }

    // ── Item creation ─────────────────────────────────────────────────────────

    public ItemStack createThrowable(String id) {
        ThrowableConfig cfg = registry.get(id.toLowerCase());
        if (cfg == null) return null;

        Material mat = switch (cfg.type()) {
            case FRAG      -> Material.FIRE_CHARGE;
            case SMOKE     -> Material.GRAY_DYE;
            case FLASHBANG -> Material.GLOWSTONE_DUST;
        };
        NamedTextColor color = switch (cfg.type()) {
            case FRAG      -> NamedTextColor.RED;
            case SMOKE     -> NamedTextColor.GRAY;
            case FLASHBANG -> NamedTextColor.YELLOW;
        };

        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta  = item.getItemMeta();
        meta.displayName(Component.text(cfg.displayName(), color)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Type: " + cfg.type().name().toLowerCase(),
            NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Right-click to throw",
            NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        if (cfg.customModelData() > 0) meta.setCustomModelData(cfg.customModelData());

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyItemType,    PersistentDataType.STRING, PDC_TYPE_VALUE);
        pdc.set(keyThrowableId, PersistentDataType.STRING, cfg.id());
        item.setItemMeta(meta);
        return item;
    }

    /** Returns the throwable ID stored on an item, or {@code null} if not a throwable. */
    public String getThrowableId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (!PDC_TYPE_VALUE.equals(pdc.get(keyItemType, PersistentDataType.STRING))) return null;
        return pdc.get(keyThrowableId, PersistentDataType.STRING);
    }

    public boolean isThrowableItem(ItemStack item) { return getThrowableId(item) != null; }

    // ── Throw ─────────────────────────────────────────────────────────────────

    /**
     * Handles a throw: launches a snowball projectile, consumes the item.
     *
     * @param player The throwing player.
     * @param item   The throwable item (must be a valid throwable).
     */
    public void handleThrow(Player player, ItemStack item) {
        String id = getThrowableId(item);
        if (id == null) return;
        ThrowableConfig cfg = registry.get(id);
        if (cfg == null) return;

        // Consume one from stack
        if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
        else player.getInventory().setItemInMainHand(null);

        // Launch projectile
        Snowball projectile = player.launchProjectile(Snowball.class);
        Vector velocity = player.getEyeLocation().getDirection().multiply(1.5);
        projectile.setVelocity(velocity);
        projectile.setShooter(player);

        liveProjectiles.put(projectile.getUniqueId(), cfg.id());

        // Auto-detonate after fuse_ticks if it hasn't hit anything
        new BukkitRunnable() {
            @Override public void run() {
                if (!liveProjectiles.containsKey(projectile.getUniqueId())) return;
                liveProjectiles.remove(projectile.getUniqueId());
                if (!projectile.isDead()) {
                    detonate(cfg, projectile.getLocation(), player);
                    projectile.remove();
                }
            }
        }.runTaskLater(plugin, cfg.fuseTicks());
    }

    /**
     * Called when a tracked projectile hits a block or entity.
     *
     * @param projectile The projectile that landed.
     */
    public void onProjectileLand(Projectile projectile) {
        String id = liveProjectiles.remove(projectile.getUniqueId());
        if (id == null) return;
        ThrowableConfig cfg = registry.get(id);
        if (cfg == null) return;

        Player shooter = projectile.getShooter() instanceof Player p ? p : null;
        detonate(cfg, projectile.getLocation(), shooter);
        projectile.remove();
    }

    // ── Detonation ────────────────────────────────────────────────────────────

    private void detonate(ThrowableConfig cfg, Location loc, Player shooter) {
        switch (cfg.type()) {
            case FRAG      -> detonateFrag(cfg, loc, shooter);
            case SMOKE     -> detonateSmoke(cfg, loc);
            case FLASHBANG -> detonateFlashbang(cfg, loc, shooter);
        }
    }

    private void detonateFrag(ThrowableConfig cfg, Location loc, Player shooter) {
        World w = loc.getWorld();
        w.createExplosion(loc, 0f, false, false); // visual only (power=0)
        w.spawnParticle(Particle.EXPLOSION, loc, 10, 0.5, 0.5, 0.5, 0.1);
        w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 4f, 1f);

        double r = cfg.explosionRadius();
        for (Entity e : w.getNearbyEntities(loc, r, r, r)) {
            if (!(e instanceof LivingEntity le)) continue;
            if (e.equals(shooter)) continue;
            double dist    = e.getLocation().distance(loc);
            double falloff = 1.0 - (dist / r);
            double dmg     = cfg.explosionDamage() * falloff;
            le.setNoDamageTicks(0);
            le.damage(dmg, shooter);
        }
    }

    private void detonateSmoke(ThrowableConfig cfg, Location loc) {
        World  w        = loc.getWorld();
        double r        = cfg.cloudRadius();
        int    steps    = cfg.durationTicks() / 4; // spawn particles every 4 ticks

        w.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 2f, 0.5f);

        new BukkitRunnable() {
            int elapsed = 0;
            @Override public void run() {
                if (elapsed >= steps) { cancel(); return; }
                // Spread smoke particles in a sphere
                for (int i = 0; i < 20; i++) {
                    double px = loc.getX() + (Math.random() - 0.5) * r * 2;
                    double py = loc.getY() + Math.random() * 2.0;
                    double pz = loc.getZ() + (Math.random() - 0.5) * r * 2;
                    w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,
                        new Location(w, px, py, pz), 1, 0, 0.1, 0, 0.02);
                }
                elapsed++;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    private void detonateFlashbang(ThrowableConfig cfg, Location loc, Player shooter) {
        World  w = loc.getWorld();
        double r = cfg.effectRadius();

        w.spawnParticle(Particle.FLASH, loc, 3, 0.5, 0.5, 0.5, 0);
        w.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 4f, 2f);

        for (Entity e : w.getNearbyEntities(loc, r, r, r)) {
            if (!(e instanceof Player target)) continue;
            if (target.equals(shooter)) continue;

            // Scale effect by distance
            double dist  = target.getLocation().distance(loc);
            double ratio = 1.0 - (dist / r);
            int    blind = (int)(cfg.blindTicks() * ratio);
            int    deaf  = (int)(cfg.deafTicks()  * ratio);

            if (blind > 0) {
                target.addPotionEffect(new PotionEffect(
                    PotionEffectType.BLINDNESS, blind, 0, false, false, false));
            }
            if (deaf > 0) {
                // Simulate deafness with heavy slowness overlay (no true deaf in API)
                target.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS, deaf, 2, false, false, false));
            }
            target.sendActionBar(Component.text("☀ FLASHBANG!", NamedTextColor.YELLOW));
        }
    }
}
