package dev.duong2012g.combatgun.event;

import dev.duong2012g.combatgun.attachment.AttachmentData;
import dev.duong2012g.combatgun.data.GunData;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when a player is about to attach an {@link AttachmentData} to a gun.
 * <p>
 * This event is <b>cancellable</b> — cancel it to block the attachment action.
 * If the target slot is already occupied, {@link #getReplacedAttachment()} returns
 * the attachment that would be displaced; it is {@code null} for an empty slot.
 *
 * <pre>{@code
 * @EventHandler
 * public void onAttach(AttachmentApplyEvent event) {
 *     if (event.getGunData().getRarity().equals("legendary")) {
 *         event.setCancelled(true); // protect legendaries from modification
 *         event.getPlayer().sendMessage("Legendary weapons cannot be modified!");
 *     }
 * }
 * }</pre>
 */
public class AttachmentApplyEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player         player;
    private final ItemStack      gunItem;
    private final GunData        gunData;
    private final AttachmentData incoming;
    private final AttachmentData replaced;   // may be null
    private boolean cancelled = false;

    public AttachmentApplyEvent(@NotNull  Player         player,
                                @NotNull  ItemStack      gunItem,
                                @NotNull  GunData        gunData,
                                @NotNull  AttachmentData incoming,
                                @Nullable AttachmentData replaced) {
        this.player   = player;
        this.gunItem  = gunItem;
        this.gunData  = gunData;
        this.incoming = incoming;
        this.replaced = replaced;
    }

    /** The player performing the attachment. */
    public @NotNull Player getPlayer()           { return player; }

    /** The gun ItemStack being modified. */
    public @NotNull ItemStack getGunItem()       { return gunItem; }

    /** Parsed stat data for the gun being modified. */
    public @NotNull GunData getGunData()         { return gunData; }

    /** The attachment being fitted. */
    public @NotNull AttachmentData getIncoming() { return incoming; }

    /**
     * The attachment being replaced (displaced from the same slot),
     * or {@code null} if the slot was previously empty.
     */
    public @Nullable AttachmentData getReplacedAttachment() { return replaced; }

    @Override public boolean isCancelled()            { return cancelled; }
    @Override public void    setCancelled(boolean v)  { this.cancelled = v; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList()          { return HANDLERS; }
}
