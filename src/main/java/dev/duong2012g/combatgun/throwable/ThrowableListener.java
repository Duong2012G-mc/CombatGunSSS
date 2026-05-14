package dev.duong2012g.combatgun.throwable;

import dev.duong2012g.combatgun.CombatGunSSSPlugin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Bukkit listener for throwable item interactions.
 * <p>
 * Registered in {@link CombatGunSSSPlugin#onEnable()} alongside the other listeners.
 */
public class ThrowableListener implements Listener {

    private final CombatGunSSSPlugin plugin;
    private final ThrowableManager   throwableManager;

    public ThrowableListener(CombatGunSSSPlugin plugin) {
        this.plugin           = plugin;
        this.throwableManager = plugin.getThrowableManager();
    }

    /**
     * Right-click with a throwable item → throw it.
     * Priority HIGH so we can cancel before other listeners process it.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player    player = event.getPlayer();
        ItemStack held   = player.getInventory().getItemInMainHand();

        if (!throwableManager.isThrowableItem(held)) return;

        event.setCancelled(true);
        throwableManager.handleThrow(player, held);
    }

    /**
     * Tracked projectile lands → detonate.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile proj = event.getEntity();
        if (!throwableManager.isTracked(proj.getUniqueId())) return;
        throwableManager.onProjectileLand(proj);
    }
}
