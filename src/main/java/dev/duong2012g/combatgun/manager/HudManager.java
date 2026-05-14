package dev.duong2012g.combatgun.manager;

import dev.duong2012g.combatgun.CombatGunSSSPlugin;
import dev.duong2012g.combatgun.data.GunData;
import dev.duong2012g.combatgun.item.GunItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/**
 * Displays a persistent action-bar HUD while a player holds a gun.
 * <p>
 * The HUD shows: rarity-colored gun name · visual ammo bar · current/max ammo · reserve count.
 * <p>
 * Example output:
 * <pre>
 *   🔫 AK47  ▐███████████████░░░░░▌  24 / 30  •  reserve: 90
 * </pre>
 * <p>
 * The bar is only drawn when the player is NOT reloading — the ReloadManager owns the
 * action bar during reloads and this manager intentionally yields to it.
 */
public class HudManager {

    // Visual bar segments
    private static final int    BAR_LENGTH    = 16;
    private static final char   BAR_FULL      = '█';
    private static final char   BAR_EMPTY     = '░';
    private static final String BAR_LEFT      = "▐";
    private static final String BAR_RIGHT     = "▌";

    // Colors that match the rarity system in GunData
    private static final TextColor COLOR_COMMON    = NamedTextColor.WHITE;
    private static final TextColor COLOR_RARE      = NamedTextColor.AQUA;
    private static final TextColor COLOR_EPIC      = NamedTextColor.LIGHT_PURPLE;
    private static final TextColor COLOR_LEGENDARY = NamedTextColor.GOLD;

    // Bar fill color thresholds (fraction of magazine remaining)
    private static final float THRESHOLD_LOW      = 0.25f;
    private static final float THRESHOLD_CRITICAL = 0.10f;

    private final CombatGunSSSPlugin plugin;
    private final AmmoManager        ammoManager;
    private final CustomItemManager  customItemManager;
    private final GunItem            gunItemHelper;
    private BukkitTask               hudTask;

    public HudManager(CombatGunSSSPlugin plugin,
                      AmmoManager ammoManager,
                      CustomItemManager customItemManager) {
        this.plugin            = plugin;
        this.ammoManager       = ammoManager;
        this.customItemManager = customItemManager;
        this.gunItemHelper     = plugin.getGunItemHelper();
    }

    /**
     * Starts the repeating HUD task.
     * Update interval is read from {@code combatgun.hud.update_interval_ticks} (default 5).
     * Call this once from {@link CombatGunSSSPlugin#onEnable()}.
     */
    public void start() {
        if (!isEnabled()) return;
        long interval = plugin.getConfig().getLong("combatgun.hud.update_interval_ticks", 5L);
        interval = Math.max(1L, interval);

        hudTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                updateHud(player);
            }
        }, interval, interval);
    }

    /** Cancels the HUD task — call from {@link CombatGunSSSPlugin#onDisable()}. */
    public void stop() {
        if (hudTask != null) {
            hudTask.cancel();
            hudTask = null;
        }
    }

    /**
     * Push an immediate HUD update for one player.
     * Called by GunListener right after a shot so the ammo count drops instantly
     * without waiting for the next timer tick.
     */
    public void sendNow(Player player) {
        if (isEnabled()) updateHud(player);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("combatgun.hud.enabled", true);
    }

    private void updateHud(Player player) {
        // Yield to ReloadManager — it owns the action bar while reloading.
        if (ammoManager.isReloading(player.getUniqueId())) return;

        ItemStack held  = player.getInventory().getItemInMainHand();
        String    gunId = gunItemHelper.getGunId(held);
        if (gunId == null) return;                         // not a gun

        GunData gun = plugin.getGunManager().getGun(gunId);
        if (gun == null || gun.isMelee()) return;          // melee needs no ammo HUD

        int current  = gunItemHelper.getCurrentAmmo(held);
        int max      = gun.getMagazineSize();
        int reserve  = customItemManager.countAmmo(player.getInventory(), gun.getAmmoType());

        player.sendActionBar(buildHud(gun, current, max, reserve));
    }

    private Component buildHud(GunData gun, int current, int max, int reserve) {
        // ── Gun name (rarity-colored) ────────────────────────────────────────
        TextColor nameColor = rarityColor(gun.getRarity());
        Component nameComp = Component.text("🔫 ", NamedTextColor.GRAY)
            .append(Component.text(gun.getName(), nameColor));

        // ── Visual ammo bar ──────────────────────────────────────────────────
        float  fraction  = max > 0 ? (float) current / max : 0f;
        int    filled    = Math.round(fraction * BAR_LENGTH);
        TextColor barColor = barColor(fraction);

        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < BAR_LENGTH; i++) {
            bar.append(i < filled ? BAR_FULL : BAR_EMPTY);
        }
        Component barComp = Component.text("  " + BAR_LEFT, NamedTextColor.DARK_GRAY)
            .append(Component.text(bar.toString(), barColor))
            .append(Component.text(BAR_RIGHT, NamedTextColor.DARK_GRAY));

        // ── Ammo count ───────────────────────────────────────────────────────
        TextColor countColor = current == 0 ? NamedTextColor.RED : NamedTextColor.WHITE;
        Component countComp = Component.text("  ", NamedTextColor.GRAY)
            .append(Component.text(current, countColor))
            .append(Component.text(" / " + max, NamedTextColor.DARK_GRAY));

        // ── Reserve ──────────────────────────────────────────────────────────
        TextColor reserveColor = reserve == 0 ? NamedTextColor.RED : NamedTextColor.GRAY;
        Component reserveComp = Component.text("  •  ", NamedTextColor.DARK_GRAY)
            .append(Component.text(reserve, reserveColor));

        return nameComp.append(barComp).append(countComp).append(reserveComp);
    }

    private TextColor rarityColor(String rarity) {
        if (rarity == null) return COLOR_COMMON;
        return switch (rarity.toLowerCase()) {
            case "rare"      -> COLOR_RARE;
            case "epic"      -> COLOR_EPIC;
            case "legendary" -> COLOR_LEGENDARY;
            default          -> COLOR_COMMON;
        };
    }

    private TextColor barColor(float fraction) {
        if (fraction <= THRESHOLD_CRITICAL) return NamedTextColor.RED;
        if (fraction <= THRESHOLD_LOW)      return NamedTextColor.GOLD;
        return NamedTextColor.GREEN;
    }
}
