package dev.duong2012g.combatgun.command;

import dev.duong2012g.combatgun.CombatGunSSSPlugin;
import dev.duong2012g.combatgun.data.AmmoTypeData;
import dev.duong2012g.combatgun.data.GunData;
import dev.duong2012g.combatgun.data.RecipeData;
import dev.duong2012g.combatgun.manager.CustomItemManager;
import dev.duong2012g.combatgun.manager.GunManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GunCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS = Arrays.asList("give","giveammo","givepart","station","list","reload","recipe","inspect","book","buy","givepouch","attach","detach","attachments","givethrowable","stats","leaderboard","statsreset","seasonreset");

    private final CombatGunSSSPlugin plugin;
    private final GunManager gunManager;
    private final CustomItemManager customItemManager;

    public GunCommand(CombatGunSSSPlugin plugin, GunManager gunManager, CustomItemManager customItemManager) {
        this.plugin = plugin;
        this.gunManager = gunManager;
        this.customItemManager = customItemManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("combatgun.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "give" -> handleGive(sender, args);
            case "giveammo" -> handleGiveAmmo(sender, args);
            case "givepart" -> handleGivePart(sender, args);
            case "station" -> handleStation(sender, args);
            case "list" -> handleList(sender, args);
            case "reload" -> handleReload(sender);
            case "recipe"  -> handleRecipe(sender, args);
            case "inspect" -> handleInspect(sender, args);
            case "book" -> handleBook(sender, args);
            case "buy" -> handleBuy(sender, args);
            case "givepouch"      -> handleGivePouch(sender, args);
            case "attach"         -> handleAttach(sender, args);
            case "detach"         -> handleDetach(sender, args);
            case "attachments"    -> handleListAttachments(sender, args);
            case "givethrowable"  -> handleGiveThrowable(sender, args);
            case "stats"          -> handleStats(sender, args);
            case "leaderboard"    -> handleLeaderboard(sender);
            case "statsreset"     -> handleStatsReset(sender, args);
            case "seasonreset"    -> handleSeasonReset(sender, args);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(err("Usage: /gun give <weapon_id> [player]"));
            return true;
        }

        GunData gun = gunManager.getGun(args[1]);
        if (gun == null) {
            sender.sendMessage(err("Unknown weapon: " + args[1]));
            return true;
        }

        Player target = resolveTarget(sender, args, 2);
        if (target == null) {
            return true;
        }

        giveItem(target, plugin.getGunItemHelper().create(gun));
        sender.sendMessage(ok("Gave ")
            .append(Component.text(gun.getName(), TextColor.color(0xFFAA00)))
            .append(ok(" to "))
            .append(Component.text(target.getName(), NamedTextColor.AQUA))
            .append(ok(".")));
        return true;
    }

    private boolean handleGiveAmmo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(err("Usage: /gun giveammo <ammo_id> [amount] [player]"));
            return true;
        }

        AmmoTypeData ammo = customItemManager.getAmmoType(args[1]);
        if (ammo == null) {
            sender.sendMessage(err("Unknown ammo type: " + args[1]));
            return true;
        }

        int amount = parseAmount(args, 2, ammo.getOutput());
        Player target = resolveTarget(sender, args, isIntegerArg(args, 2) ? 3 : 2);
        if (target == null) {
            return true;
        }

        giveItem(target, customItemManager.createAmmoItem(ammo.getId(), amount));
        sender.sendMessage(ok("Gave ")
            .append(Component.text(amount + "x " + ammo.getDisplayName(), NamedTextColor.AQUA))
            .append(ok(" to "))
            .append(Component.text(target.getName(), NamedTextColor.AQUA))
            .append(ok(".")));
        return true;
    }

    private boolean handleGivePart(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(err("Usage: /gun givepart <component_id> [amount] [player]"));
            return true;
        }

        RecipeData recipe = customItemManager.getComponentRecipe(args[1]);
        if (recipe == null) {
            sender.sendMessage(err("Unknown component: " + args[1]));
            return true;
        }

        int amount = parseAmount(args, 2, recipe.getOutputAmount());
        Player target = resolveTarget(sender, args, isIntegerArg(args, 2) ? 3 : 2);
        if (target == null) {
            return true;
        }

        giveItem(target, customItemManager.createComponentItem(recipe.getId(), amount));
        sender.sendMessage(ok("Gave ")
            .append(Component.text(amount + "x " + recipe.getDisplayName(), NamedTextColor.YELLOW))
            .append(ok(" to "))
            .append(Component.text(target.getName(), NamedTextColor.AQUA))
            .append(ok(".")));
        return true;
    }

    private boolean handleStation(CommandSender sender, String[] args) {
        Player target = resolveTarget(sender, args, 1);
        if (target == null) {
            return true;
        }

        giveItem(target, customItemManager.createStationItem());
        sender.sendMessage(ok("Gave Mechanical Crafting Table to ")
            .append(Component.text(target.getName(), NamedTextColor.AQUA))
            .append(ok(".")));
        return true;
    }

    private boolean handleRecipe(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(err("Usage: /gun recipe <gun_id|component_id|ammo_id>"));
            return true;
        }
        String targetId = args[1].toLowerCase();

        // Collect all items in the chain using BFS to avoid infinite loops
        Deque<String> queue = new ArrayDeque<>();
        java.util.Set<String> visited = new HashSet<>();
        queue.add(targetId);

        sender.sendMessage(Component.text("══ Recipe Chain: " + targetId + " ══", NamedTextColor.GOLD));

        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!visited.add(current)) continue;

            dev.duong2012g.combatgun.data.RecipeData recipe = resolveRecipe(current);
            if (recipe == null) {
                sender.sendMessage(
                    Component.text("  ✦ ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(current, NamedTextColor.GRAY))
                        .append(Component.text("  [vanilla material]", NamedTextColor.DARK_GRAY)));
                continue;
            }

            String stationLabel = recipe.getStationId().equals("vanilla_crafting_table")
                ? "Workbench" : customItemManager.getDisplayNameForIngredient(recipe.getStationId());

            sender.sendMessage(
                Component.text("  ▸ ", NamedTextColor.YELLOW)
                    .append(Component.text(recipe.getDisplayName(), NamedTextColor.WHITE))
                    .append(Component.text("  [" + stationLabel + "]", NamedTextColor.DARK_AQUA))
                    .append(Component.text("  → x" + recipe.getOutputAmount(), NamedTextColor.GREEN)));

            for (Map.Entry<String, Integer> entry : recipe.getIngredients().entrySet()) {
                sender.sendMessage(
                    Component.text("      • ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(entry.getValue() + "x  ", NamedTextColor.GRAY))
                        .append(Component.text(entry.getKey(), NamedTextColor.AQUA)));
                if (!visited.contains(entry.getKey())) {
                    queue.add(entry.getKey());
                }
            }
        }
        return true;
    }

    private dev.duong2012g.combatgun.data.RecipeData resolveRecipe(String id) {
        // Gun recipe
        dev.duong2012g.combatgun.data.RecipeData r = gunManager.getGunRecipe(id);
        if (r != null) return r;
        // Ammo recipe
        dev.duong2012g.combatgun.data.AmmoTypeData ammo = customItemManager.getAmmoType(id);
        if (ammo != null) return ammo.getRecipe();
        // Component recipe
        r = customItemManager.getComponentRecipe(id);
        if (r != null) return r;
        // Station recipe
        if (id.equals(customItemManager.getStationId())) return customItemManager.getStationRecipe();
        return null;
    }

    private boolean handleBook(CommandSender sender, String[] args) {
        Player target = resolveTarget(sender, args, 1);
        if (target == null) {
            return true;
        }

        giveItem(target, customItemManager.createRecipeBook());
        sender.sendMessage(ok("Gave Recipe Guide Book to ")
            .append(Component.text(target.getName(), NamedTextColor.AQUA))
            .append(ok(".")));
        return true;
    }

    private boolean handleInspect(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = org.bukkit.Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(err("Player '" + args[1] + "' not found."));
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(err("Specify a player when running from console."));
            return true;
        }

        org.bukkit.inventory.ItemStack held = target.getInventory().getItemInMainHand();
        String gunId = plugin.getGunItemHelper().getGunId(held);
        if (gunId == null) {
            sender.sendMessage(Component.text(target.getName() + " is not holding a gun.", NamedTextColor.GRAY));
            return true;
        }

        dev.duong2012g.combatgun.data.GunData gun = gunManager.getGun(gunId);
        if (gun == null) {
            sender.sendMessage(err("Could not load gun data for id: " + gunId));
            return true;
        }

        int ammo    = plugin.getGunItemHelper().getCurrentAmmo(held);
        boolean broken = plugin.getGunItemHelper().isBroken(held, gun);
        int dur     = gun.getMaxDurability() > 0
            ? plugin.getGunItemHelper().getCurrentDurability(held, gun.getMaxDurability()) : -1;

        sender.sendMessage(Component.text("══ Inspect: " + target.getName() + " ══", NamedTextColor.GOLD));
        sender.sendMessage(line("Weapon",    gun.getName() + "  [" + gun.getRarity() + "]"));
        sender.sendMessage(line("ID",        gunId));
        sender.sendMessage(line("Category",  gun.getCategory()));
        sender.sendMessage(line("Ammo",      ammo + " / " + gun.getMagazineSize()));
        sender.sendMessage(line("Damage",    gun.getDamage() + " HP  (HS ×" + gun.getHeadshotMultiplier() + ")"));
        sender.sendMessage(line("Fire Rate", gun.getFireRate() + " rds/s"));
        sender.sendMessage(line("Range",     (int) gun.getRange() + " blocks"));
        if (gun.getMaxDurability() > 0) {
            sender.sendMessage(line("Durability", (broken ? "BROKEN" : dur + "/" + gun.getMaxDurability())));
        }
        sender.sendMessage(line("Reloading", String.valueOf(plugin.getAmmoManager().isReloading(target.getUniqueId()))));
        sender.sendMessage(line("World",     target.getWorld().getName()
            + (plugin.getGunManager().isAllowedInWorld(target.getWorld().getName()) ? "" : "  ⛔ DISABLED")));
        return true;
    }

    private Component line(String label, String value) {
        return Component.text("  " + label + ": ", NamedTextColor.GRAY)
            .append(Component.text(value, NamedTextColor.WHITE));
    }

    private boolean handleList(CommandSender sender, String[] args) {
        String filter = args.length >= 2 ? args[1].toLowerCase() : null;
        List<GunData> guns = gunManager.getAllGuns().stream()
            .filter(g -> filter == null || g.getCategory().equalsIgnoreCase(filter))
            .sorted((a, b) -> a.getCategory().compareTo(b.getCategory()))
            .collect(Collectors.toList());

        sender.sendMessage(Component.text("══ CombatGunSSS Weapons [" + guns.size() + "] ══", NamedTextColor.GOLD));
        String lastCategory = "";
        for (GunData g : guns) {
            if (!g.getCategory().equalsIgnoreCase(lastCategory)) {
                lastCategory = g.getCategory();
                sender.sendMessage(Component.text("  ▸ " + lastCategory.toUpperCase(), NamedTextColor.YELLOW));
            }
            sender.sendMessage(Component.text("    • ", NamedTextColor.DARK_GRAY)
                .append(Component.text(g.getId(), NamedTextColor.WHITE))
                .append(Component.text("  (" + g.getName() + ")  ", NamedTextColor.GRAY))
                .append(Component.text("[" + g.getRarity() + "]", rarityColor(g.getRarity())))
                .append(Component.text("  range=" + (int) g.getRange(), NamedTextColor.DARK_GRAY)));
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.getGunManager().loadGuns();
        plugin.getCustomItemManager().load();
        plugin.getCraftingManager().registerVanillaRecipes();
        plugin.getEntityDeathListener().reloadDropTable();
        // ✅ FIX: Invalidate shop price cache so updated prices in config.yml
        // are picked up immediately after /gun reload.
        var hm = plugin.getHookManager();
        if (hm != null) hm.getVaultHook().invalidateCache();
        sender.sendMessage(ok("Config reloaded. " + gunManager.getGunCount() + " weapons loaded."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("combatgun.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return filter(SUB_COMMANDS, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filter(gunManager.getAllGuns().stream().map(GunData::getId).collect(Collectors.toList()), args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("giveammo")) {
            return filter(customItemManager.getAllAmmoTypes().stream().map(AmmoTypeData::getId).collect(Collectors.toList()), args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("givepart")) {
            return filter(customItemManager.getAllComponentRecipes().stream().map(RecipeData::getId).collect(Collectors.toList()), args[1]);
        }

        if ((args.length == 3 && Arrays.asList("give", "station").contains(args[0].toLowerCase()))
            || (args.length == 3 && Arrays.asList("giveammo", "givepart").contains(args[0].toLowerCase()) && !isIntegerArg(args, 2))
            || (args.length == 4 && Arrays.asList("giveammo", "givepart").contains(args[0].toLowerCase()))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[args.length - 1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("recipe")) {
            java.util.List<String> all = new java.util.ArrayList<>();
            gunManager.getAllGuns().stream().map(dev.duong2012g.combatgun.data.GunData::getId).forEach(all::add);
            customItemManager.getAllAmmoTypes().stream().map(dev.duong2012g.combatgun.data.AmmoTypeData::getId).forEach(all::add);
            customItemManager.getAllComponentRecipes().stream().map(dev.duong2012g.combatgun.data.RecipeData::getId).forEach(all::add);
            return filter(all, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("inspect")) {
            return filter(org.bukkit.Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
            return filter(gunManager.getAllGuns().stream().map(GunData::getCategory).distinct().sorted().collect(Collectors.toList()), args[1]);
        }

        return List.of();
    }

    private Player resolveTarget(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            Player target = Bukkit.getPlayer(args[index]);
            if (target == null) {
                sender.sendMessage(err("Player '" + args[index] + "' not found."));
            }
            return target;
        }
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(err("Specify a target player when running from console."));
        return null;
    }

    private void giveItem(Player target, ItemStack item) {
        Map<Integer, ItemStack> overflow = target.getInventory().addItem(item);
        overflow.values().forEach(extra -> target.getWorld().dropItemNaturally(target.getLocation(), extra));
    }

    private int parseAmount(String[] args, int index, int fallback) {
        if (!isIntegerArg(args, index)) {
            return fallback;
        }
        return Math.max(1, Integer.parseInt(args[index]));
    }

    private boolean isIntegerArg(String[] args, int index) {
        if (args.length <= index) {
            return false;
        }
        try {
            Integer.parseInt(args[index]);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase();
        return options.stream().filter(s -> s.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }

    /**
     * /gun buy <gun_id>
     * Requires Vault + a configured price. Any player with combatgun.use can run this.
     * Admins (combatgun.admin) can also run it for free by passing "free" as a second arg:
     *   /gun buy ak47 free
     */
    private boolean handleBuy(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(err("This command can only be used by players."));
            return true;
        }
        if (!player.hasPermission("combatgun.use") && !player.hasPermission("combatgun.admin")) {
            player.sendMessage(err("You don't have permission to buy guns."));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(err("Usage: /gun buy <gun_id>"));
            return true;
        }

        var hm = plugin.getHookManager();
        if (hm == null) { sender.sendMessage(err("Shop integrations not available.")); return true; }
        var vault = hm.getVaultHook();
        if (!vault.isEnabled()) {
            player.sendMessage(err("The shop is not enabled on this server."));
            return true;
        }

        String  gunId = args[1].toLowerCase();
        var     gun   = gunManager.getGun(gunId);
        if (gun == null) {
            player.sendMessage(err("Unknown weapon: " + args[1]));
            return true;
        }

        // Admin free-give shortcut: /gun buy <id> free
        boolean freeGive = args.length >= 3 && args[2].equalsIgnoreCase("free")
                           && player.hasPermission("combatgun.admin");
        if (freeGive) {
            var item = plugin.getGunItemHelper().create(gun);
            if (item != null) player.getInventory().addItem(item);
            player.sendMessage(ok("Admin: gave " + gun.getName() + " for free."));
            return true;
        }

        double price = vault.getPrice(gunId);
        if (price < 0) {
            player.sendMessage(err(gun.getName() + " is not available for purchase."));
            return true;
        }

        vault.buyGun(player, gun);
        return true;
    }



    private boolean handleAttach(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(err("Players only.")); return true; }
        if (args.length < 2) { sender.sendMessage(err("Usage: /gun attach <attachment_id>")); return true; }
        var am = plugin.getAttachmentManager();
        String attachId = args[1].toLowerCase();
        if (!am.exists(attachId)) { sender.sendMessage(err("Unknown attachment: " + args[1])); return true; }
        var held = player.getInventory().getItemInMainHand();
        if (plugin.getGunItemHelper().getGunId(held) == null) {
            sender.sendMessage(err("You must hold a gun to attach this.")); return true;
        }
        var prev = am.fitAttachment(held, attachId);
        player.getInventory().setItemInMainHand(held);
        if (prev != null)
            sender.sendMessage(ok("Replaced " + prev.displayName() + " → " + am.getAttachment(attachId).displayName()));
        else
            sender.sendMessage(ok("Attached " + am.getAttachment(attachId).displayName() + "."));
        return true;
    }

    private boolean handleDetach(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(err("Players only.")); return true; }
        if (args.length < 2) { sender.sendMessage(err("Usage: /gun detach <attachment_type>")); return true; }
        var am = plugin.getAttachmentManager();
        dev.duong2012g.combatgun.attachment.AttachmentType type;
        try { type = dev.duong2012g.combatgun.attachment.AttachmentType.valueOf(args[1].toUpperCase()); }
        catch (IllegalArgumentException e) {
            sender.sendMessage(err("Unknown type. Valid: SILENCER, SCOPE, EXTENDED_MAG, GRIP")); return true;
        }
        var held = player.getInventory().getItemInMainHand();
        if (plugin.getGunItemHelper().getGunId(held) == null) {
            sender.sendMessage(err("You must hold a gun.")); return true;
        }
        var removed = am.removeAttachment(held, type);
        player.getInventory().setItemInMainHand(held);
        if (removed != null) sender.sendMessage(ok("Removed " + removed.displayName() + "."));
        else sender.sendMessage(err("No " + type.name().toLowerCase() + " attachment on this gun."));
        return true;
    }

    private boolean handleListAttachments(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(err("Players only.")); return true; }
        var held = player.getInventory().getItemInMainHand();
        String gunId = plugin.getGunItemHelper().getGunId(held);
        if (gunId == null) { sender.sendMessage(err("Hold a gun to inspect attachments.")); return true; }
        var map = plugin.getAttachmentManager().getGunAttachments(held);
        sender.sendMessage(Component.text("── Attachments on " + gunId + " ──", NamedTextColor.GOLD));
        if (map.isEmpty()) {
            sender.sendMessage(Component.text("  None equipped.", NamedTextColor.GRAY));
        } else {
            map.forEach((slot, a) ->
                sender.sendMessage(Component.text("  " + slot.name() + ": ", NamedTextColor.YELLOW)
                    .append(Component.text(a.displayName(), NamedTextColor.WHITE))));
        }
        return true;
    }

    private boolean handleGiveThrowable(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(err("Usage: /gun givethrowable <throwable_id> [player]")); return true; }
        String id = args[1].toLowerCase();
        if (!plugin.getThrowableManager().exists(id)) {
            sender.sendMessage(err("Unknown throwable: " + args[1])); return true;
        }
        Player target = resolveTarget(sender, args, 2);
        if (target == null) return true;
        var item = plugin.getThrowableManager().createThrowable(id);
        if (item == null) { sender.sendMessage(err("Failed to create throwable.")); return true; }
        target.getInventory().addItem(item);
        sender.sendMessage(ok("Gave throwable [" + id + "] to ").append(
            Component.text(target.getName(), NamedTextColor.WHITE)));
        return true;
    }

    private boolean handleStats(CommandSender sender, String[] args) {
        Player target = args.length >= 2 ? plugin.getServer().getPlayer(args[1]) : 
                        (sender instanceof Player p ? p : null);
        if (target == null) { sender.sendMessage(err("Player not found.")); return true; }
        if (plugin.getStatsManager() == null) { sender.sendMessage(err("Stats system is disabled (SQLite failed).")); return true; }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            var stats = plugin.getStatsManager().getStats(target.getUniqueId(), target.getName());
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Component.text("── Stats: " + stats.name() + " ──", NamedTextColor.GOLD));
                sender.sendMessage(Component.text("  Kills: ", NamedTextColor.GRAY)
                    .append(Component.text(stats.kills(), NamedTextColor.WHITE)));
                sender.sendMessage(Component.text("  Deaths: ", NamedTextColor.GRAY)
                    .append(Component.text(stats.deaths(), NamedTextColor.WHITE)));
                sender.sendMessage(Component.text("  Headshots: ", NamedTextColor.GRAY)
                    .append(Component.text(stats.headshots(), NamedTextColor.WHITE)));
                sender.sendMessage(Component.text("  K/D: ", NamedTextColor.GRAY)
                    .append(Component.text(String.format("%.2f", stats.kd()), NamedTextColor.YELLOW)));
                if (!stats.killsByGun().isEmpty()) {
                    sender.sendMessage(Component.text("  Top guns:", NamedTextColor.GRAY));
                    stats.killsByGun().entrySet().stream().limit(3).forEach(e ->
                        sender.sendMessage(Component.text("    " + e.getKey() + ": " + e.getValue() + " kills",
                            NamedTextColor.DARK_GRAY)));
                }
            });
        });
        return true;
    }

    private boolean handleLeaderboard(CommandSender sender) {
        if (plugin.getStatsManager() == null) { sender.sendMessage(err("Stats system is disabled (SQLite failed).")); return true; }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            var top = plugin.getStatsManager().getTopKillers(10);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Component.text("── Top Killers ──", NamedTextColor.GOLD));
                for (int i = 0; i < top.size(); i++) {
                    var s = top.get(i);
                    sender.sendMessage(Component.text(
                        String.format("  %2d. %-16s %4d kills  %.2f K/D",
                            i + 1, s.name(), s.kills(), s.kd()), NamedTextColor.WHITE));
                }
                if (top.isEmpty()) sender.sendMessage(Component.text("  No data yet.", NamedTextColor.GRAY));
            });
        });
        return true;
    }

    /**
     * Resets a specific player's stats. Usage: {@code /gun statsreset <player>}
     */
    private boolean handleStatsReset(CommandSender sender, String[] args) {
        if (plugin.getStatsManager() == null) {
            sender.sendMessage(err("Stats system is disabled (SQLite failed)."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(err("Usage: /gun statsreset <player>"));
            return true;
        }
        var target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(err("Player not found or not online: " + args[1]));
            return true;
        }
        plugin.getStatsManager().resetStats(target.getUniqueId());
        sender.sendMessage(ok("Stats reset for ").append(
            Component.text(target.getName(), NamedTextColor.WHITE)));
        return true;
    }

    /**
     * Resets ALL players' stats — season reset. Requires confirmation arg.
     * Usage: {@code /gun seasonreset confirm}
     */
    private boolean handleSeasonReset(CommandSender sender, String[] args) {
        if (plugin.getStatsManager() == null) {
            sender.sendMessage(err("Stats system is disabled (SQLite failed)."));
            return true;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            sender.sendMessage(err("This will wipe ALL player stats permanently!"));
            sender.sendMessage(err("Type: /gun seasonreset confirm to proceed."));
            return true;
        }
        plugin.getStatsManager().resetAllStats();
        sender.sendMessage(Component.text(
            "✔ Season reset complete — all player stats wiped.", NamedTextColor.GOLD));
        return true;
    }

    private boolean handleGivePouch(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(err("Usage: /gun givepouch <ammo_id> <amount> [player]"));
            return true;
        }
        String ammoId = args[1].toLowerCase();
        if (customItemManager.getAmmoType(ammoId) == null) {
            sender.sendMessage(err("Unknown ammo type: " + args[1]));
            return true;
        }
        int amount;
        try { amount = Integer.parseInt(args[2]); }
        catch (NumberFormatException e) {
            sender.sendMessage(err("Amount must be a number."));
            return true;
        }
        if (amount <= 0) { sender.sendMessage(err("Amount must be > 0.")); return true; }

        Player target = resolveTarget(sender, args, 3);
        if (target == null) return true;

        ItemStack pouch = plugin.getAmmoPouchManager().createPouch(ammoId, amount);
        if (pouch == null) { sender.sendMessage(err("Failed to create ammo pouch.")); return true; }
        target.getInventory().addItem(pouch);
        sender.sendMessage(ok("Gave ammo pouch [" + ammoId + " ×" + amount + "] to ").append(
            Component.text(target.getName(), NamedTextColor.WHITE)));
        return true;
    }

    private Component ok(String text) {
        return Component.text(text, NamedTextColor.GREEN);
    }

    private Component err(String text) {
        return Component.text("✗ " + text, NamedTextColor.RED);
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("── CombatGunSSS Commands ──", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/gun book [player]", NamedTextColor.YELLOW).append(Component.text("  — Give Recipe Guide Book", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gun give <id> [player]", NamedTextColor.YELLOW).append(Component.text("  — Give a weapon", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gun giveammo <ammo_id> [amount] [player]", NamedTextColor.YELLOW).append(Component.text("  — Give ammo items", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gun givepart <component_id> [amount] [player]", NamedTextColor.YELLOW).append(Component.text("  — Give crafting components", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gun station [player]", NamedTextColor.YELLOW).append(Component.text("  — Give Mechanical Crafting Table", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gun list [category]", NamedTextColor.YELLOW).append(Component.text("  — List weapons", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gun reload", NamedTextColor.YELLOW).append(Component.text("  — Reload config", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gun recipe <id>", NamedTextColor.YELLOW).append(Component.text("  — Show full crafting chain", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gun inspect [player]", NamedTextColor.YELLOW).append(Component.text("  — Inspect held weapon", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gun buy <id>", NamedTextColor.YELLOW).append(Component.text("  — Purchase a weapon (requires Vault)", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gun givepouch <ammo_id> <amount> [player]", NamedTextColor.YELLOW).append(Component.text("  — Give an ammo pouch", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gun attach <id>", NamedTextColor.YELLOW).append(Component.text("  — Attach to held gun", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gun detach <type>", NamedTextColor.YELLOW).append(Component.text("  — Remove attachment by type", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gun attachments", NamedTextColor.YELLOW).append(Component.text("  — List attachments on held gun", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gun givethrowable <id> [player]", NamedTextColor.YELLOW).append(Component.text("  — Give grenade/smoke/flashbang", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gun stats [player]", NamedTextColor.YELLOW).append(Component.text("  — View kill stats", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gun leaderboard", NamedTextColor.YELLOW).append(Component.text("  — Top 10 killers", NamedTextColor.GRAY)));
    }

    private net.kyori.adventure.text.format.TextColor rarityColor(String rarity) {
        return switch (rarity.toLowerCase()) {
            case "legendary" -> TextColor.color(0xFF6600);
            case "epic" -> TextColor.color(0xAA00FF);
            case "rare" -> TextColor.color(0x0099FF);
            default -> NamedTextColor.WHITE;
        };
    }
}
