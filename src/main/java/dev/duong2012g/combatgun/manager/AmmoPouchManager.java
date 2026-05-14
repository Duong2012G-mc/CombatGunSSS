package dev.duong2012g.combatgun.manager;

import dev.duong2012g.combatgun.CombatGunSSSPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the Ammo Pouch item for CombatGunSSS.
 * <p>
 * An Ammo Pouch is a compressed ammo container. Shift+Right-click unpacks its contents
 * directly into the player's inventory. The pouch stores its ammo type and quantity in
 * PersistentDataContainer so the data survives server restarts.
 *
 * <h3>Creating pouches</h3>
 * Admin command: {@code /gun givepouch <ammo_id> <amount> [player]}
 * API: {@link #createPouch(String, int)}
 *
 * <h3>Config ({@code config.yml})</h3>
 * <pre>
 * combatgun:
 *   ammo_pouch:
 *     enabled: true
 *     material: BUNDLE        # Material used for the pouch item
 *     custom_model_data: 8001
 * </pre>
 *
 * <h3>PDC layout</h3>
 * <ul>
 *   <li>{@code combatgun:custom_item_type} → {@code "ammo_pouch"}</li>
 *   <li>{@code combatgun:pouch_ammo_type}  → ammo ID string, e.g. {@code "ar_ammo"}</li>
 *   <li>{@code combatgun:pouch_quantity}   → integer round count</li>
 * </ul>
 */
public class AmmoPouchManager {

    /** PDC type tag for ammo pouches — parallel to CustomItemManager.TYPE_AMMO etc. */
    public static final String TYPE_AMMO_POUCH = "ammo_pouch";

    private final CombatGunSSSPlugin plugin;
    private final NamespacedKey keyType;
    private final NamespacedKey keyAmmoType;
    private final NamespacedKey keyQuantity;

    public AmmoPouchManager(CombatGunSSSPlugin plugin) {
        this.plugin     = plugin;
        this.keyType    = new NamespacedKey(plugin, "custom_item_type");
        this.keyAmmoType = new NamespacedKey(plugin, "pouch_ammo_type");
        this.keyQuantity = new NamespacedKey(plugin, "pouch_quantity");
    }

    /** @return Whether the ammo pouch system is enabled in config. */
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("combatgun.ammo_pouch.enabled", true);
    }

    /** @return Whether the given ItemStack is an ammo pouch. */
    public boolean isAmmoPouch(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return TYPE_AMMO_POUCH.equals(pdc.get(keyType, PersistentDataType.STRING));
    }

    /** @return The ammo type stored in the pouch, or {@code null} if not a pouch. */
    public String getPouchAmmoType(ItemStack item) {
        if (!isAmmoPouch(item)) return null;
        return item.getItemMeta().getPersistentDataContainer()
            .get(keyAmmoType, PersistentDataType.STRING);
    }

    /** @return Quantity stored in the pouch, or 0 if not a pouch. */
    public int getPouchQuantity(ItemStack item) {
        if (!isAmmoPouch(item)) return 0;
        Integer qty = item.getItemMeta().getPersistentDataContainer()
            .get(keyQuantity, PersistentDataType.INTEGER);
        return qty != null ? qty : 0;
    }

    /**
     * Creates an ammo pouch ItemStack.
     *
     * @param ammoId   The ammo type ID (e.g. {@code "ar_ammo"}).
     * @param quantity The number of rounds stored in the pouch.
     * @return A new pouch ItemStack, or {@code null} if the ammo type is unknown.
     */
    public ItemStack createPouch(String ammoId, int quantity) {
        if (quantity <= 0) return null;
        CustomItemManager cim = plugin.getCustomItemManager();
        var ammoData = cim.getAmmoType(ammoId);
        if (ammoData == null) return null;

        Material mat = resolveMaterial();
        int cmdValue = plugin.getConfig().getInt(
            "combatgun.ammo_pouch.custom_model_data", 8001);

        LangManager lang = plugin.getLangManager();
        String displayName = lang.format("ammo_pouch.label", ammoData.getDisplayName());
        List<Component> lore = List.of(
            Component.text(lang.format("ammo_pouch.lore_type", ammoData.getDisplayName()),
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.text(lang.format("ammo_pouch.lore_qty", quantity),
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.text(lang.get("ammo_pouch.lore_use"),
                NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
        );

        ItemStack item = new ItemStack(mat, 1);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text(displayName, NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        if (cmdValue > 0) meta.setCustomModelData(cmdValue);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyType,     PersistentDataType.STRING,  TYPE_AMMO_POUCH);
        pdc.set(keyAmmoType, PersistentDataType.STRING,  ammoId);
        pdc.set(keyQuantity, PersistentDataType.INTEGER, quantity);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Unpacks a pouch into the player's inventory.
     * The pouch item is consumed (removed from the player's hand) on success.
     *
     * @param player The player using the pouch.
     * @param pouch  The pouch item (must be a valid ammo pouch).
     */
    public void unpackPouch(Player player, ItemStack pouch) {
        if (!isAmmoPouch(pouch)) return;

        String ammoId  = getPouchAmmoType(pouch);
        int    qty     = getPouchQuantity(pouch);
        if (ammoId == null || qty <= 0) {
            player.sendActionBar(Component.text(
                plugin.getLangManager().get("ammo_pouch.empty"), NamedTextColor.GRAY));
            return;
        }

        CustomItemManager cim = plugin.getCustomItemManager();
        var ammoData = cim.getAmmoType(ammoId);
        if (ammoData == null) return;

        // Build stacks of 64 and try to add them
        int remaining = qty;
        Map<Integer, ItemStack> overflow = new HashMap<>();
        Inventory inv = player.getInventory();

        while (remaining > 0) {
            int stackSize = Math.min(64, remaining);
            ItemStack ammoStack = cim.createAmmoItem(ammoId, stackSize);
            if (ammoStack == null) break;
            Map<Integer, ItemStack> leftover = inv.addItem(ammoStack);
            if (!leftover.isEmpty()) {
                overflow.putAll(leftover);
                remaining -= (stackSize - leftover.values().stream()
                    .mapToInt(ItemStack::getAmount).sum());
                break;
            }
            remaining -= stackSize;
        }

        LangManager lang = plugin.getLangManager();
        int unpacked = qty - remaining;

        if (unpacked > 0) {
            if (remaining > 0) {
                // ✅ FIX #1: Partial unpack — update PDC with leftover quantity, do NOT destroy pouch
                ItemMeta meta = pouch.getItemMeta();
                if (meta != null) {
                    meta.getPersistentDataContainer()
                        .set(keyQuantity, PersistentDataType.INTEGER, remaining);
                    List<Component> newLore = List.of(
                        Component.text(lang.format("ammo_pouch.lore_type", ammoData.getDisplayName()),
                            net.kyori.adventure.text.format.NamedTextColor.GRAY)
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                        Component.text(lang.format("ammo_pouch.lore_qty", remaining),
                            net.kyori.adventure.text.format.NamedTextColor.GRAY)
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                        Component.text(lang.get("ammo_pouch.lore_use"),
                            net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY)
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
                    );
                    meta.lore(newLore);
                    pouch.setItemMeta(meta);
                }
                player.sendActionBar(Component.text(
                    lang.format("ammo_pouch.unpacked", unpacked, ammoData.getDisplayName()),
                    NamedTextColor.YELLOW));
            } else {
                // Fully unpacked — consume the entire pouch
                pouch.setAmount(0);
                player.sendActionBar(Component.text(
                    lang.format("ammo_pouch.unpacked", unpacked, ammoData.getDisplayName()),
                    NamedTextColor.GREEN));
            }
        }

        if (!overflow.isEmpty()) {
            player.sendActionBar(Component.text(
                lang.get("ammo_pouch.full_inv"), NamedTextColor.RED));
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private Material resolveMaterial() {
        String name = plugin.getConfig()
            .getString("combatgun.ammo_pouch.material", "BUNDLE")
            .toUpperCase();
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Material.BUNDLE;
        }
    }
}
