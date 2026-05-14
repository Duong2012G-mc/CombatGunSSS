package dev.duong2012g.combatgun.event;

import dev.duong2012g.combatgun.data.GunData;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired before a player shoots a gun.
 * Cancelling this event prevents the shot entirely (no ammo consumed, no sound).
 * <p>
 * Other plugins can:
 * <ul>
 *   <li>Cancel the shot via {@link #setCancelled(boolean)}</li>
 *   <li>Scale damage via {@link #setDamageMultiplier(double)}</li>
 *   <li>Read pre-shot base damage via {@link #getBaseDamage()}</li>
 * </ul>
 * <p>
 * For post-shot data (final damage, headshot, distance), listen to {@link GunHitEvent} instead.
 */
public class GunShootEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player  shooter;
    private final GunData gun;
    private boolean       cancelled        = false;
    private double        damageMultiplier = 1.0;

    public GunShootEvent(Player shooter, GunData gun) {
        this.shooter = shooter;
        this.gun     = gun;
    }

    /** The player pulling the trigger. */
    public Player getShooter() { return shooter; }

    /** Stats of the gun being fired. */
    public GunData getGun() { return gun; }

    /**
     * Damage multiplier applied on top of the gun's base damage.
     * Default is {@code 1.0}. Set to {@code 0.5} to halve damage, {@code 2.0} to double it, etc.
     */
    public double getDamageMultiplier() { return damageMultiplier; }

    public void setDamageMultiplier(double multiplier) {
        this.damageMultiplier = Math.max(0.0, multiplier);
    }

    /**
     * The base damage this weapon will deal per bullet before any multipliers
     * (falloff, headshot, damageMultiplier) are applied.
     * <p>
     * Equal to {@code gun.getDamage() * gun.getProjectileDamageMultiplier()}.
     * Useful for pre-shot checks (e.g. arena min-damage caps).
     */
    public double getBaseDamage() {
        return gun.getDamage() * gun.getProjectileDamageMultiplier();
    }

    @Override public boolean isCancelled()              { return cancelled; }
    @Override public void    setCancelled(boolean c)    { this.cancelled = c; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList()          { return HANDLERS; }
}
