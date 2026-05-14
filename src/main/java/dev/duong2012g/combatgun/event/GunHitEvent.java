package dev.duong2012g.combatgun.event;

import dev.duong2012g.combatgun.data.GunData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after a bullet successfully hits a living entity and damage has been applied.
 * <p>
 * Unlike {@link GunShootEvent} (which fires before the shot and can be cancelled),
 * this event is informational only — damage has already occurred by the time listeners
 * receive it. Use it for stat tracking, cosmetic effects, or triggering side effects.
 * <p>
 * Example usage:
 * <pre>{@code
 * @EventHandler
 * public void onGunHit(GunHitEvent event) {
 *     // Give XP for every hit
 *     event.getShooter().giveExp(1);
 *
 *     // Log headshot + distance for analytics
 *     if (event.isHeadshot()) {
 *         plugin.getLogger().info(event.getShooter().getName()
 *             + " headshot at " + String.format("%.1f", event.getDistance()) + " blocks");
 *     }
 * }
 * }</pre>
 */
public class GunHitEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player       shooter;
    private final LivingEntity target;
    private final GunData      gun;
    private final double       baseDamage;
    private final double       finalDamage;
    private final boolean      headshot;
    private final double       distance;

    public GunHitEvent(Player shooter, LivingEntity target, GunData gun,
                       double baseDamage, double finalDamage,
                       boolean headshot, double distance) {
        this.shooter     = shooter;
        this.target      = target;
        this.gun         = gun;
        this.baseDamage  = baseDamage;
        this.finalDamage = finalDamage;
        this.headshot    = headshot;
        this.distance    = distance;
    }

    /** The player who fired the shot. */
    public Player getShooter() { return shooter; }

    /** The entity that was hit. */
    public LivingEntity getTarget() { return target; }

    /** Stats of the weapon that caused the hit. */
    public GunData getGun() { return gun; }

    /**
     * Base damage before falloff and headshot multipliers.
     * Equals {@code gun.getDamage() * gun.getProjectileDamageMultiplier() * shootEventMultiplier}.
     */
    public double getBaseDamage() { return baseDamage; }

    /**
     * Final damage actually applied to the target, after all multipliers:
     * base → falloff → headshot → damageMultiplier from GunShootEvent.
     */
    public double getFinalDamage() { return finalDamage; }

    /** Whether the hit registered as a headshot. */
    public boolean isHeadshot() { return headshot; }

    /** Distance in blocks from shooter's eye to the hit position. */
    public double getDistance() { return distance; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList()          { return HANDLERS; }
}
