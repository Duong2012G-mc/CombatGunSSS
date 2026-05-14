package dev.duong2012g.combatgun.manager;

import dev.duong2012g.combatgun.CombatGunSSSPlugin;
import dev.duong2012g.combatgun.data.AmmoTypeData;
import dev.duong2012g.combatgun.data.GunData;
import dev.duong2012g.combatgun.data.RecipeData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class CustomItemManager {

    public static final String TYPE_AMMO = "ammo";
    public static final String TYPE_COMPONENT = "component";
    public static final String TYPE_STATION = "station";

    private final CombatGunSSSPlugin plugin;
    private final NamespacedKey keyType;
    private final NamespacedKey keyId;

    private final Map<String, AmmoTypeData> ammoTypes = new LinkedHashMap<>();
    private final Map<String, RecipeData> componentRecipes = new LinkedHashMap<>();

    private String stationId = "mechanical_crafting_table";
    private String stationDisplayName = "Mechanical Crafting Table";
    private RecipeData stationRecipe;

    public CustomItemManager(CombatGunSSSPlugin plugin) {
        this.plugin = plugin;
        this.keyType = new NamespacedKey(plugin, "custom_item_type");
        this.keyId = new NamespacedKey(plugin, "custom_item_id");
    }

    public void load() {
        ammoTypes.clear();
        componentRecipes.clear();

        ConfigurationSection root = plugin.getConfig().getConfigurationSection("combatgun");
        if (root == null) {
            return;
        }

        ConfigurationSection stations = root.getConfigurationSection("stations.mechanical_crafting_table");
        if (stations != null) {
            stationId = stations.getString("item_id", "mechanical_crafting_table").toLowerCase(Locale.ROOT);
            stationDisplayName = stations.getString("display_name", "Mechanical Crafting Table");
            ConfigurationSection stationIngredients = stations.getConfigurationSection("recipe.ingredients");
            stationRecipe = new RecipeData(
                stationId,
                stationDisplayName,
                RecipeData.RecipeKind.STATION,
                stationIngredients == null ? Map.of() : readIngredients(stationIngredients),
                1,
                stations.getString("craft_source", "vanilla_crafting_table")
            );
        }

        ConfigurationSection ammoSection = root.getConfigurationSection("ammo_types");
        if (ammoSection != null) {
            for (String ammoId : ammoSection.getKeys(false)) {
                ConfigurationSection sec = ammoSection.getConfigurationSection(ammoId);
                if (sec == null) {
                    continue;
                }
                String displayName = sec.getString("display_name", title(ammoId));
                int output = Math.max(1, sec.getInt("output", 1));
                ConfigurationSection recipeSection = sec.getConfigurationSection("recipe.ingredients");
                RecipeData recipe = new RecipeData(
                    ammoId.toLowerCase(Locale.ROOT),
                    displayName,
                    RecipeData.RecipeKind.AMMO,
                    recipeSection == null ? Map.of() : readIngredients(recipeSection),
                    output,
                    sec.getString("recipe.station", stationId)
                );
                ammoTypes.put(ammoId.toLowerCase(Locale.ROOT), new AmmoTypeData(ammoId.toLowerCase(Locale.ROOT), displayName, output, recipe));
            }
        }

        Set<String> customIngredients = new TreeSet<>();
        if (stationRecipe != null) {
            stationRecipe.getIngredients().keySet().forEach(id -> {
                if (!isVanillaMaterialId(id)) {
                    customIngredients.add(id);
                }
            });
        }
        ammoTypes.values().forEach(ammo -> ammo.getRecipe().getIngredients().keySet().forEach(id -> {
            if (!isVanillaMaterialId(id)) {
                customIngredients.add(id);
            }
        }));
        plugin.getGunManager().getAllGunRecipes().forEach(recipe -> recipe.getIngredients().keySet().forEach(id -> {
            if (!isVanillaMaterialId(id)) {
                customIngredients.add(id);
            }
        }));

        customIngredients.remove(stationId);
        customIngredients.removeAll(ammoTypes.keySet());

        for (String componentId : customIngredients) {
            componentRecipes.put(componentId, createAutoRecipe(componentId));
        }
    }

    public Collection<AmmoTypeData> getAllAmmoTypes() {
        return Collections.unmodifiableCollection(ammoTypes.values());
    }

    public AmmoTypeData getAmmoType(String ammoId) {
        return ammoTypes.get(ammoId == null ? null : ammoId.toLowerCase(Locale.ROOT));
    }

    public Collection<RecipeData> getAllAmmoRecipes() {
        List<RecipeData> recipes = new ArrayList<>();
        for (AmmoTypeData ammoType : ammoTypes.values()) {
            recipes.add(ammoType.getRecipe());
        }
        return Collections.unmodifiableList(recipes);
    }

    public Collection<RecipeData> getAllComponentRecipes() {
        return Collections.unmodifiableCollection(componentRecipes.values());
    }

    public RecipeData getComponentRecipe(String id) {
        return componentRecipes.get(id == null ? null : id.toLowerCase(Locale.ROOT));
    }

    public RecipeData getStationRecipe() {
        return stationRecipe;
    }

    public String getStationId() {
        return stationId;
    }

    public ItemStack createAmmoItem(String ammoId, int amount) {
        AmmoTypeData ammo = getAmmoType(ammoId);
        if (ammo == null) {
            return null;
        }

        ItemStack item = new ItemStack(resolveAmmoMaterial(ammo.getId()), Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        decorate(meta,
            Component.text(ammo.getDisplayName(), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
            List.of(
                Component.text("Type: ammunition", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Used to reload compatible guns.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
            ),
            TYPE_AMMO,
            ammo.getId(),
            modelDataFor(ammo.getId(), 50000)
        );
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createComponentItem(String componentId, int amount) {
        String normalized = componentId.toLowerCase(Locale.ROOT);
        ItemStack item = new ItemStack(resolveComponentMaterial(normalized), Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        decorate(meta,
            Component.text(title(normalized), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
            List.of(
                Component.text("Mechanical component", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Used in gun and ammo recipes.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
            ),
            TYPE_COMPONENT,
            normalized,
            modelDataFor(normalized, 60000)
        );
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createStationItem() {
        ItemStack item = new ItemStack(Material.SMITHING_TABLE, 1);
        ItemMeta meta = item.getItemMeta();
        decorate(meta,
            Component.text(stationDisplayName, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true),
            List.of(
                Component.text("Custom crafting station", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Right click to open the station.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
            ),
            TYPE_STATION,
            stationId,
            modelDataFor(stationId, 70000)
        );
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createResultItem(RecipeData recipe) {
        if (recipe == null) {
            return null;
        }

        return switch (recipe.getKind()) {
            case COMPONENT -> createComponentItem(recipe.getId(), recipe.getOutputAmount());
            case AMMO -> createAmmoItem(recipe.getId(), recipe.getOutputAmount());
            case STATION -> createStationItem();
            case GUN -> {
                GunData gun = plugin.getGunManager().getGun(recipe.getId());
                yield gun == null ? null : plugin.getGunItemHelper().create(gun);
            }
        };
    }

    public boolean isStation(ItemStack item) {
        return hasType(item, TYPE_STATION);
    }

    public boolean isAmmo(ItemStack item) {
        return hasType(item, TYPE_AMMO);
    }

    public boolean isComponent(ItemStack item) {
        return hasType(item, TYPE_COMPONENT);
    }

    public String getAmmoId(ItemStack item) {
        return hasType(item, TYPE_AMMO) ? getCustomId(item) : null;
    }

    public String getComponentId(ItemStack item) {
        return hasType(item, TYPE_COMPONENT) ? getCustomId(item) : null;
    }

    public String getCustomId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.get(keyId, PersistentDataType.STRING);
    }

    public String identifyIngredient(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }

        String customId = getCustomId(item);
        if (customId != null) {
            return customId;
        }

        return item.getType().name().toLowerCase(Locale.ROOT);
    }

    public int countAmmo(Inventory inventory, String ammoId) {
        int total = 0;
        for (ItemStack item : inventory.getContents()) {
            if (ammoId.equalsIgnoreCase(getAmmoId(item))) {
                total += item.getAmount();
            }
        }
        return total;
    }

    public int consumeAmmo(Inventory inventory, String ammoId, int amount) {
        int remaining = Math.max(0, amount);
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = contents[slot];
            if (!ammoId.equalsIgnoreCase(getAmmoId(item))) {
                continue;
            }

            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            remaining -= take;
            if (item.getAmount() <= 0) {
                contents[slot] = null;
            }
        }
        inventory.setContents(contents);
        return amount - remaining;
    }

    public String getDisplayNameForIngredient(String ingredientId) {
        if (ingredientId == null) {
            return "Unknown";
        }
        String normalized = ingredientId.toLowerCase(Locale.ROOT);
        AmmoTypeData ammoType = getAmmoType(normalized);
        if (ammoType != null) {
            return ammoType.getDisplayName();
        }
        if (normalized.equals(stationId)) {
            return stationDisplayName;
        }
        return title(normalized);
    }

    public boolean isVanillaMaterialId(String id) {
        return id != null && Material.matchMaterial(id.toUpperCase(Locale.ROOT)) != null;
    }

    private boolean hasType(ItemStack item, String type) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String storedType = item.getItemMeta().getPersistentDataContainer().get(keyType, PersistentDataType.STRING);
        return type.equalsIgnoreCase(storedType);
    }

    private Map<String, Integer> readIngredients(ConfigurationSection sec) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String key : sec.getKeys(false)) {
            out.put(key.toLowerCase(Locale.ROOT), Math.max(1, sec.getInt(key, 1)));
        }
        return out;
    }

    private void decorate(ItemMeta meta,
                          Component name,
                          List<Component> lore,
                          String type,
                          String id,
                          int modelData) {
        meta.displayName(name);
        meta.lore(lore);
        meta.setCustomModelData(modelData);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyType, PersistentDataType.STRING, type);
        pdc.set(keyId, PersistentDataType.STRING, id);
    }

    private int modelDataFor(String id, int base) {
        return base + Math.floorMod(Objects.hash(id), 4000);
    }

    private Material resolveAmmoMaterial(String ammoId) {
        return switch (ammoId.toLowerCase(Locale.ROOT)) {
            case "ar_ammo" -> Material.GOLD_NUGGET;
            case "smg_ammo" -> Material.IRON_NUGGET;
            case "sniper_ammo" -> Material.PRISMARINE_CRYSTALS;
            case "sg_ammo" -> Material.CLAY_BALL;
            case "hg_ammo" -> Material.FLINT;
            default -> Material.IRON_NUGGET;
        };
    }

    private Material resolveComponentMaterial(String componentId) {
        String id = componentId.toLowerCase(Locale.ROOT);
        if (id.contains("ingot")) return Material.IRON_INGOT;
        if (id.contains("casing")) return Material.COPPER_INGOT;
        if (id.contains("primer")) return Material.REDSTONE;
        if (id.contains("pellet")) return Material.IRON_NUGGET;
        if (id.contains("shell")) return Material.PAPER;
        if (id.contains("scope") || id.contains("lens") || id.contains("sight")) return Material.SPYGLASS;
        if (id.contains("stock") || id.contains("wood") || id.contains("hardwood")) return Material.STICK;
        if (id.contains("barrel")) return Material.IRON_BARS;
        if (id.contains("receiver")) return Material.DROPPER;
        if (id.contains("spring") || id.contains("coil")) return Material.TRIPWIRE_HOOK;
        if (id.contains("grip") || id.contains("strip")) return Material.LEATHER;
        if (id.contains("circuit")) return Material.COMPARATOR;
        if (id.contains("module") || id.contains("kit") || id.contains("gear") || id.contains("mechanism")) return Material.REPEATER;
        if (id.contains("blade")) return Material.FLINT;
        if (id.contains("parts")) return Material.CHAIN;
        return Material.PAPER;
    }

    private RecipeData createAutoRecipe(String componentId) {
        String id = componentId.toLowerCase(Locale.ROOT);
        Map<String, Integer> ingredients = new LinkedHashMap<>();
        int output = 1;

        if (id.equals("steel_ingot")) {
            ingredients.put("iron_ingot", 2);
            ingredients.put("coal", 1);
            output = 2;
        } else if (id.equals("steel_scrap")) {
            ingredients.put("iron_nugget", 6);
            ingredients.put("coal", 1);
            output = 8;
        } else if (id.equals("brass_casing")) {
            ingredients.put("copper_ingot", 2);
            ingredients.put("iron_nugget", 1);
            output = 8;
        } else if (id.equals("primer_cap")) {
            ingredients.put("iron_nugget", 2);
            ingredients.put("redstone", 1);
            output = 8;
        } else if (id.equals("tungsten_core")) {
            ingredients.put("iron_ingot", 2);
            ingredients.put("flint", 1);
            ingredients.put("gold_nugget", 2);
            output = 2;
        } else if (id.equals("shotgun_shell")) {
            ingredients.put("paper", 2);
            ingredients.put("gunpowder", 1);
            ingredients.put("iron_nugget", 1);
            output = 6;
        } else if (id.equals("lead_pellet")) {
            ingredients.put("iron_nugget", 4);
            output = 12;
        } else if (id.equals("polymer")) {
            ingredients.put("slime_ball", 1);
            ingredients.put("paper", 2);
            ingredients.put("coal", 1);
            output = 4;
        } else if (id.contains("stock") || id.contains("wood") || id.contains("hardwood")) {
            ingredients.put("oak_planks", 3);
            if (id.contains("tactical") || id.contains("reinforced") || id.contains("folding")) {
                ingredients.put("iron_ingot", 2);
            } else {
                // Plain wood_stock / hardwood: add stick to differentiate from the
                // vanilla oak_slab recipe which also uses exactly {oak_planks: 3}.
                ingredients.put("stick", 1);
            }
            if (id.contains("hardwood")) {
                output = 2;
            }
        } else if (id.contains("barrel")) {
            ingredients.put("iron_ingot", id.contains("sniper") || id.contains("anti_materiel") ? 4 : 3);
            ingredients.put("steel_scrap", id.contains("heavy") || id.contains("precision") || id.contains("anti_materiel") ? 2 : 1);
            if (id.contains("suppressor")) {
                ingredients.put("white_wool", 2); // "wool" is not a valid material in Minecraft 1.13+
            }
        } else if (id.contains("receiver")) {
            ingredients.put("iron_ingot", 2);
            ingredients.put("redstone", 1);
            if (id.contains("precision") || id.contains("elite") || id.contains("anti_materiel")) {
                ingredients.put("steel_ingot", 1);
            }
        } else if (id.contains("scope") || id.contains("sight") || id.contains("lens")) {
            ingredients.put("glass_pane", id.contains("precision") ? 4 : 2);
            ingredients.put("redstone", 1);
            if (id.contains("smart") || id.contains("reflex") || id.contains("mini")) {
                ingredients.put("copper_ingot", 1);
            }
        } else if (id.contains("spring") || id.contains("coil")) {
            ingredients.put("iron_nugget", id.contains("recoil") ? 5 : 4);
            ingredients.put("redstone", 1);
            output = 2;
        } else if (id.contains("grip") || id.contains("strip")) {
            ingredients.put("leather", 2);
            if (id.contains("polymer")) {
                ingredients.put("polymer", 1);
            }
            output = id.contains("tape") || id.contains("strip") ? 2 : 1;
        } else if (id.contains("blade")) {
            ingredients.put("iron_ingot", 2);
            ingredients.put("flint", 1);
        } else if (id.contains("cylinder")) {
            ingredients.put("iron_ingot", 2);
            ingredients.put("redstone", 1);
        } else if (id.contains("module") || id.contains("kit") || id.contains("gear") || id.contains("mechanism") || id.contains("conversion")) {
            ingredients.put("iron_ingot", 2);
            ingredients.put("redstone", 2);
            ingredients.put("copper_ingot", 1);
            if (id.contains("charge") || id.contains("burst") || id.contains("grenade")) {
                ingredients.put("gunpowder", 1);
            }
        } else if (id.contains("parts")) {
            ingredients.put("iron_nugget", 6);
            ingredients.put("redstone", 1);
            output = 3;
        } else if (id.contains("circuit")) {
            ingredients.put("redstone", 2);
            ingredients.put("gold_ingot", 1);
            output = 2;
        } else {
            ingredients.put("iron_ingot", 1);
            ingredients.put("redstone", 1);
        }

        return new RecipeData(id, title(id), RecipeData.RecipeKind.COMPONENT, ingredients, output, "vanilla_crafting_table");
    }

    private String title(String raw) {
        String[] parts = raw.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    public ItemStack createRecipeBook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        meta.displayName(Component.text("CombatGunSSS Recipe Guide", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));
        meta.setAuthor("Duong2012G");
        meta.setTitle("CombatGunSSS Recipes");

        // Stamp a PDC marker so RecipeBookListener can identify this book reliably,
        // instead of matching on the display name which any written book could spoof.
        meta.getPersistentDataContainer().set(
            new org.bukkit.NamespacedKey(plugin, "recipe_book_marker"),
            PersistentDataType.BYTE,
            (byte) 1
        );

        List<Component> pages = new ArrayList<>();

        // Page 1: Introduction
        pages.add(Component.text()
            .append(Component.text("CombatGunSSS", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true))
            .append(Component.newline())
            .append(Component.text("Recipe Guide", NamedTextColor.YELLOW))
            .append(Component.newline()).append(Component.newline())
            .append(Component.text("Contents:", NamedTextColor.AQUA))
            .append(Component.newline())
            .append(Component.text("• Components (p.2-4)", NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("• Station (p.5)", NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("• Ammo (p.6-7)", NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("• Guns (p.8+)", NamedTextColor.GRAY))
            .append(Component.newline()).append(Component.newline())
            .append(Component.text("Use ", NamedTextColor.DARK_GRAY))
            .append(Component.text("/gun list", NamedTextColor.YELLOW))
            .append(Component.text(" to see all guns", NamedTextColor.DARK_GRAY))
            .build()
        );

        // Components pages
        pages.addAll(buildRecipePages("Components (Vanilla Workbench):", componentRecipes.values()));

        // Station page
        if (stationRecipe != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("Mechanical Crafting Table:\n\n");
            for (Map.Entry<String, Integer> entry : stationRecipe.getIngredients().entrySet()) {
                sb.append("• ").append(entry.getValue()).append("x ").append(title(entry.getKey())).append("\n");
            }
            sb.append("\nCraft at: Vanilla Workbench");
            pages.add(Component.text(sb.toString(), NamedTextColor.BLACK));
        }

        // Ammo pages
        pages.addAll(buildAmmoPages());

        // Gun pages
        pages.addAll(buildGunPages());

        meta.pages(pages);
        book.setItemMeta(meta);
        return book;
    }

    private List<Component> buildRecipePages(String header, Collection<RecipeData> recipes) {
        List<Component> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        current.append(header).append("\n\n");

        for (RecipeData recipe : recipes) {
            current.append("• ").append(recipe.getDisplayName()).append("\n");
            for (Map.Entry<String, Integer> entry : recipe.getIngredients().entrySet()) {
                current.append("  ").append(entry.getValue()).append("x ").append(title(entry.getKey())).append("\n");
            }
            current.append("  → ").append(recipe.getOutputAmount()).append("x\n\n");

            if (current.length() > 180) {
                result.add(Component.text(current.toString(), NamedTextColor.BLACK));
                current = new StringBuilder();
                current.append(header).append(" (cont'd):\n\n");
            }
        }
        if (current.length() > 20) {
            result.add(Component.text(current.toString(), NamedTextColor.BLACK));
        }
        return result;
    }

    private List<Component> buildAmmoPages() {
        List<Component> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        current.append("Ammo (Mechanical Station):\n\n");

        for (AmmoTypeData ammo : ammoTypes.values()) {
            RecipeData recipe = ammo.getRecipe();
            current.append("• ").append(ammo.getDisplayName()).append("\n");
            for (Map.Entry<String, Integer> entry : recipe.getIngredients().entrySet()) {
                current.append("  ").append(entry.getValue()).append("x ").append(title(entry.getKey())).append("\n");
            }
            current.append("  → ").append(recipe.getOutputAmount()).append("x\n\n");

            if (current.length() > 180) {
                result.add(Component.text(current.toString(), NamedTextColor.BLACK));
                current = new StringBuilder();
                current.append("Ammo (cont'd):\n\n");
            }
        }
        if (current.length() > 20) {
            result.add(Component.text(current.toString(), NamedTextColor.BLACK));
        }
        return result;
    }

    private List<Component> buildGunPages() {
        List<Component> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        current.append("Guns (Mechanical Station):\n\n");

        for (GunData gun : plugin.getGunManager().getAllGuns()) {
            RecipeData recipe = gun.getRecipe();
            if (recipe != null) {
                current.append("• ").append(gun.getName()).append("\n");
                for (Map.Entry<String, Integer> entry : recipe.getIngredients().entrySet()) {
                    current.append("  ").append(entry.getValue()).append("x ").append(title(entry.getKey())).append("\n");
                }
                current.append("\n");

                if (current.length() > 180) {
                    result.add(Component.text(current.toString(), NamedTextColor.BLACK));
                    current = new StringBuilder();
                    current.append("Guns (cont'd):\n\n");
                }
            }
        }
        if (current.length() > 20) {
            result.add(Component.text(current.toString(), NamedTextColor.BLACK));
        }
        return result;
    }
}
