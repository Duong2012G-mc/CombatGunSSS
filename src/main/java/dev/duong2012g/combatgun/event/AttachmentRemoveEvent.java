package dev.duong2012g.combatgun.event;

import dev.duong2012g.combatgun.attachment.AttachmentData;
import dev.duong2012g.combatgun.attachment.AttachmentType;
import dev.duong2012g.combatgun.data.GunData;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player is about to remove an {@link AttachmentData} from a gun.
 * <p>
 * This event is <b>cancellable</b> — cancel it to block the removal action.
 *
 * <pre>{@code
 * @EventHandler
 * public void onDetach(AttachmentRemoveEvent event) {
 *     // Return the attachment item to the player's inventory automatically
 *     event.getPlayer().getInventory().addItem(
 *         plugin.getAttachmentManager().createAttachmentItem(event.getRemoved().id()));
 * }
 * }</pre>
 */
public class AttachmentRemoveEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player         player;
    private final ItemStack      gunItem;
    private final GunData        gunData;
    private final AttachmentType slot;
    private final AttachmentData removed;
    private boolean cancelled = false;

    public AttachmentRemoveEvent(@NotNull Player         player,
                                 @NotNull ItemStack      gunItem,
                                 @NotNull GunData        gunData,
                                 @NotNull AttachmentType slot,
                                 @NotNull AttachmentData removed) {
        this.player  = player;
        this.gunItem = gunItem;
        this.gunData = gunData;
        this.slot    = slot;
        this.removed = removed;
    }

    /** The player performing the detach action. */
    public @NotNull Player          getPlayer()   { return player; }

    /** The gun ItemStack being modified. */
    public @NotNull ItemStack       getGunItem()  { return gunItem; }

    /** Parsed stat data for the gun being modified. */
    public @NotNull GunData         getGunData()  { return gunData; }

    /** The slot from which the attachment is being removed. */
    public @NotNull AttachmentType  getSlot()     { return slot; }

    /** The attachment being removed. */
    public @NotNull AttachmentData  getRemoved()  { return removed; }

    @Override public boolean isCancelled()            { return cancelled; }
    @Override public void    setCancelled(boolean v)  { this.cancelled = v; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList()          { return HANDLERS; }
}
