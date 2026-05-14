package dev.duong2012g.combatgun.hook;

import dev.duong2012g.combatgun.CombatGunSSSPlugin;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Method;

/**
 * Anti-cheat integration hook for CombatGunSSS.
 * <p>
 * CombatGunSSS applies camera recoil ({@code Player#setRotation}) that can trigger
 * false positives in anti-cheat plugins. This class exempts the affected player for
 * a short window (default 3 ticks) so the anti-cheat ignores those movements.
 *
 * <h3>Supported anti-cheats</h3>
 * <ul>
 *   <li><strong>Vulcan</strong> — {@code me.frep.vulcan.api.VulcanAPI}</li>
 *   <li><strong>Matrix</strong> — {@code me.rerere.matrix.api.MatrixAPIProvider}</li>
 * </ul>
 *
 * All calls are made via <strong>reflection</strong> so no anti-cheat jar is needed
 * at compile time. If neither plugin is installed, all methods are no-ops.
 *
 * <h3>Config</h3>
 * <pre>
 * combatgun:
 *   anticheat:
 *     exempt_ticks: 3
 * </pre>
 */
public class AntiCheatHook {

    private final CombatGunSSSPlugin plugin;

    // Vulcan reflection cache
    private boolean vulcanEnabled  = false;
    private Object  vulcanInstance = null;   // me.frep.vulcan.api.VulcanAPI
    private Method  vulcanExempt   = null;
    private Method  vulcanUnExempt = null;

    // Matrix reflection cache
    private boolean matrixEnabled     = false;
    private Object  matrixExemptMgr   = null;  // ExemptionManager
    private Method  matrixExemptMethod = null;
    private Method  matrixUnExemptMethod = null;

    public AntiCheatHook(CombatGunSSSPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Detects which anti-cheat plugins are present and caches their API via reflection.
     * Call from {@code onEnable()}.
     *
     * @return {@code true} if at least one supported anti-cheat was found.
     */
    public boolean setup() {
        vulcanEnabled = trySetupVulcan();
        matrixEnabled = trySetupMatrix();

        if (vulcanEnabled) plugin.getLogger().info("[AntiCheatHook] Vulcan detected — recoil exemptions enabled.");
        if (matrixEnabled) plugin.getLogger().info("[AntiCheatHook] Matrix detected — recoil exemptions enabled.");

        return vulcanEnabled || matrixEnabled;
    }

    /** @return Whether any anti-cheat hook is active. */
    public boolean isActive() { return vulcanEnabled || matrixEnabled; }

    /**
     * Exempts the player from anti-cheat checks for {@code exempt_ticks} ticks.
     * Call immediately before applying recoil or velocity changes.
     */
    public void exempt(Player player) {
        if (!isActive()) return;
        int ticks = plugin.getConfig().getInt("combatgun.anticheat.exempt_ticks", 3);
        if (ticks <= 0) ticks = 3;

        callExempt(player);

        final int finalTicks = ticks;
        new BukkitRunnable() {
            @Override public void run() {
                if (player.isOnline()) callUnExempt(player);
            }
        }.runTaskLater(plugin, finalTicks);
    }

    // ── Vulcan setup ──────────────────────────────────────────────────────────

    private boolean trySetupVulcan() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("Vulcan")) return false;
        try {
            Class<?> apiClass  = Class.forName("me.frep.vulcan.api.VulcanAPI");
            Class<?> implClass = Class.forName("me.frep.vulcan.api.VulcanAPI$Implementation");
            Method   getAPI    = implClass.getMethod("getAPI");
            vulcanInstance  = getAPI.invoke(null);
            if (vulcanInstance == null) return false;
            vulcanExempt    = apiClass.getMethod("exemptPlayer", Player.class);
            vulcanUnExempt  = apiClass.getMethod("unExemptPlayer", Player.class);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[AntiCheatHook] Vulcan found but API unavailable: " + e.getMessage());
            return false;
        }
    }

    // ── Matrix setup ──────────────────────────────────────────────────────────

    private boolean trySetupMatrix() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("Matrix")) return false;
        try {
            Class<?> providerClass = Class.forName("me.rerere.matrix.api.MatrixAPIProvider");
            Method   get           = providerClass.getMethod("get");
            Object   api           = get.invoke(null);
            Method   getExemptMgr  = api.getClass().getMethod("getExemptionManager");
            matrixExemptMgr      = getExemptMgr.invoke(api);
            matrixExemptMethod   = matrixExemptMgr.getClass().getMethod("exempt", Player.class);
            matrixUnExemptMethod = matrixExemptMgr.getClass().getMethod("unExempt", Player.class);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[AntiCheatHook] Matrix found but API unavailable: " + e.getMessage());
            return false;
        }
    }

    // ── Exempt / unExempt calls ───────────────────────────────────────────────

    private void callExempt(Player player) {
        if (vulcanEnabled && vulcanInstance != null && vulcanExempt != null) {
            try { vulcanExempt.invoke(vulcanInstance, player); } catch (Exception ignored) {}
        }
        if (matrixEnabled && matrixExemptMgr != null && matrixExemptMethod != null) {
            try { matrixExemptMethod.invoke(matrixExemptMgr, player); } catch (Exception ignored) {}
        }
    }

    private void callUnExempt(Player player) {
        if (vulcanEnabled && vulcanInstance != null && vulcanUnExempt != null) {
            try { vulcanUnExempt.invoke(vulcanInstance, player); } catch (Exception ignored) {}
        }
        if (matrixEnabled && matrixExemptMgr != null && matrixUnExemptMethod != null) {
            try { matrixUnExemptMethod.invoke(matrixExemptMgr, player); } catch (Exception ignored) {}
        }
    }
}
