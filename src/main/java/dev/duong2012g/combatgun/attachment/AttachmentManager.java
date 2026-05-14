package dev.duong2012g.combatgun.attachment;

import dev.duong2012g.combatgun.CombatGunSSSPlugin;
import dev.duong2012g.combatgun.data.GunData;
import dev.duong2012g.combatgun.event.AttachmentApplyEvent;
import dev.duong2012g.combatgun.event.AttachmentRemoveEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Central manager for weapon attachments.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Loading attachment definitions from {@code config.yml}.</li>
 *   <li>Creating attachment {@link ItemStack}s.</li>
 *   <li>Reading and writing the attachment state stored in a gun's PDC.</li>
 *   <li>Applying attachment modifiers to a gun's computed stats at shoot time.</li>
 * </ul>
 *
 * <h3>PDC layout (on gun items)</h3>
 * <pre>
 *   combatgun:attach_silencer    → "silencer"         (String, attachment ID or "")
 *   combatgun:attach_scope       → "acog_scope"
 *   combatgun:attach_extended_mag → "extended_mag_ar"
 *   combatgun:attach_grip        → "tactical_grip"
 * </pre>
 *
 * <h3>Attaching via command</h3>
 * <pre>
 *   /gun attach silencer          attach to held gun
 *   /gun detach silencer          remove attachment by type name
 *   /gun attachments              list current attachments on held gun
 * </pre>
 */
public class AttachmentManager {

    public static final String PDC_PREFIX   = "attach_";
    public static final String ITEM_TYPE    = "attachment";

    private final CombatGunSSSPlugin    plugin;
    private final NamespacedKey         keyItemType;
    private final NamespacedKey         keyAttachId;
    private final Map<String, AttachmentData> registry = new LinkedHashMap<>();

    public AttachmentManager(CombatGunSSSPlugin plugin) {
        this.plugin      = plugin;
        this.keyItemType = new NamespacedKey(plugin, "custom_item_type");
        this.keyAttachId = new NamespacedKey(plugin, "attachment_id");
    }

    // ── Loading ───────────────────────────────────────────────────────────────

    /**
     * (Re)loads all attachment definitions from {@code config.yml}.
     * Call from {@code onEnable()} and {@code /gun reload}.
     */
    public void load() {
        registry.clear();
        ConfigurationSection sec = plugin.getConfig()
            .getConfigurationSection("combatgun.attachments");
        if (sec == null) return;

        for (String id : sec.getKeys(false)) {
            ConfigurationSection a = sec.getConfigurationSection(id);
            if (a == null) continue;
            try {
                AttachmentType type = AttachmentType.valueOf(
                    a.getString("type", "GRIP").toUpperCase());
                AttachmentData data = new AttachmentData(
                    id.toLowerCase(),
                    type,
                    a.getString("display_name", id),
                    a.getInt("custom_model_data", 0),
                    a.getDouble("sound_radius_multiplier", 1.0),
                    a.getDouble("damage_multiplier",       1.0),
                    a.getInt(  "magazine_bonus",           0),
                    a.getDouble("recoil_pitch_multiplier", 1.0),
                    a.getDouble("spread_multiplier",       1.0)
                );
                registry.put(id.toLowerCase(), data);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[AttachmentManager] Unknown type for attachment '"
                    + id + "': " + a.getString("type"));
            }
        }
        plugin.getLogger().info("[AttachmentManager] Loaded " + registry.size() + " attachment(s).");
    }

    public Collection<AttachmentData> getAllAttachments() { return registry.values(); }

    public AttachmentData getAttachment(String id) {
        return id == null ? null : registry.get(id.toLowerCase());
    }

    public boolean exists(String id) { return registry.containsKey(id.toLowerCase()); }

    // ── Item creation ─────────────────────────────────────────────────────────

    /**
     * Creates an attachment ItemStack for a player to hold and apply to a gun.
     *
     * @param id The attachment definition ID (e.g. {@code "silencer"}).
     * @return The attachment item, or {@code null} if the ID is unknown.
     */
    public ItemStack createAttachmentItem(String id) {
        AttachmentData data = getAttachment(id);
        if (data == null) return null;

        ItemStack item = new ItemStack(Material.IRON_NUGGET, 1);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text(data.displayName(), NamedTextColor.LIGHT_PURPLE)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Type: " + data.type().name().toLowerCase().replace('_', ' '),
            NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        addStatLore(lore, data);
        lore.add(Component.text("Hold and use /gun attach <gun>", NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        if (data.customModelData() > 0) meta.setCustomModelData(data.customModelData());

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyItemType, PersistentDataType.STRING, ITEM_TYPE);
        pdc.set(keyAttachId, PersistentDataType.STRING, data.id());
        item.setItemMeta(meta);
        return item;
    }

    /** Returns the attachment ID stored on an attachment item, or {@code null}. */
    public String getAttachmentIdFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (!ITEM_TYPE.equals(pdc.get(keyItemType, PersistentDataType.STRING))) return null;
        return pdc.get(keyAttachId, PersistentDataType.STRING);
    }

    public boolean isAttachmentItem(ItemStack item) {
        return getAttachmentIdFromItem(item) != null;
    }

    // ── Gun PDC read/write ────────────────────────────────────────────────────

    private NamespacedKey slotKey(AttachmentType type) {
        return new NamespacedKey(plugin,
            PDC_PREFIX + type.name().toLowerCase());
    }

    /**
     * Returns the attachment currently fitted in a given slot on a gun item,
     * or {@code null} if the slot is empty.
     */
    public AttachmentData getGunAttachment(ItemStack gunItem, AttachmentType slot) {
        if (gunItem == null || !gunItem.hasItemMeta()) return null;
        PersistentDataContainer pdc = gunItem.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(slotKey(slot), PersistentDataType.STRING);
        return (id == null || id.isEmpty()) ? null : getAttachment(id);
    }

    /** Returns all attachments currently on a gun item, keyed by slot. */
    public Map<AttachmentType, AttachmentData> getGunAttachments(ItemStack gunItem) {
        Map<AttachmentType, AttachmentData> result = new EnumMap<>(AttachmentType.class);
        for (AttachmentType slot : AttachmentType.values()) {
            AttachmentData a = getGunAttachment(gunItem, slot);
            if (a != null) result.put(slot, a);
        }
        return result;
    }

    /**
     * Fits an attachment onto a gun item. Replaces any existing attachment in
     * the same slot. Modifies the item meta in-place; caller must
     * {@code player.getInventory().setItemInMainHand(gunItem)} afterwards.
     *
     * @param gunItem  The gun ItemStack to modify.
     * @param attachId The attachment ID to fit.
     * @return The previously equipped attachment in that slot, or {@code null}.
     */
    public AttachmentData fitAttachment(ItemStack gunItem, String attachId) {
        AttachmentData incoming = getAttachment(attachId);
        if (incoming == null) return null;
        AttachmentData previous = getGunAttachment(gunItem, incoming.type());

        ItemMeta meta = gunItem.getItemMeta();
        meta.getPersistentDataContainer().set(
            slotKey(incoming.type()), PersistentDataType.STRING, incoming.id());
        gunItem.setItemMeta(meta);
        return previous;
    }

    /**
     * Fits an attachment onto a gun item, firing {@link AttachmentApplyEvent}.
     * Returns {@code null} and makes no changes if the event is cancelled.
     *
     * @param player   The player performing the action.
     * @param gunItem  The gun ItemStack to modify.
     * @param gunData  Parsed data for the gun.
     * @param attachId The attachment ID to fit.
     * @return The previously equipped attachment (may be {@code null} for empty slot),
     *         or {@code null} if the action was cancelled by the event.
     */
    public AttachmentData fitAttachment(Player player, ItemStack gunItem, GunData gunData, String attachId) {
        AttachmentData incoming = getAttachment(attachId);
        if (incoming == null) return null;
        AttachmentData previous = getGunAttachment(gunItem, incoming.type());

        AttachmentApplyEvent event = new AttachmentApplyEvent(player, gunItem, gunData, incoming, previous);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) return null;

        ItemMeta meta = gunItem.getItemMeta();
        meta.getPersistentDataContainer().set(
            slotKey(incoming.type()), PersistentDataType.STRING, incoming.id());
        gunItem.setItemMeta(meta);
        return previous;
    }

    /**
     * Removes an attachment from a gun item by slot type.
     *
     * @return The removed attachment, or {@code null} if the slot was empty.
     */
    public AttachmentData removeAttachment(ItemStack gunItem, AttachmentType slot) {
        AttachmentData existing = getGunAttachment(gunItem, slot);
        if (existing == null) return null;
        ItemMeta meta = gunItem.getItemMeta();
        meta.getPersistentDataContainer().set(
            slotKey(slot), PersistentDataType.STRING, "");
        gunItem.setItemMeta(meta);
        return existing;
    }

    /**
     * Removes an attachment from a gun item, firing {@link AttachmentRemoveEvent}.
     * Returns {@code null} and makes no changes if the event is cancelled or the slot is empty.
     *
     * @param player  The player performing the action.
     * @param gunItem The gun ItemStack to modify.
     * @param gunData Parsed data for the gun.
     * @param slot    The attachment slot to clear.
     * @return The removed attachment, or {@code null} if the slot was empty or the event was cancelled.
     */
    public AttachmentData removeAttachment(Player player, ItemStack gunItem, GunData gunData, AttachmentType slot) {
        AttachmentData existing = getGunAttachment(gunItem, slot);
        if (existing == null) return null;

        AttachmentRemoveEvent event = new AttachmentRemoveEvent(player, gunItem, gunData, slot, existing);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) return null;

        ItemMeta meta = gunItem.getItemMeta();
        meta.getPersistentDataContainer().set(
            slotKey(slot), PersistentDataType.STRING, "");
        gunItem.setItemMeta(meta);
        return existing;
    }

    // ── Stat modifiers ────────────────────────────────────────────────────────

    /**
     * Computes the effective sound volume multiplier for a gun given its attachments.
     * Only {@link AttachmentType#SILENCER} affects this.
     */
    public double soundMultiplier(ItemStack gunItem) {
        AttachmentData a = getGunAttachment(gunItem, AttachmentType.SILENCER);
        return a != null ? a.soundRadiusMultiplier() : 1.0;
    }

    /**
     * Computes the effective damage multiplier (product of all damage-modifying attachments).
     */
    public double damageMultiplier(ItemStack gunItem) {
        double mult = 1.0;
        for (AttachmentData a : getGunAttachments(gunItem).values()) {
            mult *= a.damageMultiplier();
        }
        return mult;
    }

    /**
     * Returns the extra magazine bonus from an EXTENDED_MAG attachment, or 0.
     */
    public int magBonus(ItemStack gunItem) {
        AttachmentData a = getGunAttachment(gunItem, AttachmentType.EXTENDED_MAG);
        return a != null ? a.magazineBonus() : 0;
    }

    /**
     * Computes the effective spread multiplier from GRIP and SCOPE attachments.
     */
    public double spreadMultiplier(ItemStack gunItem) {
        double mult = 1.0;
        AttachmentData grip = getGunAttachment(gunItem, AttachmentType.GRIP);
        if (grip != null) mult *= grip.spreadMultiplier();
        AttachmentData scope = getGunAttachment(gunItem, AttachmentType.SCOPE);
        if (scope != null) mult *= 0.6; // scope tightens spread by default
        return mult;
    }

    /**
     * Computes the effective recoil pitch multiplier from GRIP.
     */
    public double recoilPitchMultiplier(ItemStack gunItem) {
        AttachmentData grip = getGunAttachment(gunItem, AttachmentType.GRIP);
        return grip != null ? grip.recoilPitchMultiplier() : 1.0;
    }

    /**
     * Returns true if a SCOPE attachment is fitted (makes gun scopeable at runtime).
     */
    public boolean hasScopeAttachment(ItemStack gunItem) {
        return getGunAttachment(gunItem, AttachmentType.SCOPE) != null;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void addStatLore(List<Component> lore, AttachmentData data) {
        switch (data.type()) {
            case SILENCER -> {
                lore.add(statLine("Sound radius", data.soundRadiusMultiplier(), true));
                if (data.damageMultiplier() != 1.0)
                    lore.add(statLine("Damage", data.damageMultiplier(), false));
            }
            case EXTENDED_MAG ->
                lore.add(Component.text("+" + data.magazineBonus() + " magazine rounds",
                    NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            case GRIP -> {
                lore.add(statLine("Recoil", data.recoilPitchMultiplier(), true));
                lore.add(statLine("Spread", data.spreadMultiplier(), true));
            }
            case SCOPE ->
                lore.add(Component.text("Enables precision scope mode",
                    NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        }
    }

    private Component statLine(String label, double mult, boolean lowerIsBetter) {
        boolean isPositive = lowerIsBetter ? mult < 1.0 : mult > 1.0;
        NamedTextColor color = isPositive ? NamedTextColor.GREEN : NamedTextColor.RED;
        String pct = String.format("%.0f%%", mult * 100);
        return Component.text(label + ": " + pct, color)
            .decoration(TextDecoration.ITALIC, false);
    }
}
