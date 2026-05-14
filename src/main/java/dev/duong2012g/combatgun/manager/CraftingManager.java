package dev.duong2012g.combatgun.manager;

import dev.duong2012g.combatgun.CombatGunSSSPlugin;
import dev.duong2012g.combatgun.data.RecipeData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CraftingManager {

    public static final int OUTPUT_SLOT = 49;
    private static final String VANILLA_CRAFTING_SOURCE = "vanilla_crafting_table";
    public static final List<Integer> INPUT_SLOTS = List.of(
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    );

    private final CombatGunSSSPlugin plugin;
    private final GunManager gunManager;
    private final CustomItemManager customItemManager;
    private final Map<UUID, RecipeData> lastPreview = new HashMap<>();

    public CraftingManager(CombatGunSSSPlugin plugin, GunManager gunManager, CustomItemManager customItemManager) {
        this.plugin = plugin;
        this.gunManager = gunManager;
        this.customItemManager = customItemManager;
    }

    public void registerVanillaRecipes() {
        List<RecipeData> recipes = new ArrayList<>();
        recipes.addAll(customItemManager.getAllComponentRecipes());
        RecipeData station = customItemManager.getStationRecipe();
        if (station != null) {
            recipes.add(station);
        }

        // Include any ammo that might be crafted at a vanilla workbench (though usually not)
        recipes.addAll(customItemManager.getAllAmmoRecipes());

        for (RecipeData recipe : recipes) {
            if (!VANILLA_CRAFTING_SOURCE.equals(recipe.getStationId())) {
                continue;
            }

            ItemStack result = customItemManager.createResultItem(recipe);
            if (result == null) continue;

            NamespacedKey key = new NamespacedKey(plugin, "recipe_" + recipe.getId());
            ShapelessRecipe shapeless = new ShapelessRecipe(key, result);

            Map<String, Integer> ingredients = recipe.getIngredients();
            boolean tooManyIngredients = false;
            outer:
            for (Map.Entry<String, Integer> entry : ingredients.entrySet()) {
                String id = entry.getKey();
                int amount = entry.getValue();

                RecipeChoice choice;
                if (customItemManager.isVanillaMaterialId(id)) {
                    Material mat = Material.matchMaterial(id.toUpperCase());
                    if (mat == null) continue;
                    choice = new RecipeChoice.MaterialChoice(mat);
                } else {
                    ItemStack custom = customItemManager.createComponentItem(id, 1);
                    if (custom == null) {
                        custom = customItemManager.createAmmoItem(id, 1);
                    }
                    if (custom == null) continue;
                    choice = new RecipeChoice.ExactChoice(custom);
                }

                for (int i = 0; i < amount; i++) {
                    try {
                        shapeless.addIngredient(choice);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Skip registering vanilla recipe for " + recipe.getId() + ": too many ingredients (>9)");
                        tooManyIngredients = true;
                        break outer;
                    }
                }
            }
            if (tooManyIngredients) continue;

            // Remove existing to avoid duplicates on reload
            if (Bukkit.getRecipe(key) != null) {
                Bukkit.removeRecipe(key);
            }
            Bukkit.addRecipe(shapeless);
        }
    }

    public void openStation(Player player) {
        Inventory inventory = Bukkit.createInventory(new MechanicalCraftingHolder(player.getUniqueId()), 54,
            Component.text(customItemManager.getDisplayNameForIngredient(customItemManager.getStationId()), NamedTextColor.GOLD));
        fillLayout(inventory);
        player.openInventory(inventory);
        refreshStationPreview(player);
    }

    public boolean isStationInventory(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof MechanicalCraftingHolder;
    }

    public boolean isInputSlot(int slot) {
        return INPUT_SLOTS.contains(slot);
    }

    public void refreshStationPreview(Player player) {
        if (player == null || player.getOpenInventory() == null) {
            return;
        }
        Inventory top = player.getOpenInventory().getTopInventory();
        if (!isStationInventory(top)) {
            return;
        }

        Map<String, Integer> counts = countIngredients(top, INPUT_SLOTS);
        RecipeData recipe = findStationRecipe(counts);
        lastPreview.put(player.getUniqueId(), recipe);
        top.setItem(OUTPUT_SLOT, createPreview(recipe));
    }

    public RecipeData getLastPreview(UUID playerId) {
        return lastPreview.get(playerId);
    }

    public void clearPreview(UUID playerId) {
        lastPreview.remove(playerId);
    }

    public ItemStack craftFromStation(Player player) {
        Inventory top = player.getOpenInventory().getTopInventory();
        if (!isStationInventory(top)) {
            return null;
        }
        RecipeData recipe = findStationRecipe(countIngredients(top, INPUT_SLOTS));
        if (recipe == null) {
            return null;
        }
        // ✅ FIX #11: Snapshot input slots BEFORE consuming so we can roll back on failure
        Map<Integer, ItemStack> snapshot = new java.util.LinkedHashMap<>();
        for (int slot : INPUT_SLOTS) {
            ItemStack item = top.getItem(slot);
            snapshot.put(slot, item != null ? item.clone() : null);
        }
        consumeIngredients(top, INPUT_SLOTS, recipe.getIngredients());
        ItemStack result = customItemManager.createResultItem(recipe);
        if (result == null) {
            // Roll back — restore original ingredients
            for (Map.Entry<Integer, ItemStack> entry : snapshot.entrySet()) {
                top.setItem(entry.getKey(), entry.getValue());
            }
            return null;
        }
        refreshStationPreview(player);
        return result;
    }

    public RecipeData findWorkbenchRecipe(ItemStack[] matrix) {
        Map<String, Integer> counts = countIngredients(matrix);
        if (counts.isEmpty()) {
            return null;
        }

        // Only match recipes whose craft_source is the vanilla crafting table.
        // Component and station-item recipes both carry VANILLA_CRAFTING_SOURCE.
        for (RecipeData recipe : customItemManager.getAllComponentRecipes()) {
            if (VANILLA_CRAFTING_SOURCE.equals(recipe.getStationId()) && matchesExact(recipe, counts)) {
                return recipe;
            }
        }

        RecipeData stationRecipe = customItemManager.getStationRecipe();
        if (stationRecipe != null
                && VANILLA_CRAFTING_SOURCE.equals(stationRecipe.getStationId())
                && matchesExact(stationRecipe, counts)) {
            return stationRecipe;
        }
        return null;
    }

    public ItemStack craftWorkbench(ItemStack[] matrix) {
        RecipeData recipe = findWorkbenchRecipe(matrix);
        if (recipe == null) {
            return null;
        }
        // consumeIngredients sets null slots in the passed array.
        // Caller (CraftingListener) must call setMatrix(matrix) afterwards.
        consumeIngredients(matrix, recipe.getIngredients());
        return customItemManager.createResultItem(recipe);
    }

    public ItemStack createWorkbenchPreview(ItemStack[] matrix) {
        RecipeData recipe = findWorkbenchRecipe(matrix);
        return recipe == null ? null : createPreview(recipe);
    }

    public void returnInputs(Player player, Inventory inventory) {
        for (int slot : INPUT_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            inventory.setItem(slot, null);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            overflow.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
        }
    }

    private RecipeData findStationRecipe(Map<String, Integer> counts) {
        if (counts.isEmpty()) {
            return null;
        }

        // Use best-match (lowest total surplus) instead of first-match so that
        // recipes sharing the same ingredient key-set are disambiguated correctly.
        // Example: ar_ammo, smg_ammo and hg_ammo all use the same 4 ingredient
        // types; without this, placing exact hg_ammo amounts could accidentally
        // match ar_ammo first because ar_ammo is iterated earlier.
        String sid = customItemManager.getStationId();
        RecipeData bestMatch = null;
        long bestSurplus = Long.MAX_VALUE;

        for (RecipeData recipe : gunManager.getAllGunRecipes()) {
            if (sid.equals(recipe.getStationId()) && matchesAtLeast(recipe, counts)) {
                long surplus = computeSurplus(recipe, counts);
                if (surplus < bestSurplus) {
                    bestSurplus = surplus;
                    bestMatch = recipe;
                }
            }
        }

        for (RecipeData recipe : customItemManager.getAllAmmoRecipes()) {
            if (sid.equals(recipe.getStationId()) && matchesAtLeast(recipe, counts)) {
                long surplus = computeSurplus(recipe, counts);
                if (surplus < bestSurplus) {
                    bestSurplus = surplus;
                    bestMatch = recipe;
                }
            }
        }
        return bestMatch;
    }

    /** Sum of (have - need) across all ingredients; lower = closer match. */
    private long computeSurplus(RecipeData recipe, Map<String, Integer> counts) {
        long surplus = 0;
        for (Map.Entry<String, Integer> e : recipe.getIngredients().entrySet()) {
            surplus += (long) counts.getOrDefault(e.getKey(), 0) - e.getValue();
        }
        return surplus;
    }

    private ItemStack createPreview(RecipeData recipe) {
        if (recipe == null) {
            return placeholder(Material.GRAY_STAINED_GLASS_PANE, "Place ingredients here");
        }

        ItemStack item = customItemManager.createResultItem(recipe);
        if (item == null) {
            return placeholder(Material.BARRIER, "Invalid recipe");
        }

        ItemMetaState.appendPreviewLore(item, recipe, customItemManager);
        return item;
    }

    private void fillLayout(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (INPUT_SLOTS.contains(slot) || slot == OUTPUT_SLOT) {
                continue;
            }
            inventory.setItem(slot, placeholder(Material.BLACK_STAINED_GLASS_PANE, " "));
        }
        inventory.setItem(OUTPUT_SLOT, placeholder(Material.GRAY_STAINED_GLASS_PANE, "Place ingredients here"));
        inventory.setItem(4, placeholder(Material.CRAFTING_TABLE, "Mechanical Crafting"));
        inventory.setItem(46, interactivePlaceholder(Material.BOOK, "Recipe Browser", "Click to view all gun & ammo recipes"));
        inventory.setItem(52, placeholder(Material.HOPPER, "Take result here"));
    }

    private ItemStack interactivePlaceholder(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    public void openRecipeBrowser(Player player, int page) {
        List<RecipeData> allRecipes = new ArrayList<>();
        allRecipes.addAll(gunManager.getAllGunRecipes());
        allRecipes.addAll(customItemManager.getAllAmmoRecipes());

        int pageSize = 45; // 9 * 5
        int totalPages = (int) Math.ceil((double) allRecipes.size() / pageSize);
        int finalPage = Math.max(0, Math.min(page, totalPages - 1));

        Inventory browser = Bukkit.createInventory(new RecipeBrowserHolder(finalPage), 54,
            Component.text("Recipe Browser (Page " + (finalPage + 1) + "/" + Math.max(1, totalPages) + ")", NamedTextColor.DARK_AQUA));

        // Fill background
        for (int i = 45; i < 54; i++) {
            browser.setItem(i, placeholder(Material.BLACK_STAINED_GLASS_PANE, " "));
        }

        // Fill items
        int start = finalPage * pageSize;
        int end = Math.min(start + pageSize, allRecipes.size());
        int slot = 0;
        for (int i = start; i < end; i++) {
            RecipeData recipe = allRecipes.get(i);
            ItemStack item = customItemManager.createResultItem(recipe);
            if (item != null) {
                var meta = item.getItemMeta();
                List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                lore.add(Component.empty());
                lore.add(Component.text("Ingredients:", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                recipe.getIngredients().forEach((id, amount) ->
                    lore.add(Component.text("- " + amount + "x " + customItemManager.getDisplayNameForIngredient(id), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
                meta.lore(lore);
                item.setItemMeta(meta);
                browser.setItem(slot++, item);
            }
        }

        // Navigation
        if (finalPage > 0) {
            browser.setItem(48, interactivePlaceholder(Material.ARROW, "Previous Page", "Go to page " + finalPage));
        }
        browser.setItem(49, interactivePlaceholder(Material.BARRIER, "Back to Station", "Return to crafting grid"));
        if (finalPage < totalPages - 1) {
            browser.setItem(50, interactivePlaceholder(Material.ARROW, "Next Page", "Go to page " + (finalPage + 2)));
        }

        player.openInventory(browser);
    }

    private ItemStack placeholder(Material material, String name) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    public Map<String, Integer> countIngredients(Inventory inventory, Collection<Integer> slots) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int slot : slots) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) { // null-check BEFORE identifyIngredient
                continue;
            }
            String ingredientId = customItemManager.identifyIngredient(item);
            if (ingredientId == null) {
                continue;
            }
            counts.merge(ingredientId, item.getAmount(), Integer::sum);
        }
        return counts;
    }

    public Map<String, Integer> countIngredients(ItemStack[] contents) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir()) { // null-check BEFORE calling identifyIngredient
                continue;
            }
            String ingredientId = customItemManager.identifyIngredient(item);
            if (ingredientId == null) {
                continue;
            }
            counts.merge(ingredientId, item.getAmount(), Integer::sum);
        }
        return counts;
    }

    /**
     * Returns true when {@code counts} satisfies every ingredient in {@code recipe}
     * with at least the required amount, and contains no extra ingredient IDs.
     * This prevents a recipe from matching when unrelated items are present but
     * still allows stacks larger than the recipe minimum (e.g. 32 steel when only 14
     * are needed) so the crafting preview works correctly.
     */
    /**
     * Station matching: ingredient counts must be >= recipe amounts.
     * Players can place whole stacks in the station's large input area, so we
     * only require they have *at least* the required amount of each ingredient.
     */
    private boolean matchesAtLeast(RecipeData recipe, Map<String, Integer> counts) {
        if (recipe == null || counts.isEmpty()) {
            return false;
        }
        Map<String, Integer> required = recipe.getIngredients();
        if (required.isEmpty() || required.size() != counts.size()) {
            return false;
        }
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            Integer present = counts.get(entry.getKey());
            if (present == null || present < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Workbench matching: ingredient counts must equal recipe amounts EXACTLY.
     * This prevents plugin component recipes from intercepting vanilla crafting
     * when a player places more items than the component recipe requires
     * (e.g. 4 iron + 1 redstone for a compass would otherwise match a receiver
     * recipe that only needs 2 iron + 1 redstone).
     */
    private boolean matchesExact(RecipeData recipe, Map<String, Integer> counts) {
        if (recipe == null || counts.isEmpty()) {
            return false;
        }
        Map<String, Integer> required = recipe.getIngredients();
        if (required.isEmpty() || required.size() != counts.size()) {
            return false;
        }
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            Integer present = counts.get(entry.getKey());
            if (present == null || !present.equals(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private void consumeIngredients(Inventory inventory, Collection<Integer> slots, Map<String, Integer> required) {
        Map<String, Integer> remaining = new HashMap<>(required);
        for (int slot : slots) {
            ItemStack item = inventory.getItem(slot);
            // ✅ FIX #4: null-check BEFORE calling identifyIngredient to prevent NPE on empty slots
            if (item == null || item.getType().isAir()) continue;
            String id = customItemManager.identifyIngredient(item);
            if (id == null || !remaining.containsKey(id)) {
                continue;
            }
            int need = remaining.get(id);
            int take = Math.min(need, item.getAmount());
            item.setAmount(item.getAmount() - take);
            need -= take;
            if (item.getAmount() <= 0) {
                inventory.setItem(slot, null);
            }
            if (need <= 0) {
                remaining.remove(id);
            } else {
                remaining.put(id, need);
            }
        }
    }

    private void consumeIngredients(ItemStack[] contents, Map<String, Integer> required) {
        Map<String, Integer> remaining = new HashMap<>(required);
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType().isAir()) {
                continue;
            }
            String id = customItemManager.identifyIngredient(item);
            if (id == null || !remaining.containsKey(id)) {
                continue;
            }
            int need = remaining.get(id);
            int take = Math.min(need, item.getAmount());
            int newAmount = item.getAmount() - take;
            need -= take;
            if (newAmount <= 0) {
                contents[slot] = null;
            } else {
                item.setAmount(newAmount);
            }
            if (need <= 0) {
                remaining.remove(id);
            } else {
                remaining.put(id, need);
            }
        }
    }

    private static final class MechanicalCraftingHolder implements InventoryHolder {
        private final UUID owner;

        private MechanicalCraftingHolder(UUID owner) {
            this.owner = owner;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }

        public UUID getOwner() {
            return owner;
        }
    }

    public static final class RecipeBrowserHolder implements InventoryHolder {
        private final int page;

        private RecipeBrowserHolder(int page) {
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }

        public int getPage() {
            return page;
        }
    }

    private static final class ItemMetaState {
        private ItemMetaState() {
        }

        private static void appendPreviewLore(ItemStack item, RecipeData recipe, CustomItemManager itemManager) {
            var meta = item.getItemMeta();
            List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Component.empty().decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Recipe:", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            recipe.getIngredients().forEach((id, amount) -> lore.add(Component.text("- " + amount + "x " + itemManager.getDisplayNameForIngredient(id), NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
            lore.add(Component.empty().decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Click result slot to craft.", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
    }
}
