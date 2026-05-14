package dev.duong2012g.combatgun.hook;

import dev.duong2012g.combatgun.CombatGunSSSPlugin;
import dev.duong2012g.combatgun.data.GunData;
import java.util.HashMap;
import java.util.Map;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Vault economy integration for CombatGunSSS.
 * <p>
 * Enabled only when Vault is present AND a valid economy provider is registered.
 * All methods are safe to call even when Vault is absent — they return sensible
 * defaults ({@code false} / {@code 0.0}) so callers never need to null-check.
 *
 * <h3>Config structure ({@code config.yml})</h3>
 * <pre>
 * combatgun:
 *   shop:
 *     enabled: true
 *     currency_symbol: "$"   # displayed in messages only
 *     guns:
 *       ak47:   500.0
 *       awm:   2500.0
 *       knife:  150.0
 * </pre>
 *
 * <h3>Usage — {@code /gun buy <id>}</h3>
 * Players run {@code /gun buy ak47}. The command handler calls
 * {@link #buyGun(Player, GunData)} and lets this class handle the charge,
 * message, and item delivery.
 */
public class VaultHook {

    private final CombatGunSSSPlugin plugin;
    private Economy economy = null;

    // ✅ FIX: Cache gun prices after first read so we don't re-read config on
    // every /gun buy call. Invalidated when /gun reload is executed.
    private final Map<String, Double> priceCache = new HashMap<>();

    public VaultHook(CombatGunSSSPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Attempts to connect to the registered Vault economy provider.
     *
     * @return {@code true} if an economy was found and hooked successfully.
     */
    public boolean setup() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp =
            plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    /** @return Whether Vault and an economy provider are available. */
    public boolean isEnabled() {
        return economy != null
            && plugin.getConfig().getBoolean("combatgun.shop.enabled", false);
    }

    /**
     * Returns the configured price for a gun, or {@code -1.0} if no price is set.
     * A price of {@code -1.0} means the gun is not for sale.
     * Results are cached in memory after the first lookup.
     */
    public double getPrice(String gunId) {
        if (!isEnabled()) return -1.0;
        String key = gunId.toLowerCase();
        return priceCache.computeIfAbsent(key,
            k -> plugin.getConfig().getDouble("combatgun.shop.guns." + k, -1.0));
    }

    /**
     * Clears the internal price cache. Call this after {@code /gun reload} so
     * updated prices from {@code config.yml} are picked up on the next query.
     */
    public void invalidateCache() {
        priceCache.clear();
    }

    /**
     * Returns the currency symbol string from config (default {@code "$"}).
     * Used only in chat messages — not passed to Vault.
     */
    public String getSymbol() {
        return plugin.getConfig().getString("combatgun.shop.currency_symbol", "$");
    }

    /**
     * Attempts to charge the player and give them the gun.
     *
     * @param player The buyer.
     * @param gun    The gun to purchase.
     * @return {@code true} if the transaction succeeded and the item was given.
     */
    public boolean buyGun(Player player, GunData gun) {
        if (!isEnabled()) {
            player.sendMessage("§cShop is not available on this server.");
            return false;
        }

        double price = getPrice(gun.getId());
        if (price < 0) {
            player.sendMessage("§c" + gun.getName() + " is not available for purchase.");
            return false;
        }

        if (!economy.has(player, price)) {
            double balance = economy.getBalance(player);
            player.sendMessage(String.format(
                "§cInsufficient funds. §7Need §f%s%.2f §7, you have §f%s%.2f§7.",
                getSymbol(), price, getSymbol(), balance));
            return false;
        }

        EconomyResponse response = economy.withdrawPlayer(player, price);
        if (!response.transactionSuccess()) {
            player.sendMessage("§cTransaction failed: " + response.errorMessage);
            return false;
        }

        // Give the gun item
        var gunItem = plugin.getGunItemHelper().create(gun);
        if (gunItem == null) {
            // Refund — item creation failed
            economy.depositPlayer(player, price);
            player.sendMessage("§cFailed to create gun item. Your money has been refunded.");
            return false;
        }

        player.getInventory().addItem(gunItem);
        player.sendMessage(String.format(
            "§a✔ Purchased §f%s §afor §f%s%.2f§a! §7(Balance: %s%.2f)",
            gun.getName(), getSymbol(), price,
            getSymbol(), economy.getBalance(player)));
        return true;
    }

    /**
     * Returns the player's current balance, or {@code 0.0} if Vault is unavailable.
     */
    public double getBalance(Player player) {
        return isEnabled() ? economy.getBalance(player) : 0.0;
    }
}
