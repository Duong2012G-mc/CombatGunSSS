package dev.duong2012g.combatgun.event;

import dev.duong2012g.combatgun.data.GunData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a shot hits the head zone of a living entity.
 * Not cancellable — headshot detection has already happened.
 * Use {@link #getFinalDamage()} to read the total damage after all multipliers.
 */
public class GunHeadshotEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player      shooter;
    private final LivingEntity target;
    private final GunData     gun;
    private final double      finalDamage;

    public GunHeadshotEvent(Player shooter, LivingEntity target, GunData gun, double finalDamage) {
        this.shooter     = shooter;
        this.target      = target;
        this.gun         = gun;
        this.finalDamage = finalDamage;
    }

    public Player       getShooter()     { return shooter; }
    public LivingEntity getTarget()      { return target; }
    public GunData      getGun()         { return gun; }
    /** Damage actually applied (base × falloff × headshot multiplier). */
    public double       getFinalDamage() { return finalDamage; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList()          { return HANDLERS; }
}
