package dev.duong2012g.combatgun.event;

import dev.duong2012g.combatgun.data.GunData;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player starts a reload animation.
 * Cancelling this event prevents the reload from starting.
 * Useful for zone-based mechanics (e.g. "cannot reload inside arena").
 */
public class GunReloadEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player  player;
    private final GunData gun;
    private boolean       cancelled = false;

    public GunReloadEvent(Player player, GunData gun) {
        this.player = player;
        this.gun    = gun;
    }

    public Player  getPlayer() { return player; }
    public GunData getGun()    { return gun; }

    @Override public boolean isCancelled()           { return cancelled; }
    @Override public void    setCancelled(boolean c) { this.cancelled = c; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList()          { return HANDLERS; }
}
