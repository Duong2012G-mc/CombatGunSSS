package dev.duong2012g.combatgun.manager;

import dev.duong2012g.combatgun.CombatGunSSSPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the bleeding damage-over-time effect for CombatGunSSS.
 * <p>
 * When a bullet hits a player there is a configurable chance to apply bleeding.
 * A bleeding player loses {@code damage_per_second} HP every second until either
 * the duration expires or they use a cure item (default: bandage).
 *
 * <h3>Config ({@code config.yml})</h3>
 * <pre>
 * combatgun:
 *   bleeding:
 *     enabled: true
 *     chance: 0.15            # 15% chance per bullet hit
 *     damage_per_second: 1.0  # HP lost per tick (every 20 ticks)
 *     duration_seconds: 10    # how many seconds bleeding lasts
 *     cure_item: bandage       # custom item ID that cures bleeding
 *                              # set to "" to disable item cure
 * </pre>
 *
 * <h3>Developer access</h3>
 * <pre>{@code
 * BleedingManager bm = plugin.getBleedingManager();
 * bm.applyBleeding(player);      // force-apply
 * bm.isBleeding(player);         // check state
 * bm.cure(player);               // remove bleeding
 * }</pre>
 */
public class BleedingManager {

    private final CombatGunSSSPlugin plugin;
    /** Active bleeding tasks keyed by player UUID. */
    private final Map<UUID, BukkitTask> activeTasks = new ConcurrentHashMap<>();

    public BleedingManager(CombatGunSSSPlugin plugin) {
        this.plugin = plugin;
    }

    /** @return Whether the bleeding system is enabled in config. */
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("combatgun.bleeding.enabled", false);
    }

    /** @return Whether the player currently has an active bleed task. */
    public boolean isBleeding(Player player) {
        return activeTasks.containsKey(player.getUniqueId());
    }

    /**
     * Attempts to apply bleeding to a player based on the configured chance.
     * If the player is already bleeding, the existing task is refreshed (duration resets).
     *
     * @param player The player to potentially bleed.
     * @return {@code true} if bleeding was applied.
     */
    public boolean tryApply(Player player) {
        if (!isEnabled()) return false;
        double chance = plugin.getConfig().getDouble("combatgun.bleeding.chance", 0.15);
        if (Math.random() > chance) return false;
        applyBleeding(player);
        return true;
    }

    /**
     * Force-applies bleeding regardless of chance config.
     * Resets duration if the player is already bleeding.
     */
    public void applyBleeding(Player player) {
        // Cancel existing task before starting a new one (refreshes duration)
        cancelTask(player.getUniqueId());

        double damagePerSecond = plugin.getConfig().getDouble("combatgun.bleeding.damage_per_second", 1.0);
        int    durationSeconds = plugin.getConfig().getInt("combatgun.bleeding.duration_seconds", 10);
        int    totalTicks      = durationSeconds * 20;

        LangManager lang = plugin.getLangManager();
        player.sendActionBar(Component.text(
            lang.get("bleeding.applied"), NamedTextColor.RED));

        BukkitTask task = new BukkitRunnable() {
            // ✅ FIX #7: Track wall-clock time instead of tick count to handle server lag
            final long startTime = System.currentTimeMillis();
            final long durationMs = (long) durationSeconds * 1000L;
            long lastDamageTime = System.currentTimeMillis();

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    cancelTask(player.getUniqueId());
                    cancel();
                    return;
                }
                if (!activeTasks.containsKey(player.getUniqueId())) {
                    cancel();
                    return;
                }
                long now = System.currentTimeMillis();
                if (now - startTime >= durationMs) {
                    cancelTask(player.getUniqueId());
                    cancel();
                    return;
                }
                // Only deal damage if at least 1 second has passed since last damage tick
                if (now - lastDamageTime >= 1000L) {
                    lastDamageTime = now;
                    double hp    = player.getHealth();
                    double newHp = Math.max(0, hp - damagePerSecond);
                    player.setHealth(newHp);
                    player.sendActionBar(Component.text(
                        lang.get("bleeding.tick"), NamedTextColor.RED));
                    if (newHp <= 0) {
                        cancelTask(player.getUniqueId());
                        cancel();
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        activeTasks.put(player.getUniqueId(), task);
    }

    /**
     * Removes bleeding from the player and cancels any active damage task.
     *
     * @param player   The player to cure.
     * @param notify   Whether to send a cure message to the player.
     */
    public void cure(Player player, boolean notify) {
        boolean wasBleeding = activeTasks.containsKey(player.getUniqueId());
        cancelTask(player.getUniqueId());
        if (wasBleeding && notify) {
            player.sendActionBar(Component.text(
                plugin.getLangManager().get("bleeding.cured"), NamedTextColor.GREEN));
        }
    }

    /** Removes all active bleeding tasks — call from {@code onDisable()}. */
    public void cancelAll() {
        activeTasks.values().forEach(BukkitTask::cancel);
        activeTasks.clear();
    }

    /** Returns the configured cure item ID, or empty string if disabled. */
    public String getCureItemId() {
        return plugin.getConfig().getString("combatgun.bleeding.cure_item", "bandage");
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void cancelTask(UUID uid) {
        BukkitTask task = activeTasks.remove(uid);
        if (task != null) task.cancel();
    }
}
