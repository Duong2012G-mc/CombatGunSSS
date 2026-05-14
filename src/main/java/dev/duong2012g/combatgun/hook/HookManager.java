package dev.duong2012g.combatgun.hook;

import dev.duong2012g.combatgun.CombatGunSSSPlugin;

/**
 * Central manager that owns all soft-depend plugin hooks for CombatGunSSS.
 * <p>
 * Instantiated once in {@link dev.duong2012g.combatgun.CombatGunSSSPlugin#onEnable()}
 * after all managers are ready. Each hook is initialized independently — failure
 * in one hook never prevents the others from loading.
 * <p>
 * Hooks are intentionally kept as simple adapters: they do not register Bukkit
 * listeners or tasks themselves. The GunListener and GunCommand call into hooks
 * via the methods exposed here.
 */
public class HookManager {

    private final PlaceholderHook  placeholderHook;
    private final VaultHook        vaultHook;
    private final WorldGuardHook   worldGuardHook;
    private final AntiCheatHook    antiCheatHook;

    public HookManager(CombatGunSSSPlugin plugin) {
        this.placeholderHook = new PlaceholderHook(plugin);
        this.vaultHook       = new VaultHook(plugin);
        this.worldGuardHook  = new WorldGuardHook(plugin);
        this.antiCheatHook   = new AntiCheatHook(plugin);
    }

    /**
     * Initializes all hooks. Call once from {@code onEnable()} after all managers
     * are ready. Logs a summary line per hook to the server console.
     */
    public void initAll() {
        // PlaceholderAPI
        try {
            if (placeholderHook.register()) {
                log("PlaceholderAPI", true, "8 placeholders registered.");
            } else {
                log("PlaceholderAPI", false, "plugin not found.");
            }
        } catch (Exception e) {
            log("PlaceholderAPI", false, e.getMessage());
        }

        // Vault
        try {
            boolean vaultOk = vaultHook.setup();
            log("Vault", vaultOk,
                vaultOk ? "economy provider found — /gun buy enabled."
                         : "plugin not found or no economy provider.");
        } catch (Exception e) {
            log("Vault", false, e.getMessage());
        }

        // WorldGuard
        try {
            boolean wgOk = worldGuardHook.setup();
            log("WorldGuard", wgOk,
                wgOk ? "region checks active (flag: gun-shooting)."
                      : "plugin not found.");
        } catch (Exception e) {
            log("WorldGuard", false, e.getMessage());
        }

        // Anti-cheat
        try {
            boolean acOk = antiCheatHook.setup();
            log("AntiCheat", acOk,
                acOk ? "exemptions enabled."
                      : "no supported anti-cheat found (Vulcan / Matrix).");
        } catch (Exception e) {
            log("AntiCheat", false, e.getMessage());
        }
    }

    private void log(String name, boolean ok, String detail) {
        String status = ok ? "§a[✔]" : "§7[–]";
        // Strip color for console (Paper strips §-codes for us in logger output)
        System.out.printf("  [CombatGunSSS] %s %-16s %s%n",
            ok ? "[+]" : "[-]", name, detail);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public PlaceholderHook  getPlaceholderHook()  { return placeholderHook; }
    public VaultHook        getVaultHook()         { return vaultHook; }
    public WorldGuardHook   getWorldGuardHook()    { return worldGuardHook; }
    public AntiCheatHook    getAntiCheatHook()     { return antiCheatHook; }
}
