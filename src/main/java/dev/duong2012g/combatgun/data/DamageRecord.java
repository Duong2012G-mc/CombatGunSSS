package dev.duong2012g.combatgun.data;

import java.util.UUID;

/**
 * Stores the context of the last gun hit on a living entity.
 * Kept in a short-lived map (TTL ~5 s) so {@link dev.duong2012g.combatgun.listener.EntityDeathListener}
 * can produce accurate kill/death messages without relying on Bukkit's kill cause.
 */
public record DamageRecord(
    UUID   shooterUuid,
    String gunId,
    String gunName,
    boolean headshot,
    double  finalDamage,
    long    timestamp
) {
    /** Returns true if this record is older than {@code ttlMs} milliseconds. */
    public boolean isExpired(long ttlMs) {
        return (System.currentTimeMillis() - timestamp) > ttlMs;
    }
}
