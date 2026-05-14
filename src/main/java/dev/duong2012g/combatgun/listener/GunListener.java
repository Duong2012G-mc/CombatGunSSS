package dev.duong2012g.combatgun.listener;

import dev.duong2012g.combatgun.CombatGunSSSPlugin;
import dev.duong2012g.combatgun.data.DamageRecord;
import dev.duong2012g.combatgun.data.GunData;
import dev.duong2012g.combatgun.event.GunHeadshotEvent;
import dev.duong2012g.combatgun.event.GunHitEvent;
import dev.duong2012g.combatgun.event.GunShootEvent;
import dev.duong2012g.combatgun.item.GunItem;
import dev.duong2012g.combatgun.manager.*;
import dev.duong2012g.combatgun.util.DamageCalculator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadLocalRandom;

public class GunListener implements Listener {

    private static final double RAY_SIZE       = 0.40;
    private static final double SCOPE_SPREAD   = 0.005;
    private static final int    SCOPE_SLOWNESS = 3;
    private static final long   DAMAGE_TTL_MS  = 5_000;
    // ✅ FIX #9: ThreadLocalRandom instead of shared static Random.
    // A shared Random causes thread contention and can produce predictable spread.
    private static ThreadLocalRandom rng() { return ThreadLocalRandom.current(); }

    // Players currently in scope OR ADS mode (both reduce spread)
    private final Set<UUID>               scopedPlayers = new HashSet<>();
    // Players in ADS specifically (separate from full scope)
    private final Set<UUID>               adsPlayers    = new HashSet<>();
    private final Map<UUID, DamageRecord> recentDamage  = new HashMap<>();

    private final CombatGunSSSPlugin plugin;
    private final GunManager         gunManager;
    private final AmmoManager        ammoManager;
    private final ReloadManager      reloadManager;
    private final GunItem            gunItem;
    private final CustomItemManager  customItemManager;

    public GunListener(CombatGunSSSPlugin plugin,
                       GunManager gunManager,
                       AmmoManager ammoManager,
                       ReloadManager reloadManager,
                       CustomItemManager customItemManager) {
        this.plugin           = plugin;
        this.gunManager       = gunManager;
        this.ammoManager      = ammoManager;
        this.reloadManager    = reloadManager;
        this.gunItem          = plugin.getGunItemHelper();
        this.customItemManager = customItemManager;
    }

    public Map<UUID, DamageRecord> getRecentDamage() { return recentDamage; }

    // ── Config helpers ────────────────────────────────────────────────────────

    private boolean isAutoReloadEnabled() {
        return plugin.getConfig().getBoolean("combatgun.auto_reload_when_empty", true);
    }

    private LangManager lang() { return plugin.getLangManager(); }

    // ── Permission / world / region checks ───────────────────────────────────

    private boolean isAllowed(Player player, GunData gun) {
        if (!gunManager.isAllowedInWorld(player.getWorld().getName())) {
            player.sendActionBar(Component.text(lang().get("gun.disabled_world"), NamedTextColor.RED));
            return false;
        }
        var hm = plugin.getHookManager();
        if (hm != null && !hm.getWorldGuardHook().canShoot(player, player.getLocation())) {
            player.sendActionBar(Component.text(lang().get("gun.disabled_region"), NamedTextColor.RED));
            return false;
        }
        if (player.hasPermission("combatgun.use")) return true;
        if (player.hasPermission("combatgun.use." + gun.getId())) return true;
        player.sendActionBar(Component.text(
            lang().format("gun.no_permission", gun.getName()), NamedTextColor.RED));
        return false;
    }

    private boolean isFriendlyFire(Player shooter, Entity target) {
        if (!plugin.getConfig().contains("combatgun.friendly_fire")) return false;
        if (plugin.getConfig().getBoolean("combatgun.friendly_fire", true)) return false;
        if (!(target instanceof Player targetPlayer)) return false;
        String provider = plugin.getConfig().getString("combatgun.team_provider", "scoreboard");
        if ("scoreboard".equalsIgnoreCase(provider)) {
            // ✅ FIX #3: Always use the main scoreboard instead of each player's
            // personal scoreboard. Players may have different scoreboard views
            // (e.g. from a plugin), so team membership must be checked on the
            // authoritative main scoreboard to avoid friendly-fire bypass exploits.
            org.bukkit.scoreboard.Scoreboard mainSb =
                org.bukkit.Bukkit.getScoreboardManager().getMainScoreboard();
            Team sTeam = mainSb.getEntryTeam(shooter.getName());
            Team tTeam = mainSb.getEntryTeam(targetPlayer.getName());
            return sTeam != null && sTeam.equals(tTeam);
        }
        if ("permission_group".equalsIgnoreCase(provider)) {
            return shooter.getEffectivePermissions().stream()
                .filter(p -> p.getPermission().startsWith("combatgun.team.") && p.getValue())
                .anyMatch(p -> targetPlayer.hasPermission(p.getPermission()));
        }
        return false;
    }

    private boolean effectEnabled(String key) {
        return plugin.getConfig().getBoolean("combatgun.effects." + key, true);
    }
    private float soundVolume() {
        return (float) plugin.getConfig().getDouble("combatgun.effects.sound_volume", 1.5);
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Action action = event.getAction();
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();

        // ── Ammo Pouch: Shift+Right-click to unpack ───────────────────────
        AmmoPouchManager pouch = plugin.getAmmoPouchManager();
        if (pouch.isEnabled() && pouch.isAmmoPouch(held)
                && player.isSneaking()
                && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            pouch.unpackPouch(player, held);
            return;
        }

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        String id = gunItem.getGunId(held);
        if (id == null) return;
        GunData gun = gunManager.getGun(id);
        if (gun == null || gun.isMelee()) return;

        event.setCancelled(true);
        if (!isAllowed(player, gun)) return;

        // ── ADS toggle: Shift+Right-click on ADS-capable gun ─────────────
        if (gun.isAdsEnabled() && player.isSneaking()) {
            toggleAds(player, gun);
            return;
        }
        handleShoot(player, held, gun);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityAttack(PlayerInteractEntityEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        String id = gunItem.getGunId(held);
        if (id == null) return;
        GunData gun = gunManager.getGun(id);
        if (gun == null || gun.isMelee()) return;
        event.setCancelled(true);
        if (!isAllowed(player, gun)) return;
        // ADS toggle when sneaking + right-clicking on entity
        if (gun.isAdsEnabled() && player.isSneaking()) {
            toggleAds(player, gun);
            return;
        }
        handleShoot(player, held, gun);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMeleeAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        String id = gunItem.getGunId(held);
        if (id == null) return;
        GunData gun = gunManager.getGun(id);
        if (gun == null || !gun.isMelee()) return;
        if (!isAllowed(player, gun)) { event.setCancelled(true); return; }
        if (!(event.getEntity() instanceof LivingEntity target)) { event.setCancelled(true); return; }
        if (isFriendlyFire(player, target)) { event.setCancelled(true); return; }
        double distance = player.getLocation().distance(target.getLocation());
        if (distance > gun.getRange()) { event.setCancelled(true); return; }
        UUID uid = player.getUniqueId();
        if (!ammoManager.tryMeleeAttack(uid, gun.getShotCooldownMs())) {
            event.setCancelled(true);
            long rem = ammoManager.getMeleeCooldownRemaining(uid, gun.getShotCooldownMs());
            if (rem > 0) {
                player.sendActionBar(Component.text(
                    lang().format("gun.melee_cooldown", String.format("%.1f", rem / 1000.0)),
                    NamedTextColor.YELLOW));
            }
            return;
        }
        event.setCancelled(true);
        target.damage(gun.getDamage(), player);
        if (effectEnabled("melee_sound"))     playMeleeSound(target.getLocation(), gun);
        if (effectEnabled("melee_particles")) spawnMeleeParticles(target.getLocation());
        double kbStrength = gun.getKnockbackStrength();
        if (kbStrength > 0 && !target.isDead()) {
            Vector kb = player.getLocation().getDirection().normalize()
                .multiply(kbStrength * 0.5).setY(kbStrength * 0.2);
            target.setVelocity(target.getVelocity().add(kb));
        }
        player.sendActionBar(Component.text(
            lang().format("gun.melee_hit", gun.getName(), gun.getDamage()), NamedTextColor.GOLD));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        // Check if holding bandage → cure bleeding
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();

        // Bandage cure check first
        BleedingManager bm = plugin.getBleedingManager();
        String cureId = bm.getCureItemId();
        if (!cureId.isEmpty() && bm.isBleeding(player)) {
            String heldId = customItemManager.getComponentId(held);
            if (cureId.equalsIgnoreCase(heldId)) {
                event.setCancelled(true);
                bm.cure(player, true);
                // Consume one bandage
                if (held.getAmount() > 1) held.setAmount(held.getAmount() - 1);
                else player.getInventory().setItemInMainHand(null);
                return;
            }
        }

        String id = gunItem.getGunId(held);
        if (id == null) return;
        GunData gun = gunManager.getGun(id);
        if (gun == null || gun.isMelee()) return;
        event.setCancelled(true);
        if (!isAllowed(player, gun)) return;
        reloadManager.startReload(player, held, gun);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        String id = gunItem.getGunId(held);
        if (id == null) { clearScope(player); return; }
        GunData gun = gunManager.getGun(id);
        // ADS-capable guns handle scope via Shift+RightClick, not sneak toggle alone
        if (gun == null || gun.isAdsEnabled()) { return; }
        if (!gun.isScopeable()) { clearScope(player); return; }
        if (!isAllowed(player, gun)) return;
        if (ammoManager.isReloading(player.getUniqueId())) return;
        if (event.isSneaking()) {
            scopedPlayers.add(player.getUniqueId());
            player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS, Integer.MAX_VALUE, SCOPE_SLOWNESS, false, false, false));
            player.sendActionBar(Component.text(lang().get("scope.enter"), NamedTextColor.AQUA));
        } else {
            clearScope(player);
        }
    }

    @EventHandler
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        clearScope(player);
        clearAds(player);
        cancelReload(player, true);
        // ✅ FIX #12: Capture the target slot from the event to avoid race condition
        // (rapid slot switching could cause getItemInMainHand to return the wrong item
        //  if another slot change fires before the 0-tick delayed task runs)
        final int newSlot = event.getNewSlot();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            // Abort if the player has switched slots again before this task ran
            if (player.getInventory().getHeldItemSlot() != newSlot) return;
            ItemStack newHeld = player.getInventory().getItem(newSlot);
            String newId = gunItem.getGunId(newHeld);
            if (newId == null) return;
            GunData newGun = gunManager.getGun(newId);
            if (newGun == null || newGun.isMelee()) return;
            if (ammoManager.isReloading(player.getUniqueId())) return;
            if (gunItem.getCurrentAmmo(newHeld) <= 0 && isAutoReloadEnabled()) {
                reloadManager.startReload(player, newHeld, newGun);
            } else {
                if (plugin.getHudManager() != null) plugin.getHudManager().sendNow(player);
            }
        });
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        clearScope(p); clearAds(p);
        cancelReload(p, false);
        ammoManager.clearPlayer(p.getUniqueId());
        plugin.getBleedingManager().cure(p, false);
        // ✅ FIX #2: Remove recentDamage entry on quit to prevent memory leak.
        // Without this, stale DamageRecord entries accumulate indefinitely for
        // players who disconnect — pruneRecentDamage() only fires after shots.
        recentDamage.remove(p.getUniqueId());
    }

    @EventHandler public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (scopedPlayers.contains(player.getUniqueId())) {
            String droppedId = gunItem.getGunId(event.getItemDrop().getItemStack());
            String heldId    = gunItem.getGunId(player.getInventory().getItemInMainHand());
            if (droppedId != null && droppedId.equalsIgnoreCase(heldId)) clearScope(player);
        }
        clearAds(player);
        cancelReload(player, true);
    }

    @EventHandler public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        clearScope(p); clearAds(p);
        cancelReload(p, false);
        ammoManager.clearPlayer(p.getUniqueId());
        plugin.getBleedingManager().cure(p, false);
        // ✅ FIX #2: Also remove recentDamage on death — the entry is no longer
        // useful once the player is dead and respawns with a fresh state.
        recentDamage.remove(p.getUniqueId());
    }

    @EventHandler public void onTeleport(PlayerTeleportEvent event) {
        clearScope(event.getPlayer()); clearAds(event.getPlayer());
        cancelReload(event.getPlayer(), false);
    }

    @EventHandler public void onChangedWorld(PlayerChangedWorldEvent event) {
        clearScope(event.getPlayer()); clearAds(event.getPlayer());
        cancelReload(event.getPlayer(), false);
    }

    // ── ADS ───────────────────────────────────────────────────────────────────

    private void toggleAds(Player player, GunData gun) {
        UUID uid = player.getUniqueId();
        if (adsPlayers.contains(uid)) {
            clearAds(player);
        } else {
            adsPlayers.add(uid);
            // Apply slow-walk penalty while ADS
            double penalty = gun.getAdsMovementPenalty();
            int slowLevel = (int) Math.round((1.0 - penalty) * 10);
            if (slowLevel > 0) {
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS, Integer.MAX_VALUE,
                    Math.min(slowLevel, 5), false, false, false));
            }
            player.sendActionBar(Component.text(
                lang().format("ads.enter", gun.getAdsSpreadMultiplier()), NamedTextColor.AQUA));
        }
    }

    private void clearAds(Player player) {
        boolean wasAds = adsPlayers.remove(player.getUniqueId());
        if (wasAds) {
            PotionEffect existing = player.getPotionEffect(PotionEffectType.SLOWNESS);
            if (existing != null && existing.getAmplifier() <= 5) {
                player.removePotionEffect(PotionEffectType.SLOWNESS);
            }
            player.sendActionBar(Component.text(lang().get("ads.exit"), NamedTextColor.GRAY));
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void clearScope(Player player) {
        boolean wasScoped = scopedPlayers.remove(player.getUniqueId());
        if (wasScoped) {
            PotionEffect existing = player.getPotionEffect(PotionEffectType.SLOWNESS);
            if (existing != null && existing.getAmplifier() == SCOPE_SLOWNESS)
                player.removePotionEffect(PotionEffectType.SLOWNESS);
        }
    }

    private void cancelReload(Player player, boolean notify) {
        UUID uid = player.getUniqueId();
        if (!ammoManager.isReloading(uid)) return;
        reloadManager.cancelReload(uid);
        if (notify) player.sendActionBar(Component.text(lang().get("gun.reload_cancelled"), NamedTextColor.RED));
    }

    // ── Shoot pipeline ────────────────────────────────────────────────────────

    private void handleShoot(Player player, ItemStack held, GunData gun) {
        if (gunItem.isBroken(held, gun)) {
            player.sendActionBar(Component.text(
                lang().format("gun.broken", gun.getName()), NamedTextColor.RED));
            return;
        }
        UUID uid = player.getUniqueId();
        if (ammoManager.isReloading(uid)) {
            player.sendActionBar(Component.text(lang().get("gun.reloading"), NamedTextColor.YELLOW));
            return;
        }
        if (!ammoManager.tryShoot(uid, gun.getShotCooldownMs())) return;

        GunShootEvent shootEvent = new GunShootEvent(player, gun);
        plugin.getServer().getPluginManager().callEvent(shootEvent);
        if (shootEvent.isCancelled()) return;

        int currentAmmo = gunItem.getCurrentAmmo(held);
        if (currentAmmo <= 0) {
            player.sendActionBar(Component.text(lang().get("gun.empty"), NamedTextColor.RED));
            playEmptyClick(player);
            return;
        }

        int shotsRequested = Math.max(1, gun.getBurstCount());
        int shotsToFire    = Math.min(currentAmmo, shotsRequested);
        int remainingAmmo  = currentAmmo - shotsToFire;
        gunItem.setCurrentAmmo(held, remainingAmmo);

        if (gun.getMaxDurability() > 0) {
            int dur = gunItem.getCurrentDurability(held, gun.getMaxDurability());
            gunItem.setCurrentDurability(held, dur - 1);
            if (dur - 1 <= 0) {
                player.sendActionBar(Component.text(
                    lang().format("gun.durability_broken", gun.getName()), NamedTextColor.RED));
            } else if (dur - 1 <= gun.getMaxDurability() * 0.2) {
                player.sendActionBar(Component.text(
                    lang().format("gun.durability_low", dur - 1, gun.getMaxDurability()), NamedTextColor.GOLD));
            }
        }
        player.getInventory().setItemInMainHand(held);
        if (plugin.getHudManager() != null) plugin.getHudManager().sendNow(player);
        fireShotSequence(player, gun, shotsToFire, remainingAmmo, shootEvent.getDamageMultiplier());
        if (gun.getBurstCount() > 1 && gun.getBurstIntervalTicks() > 0) {
            long burstDurationMs = gun.getBurstIntervalTicks() * Math.max(0, shotsToFire - 1) * 50L;
            ammoManager.extendCooldown(uid, burstDurationMs);
        }
    }

    private void fireShotSequence(Player player, GunData gun, int shotsToFire, int remainingAmmo, double damageMultiplier) {
        long delayStep = Math.max(0L, gun.getBurstIntervalTicks());
        for (int burstIndex = 0; burstIndex < shotsToFire; burstIndex++) {
            final int idx = burstIndex;
            new BukkitRunnable() {
                @Override public void run() {
                    if (!player.isOnline()) return;
                    if (!gun.getId().equalsIgnoreCase(gunItem.getGunId(player.getInventory().getItemInMainHand()))) return;
                    performShot(player, gun, idx, damageMultiplier);
                }
            }.runTaskLater(plugin, delayStep * burstIndex);
        }
        long afterBurstDelay = Math.max(1L, delayStep * Math.max(0, shotsToFire - 1) + 1L);
        new BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline()) return;
                ItemStack held = player.getInventory().getItemInMainHand();
                if (!gun.getId().equalsIgnoreCase(gunItem.getGunId(held))) return;
                if (remainingAmmo == 0 && isAutoReloadEnabled()) {
                    reloadManager.startReload(player, held, gun);
                }
            }
        }.runTaskLater(plugin, afterBurstDelay);
    }

    private void performShot(Player player, GunData gun, int burstIndex, double damageMultiplier) {
        Location eye    = player.getEyeLocation();
        boolean  scoped = scopedPlayers.contains(player.getUniqueId());
        boolean  ads    = adsPlayers.contains(player.getUniqueId());

        double moveSpreadMult = isMoving(player) ? gun.getMovementSpreadMultiplier() : 1.0;
        // ✅ IMPROVEMENT: Delegate spread calculation to DamageCalculator (extracted from God Class)
        double spread = DamageCalculator.effectiveSpread(gun, SCOPE_SPREAD, scoped, ads, moveSpreadMult);

        if (effectEnabled("gun_sound"))    playGunSound(player, gun);
        if (effectEnabled("muzzle_flash")) spawnMuzzleFlash(eye.clone().add(eye.getDirection().clone().multiply(1.1)));
        applyRecoil(player, gun, 1.0 + (burstIndex * 0.12));

        Vector baseDir = eye.getDirection();
        for (int p = 0; p < gun.getProjectilesPerShot(); p++) {
            // ✅ IMPROVEMENT: Delegate pellet spread to DamageCalculator
            double pelletSpread = DamageCalculator.pelletSpread(spread, gun.getProjectilesPerShot() > 1);
            Vector direction    = applySpread(baseDir, pelletSpread);
            TraceOutcome outcome = traceProjectile(player, eye, direction, gun, damageMultiplier);
            if (effectEnabled("bullet_trail")) drawBulletTrail(eye, outcome.endLocation());
        }
    }

    private TraceOutcome traceProjectile(Player player, Location origin, Vector direction, GunData gun, double damageMultiplier) {
        World     world                 = player.getWorld();
        Location  cursor                = origin.clone();
        Location  end                   = origin.clone().add(direction.clone().multiply(gun.getRange()));
        double    remainingDistance     = gun.getRange();
        double    travelled             = 0.0;
        double    remainingPenetration  = gun.getBlockPenetration();
        int       remainingEntityPasses = gun.getEntityPenetration();
        Set<UUID> alreadyHit            = new HashSet<>();

        while (remainingDistance > 0.2) {
            RayTraceResult result = world.rayTrace(
                cursor, direction, remainingDistance,
                FluidCollisionMode.NEVER, true, RAY_SIZE,
                e -> isValidTarget(player, e, alreadyHit));
            if (result == null) {
                end = cursor.clone().add(direction.clone().multiply(remainingDistance));
                break;
            }
            Location hitLocation = result.getHitPosition().toLocation(world);
            double   segmentDist = cursor.toVector().distance(result.getHitPosition());
            travelled           += segmentDist;
            remainingDistance   -= segmentDist;
            end = hitLocation;
            if (result.getHitEntity() instanceof LivingEntity target) {
                alreadyHit.add(target.getUniqueId());
                applyEntityDamage(player, target, result, gun, travelled, damageMultiplier);
                if (effectEnabled("hit_particles")) spawnHitParticles(hitLocation);
                if (remainingEntityPasses-- <= 0) break;
                remainingPenetration -= 0.35;
                if (remainingPenetration <= 0) break;
                cursor = hitLocation.clone().add(direction.clone().multiply(0.35));
                remainingDistance -= 0.35;
                continue;
            }
            Block hitBlock = result.getHitBlock();
            if (hitBlock != null) {
                Material type = hitBlock.getType();
                if (effectEnabled("block_impact")) spawnBlockImpact(hitLocation, type);
                double blockCost = getBlockPenetrationCost(type);
                if (blockCost >= 99.0 || remainingPenetration < blockCost) break;
                remainingPenetration -= blockCost;
                cursor = hitLocation.clone().add(direction.clone().multiply(0.45));
                remainingDistance -= 0.45;
                continue;
            }
            break;
        }
        return new TraceOutcome(end);
    }

    private boolean isValidTarget(Player shooter, Entity entity, Set<UUID> alreadyHit) {
        if (!(entity instanceof LivingEntity)) return false;
        if (entity.equals(shooter)) return false;
        if (alreadyHit.contains(entity.getUniqueId())) return false;
        if (isFriendlyFire(shooter, entity)) return false;
        return true;
    }

    private void applyEntityDamage(Player shooter, LivingEntity target,
                                   RayTraceResult result, GunData gun,
                                   double distanceTravelled, double damageMultiplier) {
        boolean headshot = isHeadshot(result, target);
        // ✅ IMPROVEMENT: Delegate all damage math to DamageCalculator
        double  baseDmg  = DamageCalculator.baseDamage(gun, damageMultiplier);
        double  finalDmg = DamageCalculator.finalDamage(gun, distanceTravelled, headshot, damageMultiplier);

        target.setNoDamageTicks(0);
        target.damage(finalDmg, shooter);

        // ── GunHitEvent ──────────────────────────────────────────────────────
        GunHitEvent hitEvent = new GunHitEvent(shooter, target, gun, baseDmg, finalDmg, headshot, distanceTravelled);
        plugin.getServer().getPluginManager().callEvent(hitEvent);

        // ── Bleeding ─────────────────────────────────────────────────────────
        if (target instanceof Player targetPlayer) {
            plugin.getBleedingManager().tryApply(targetPlayer);
        }

        recentDamage.put(target.getUniqueId(),
            new DamageRecord(shooter.getUniqueId(), gun.getId(), gun.getName(),
                             headshot, finalDmg, System.currentTimeMillis()));
        pruneRecentDamage();

        double kbStrength = gun.getKnockbackStrength();
        if (kbStrength > 0 && !target.isDead()) {
            Vector kb = shooter.getEyeLocation().getDirection().normalize()
                .multiply(kbStrength * 0.4).setY(kbStrength * 0.15);
            target.setVelocity(target.getVelocity().add(kb));
        }
        if (headshot) {
            GunHeadshotEvent hs = new GunHeadshotEvent(shooter, target, gun, finalDmg);
            plugin.getServer().getPluginManager().callEvent(hs);
            shooter.sendActionBar(Component.text(
                lang().format("gun.headshot", (int) finalDmg), NamedTextColor.RED));
            if (effectEnabled("headshot_particles")) spawnHeadshotParticles(target.getEyeLocation());
        }
    }

    private void pruneRecentDamage() {
        Iterator<Map.Entry<UUID, DamageRecord>> it = recentDamage.entrySet().iterator();
        while (it.hasNext()) { if (it.next().getValue().isExpired(DAMAGE_TTL_MS)) it.remove(); }
    }

    private boolean isMoving(Player player) {
        return player.isSprinting() || player.getVelocity().lengthSquared() > 0.08 || !player.isOnGround();
    }

    private Vector applySpread(Vector direction, double spread) {
        if (spread <= 0) return direction.clone();
        return direction.clone().add(new Vector(
            (rng().nextDouble() - 0.5) * spread,
            (rng().nextDouble() - 0.5) * spread,
            (rng().nextDouble() - 0.5) * spread)).normalize();
    }

    private boolean isHeadshot(RayTraceResult result, LivingEntity entity) {
        // ✅ IMPROVEMENT: Delegate to DamageCalculator — single source of truth for headshot math
        return DamageCalculator.isHeadshot(
            result.getHitPosition().getY(),
            entity.getLocation().getY(),
            entity.getHeight(),
            entity instanceof Player p && p.isSneaking()
        );
    }

    private void applyRecoil(Player player, GunData gun, double strengthMod) {
        // ✅ FIX #8: Guard both HookManager AND AntiCheatHook against null before calling exempt
        var hm2 = plugin.getHookManager();
        if (hm2 != null && hm2.getAntiCheatHook() != null && hm2.getAntiCheatHook().isActive()) {
            hm2.getAntiCheatHook().exempt(player);
        }
        Location loc   = player.getLocation();
        float    yaw   = (float)(loc.getYaw() + (rng().nextDouble() - 0.5) * gun.getRecoil().getYaw() * 8.0 * strengthMod);
        float    pitch = (float) Math.max(-90.0, loc.getPitch() - gun.getRecoil().getPitch() * strengthMod);
        player.setRotation(yaw, pitch);
    }

    private double getBlockPenetrationCost(Material material) {
        if (material.isAir()) return 0.0;
        String name = material.name();
        if (name.contains("GLASS") || name.contains("PANE") || name.contains("LEAVES")
            || name.contains("VINE") || name.contains("WOOL") || name.contains("CARPET")) return 0.35;
        if (name.contains("IRON_DOOR") || name.contains("IRON_TRAPDOOR")) return 99.0;
        if (name.contains("BAMBOO") || name.contains("PLANK") || name.contains("LOG")
            || name.contains("WOOD") || name.contains("DOOR") || name.contains("TRAPDOOR")) return 0.95;
        if (name.contains("SANDSTONE")) return 99.0;
        if (name.contains("DIRT") || name.contains("SAND") || name.contains("GRAVEL")
            || name.contains("MUD")) return 1.2;
        return 99.0;
    }

    private void playGunSound(Player player, GunData gun) {
        try { Sound s = Sound.valueOf(gun.getSound());
            player.getWorld().playSound(player.getLocation(), s, soundVolume(), 1.0f);
        } catch (IllegalArgumentException ignored) {
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, soundVolume(), 1.0f);
        }
    }
    private void playMeleeSound(Location loc, GunData gun) {
        try { Sound s = Sound.valueOf(gun.getSound());
            loc.getWorld().playSound(loc, s, 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 1.0f);
        }
    }
    private void playEmptyClick(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 1f, 2f);
    }
    private void spawnMuzzleFlash(Location loc) {
        loc.getWorld().spawnParticle(Particle.FLASH, loc, 1, 0, 0, 0, 0, Color.YELLOW);
        loc.getWorld().spawnParticle(Particle.SMOKE, loc, 4, 0.02, 0.02, 0.02, 0.01);
    }
    private void spawnHitParticles(Location loc) {
        loc.getWorld().spawnParticle(Particle.CRIT, loc, 8, 0.12, 0.12, 0.12, 0.3);
        loc.getWorld().playSound(loc, Sound.ENTITY_ARROW_HIT_PLAYER, 0.5f, 1.2f);
    }
    private void spawnHeadshotParticles(Location loc) {
        loc.getWorld().spawnParticle(Particle.ENCHANT, loc, 20, 0.2, 0.2, 0.2, 0.5);
        loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 1f, 0.8f);
    }
    private void spawnMeleeParticles(Location loc) {
        loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc, 1);
    }
    private void spawnBlockImpact(Location loc, Material material) {
        World w = loc.getWorld();
        w.spawnParticle(Particle.BLOCK, loc, 10, 0.08, 0.08, 0.08, 0.0, material.createBlockData());
        w.playSound(loc, Sound.BLOCK_STONE_HIT, 0.4f, 1.4f);
    }
    private void drawBulletTrail(Location origin, Location end) {
        World  world    = origin.getWorld();
        double distance = origin.distance(end);
        int steps = distance < 20 ? (int)(distance * 2) : (int)(distance * 0.8);
        steps = Math.min(40, Math.max(2, steps));
        Particle.DustOptions dust = new Particle.DustOptions(org.bukkit.Color.fromRGB(180, 180, 180), 0.3f);
        Vector delta = end.toVector().subtract(origin.toVector()).multiply(1.0 / steps);
        Location point = origin.clone();
        for (int i = 0; i < steps; i++) { point.add(delta);
            world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dust); }
    }

    private record TraceOutcome(Location endLocation) {}
}
