package dev.duong2012g.combatgun.hook;

import dev.duong2012g.combatgun.CombatGunSSSPlugin;
import dev.duong2012g.combatgun.data.GunData;
import dev.duong2012g.combatgun.item.GunItem;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for CombatGunSSS.
 * <p>
 * Registered automatically when PlaceholderAPI is detected on startup.
 * All placeholders are evaluated in real-time (no caching).
 *
 * <h3>Available placeholders</h3>
 * <table>
 *   <tr><th>Placeholder</th><th>Example output</th><th>Description</th></tr>
 *   <tr><td>%combatgun_gun_name%</td><td>AK47</td><td>Display name of the held gun, or empty string.</td></tr>
 *   <tr><td>%combatgun_gun_id%</td><td>ak47</td><td>Internal ID of the held gun, or empty string.</td></tr>
 *   <tr><td>%combatgun_gun_rarity%</td><td>epic</td><td>Rarity of the held gun, or empty string.</td></tr>
 *   <tr><td>%combatgun_ammo%</td><td>24</td><td>Current magazine ammo of the held gun, or 0.</td></tr>
 *   <tr><td>%combatgun_ammo_max%</td><td>30</td><td>Max magazine size of the held gun, or 0.</td></tr>
 *   <tr><td>%combatgun_ammo_reserve%</td><td>90</td><td>Total reserve ammo in inventory for the held gun's ammo type.</td></tr>
 *   <tr><td>%combatgun_is_reloading%</td><td>true</td><td>Whether the player is currently reloading.</td></tr>
 *   <tr><td>%combatgun_is_gun%</td><td>true</td><td>Whether the held item is a CombatGunSSS gun.</td></tr>
 * </table>
 *
 * <h3>Usage examples (scoreboard / TAB / AdvancedHud)</h3>
 * <pre>
 *   %combatgun_gun_name%  %combatgun_ammo%/%combatgun_ammo_max%
 *   Reserve: %combatgun_ammo_reserve%
 * </pre>
 */
public class PlaceholderHook extends PlaceholderExpansion {

    private final CombatGunSSSPlugin plugin;

    public PlaceholderHook(CombatGunSSSPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "combatgun"; }
    @Override public @NotNull String getAuthor()     { return "Duong2012G"; }
    @Override public @NotNull String getVersion()    { return plugin.getDescription().getVersion(); }
    @Override public boolean        persist()        { return true; }
    @Override public boolean        canRegister()    { return true; }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        GunItem gunItemHelper = plugin.getGunItemHelper();
        ItemStack held        = player.getInventory().getItemInMainHand();
        String    gunId       = gunItemHelper.getGunId(held);
        GunData   gun         = gunId != null ? plugin.getGunManager().getGun(gunId) : null;

        return switch (params.toLowerCase()) {
            case "gun_name"      -> gun != null ? gun.getName() : "";
            case "gun_id"        -> gun != null ? gun.getId()   : "";
            case "gun_rarity"    -> gun != null ? gun.getRarity() : "";
            case "ammo"          -> gun != null && !gun.isMelee()
                                        ? String.valueOf(gunItemHelper.getCurrentAmmo(held)) : "0";
            case "ammo_max"      -> gun != null && !gun.isMelee()
                                        ? String.valueOf(gun.getMagazineSize()) : "0";
            case "ammo_reserve"  -> gun != null && !gun.isMelee()
                                        ? String.valueOf(plugin.getCustomItemManager()
                                            .countAmmo(player.getInventory(), gun.getAmmoType())) : "0";
            case "is_reloading"  -> String.valueOf(plugin.getAmmoManager().isReloading(player.getUniqueId()));
            case "is_gun"        -> String.valueOf(gun != null);
            // ── Stats placeholders ──────────────────────────────────────
            case "kills" -> {
                var stats = plugin.getStatsManager().getStats(player.getUniqueId(), player.getName());
                yield String.valueOf(stats.kills());
            }
            case "deaths" -> {
                var stats = plugin.getStatsManager().getStats(player.getUniqueId(), player.getName());
                yield String.valueOf(stats.deaths());
            }
            case "headshots" -> {
                var stats = plugin.getStatsManager().getStats(player.getUniqueId(), player.getName());
                yield String.valueOf(stats.headshots());
            }
            case "kd" -> {
                var stats = plugin.getStatsManager().getStats(player.getUniqueId(), player.getName());
                yield String.format("%.2f", stats.kd());
            }
            default              -> null; // unknown placeholder
        };
    }
}
