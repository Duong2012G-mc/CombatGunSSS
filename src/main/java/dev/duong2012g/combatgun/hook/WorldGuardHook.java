package dev.duong2012g.combatgun.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import dev.duong2012g.combatgun.CombatGunSSSPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * WorldGuard region protection hook for CombatGunSSS.
 * <p>
 * When WorldGuard is present, shooting is blocked inside any region that has
 * the custom flag {@code gun-shooting} set to {@code deny}, OR inside any region
 * where WorldGuard's built-in {@code PVP} flag is denied (for player targets).
 * <p>
 * A custom flag {@code gun-shooting} is registered during
 * {@link #registerFlags()} which must be called from {@code onLoad()} —
 * <strong>before</strong> WorldGuard reads its region data.
 * If flag registration is too late, the flag silently falls back to the PVP check only.
 *
 * <h3>Setting the flag in-game</h3>
 * <pre>
 *   /rg flag &lt;region&gt; gun-shooting deny    # block all gun use
 *   /rg flag &lt;region&gt; gun-shooting allow   # explicitly allow
 *   /rg flag &lt;region&gt; gun-shooting -g      # remove flag (inherit parent)
 * </pre>
 */
public class WorldGuardHook {

    /** Custom WorldGuard flag: controls whether guns may be fired in a region. */
    public static StateFlag GUN_SHOOTING_FLAG;

    private final CombatGunSSSPlugin plugin;
    private boolean available = false;

    public WorldGuardHook(CombatGunSSSPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers the {@code gun-shooting} custom flag.
     * Must be called from {@link org.bukkit.plugin.java.JavaPlugin#onLoad()},
     * <em>not</em> from {@code onEnable()}.
     */
    public static void registerFlags() {
        try {
            FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
            StateFlag flag = new StateFlag("gun-shooting", true /* default allow */);
            registry.register(flag);
            GUN_SHOOTING_FLAG = flag;
        } catch (FlagConflictException e) {
            // Another plugin already registered this flag name — grab the existing one.
            var existing = WorldGuard.getInstance().getFlagRegistry().get("gun-shooting");
            if (existing instanceof StateFlag sf) {
                GUN_SHOOTING_FLAG = sf;
            }
        } catch (Exception e) {
            // WorldGuard not present or too old — flag stays null, checks fall back to PVP flag.
        }
    }

    /**
     * Connects to WorldGuard. Call from {@code onEnable()}.
     *
     * @return {@code true} if WorldGuard is present and the hook is ready.
     */
    public boolean setup() {
        if (plugin.getServer().getPluginManager().getPlugin("WorldGuard") == null) return false;
        try {
            // Accessing getInstance() will throw if WG failed to load
            WorldGuard.getInstance();
            available = true;
        } catch (Exception e) {
            available = false;
        }
        return available;
    }

    /** @return Whether WorldGuard is available and the hook is active. */
    public boolean isAvailable() { return available; }

    /**
     * Returns {@code true} if the player is <em>allowed</em> to shoot at the given location.
     * <p>
     * Checks in order:
     * <ol>
     *   <li>If WorldGuard is not available — always returns {@code true}.</li>
     *   <li>Custom {@code gun-shooting} flag in the region at the shooter's location.</li>
     *   <li>WorldGuard's built-in {@code PVP} flag (as a secondary safety net).</li>
     * </ol>
     *
     * @param player   The shooter.
     * @param location The shooter's location (typically {@code player.getLocation()}).
     * @return {@code true} if shooting is permitted.
     */
    public boolean canShoot(Player player, Location location) {
        if (!available) return true;

        try {
            RegionContainer  container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager    manager   = container.get(BukkitAdapter.adapt(location.getWorld()));
            if (manager == null) return true;

            RegionQuery query        = container.createQuery();
            com.sk89q.worldedit.util.Location wgLoc = BukkitAdapter.adapt(location);
            com.sk89q.worldguard.LocalPlayer  wgPlayer = WorldGuardPlugin.inst().wrapPlayer(player);

            // Check custom gun-shooting flag first
            if (GUN_SHOOTING_FLAG != null) {
                StateFlag.State state = query.queryState(wgLoc, wgPlayer, GUN_SHOOTING_FLAG);
                if (state == StateFlag.State.DENY)  return false;
                if (state == StateFlag.State.ALLOW) return true;
            }

            // Fall back to WorldGuard PVP flag
            StateFlag.State pvpState = query.queryState(wgLoc, wgPlayer, Flags.PVP);
            if (pvpState == StateFlag.State.DENY) return false;

        } catch (Exception e) {
            // Any unexpected error — fail open (allow) to avoid breaking gameplay
            plugin.getLogger().warning("[WorldGuardHook] Error during region check: " + e.getMessage());
        }
        return true;
    }
}
