package dev.duong2012g.combatgun.listener;

import dev.duong2012g.combatgun.CombatGunSSSPlugin;
import dev.duong2012g.combatgun.manager.CraftingManager;
import dev.duong2012g.combatgun.manager.CustomItemManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class CraftingListener implements Listener {

    private final CombatGunSSSPlugin plugin;
    private final CraftingManager craftingManager;
    private final CustomItemManager customItemManager;

    public CraftingListener(CombatGunSSSPlugin plugin, CraftingManager craftingManager, CustomItemManager customItemManager) {
        this.plugin = plugin;
        this.craftingManager = craftingManager;
        this.customItemManager = customItemManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStationUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (!customItemManager.isStation(item)) {
            return;
        }

        event.setCancelled(true);
        craftingManager.openStation(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!(event.getInventory() instanceof CraftingInventory craftingInventory)) {
            return;
        }

        ItemStack preview = craftingManager.createWorkbenchPreview(craftingInventory.getMatrix());
        if (preview != null) {
            craftingInventory.setResult(preview);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory() instanceof CraftingInventory craftingInventory)) {
            return;
        }
        if (craftingManager.findWorkbenchRecipe(craftingInventory.getMatrix()) == null) {
            return;
        }

        event.setCancelled(true);
        // Get matrix ONCE; consumeIngredients mutates item amounts in-place but
        // null-slot replacements only live in the local array copy – we must
        // call setMatrix to push those changes back into the inventory.
        ItemStack[] matrix = craftingInventory.getMatrix();
        ItemStack result = craftingManager.craftWorkbench(matrix);
        if (result == null) {
            return;
        }
        // Push consumed/nulled slots back so the grid shows correctly.
        craftingInventory.setMatrix(matrix);

        Map<Integer, ItemStack> overflow = player.getInventory().addItem(result);
        overflow.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
        plugin.getServer().getScheduler().runTask(plugin,
            () -> craftingInventory.setResult(craftingManager.createWorkbenchPreview(craftingInventory.getMatrix())));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!craftingManager.isStationInventory(top)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Double-click (COLLECT) sweeps the entire open inventory for matching items,
        // which would silently pull ingredients out of the station's input slots and
        // corrupt the crafting preview without going through returnInputs().
        if (event.getClick() == org.bukkit.event.inventory.ClickType.DOUBLE_CLICK) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot < top.getSize()) {
            if (rawSlot == CraftingManager.OUTPUT_SLOT) {
                event.setCancelled(true);
                ItemStack crafted = craftingManager.craftFromStation(player);
                if (crafted == null) {
                    return;
                }
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(crafted);
                overflow.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
                return;
            }
            if (rawSlot == 46) {
                event.setCancelled(true);
                craftingManager.openRecipeBrowser(player, 0);
                return;
            }
            if (!craftingManager.isInputSlot(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }

        // Block shift-click OUT of the top inventory (e.g. accidentally moving
        // ingredients from an input slot to the player inventory). Shift-clicks
        // that originate from the bottom inventory (rawSlot >= top.getSize())
        // are allowed so players can quickly fill input slots from their inventory.
        if (event.isShiftClick() && rawSlot < top.getSize()) {
            event.setCancelled(true);
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> craftingManager.refreshStationPreview(player));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!craftingManager.isStationInventory(top)) {
            return;
        }

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < top.getSize() && !craftingManager.isInputSlot(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }

        if (event.getWhoClicked() instanceof Player player) {
            plugin.getServer().getScheduler().runTask(plugin, () -> craftingManager.refreshStationPreview(player));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRecipeBrowserClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof CraftingManager.RecipeBrowserHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot == 48 && top.getItem(48) != null) {
            craftingManager.openRecipeBrowser(player, holder.getPage() - 1);
        } else if (slot == 49) {
            craftingManager.openStation(player);
        } else if (slot == 50 && top.getItem(50) != null) {
            craftingManager.openRecipeBrowser(player, holder.getPage() + 1);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Inventory top = event.getInventory();
        if (!craftingManager.isStationInventory(top)) {
            return;
        }

        craftingManager.returnInputs(player, top);
        craftingManager.clearPreview(player.getUniqueId());
    }
}
