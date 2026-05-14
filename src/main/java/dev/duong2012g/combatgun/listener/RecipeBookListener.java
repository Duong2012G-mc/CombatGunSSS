package dev.duong2012g.combatgun.listener;

import dev.duong2012g.combatgun.CombatGunSSSPlugin;
import dev.duong2012g.combatgun.data.GunData;
import dev.duong2012g.combatgun.data.RecipeData;
import dev.duong2012g.combatgun.manager.CustomItemManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RecipeBookListener implements Listener {

    private static final int GUI_SIZE = 54;
    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    private final CombatGunSSSPlugin plugin;
    private final CustomItemManager itemManager;
    private final NamespacedKey recipeBookKey;

    public RecipeBookListener(CombatGunSSSPlugin plugin, CustomItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.recipeBookKey = new NamespacedKey(plugin, "recipe_book_gui");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!isRecipeBookGUI(event.getInventory())) {
            return;
        }

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        // Check if clicked a category button
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String category = pdc.get(recipeBookKey, PersistentDataType.STRING);
        
        if (category != null) {
            handleCategoryClick(player, event.getInventory(), category);
        }
    }

    @EventHandler
    public void onBookOpen(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.WRITTEN_BOOK) {
            return;
        }

        // Identify the recipe book via its PDC marker, not via display-name string
        // matching which any written book with "Recipe Guide" in its name could trigger.
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        org.bukkit.NamespacedKey markerKey = new org.bukkit.NamespacedKey(plugin, "recipe_book_marker");
        if (!meta.getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE)) {
            return;
        }

        event.setCancelled(true);
        openRecipeBookGUI(event.getPlayer());
    }

    public void openRecipeBookGUI(Player player) {
        Inventory gui = Bukkit.createInventory(
            new RecipeBookHolder(player.getUniqueId(), RecipeCategory.COMPONENTS),
            GUI_SIZE,
            Component.text("Recipe Book", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true)
        );

        fillBorder(gui);
        fillCategoryNav(gui, RecipeCategory.COMPONENTS);
        fillContent(gui, RecipeCategory.COMPONENTS);

        player.openInventory(gui);
    }

    private void fillBorder(Inventory gui) {
        ItemStack border = createPlaceholder(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < GUI_SIZE; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                gui.setItem(i, border);
            }
        }
    }

    private void fillCategoryNav(Inventory gui, RecipeCategory current) {
        // Components button
        gui.setItem(0, createNavButton(Material.IRON_INGOT, "Components", 
            current == RecipeCategory.COMPONENTS, RecipeCategory.COMPONENTS));
        
        // Ammo button
        gui.setItem(1, createNavButton(Material.GOLD_NUGGET, "Ammo", 
            current == RecipeCategory.AMMO, RecipeCategory.AMMO));
        
        // Guns button
        gui.setItem(2, createNavButton(Material.DIAMOND_SWORD, "Guns", 
            current == RecipeCategory.GUNS, RecipeCategory.GUNS));
        
        // Station button
        gui.setItem(3, createNavButton(Material.SMITHING_TABLE, "Station", 
            current == RecipeCategory.STATION, RecipeCategory.STATION));
    }

    private ItemStack createNavButton(Material material, String name, boolean active, RecipeCategory category) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        NamedTextColor color = active ? NamedTextColor.GREEN : NamedTextColor.GRAY;
        meta.displayName(Component.text(name, color).decoration(TextDecoration.BOLD, active));
        
        // Store category in PDC for click handling
        meta.getPersistentDataContainer().set(
            recipeBookKey,
            PersistentDataType.STRING,
            category.name()
        );
        
        if (active) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        }
        
        item.setItemMeta(meta);
        return item;
    }

    private void fillContent(Inventory gui, RecipeCategory category) {
        int slotIndex = 0;
        
        switch (category) {
            case COMPONENTS -> {
                for (RecipeData recipe : itemManager.getAllComponentRecipes()) {
                    if (slotIndex >= CONTENT_SLOTS.length) break;
                    gui.setItem(CONTENT_SLOTS[slotIndex++], createRecipeItem(recipe));
                }
            }
            case AMMO -> {
                for (var ammo : itemManager.getAllAmmoTypes()) {
                    if (slotIndex >= CONTENT_SLOTS.length) break;
                    gui.setItem(CONTENT_SLOTS[slotIndex++], createRecipeItem(ammo.getRecipe(), ammo.getId()));
                }
            }
            case GUNS -> {
                for (GunData gun : plugin.getGunManager().getAllGuns()) {
                    if (slotIndex >= CONTENT_SLOTS.length) break;
                    if (gun.getRecipe() != null) {
                        gui.setItem(CONTENT_SLOTS[slotIndex++], createGunRecipeItem(gun));
                    }
                }
            }
            case STATION -> {
                RecipeData station = itemManager.getStationRecipe();
                if (station != null) {
                    gui.setItem(CONTENT_SLOTS[0], createRecipeItem(station, "Station"));
                }
            }
        }
    }

    private ItemStack createRecipeItem(RecipeData recipe) {
        return createRecipeItem(recipe, recipe.getId());
    }

    private ItemStack createRecipeItem(RecipeData recipe, String id) {
        ItemStack result = itemManager.createResultItem(recipe);
        if (result == null) {
            result = new ItemStack(Material.PAPER);
        }
        
        ItemMeta meta = result.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Ingredients:", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        
        recipe.getIngredients().forEach((ingredientId, amount) -> {
            String name = itemManager.getDisplayNameForIngredient(ingredientId);
            lore.add(Component.text("  " + amount + "x " + name, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        });
        
        lore.add(Component.empty());
        lore.add(Component.text("Output: " + recipe.getOutputAmount() + "x", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        
        meta.lore(lore);
        result.setItemMeta(meta);
        return result;
    }

    private ItemStack createGunRecipeItem(GunData gun) {
        ItemStack gunItem = plugin.getGunItemHelper().create(gun);
        ItemMeta meta = gunItem.getItemMeta();
        
        RecipeData recipe = gun.getRecipe();
        if (recipe != null) {
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("Ingredients:", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            
            recipe.getIngredients().forEach((ingredientId, amount) -> {
                String name = itemManager.getDisplayNameForIngredient(ingredientId);
                lore.add(Component.text("  " + amount + "x " + name, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            });
            
            lore.add(Component.empty());
            lore.add(Component.text("Craft at: Mechanical Station", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            
            List<Component> existingLore = meta.lore();
            if (existingLore != null) {
                lore.addAll(0, existingLore);
            }
            meta.lore(lore);
        }
        
        gunItem.setItemMeta(meta);
        return gunItem;
    }

    private ItemStack createPlaceholder(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    public boolean isRecipeBookGUI(Inventory inventory) {
        return inventory.getHolder() instanceof RecipeBookHolder;
    }

    public void handleCategoryClick(Player player, Inventory gui, String categoryName) {
        RecipeCategory newCategory;
        try {
            newCategory = RecipeCategory.valueOf(categoryName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("RecipeBookListener: unknown category '" + categoryName + "', ignoring click.");
            return;
        }
        RecipeBookHolder holder = (RecipeBookHolder) gui.getHolder();
        
        // Clear content slots
        for (int slot : CONTENT_SLOTS) {
            gui.setItem(slot, null);
        }
        
        // Update navigation
        fillCategoryNav(gui, newCategory);
        
        // Update content
        fillContent(gui, newCategory);
        
        // Update holder
        holder.setCategory(newCategory);
    }

    private enum RecipeCategory {
        COMPONENTS,
        AMMO,
        GUNS,
        STATION
    }

    private static class RecipeBookHolder implements InventoryHolder {
        private final UUID owner;
        private RecipeCategory category;

        public RecipeBookHolder(UUID owner, RecipeCategory category) {
            this.owner = owner;
            this.category = category;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }

        public UUID getOwner() {
            return owner;
        }

        public RecipeCategory getCategory() {
            return category;
        }

        public void setCategory(RecipeCategory category) {
            this.category = category;
        }
    }
}
