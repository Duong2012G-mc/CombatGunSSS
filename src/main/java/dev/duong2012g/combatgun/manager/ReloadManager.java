package dev.duong2012g.combatgun.manager;

import dev.duong2012g.combatgun.CombatGunSSSPlugin;
import dev.duong2012g.combatgun.data.GunData;
import dev.duong2012g.combatgun.item.GunItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import dev.duong2012g.combatgun.event.GunReloadEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ReloadManager {

    private final CombatGunSSSPlugin plugin;
    private final AmmoManager ammoManager;
    private final CustomItemManager customItemManager;
    private final Map<UUID, BukkitTask> activeTasks = new HashMap<>();
    private static final int BAR_LENGTH = 20;

    public ReloadManager(CombatGunSSSPlugin plugin, AmmoManager ammoManager, CustomItemManager customItemManager) {
        this.plugin = plugin;
        this.ammoManager = ammoManager;
        this.customItemManager = customItemManager;
    }

    public void startReload(Player player, ItemStack gunItem, GunData gun) {
        UUID uid = player.getUniqueId();
        if (ammoManager.isReloading(uid)) {
            return;
        }

        GunItem gunItemHelper = plugin.getGunItemHelper();
        int current = gunItemHelper.getCurrentAmmo(gunItem);
        if (current >= gun.getMagazineSize()) {
            player.sendActionBar(Component.text("Magazine is already full!", NamedTextColor.YELLOW));
            return;
        }

        // Fire GunReloadEvent — other plugins can cancel (e.g. zone-based restrictions).
        GunReloadEvent reloadEvent = new GunReloadEvent(player, gun);
        plugin.getServer().getPluginManager().callEvent(reloadEvent);
        if (reloadEvent.isCancelled()) return;

        int needed = gun.getMagazineSize() - current;
        int available = customItemManager.countAmmo(player.getInventory(), gun.getAmmoType());
        if (available <= 0) {
            player.sendActionBar(Component.text("No " + customItemManager.getDisplayNameForIngredient(gun.getAmmoType()) + " in inventory.", NamedTextColor.RED));
            return;
        }

        int amountToLoad = Math.min(needed, available);
        ammoManager.setReloading(uid, true);
        int totalTicks = Math.max(1, (int) Math.round(gun.getReloadTime() * 10.0)); // ✅ FIX #2: interval=2L → 10 steps/sec

        BukkitTask task = new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    finishReload(uid, false, player, gun.getId(), amountToLoad);
                    cancel();
                    return;
                }

                ItemStack held = player.getInventory().getItemInMainHand();
                String heldId = plugin.getGunItemHelper().getGunId(held);
                if (!gun.getId().equalsIgnoreCase(heldId)) {
                    finishReload(uid, false, player, gun.getId(), amountToLoad);
                    cancel();
                    return;
                }

                elapsed++;
                double progress = Math.min(1.0, (double) elapsed / totalTicks);
                player.sendActionBar(buildBar(progress, gun.getName(), amountToLoad));

                if (elapsed >= totalTicks) {
                    finishReload(uid, true, player, gun.getId(), amountToLoad);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 2L); // ✅ FIX #2: was 1L — 2-tick interval reduces CPU ~50%

        activeTasks.put(uid, task);
    }

    public void cancelReload(UUID playerId) {
        BukkitTask task = activeTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        ammoManager.setReloading(playerId, false);
    }

    public void cancelAll() {
        activeTasks.values().forEach(BukkitTask::cancel);
        activeTasks.clear();
    }

    private void finishReload(UUID uid, boolean success, Player player, String gunId, int requestedLoad) {
        activeTasks.remove(uid);
        ammoManager.setReloading(uid, false);

        if (!success || !player.isOnline()) {
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        String heldId = plugin.getGunItemHelper().getGunId(held);
        if (!gunId.equalsIgnoreCase(heldId)) {
            return;
        }

        GunData gun = plugin.getGunManager().getGun(gunId);
        if (gun == null) {
            return;
        }

        int consumed = customItemManager.consumeAmmo(player.getInventory(), gun.getAmmoType(), requestedLoad);
        if (consumed <= 0) {
            player.sendActionBar(Component.text("Reload failed: no ammo left.", NamedTextColor.RED));
            return;
        }

        GunItem helper = plugin.getGunItemHelper();
        int current = helper.getCurrentAmmo(held);
        int newAmmo = Math.min(gun.getMagazineSize(), current + consumed);
        helper.setCurrentAmmo(held, newAmmo);
        player.getInventory().setItemInMainHand(held); // persist PDC changes into inventory slot
        player.sendActionBar(Component.text("✔ Reloaded! ", NamedTextColor.GREEN)
            .append(Component.text("[" + newAmmo + "/" + gun.getMagazineSize() + "]", NamedTextColor.WHITE))
            .append(Component.text("  -" + consumed + " " + customItemManager.getDisplayNameForIngredient(gun.getAmmoType()), NamedTextColor.GRAY)));
    }

    private Component buildBar(double progress, String gunName, int loadAmount) {
        int filled = (int) Math.round(progress * BAR_LENGTH);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < BAR_LENGTH; i++) {
            bar.append(i < filled ? '█' : '░');
        }

        return Component.text("🔄 Reloading ", NamedTextColor.YELLOW)
            .append(Component.text(gunName, NamedTextColor.WHITE))
            .append(Component.text("  [", NamedTextColor.DARK_GRAY))
            .append(Component.text(bar.toString(), NamedTextColor.AQUA))
            .append(Component.text("]", NamedTextColor.DARK_GRAY))
            .append(Component.text("  +" + loadAmount, NamedTextColor.GRAY));
    }
}
