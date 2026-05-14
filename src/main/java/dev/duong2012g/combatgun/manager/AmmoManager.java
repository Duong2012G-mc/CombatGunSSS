package dev.duong2012g.combatgun.manager;

import dev.duong2012g.combatgun.CombatGunSSSPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AmmoManager {

    private final CombatGunSSSPlugin plugin;

    /** Players currently in reload animation. */
    private final Map<UUID, Boolean> reloading       = new HashMap<>();

    /** Last-shot timestamp per player (for fire-rate cooldown). */
    private final Map<UUID, Long>    lastShot        = new HashMap<>();

    /** Last melee-attack timestamp per player (separate cooldown). */
    private final Map<UUID, Long>    lastMeleeAttack = new HashMap<>();

    public AmmoManager(CombatGunSSSPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Reload state ──────────────────────────────────────────────────────────

    public boolean isReloading(UUID playerId) {
        return reloading.getOrDefault(playerId, false);
    }

    public void setReloading(UUID playerId, boolean state) {
        if (state) reloading.put(playerId, true);
        else       reloading.remove(playerId);
    }

    // ── Fire-rate cooldown ────────────────────────────────────────────────────

    public boolean tryShoot(UUID playerId, long cooldownMs) {
        long now  = System.currentTimeMillis();
        long last = lastShot.getOrDefault(playerId, 0L);
        if (now - last < cooldownMs) return false;
        lastShot.put(playerId, now);
        return true;
    }

    public void extendCooldown(UUID playerId, long extraMs) {
        long current = lastShot.getOrDefault(playerId, 0L);
        lastShot.put(playerId, current + extraMs);
    }

    // ── Melee cooldown ────────────────────────────────────────────────────────

    public boolean tryMeleeAttack(UUID playerId, long cooldownMs) {
        long now  = System.currentTimeMillis();
        long last = lastMeleeAttack.getOrDefault(playerId, 0L);
        if (now - last < cooldownMs) return false;
        lastMeleeAttack.put(playerId, now);
        return true;
    }

    /**
     * Returns remaining melee cooldown in ms for the given weapon cooldown.
     * Always pass the weapon's actual cooldown — no hardcoded default.
     */
    public long getMeleeCooldownRemaining(UUID playerId, long weaponCooldownMs) {
        long elapsed = System.currentTimeMillis() - lastMeleeAttack.getOrDefault(playerId, 0L);
        return Math.max(0L, weaponCooldownMs - elapsed);
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    public void clearPlayer(UUID playerId) {
        reloading.remove(playerId);
        lastShot.remove(playerId);
        lastMeleeAttack.remove(playerId);
    }

    /**
     * Prune stale entries older than {@code olderThan} ms timestamp.
     * Called periodically to prevent memory leaks when players disconnect unexpectedly.
     */
    public void pruneStale(long olderThan) {
        lastShot.entrySet().removeIf(e -> e.getValue() < olderThan);
        lastMeleeAttack.entrySet().removeIf(e -> e.getValue() < olderThan);
    }
}
