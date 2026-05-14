package dev.duong2012g.combatgun.item;

import dev.duong2012g.combatgun.CombatGunSSSPlugin;
import dev.duong2012g.combatgun.data.GunData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class GunItem {

    public static final String KEY_GUN_ID = "gun_id";
    public static final String KEY_CURRENT_AMMO = "current_ammo";
    public static final String KEY_DURABILITY = "durability";

    private final CombatGunSSSPlugin plugin;
    private final NamespacedKey keyGunId;
    private final NamespacedKey keyAmmo;
    private final NamespacedKey keyDurability;

    public GunItem(CombatGunSSSPlugin plugin) {
        this.plugin = plugin;
        this.keyGunId = new NamespacedKey(plugin, KEY_GUN_ID);
        this.keyAmmo = new NamespacedKey(plugin, KEY_CURRENT_AMMO);
        this.keyDurability = new NamespacedKey(plugin, KEY_DURABILITY);
    }

    public ItemStack create(GunData gun) {
        ItemStack item = new ItemStack(getMaterial(gun));
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(gun.getName())
            .color(rarityColor(gun.getRarity()))
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, true));
        // Pass maxDurability as initial durability (gun is brand new)
        meta.lore(buildLore(gun, gun.getMagazineSize(), gun.getMaxDurability()));
        meta.setCustomModelData(gun.getCustomModelData());
        meta.getPersistentDataContainer().set(keyGunId, PersistentDataType.STRING, gun.getId());
        meta.getPersistentDataContainer().set(keyAmmo, PersistentDataType.INTEGER, gun.getMagazineSize());
        if (gun.getMaxDurability() > 0) {
            meta.getPersistentDataContainer().set(keyDurability, PersistentDataType.INTEGER, gun.getMaxDurability());
        }

        item.setItemMeta(meta);
        return item;
    }

    public String getGunId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(keyGunId, PersistentDataType.STRING);
    }

    public int getCurrentAmmo(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer val = item.getItemMeta().getPersistentDataContainer().get(keyAmmo, PersistentDataType.INTEGER);
        return val == null ? 0 : val;
    }

    public void setCurrentAmmo(ItemStack item, int ammo) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(keyAmmo, PersistentDataType.INTEGER, Math.max(0, ammo));
        String gunId = meta.getPersistentDataContainer().get(keyGunId, PersistentDataType.STRING);
        if (gunId != null) {
            GunData gun = plugin.getGunManager().getGun(gunId);
            if (gun != null) {
                // Read current durability from the already-updated meta (PDC is in meta)
                Integer durPdc = meta.getPersistentDataContainer().get(keyDurability, PersistentDataType.INTEGER);
                int currentDur = durPdc != null ? durPdc : gun.getMaxDurability();
                meta.lore(buildLore(gun, Math.max(0, ammo), currentDur));
            }
        }
        item.setItemMeta(meta);
    }

    public int getCurrentDurability(ItemStack item, int maxDurability) {
        if (item == null || !item.hasItemMeta()) return maxDurability;
        Integer val = item.getItemMeta().getPersistentDataContainer().get(keyDurability, PersistentDataType.INTEGER);
        return val == null ? maxDurability : val;
    }

    public void setCurrentDurability(ItemStack item, int durability) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        int clamped = Math.max(0, durability);
        meta.getPersistentDataContainer().set(keyDurability, PersistentDataType.INTEGER, clamped);
        // Rebuild lore so the durability bar updates visually
        String gunId = meta.getPersistentDataContainer().get(keyGunId, PersistentDataType.STRING);
        if (gunId != null) {
            GunData gun = plugin.getGunManager().getGun(gunId);
            if (gun != null && gun.getMaxDurability() > 0) {
                Integer ammoPdc = meta.getPersistentDataContainer().get(keyAmmo, PersistentDataType.INTEGER);
                int currentAmmo = ammoPdc != null ? ammoPdc : 0;
                meta.lore(buildLore(gun, currentAmmo, clamped));
            }
        }
        item.setItemMeta(meta);
    }

    public boolean isBroken(ItemStack item, GunData gun) {
        if (gun.getMaxDurability() <= 0) return false;
        return getCurrentDurability(item, gun.getMaxDurability()) <= 0;
    }

    public boolean isGun(ItemStack item) {
        return getGunId(item) != null;
    }

    private List<Component> buildLore(GunData gun, int currentAmmo, int currentDurability) {
        List<Component> lore = new ArrayList<>();
        Component blank = Component.empty().decoration(TextDecoration.ITALIC, false);

        lore.add(blank);
        lore.add(stat("⚔ Damage", gun.getDamage() + " HP"));
        lore.add(stat("🔥 Fire Rate", gun.getFireRate() + " rds/s"));
        lore.add(stat("🎯 Headshot", "×" + gun.getHeadshotMultiplier()));
        lore.add(stat("📏 Range", (int) gun.getRange() + " blocks"));
        lore.add(stat("🧱 Penetration", String.format("%.2f / %d target", gun.getBlockPenetration(), gun.getEntityPenetration() + 1)));
        if (gun.getBurstCount() > 1) {
            lore.add(stat("💥 Burst", gun.getBurstCount() + " rounds"));
        }
        if (gun.getProjectilesPerShot() > 1) {
            lore.add(stat("🪶 Pellets", String.valueOf(gun.getProjectilesPerShot())));
        }
        lore.add(stat("🔄 Reload", gun.getReloadTime() + "s"));

        if (!gun.isMelee()) {
            lore.add(stat("📦 Ammo", currentAmmo + " / " + gun.getMagazineSize()));
            lore.add(stat("🧰 Ammo Type", plugin.getCustomItemManager().getDisplayNameForIngredient(gun.getAmmoType())));
            if (gun.getMaxDurability() > 0) {
                lore.add(stat("🔧 Durability", buildDurabilityBar(currentDurability, gun.getMaxDurability())));
            }
        }

        lore.add(blank);
        lore.add(Component.text("  " + rarityLabel(gun.getRarity()))
            .color(rarityColor(gun.getRarity()))
            .decoration(TextDecoration.ITALIC, false));
        lore.add(blank);

        if (!gun.isMelee()) {
            lore.add(hint("RIGHT CLICK", "to shoot"));
            lore.add(hint("SWAP HAND [F]", "to reload"));
        } else {
            lore.add(hint("LEFT CLICK", "to attack"));
        }

        return lore;
    }

    private String buildDurabilityBar(int current, int max) {
        if (max <= 0) return current + "/0";   // defensive: should never happen
        int bars = 10;
        int filled = Math.round((float) current / max * bars);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bars; i++) sb.append(i < filled ? '█' : '░');
        return sb + "  " + current + "/" + max;
    }

    private Component stat(String label, String value) {
        return Component.text(label + ": ", NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text(value, NamedTextColor.WHITE));
    }

    private Component hint(String key, String action) {
        return Component.text("[" + key + "] ", NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text(action, NamedTextColor.DARK_GRAY));
    }

    private TextColor rarityColor(String rarity) {
        return switch (rarity.toLowerCase()) {
            case "legendary" -> TextColor.color(0xFF6600);
            case "epic" -> TextColor.color(0xAA00FF);
            case "rare" -> TextColor.color(0x0099FF);
            default -> NamedTextColor.WHITE;
        };
    }

    private String rarityLabel(String rarity) {
        return switch (rarity.toLowerCase()) {
            case "legendary" -> "★★★★ LEGENDARY";
            case "epic" -> "★★★ EPIC";
            case "rare" -> "★★ RARE";
            default -> "★ COMMON";
        };
    }

    private Material getMaterial(GunData gun) {
        if (gun.isMelee()) return Material.IRON_SWORD;
        return switch (gun.getCategory()) {
            case "snipers" -> Material.GOLDEN_HOE;
            case "shotguns" -> Material.STONE_HOE;
            case "pistols" -> Material.WOODEN_HOE;
            case "smgs" -> Material.DIAMOND_HOE;
            default -> Material.IRON_HOE;
        };
    }
}
