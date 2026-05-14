package dev.duong2012g.combatgun;

import dev.duong2012g.combatgun.api.CombatGunAPI;
import dev.duong2012g.combatgun.attachment.AttachmentManager;
import dev.duong2012g.combatgun.command.GunCommand;
import dev.duong2012g.combatgun.hook.HookManager;
import dev.duong2012g.combatgun.hook.WorldGuardHook;
import dev.duong2012g.combatgun.item.GunItem;
import dev.duong2012g.combatgun.listener.*;
import dev.duong2012g.combatgun.manager.*;
import dev.duong2012g.combatgun.stats.StatsManager;
import dev.duong2012g.combatgun.throwable.ThrowableListener;
import dev.duong2012g.combatgun.throwable.ThrowableManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class CombatGunSSSPlugin extends JavaPlugin {

    private GunManager          gunManager;
    private AmmoManager         ammoManager;
    private ReloadManager       reloadManager;
    private GunItem             gunItemHelper;
    private CustomItemManager   customItemManager;
    private CraftingManager     craftingManager;
    private HudManager          hudManager;
    private LangManager         langManager;
    private BleedingManager     bleedingManager;
    private AmmoPouchManager    ammoPouchManager;
    private AttachmentManager   attachmentManager;
    private ThrowableManager    throwableManager;
    private StatsManager        statsManager;
    private HookManager         hookManager;
    private EntityDeathListener entityDeathListener;
    private GunListener         gunListener;

    @Override
    public void onLoad() {
        // Must run before WorldGuard reads region data.
        // Wrapped in try-catch so a missing WorldGuard never crashes onLoad.
        try { WorldGuardHook.registerFlags(); }
        catch (Throwable t) { /* WorldGuard absent or incompatible — safe to ignore */ }
    }

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            initManagers();
            registerListeners();
            startServices();
            registerCommands();
            scheduleCleanup();
            printStartupBanner();
        } catch (Throwable t) {
            getLogger().severe("═══════════════════════════════════════════════");
            getLogger().severe("  CombatGunSSS FAILED to enable!");
            getLogger().severe("  Error: " + t.getMessage());
            getLogger().severe("  See stack trace below — please report this.");
            getLogger().severe("═══════════════════════════════════════════════");
            t.printStackTrace();
            // Disable cleanly instead of leaving the plugin in a broken half-state.
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void initManagers() {
        // Language first — other managers may log translated messages.
        langManager       = new LangManager(this);
        langManager.load();

        // ✅ FIX: Check config version so server admins are warned when their
        // config.yml is outdated after a plugin update, rather than silently
        // using missing/wrong defaults.
        checkConfigVersion();

        gunItemHelper     = new GunItem(this);
        gunManager        = new GunManager(this);
        ammoManager       = new AmmoManager(this);
        customItemManager = new CustomItemManager(this);
        bleedingManager   = new BleedingManager(this);
        ammoPouchManager  = new AmmoPouchManager(this);
        attachmentManager = new AttachmentManager(this);
        throwableManager  = new ThrowableManager(this);

        // StatsManager is optional — if SQLite fails the plugin still works,
        // just without persistent stats.
        statsManager = new StatsManager(this);
        try { statsManager.init(); }
        catch (Throwable t) {
            getLogger().warning("[StatsManager] Failed to initialise SQLite — stats disabled.");
            getLogger().warning("  Cause: " + t.getMessage());
            statsManager = null;
        }

        gunManager.loadGuns();
        customItemManager.load();
        attachmentManager.load();
        throwableManager.load();

        CombatGunAPI.init(this);

        reloadManager   = new ReloadManager(this, ammoManager, customItemManager);
        craftingManager = new CraftingManager(this, gunManager, customItemManager);
        craftingManager.registerVanillaRecipes();
    }

    private void registerListeners() {
        gunListener = new GunListener(this, gunManager, ammoManager, reloadManager, customItemManager);
        getServer().getPluginManager().registerEvents(gunListener, this);
        getServer().getPluginManager().registerEvents(
            new CraftingListener(this, craftingManager, customItemManager), this);
        getServer().getPluginManager().registerEvents(
            new RecipeBookListener(this, customItemManager), this);
        getServer().getPluginManager().registerEvents(new ThrowableListener(this), this);
        entityDeathListener = new EntityDeathListener(this, gunManager, customItemManager);
        getServer().getPluginManager().registerEvents(entityDeathListener, this);
    }

    private void startServices() {
        hudManager = new HudManager(this, ammoManager, customItemManager);
        hudManager.start();

        // Hooks are soft-depend — failures here must never disable the plugin.
        try {
            hookManager = new HookManager(this);
            hookManager.initAll();
        } catch (Throwable t) {
            getLogger().warning("[HookManager] Integration hooks failed: " + t.getMessage());
            hookManager = null;
        }
    }

    private void registerCommands() {
        GunCommand gunCmd = new GunCommand(this, gunManager, customItemManager);
        var cmd = getCommand("gun");
        if (cmd != null) {
            cmd.setExecutor(gunCmd);
            cmd.setTabCompleter(gunCmd);
        }
    }

    private void scheduleCleanup() {
        // Prune stale fire-rate cooldown entries every 2 minutes to prevent memory leaks.
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            long cutoff = System.currentTimeMillis() - 60_000;
            ammoManager.pruneStale(cutoff);
        }, 20L * 120, 20L * 120);
    }

    /**
     * Checks the {@code config-version} key in config.yml and warns the admin
     * if their config is older than the current plugin version.
     * This prevents silent misconfiguration after plugin updates.
     */
    private void checkConfigVersion() {
        // ✅ FIX: Config versioning — admins are warned when config.yml is outdated.
        // The expected version is 2 (introduced in 2.0.4).
        final int EXPECTED_CONFIG_VERSION = 2;
        int configVersion = getConfig().getInt("config-version", 0);
        if (configVersion < EXPECTED_CONFIG_VERSION) {
            getLogger().warning("══════════════════════════════════════════════");
            getLogger().warning("  [CombatGunSSS] Your config.yml is OUTDATED!");
            getLogger().warning("  Current config-version: " + configVersion);
            getLogger().warning("  Expected config-version: " + EXPECTED_CONFIG_VERSION);
            getLogger().warning("  Some new features may use default values.");
            getLogger().warning("  Recommended: back up and delete config.yml,");
            getLogger().warning("  then restart to regenerate a fresh config.");
            getLogger().warning("══════════════════════════════════════════════");
        }
    }

    private void printStartupBanner() {
        getLogger().info("════════════════════════════════");
        getLogger().info("  CombatGunSSS v" + getDescription().getVersion() + " enabled!");
        getLogger().info("  Weapons     : " + gunManager.getGunCount() + " loaded");
        getLogger().info("  Ammo types  : " + customItemManager.getAllAmmoTypes().size());
        getLogger().info("  Attachments : " + attachmentManager.getAllAttachments().size() + " loaded");
        getLogger().info("  Throwables  : " + throwableManager.getAll().size() + " loaded");
        getLogger().info("  Lang        : " + getConfig().getString("combatgun.language", "en"));
        getLogger().info("  Stats DB    : " + (statsManager != null ? "ON (SQLite)" : "DISABLED"));
        getLogger().info("  HUD         : " + (getConfig().getBoolean("combatgun.hud.enabled", true) ? "ON" : "OFF"));
        getLogger().info("  Bleeding    : " + (getConfig().getBoolean("combatgun.bleeding.enabled", false) ? "ON" : "OFF"));
        getLogger().info("  Shop        : " + (hookManager != null && hookManager.getVaultHook().isEnabled() ? "ON (Vault)" : "OFF"));
        getLogger().info("  WGuard      : " + (hookManager != null && hookManager.getWorldGuardHook().isAvailable() ? "ON" : "OFF"));
        getLogger().info("════════════════════════════════");
    }

    @Override
    public void onDisable() {
        try { if (hudManager      != null) hudManager.stop();           } catch (Throwable ignored) {}
        try { if (bleedingManager != null) bleedingManager.cancelAll(); } catch (Throwable ignored) {}
        try { if (reloadManager   != null) reloadManager.cancelAll();   } catch (Throwable ignored) {}
        try { if (statsManager    != null) statsManager.shutdown();     } catch (Throwable ignored) {}
        getLogger().info("CombatGunSSS disabled.");
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public GunManager          getGunManager()          { return gunManager; }
    public AmmoManager         getAmmoManager()          { return ammoManager; }
    public ReloadManager       getReloadManager()        { return reloadManager; }
    public GunItem             getGunItemHelper()        { return gunItemHelper; }
    public CustomItemManager   getCustomItemManager()    { return customItemManager; }
    public CraftingManager     getCraftingManager()      { return craftingManager; }
    public HudManager          getHudManager()           { return hudManager; }
    public LangManager         getLangManager()          { return langManager; }
    public BleedingManager     getBleedingManager()      { return bleedingManager; }
    public AmmoPouchManager    getAmmoPouchManager()     { return ammoPouchManager; }
    public AttachmentManager   getAttachmentManager()    { return attachmentManager; }
    public ThrowableManager    getThrowableManager()     { return throwableManager; }
    public StatsManager        getStatsManager()         { return statsManager; }
    public HookManager         getHookManager()          { return hookManager; }
    public EntityDeathListener getEntityDeathListener()  { return entityDeathListener; }
    public GunListener         getGunListener()          { return gunListener; }
}
