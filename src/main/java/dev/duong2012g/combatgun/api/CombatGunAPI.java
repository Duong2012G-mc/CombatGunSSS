package dev.duong2012g.combatgun.api;

import dev.duong2012g.combatgun.CombatGunSSSPlugin;
import dev.duong2012g.combatgun.data.GunData;
import dev.duong2012g.combatgun.data.RecipeData;
import org.bukkit.NamespacedKey;
import dev.duong2012g.combatgun.event.GunHeadshotEvent;
import dev.duong2012g.combatgun.event.GunHitEvent;
import dev.duong2012g.combatgun.event.GunReloadEvent;
import dev.duong2012g.combatgun.event.GunShootEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.UUID;

/**
 * Public API for CombatGunSSS.
 * Other plugins can depend on this to interact with the gun system.
 */
public class CombatGunAPI {

    /**
     * Custom events fired by CombatGunSSS — listen to these in your plugin:
     * <ul>
     *   <li>{@link GunShootEvent}    — cancellable, fired before each shot; exposes base damage
     *                                  and supports a damage multiplier.</li>
     *   <li>{@link GunReloadEvent}   — cancellable, fired when a player starts reloading.</li>
     *   <li>{@link GunHitEvent}      — informational, fired after damage is applied; exposes
     *                                  base damage, final damage, headshot flag, and distance.</li>
     *   <li>{@link GunHeadshotEvent} — informational, fired specifically when a headshot occurs.</li>
     * </ul>
     * Register listeners with {@code Bukkit.getPluginManager().registerEvents(listener, yourPlugin)}.
     */
    @SuppressWarnings("unused")
    private static final Class<?>[] CUSTOM_EVENTS = {
        GunShootEvent.class, GunReloadEvent.class, GunHitEvent.class, GunHeadshotEvent.class
    };

    private static CombatGunAPI instance;
    private final CombatGunSSSPlugin plugin;

    private CombatGunAPI(CombatGunSSSPlugin plugin) {
        this.plugin = plugin;
    }

    public static void init(CombatGunSSSPlugin plugin) {
        if (instance == null) {
            instance = new CombatGunAPI(plugin);
        }
    }

    public static CombatGunAPI getInstance() {
        if (instance == null) {
            throw new IllegalStateException("CombatGunAPI is not initialized! Is CombatGunSSS enabled?");
        }
        return instance;
    }

    // ── Gun Queries ─────────────────────────────────────────────────────────────

    /** Returns gun data by ID, or null if not found. */
    @Nullable
    public GunData getGun(String gunId) {
        return plugin.getGunManager().getGun(gunId);
    }

    /** Returns all loaded guns. */
    public Collection<GunData> getAllGuns() {
        return plugin.getGunManager().getAllGuns();
    }

    /** Returns true if gun ID exists. */
    public boolean hasGun(String gunId) {
        return plugin.getGunManager().exists(gunId);
    }

    /** Returns recipe for a gun, or null if no recipe. */
    @Nullable
    public RecipeData getGunRecipe(String gunId) {
        return plugin.getGunManager().getGunRecipe(gunId);
    }

    // ── Item Creation ───────────────────────────────────────────────────────────

    /** Creates a gun ItemStack. */
    @Nullable
    public ItemStack createGunItem(String gunId) {
        GunData gun = getGun(gunId);
        if (gun == null) return null;
        return plugin.getGunItemHelper().create(gun);
    }

    /** Creates ammo ItemStack. */
    @Nullable
    public ItemStack createAmmoItem(String ammoId, int amount) {
        return plugin.getCustomItemManager().createAmmoItem(ammoId, amount);
    }

    /** Creates component ItemStack. */
    @Nullable
    public ItemStack createComponentItem(String componentId, int amount) {
        return plugin.getCustomItemManager().createComponentItem(componentId, amount);
    }

    /** Creates mechanical crafting table ItemStack. */
    public ItemStack createMechanicalCraftingTable() {
        return plugin.getCustomItemManager().createStationItem();
    }

    // ── Item Checks ──────────────────────────────────────────────────────────────

    /** Returns true if item is a gun. */
    public boolean isGun(ItemStack item) {
        return getGunId(item) != null;
    }

    /** Returns true if item is ammo. */
    public boolean isAmmo(ItemStack item) {
        return plugin.getCustomItemManager().isAmmo(item);
    }

    /** Returns true if item is a component. */
    public boolean isComponent(ItemStack item) {
        return plugin.getCustomItemManager().isComponent(item);
    }

    /** Returns true if item is the mechanical crafting table. */
    public boolean isMechanicalCraftingTable(ItemStack item) {
        return plugin.getCustomItemManager().isStation(item);
    }

    // ── Item Data ───────────────────────────────────────────────────────────────

    /** Returns gun ID from item, or null if not a gun. */
    @Nullable
    public String getGunId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        NamespacedKey key = new NamespacedKey(plugin, "gun_id");
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.get(key, PersistentDataType.STRING);
    }

    /** Returns ammo ID from item, or null if not ammo. */
    @Nullable
    public String getAmmoId(ItemStack item) {
        return plugin.getCustomItemManager().getAmmoId(item);
    }

    /** Returns component ID from item, or null if not a component. */
    @Nullable
    public String getComponentId(ItemStack item) {
        return plugin.getCustomItemManager().getComponentId(item);
    }

    // ── Ammo Management ──────────────────────────────────────────────────────────

    /** Returns ammo count in player's inventory. */
    public int countAmmo(Player player, String ammoId) {
        return plugin.getCustomItemManager().countAmmo(player.getInventory(), ammoId);
    }

    /** Returns true if player can shoot (cooldown check). */
    public boolean canShoot(UUID playerId, long cooldownMs) {
        return plugin.getAmmoManager().tryShoot(playerId, cooldownMs);
    }

    /** Returns true if player is currently reloading. */
    public boolean isReloading(UUID playerId) {
        return plugin.getAmmoManager().isReloading(playerId);
    }

    // ── World Checks ────────────────────────────────────────────────────────────

    /** Returns true if guns are allowed in this world. */
    public boolean isAllowedInWorld(String worldName) {
        return plugin.getGunManager().isAllowedInWorld(worldName);
    }

    // ── Recipe Book ─────────────────────────────────────────────────────────────

    /** Creates the recipe guide book. */
    public ItemStack createRecipeBook() {
        return plugin.getCustomItemManager().createRecipeBook();
    }

    // ── Stats ───────────────────────────────────────────────────────────────────

    /** Returns number of loaded guns. */
    public int getGunCount() {
        return plugin.getGunManager().getGunCount();
    }

    /** Returns all ammo types. */
    public Collection<String> getAmmoTypes() {
        return plugin.getCustomItemManager().getAllAmmoTypes()
                .stream()
                .map(data -> data.getId())
                .toList();
    }
}
