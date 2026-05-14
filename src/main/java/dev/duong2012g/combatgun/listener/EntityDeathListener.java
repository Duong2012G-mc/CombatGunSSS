package dev.duong2012g.combatgun.listener;

import dev.duong2012g.combatgun.CombatGunSSSPlugin;
import dev.duong2012g.combatgun.data.GunData;
import dev.duong2012g.combatgun.manager.CustomItemManager;
import dev.duong2012g.combatgun.manager.GunManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import java.util.Map;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import dev.duong2012g.combatgun.data.DamageRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EntityDeathListener implements Listener {

    private final CombatGunSSSPlugin plugin;
    private final GunManager gunManager;
    private final CustomItemManager customItemManager;
    private final Random random = new Random();

    // Weighted drop entry loaded from config
    private record DropEntry(String type, int amount, int weight) {}
    private final List<DropEntry> dropTable = new ArrayList<>();
    private int totalWeight = 0;

    public EntityDeathListener(CombatGunSSSPlugin plugin,
                                GunManager gunManager,
                                CustomItemManager customItemManager) {
        this.plugin = plugin;
        this.gunManager = gunManager;
        this.customItemManager = customItemManager;
        loadDropTable();
    }

    public void reloadDropTable() {
        dropTable.clear();
        totalWeight = 0;
        loadDropTable();
    }

    private void loadDropTable() {
        // Config uses YAML list syntax with `-` entries, so we must use getMapList
        // instead of getConfigurationSection which only works with keyed maps.
        List<Map<?, ?>> drops = plugin.getConfig()
            .getMapList("combatgun.mob_drops.drops");
        for (Map<?, ?> entry : drops) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) entry;
            String type   = (String) map.getOrDefault("type", "ar_ammo");
            int    amount = ((Number) map.getOrDefault("amount", 1)).intValue();
            int    weight = ((Number) map.getOrDefault("weight", 10)).intValue();
            if (weight > 0) {
                dropTable.add(new DropEntry(type, amount, weight));
                totalWeight += weight;
            }
        }
    }

    // ── Player death: custom death message + PvP kill feed ──────────────────
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();

        GunListener gunListener = plugin.getGunListener();
        DamageRecord rec = gunListener != null
            ? gunListener.getRecentDamage().remove(dead.getUniqueId())
            : null;

        if (rec != null) {
            Player shooter = plugin.getServer().getPlayer(rec.shooterUuid());
            GunData recGun  = gunManager.getGun(rec.gunId());
            if (shooter != null && recGun != null) {
                broadcastKillFeedWithContext(shooter, dead, recGun, rec.headshot());
                // Record stats (StatsManager may be null if SQLite failed)
                if (plugin.getStatsManager() != null) {
                    plugin.getStatsManager().recordKill(shooter, rec.gunId(), rec.headshot());
                    plugin.getStatsManager().recordDeath(dead);
                }
                // Override the vanilla death message (PlayerDeathEvent has deathMessage())
                String prefix = rec.headshot() ? "☠ HEADSHOT  " : "🔫 ";
                event.deathMessage(
                    Component.text(prefix, NamedTextColor.RED)
                        .append(Component.text(dead.getName(), NamedTextColor.YELLOW))
                        .append(Component.text(" was killed by ", NamedTextColor.GRAY))
                        .append(Component.text(shooter.getName(), NamedTextColor.YELLOW))
                        .append(Component.text(" using ", NamedTextColor.GRAY))
                        .append(Component.text(recGun.getName(), gunRarityColor(recGun.getRarity())))
                );
            }
        } else {
            // Fallback: player died to a gun but DamageRecord already expired — keep vanilla message
            Player killer = dead.getKiller();
            if (killer != null) {
                ItemStack held = killer.getInventory().getItemInMainHand();
                GunData gun = gunManager.getGun(plugin.getGunItemHelper().getGunId(held));
                if (gun != null) {
                    broadcastKillFeedWithContext(killer, dead, gun, false);
                }
            }
        }
    }

    // ── Non-player entity death: PvE kill feed + mob drops ───────────────────
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Entity dead = event.getEntity();
        // Player deaths are handled by onPlayerDeath above
        if (dead instanceof Player) return;

        Player killer = event.getEntity().getKiller();

        // PvE kill feed
        GunListener gunListener = plugin.getGunListener();
        DamageRecord rec = gunListener != null
            ? gunListener.getRecentDamage().remove(dead.getUniqueId())
            : null;

        if (rec != null) {
            Player shooter = plugin.getServer().getPlayer(rec.shooterUuid());
            GunData recGun  = gunManager.getGun(rec.gunId());
            if (shooter != null && recGun != null) {
                broadcastKillFeedWithContext(shooter, dead, recGun, rec.headshot());
            }
        } else if (killer != null) {
            ItemStack held = killer.getInventory().getItemInMainHand();
            GunData gun = gunManager.getGun(plugin.getGunItemHelper().getGunId(held));
            if (gun != null) {
                broadcastKillFeedWithContext(killer, dead, gun, false);
            }
        }

        // ── Mob drops ─────────────────────────────────────────────────────
        if (!plugin.getConfig().getBoolean("combatgun.mob_drops.enabled", true)) return;
        if (dropTable.isEmpty() || totalWeight <= 0) return;

        double chance = plugin.getConfig().getDouble("combatgun.mob_drops.chance", 0.04);
        if (random.nextDouble() >= chance) return;

        DropEntry chosen = pickDrop();
        if (chosen == null) return;

        ItemStack ammoItem = customItemManager.createAmmoItem(chosen.type(), chosen.amount());
        if (ammoItem == null) return;

        dead.getWorld().dropItemNaturally(dead.getLocation(), ammoItem);

        // Notify the killer if they are nearby and holding a gun
        if (killer != null) {
            killer.sendActionBar(
                Component.text("🎁 +", NamedTextColor.GREEN)
                    .append(Component.text(chosen.amount() + "x ", NamedTextColor.WHITE))
                    .append(Component.text(
                        customItemManager.getDisplayNameForIngredient(chosen.type()),
                        NamedTextColor.AQUA))
                    .append(Component.text(" dropped!", NamedTextColor.GREEN))
            );
        }
    }

    // ── Kill feed formatting ───────────────────────────────────────────────

    private void broadcastKillFeedWithContext(Player killer, Entity victim, GunData gun, boolean headshot) {
        boolean pvp = victim instanceof Player;
        String verb = headshot ? "☠ headshot" : pickVerb(gun, pvp);

        Component msg = Component.text("☠ ", NamedTextColor.DARK_RED)
            .append(Component.text(killer.getName(), NamedTextColor.YELLOW))
            .append(Component.text(" " + verb + " ", NamedTextColor.GRAY))
            .append(victimName(victim))
            .append(Component.text(" with ", NamedTextColor.GRAY))
            .append(Component.text(gun.getName(), gunRarityColor(gun.getRarity())));

        if (pvp) {
            // Broadcast to all players on the server for PvP
            plugin.getServer().broadcast(msg);
        } else {
            // Notify only the killer for PvE
            killer.sendMessage(msg);
        }
    }

    private String pickVerb(GunData gun, boolean pvp) {
        if (gun.isMelee()) return pvp ? "slashed" : "slew";
        return switch (gun.getCategory()) {
            case "snipers" -> "sniped";
            case "shotguns" -> "blasted";
            case "smgs" -> "sprayed";
            default -> pvp ? "eliminated" : "shot";
        };
    }

    private Component victimName(Entity victim) {
        if (victim instanceof Player p) {
            return Component.text(p.getName(), NamedTextColor.RED);
        }
        String raw = victim.getType().name().toLowerCase().replace('_', ' ');
        String name = Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
        return Component.text(name, NamedTextColor.GRAY);
    }

    private TextColor gunRarityColor(String rarity) {
        return switch (rarity.toLowerCase()) {
            case "legendary" -> TextColor.color(0xFF6600);
            case "epic"      -> TextColor.color(0xAA00FF);
            case "rare"      -> TextColor.color(0x0099FF);
            default          -> NamedTextColor.WHITE;
        };
    }

    // ── Weighted random ────────────────────────────────────────────────────

    private DropEntry pickDrop() {
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (DropEntry entry : dropTable) {
            cumulative += entry.weight();
            if (roll < cumulative) return entry;
        }
        return dropTable.isEmpty() ? null : dropTable.get(dropTable.size() - 1);
    }
}
