package dev.duong2012g.combatgun.util;

import dev.duong2012g.combatgun.data.GunData;

/**
 * Stateless utility class that centralises all damage-pipeline calculations
 * for CombatGunSSS ranged weapons.
 *
 * <p>Extracting these helpers from {@code GunListener} keeps the listener class
 * focused on event routing while making the math independently testable and
 * reusable by third-party add-ons that depend on CombatGunSSS.</p>
 *
 * <h3>Damage pipeline (in order)</h3>
 * <ol>
 *   <li>{@link #baseDamage(GunData, double)} — gun damage × projectile multiplier × event multiplier</li>
 *   <li>{@link #falloffMultiplier(GunData, double)} — range-based damage reduction</li>
 *   <li>{@link #headshotMultiplier(GunData, boolean)} — headshot bonus</li>
 *   <li>Final = base × falloff × headshot</li>
 * </ol>
 */
public final class DamageCalculator {

    private DamageCalculator() { /* utility class */ }

    // ── Pipeline steps ────────────────────────────────────────────────────────

    /**
     * Computes base damage before range falloff.
     *
     * @param gun             The weapon data.
     * @param eventMultiplier The multiplier from {@code GunShootEvent.getDamageMultiplier()}.
     * @return Base damage value (always positive).
     */
    public static double baseDamage(GunData gun, double eventMultiplier) {
        return gun.getDamage() * gun.getProjectileDamageMultiplier() * eventMultiplier;
    }

    /**
     * Returns the damage multiplier due to distance falloff (0.0–1.0 range).
     * <ul>
     *   <li>Returns {@code 1.0} when {@code dist} is within {@code damageFalloffStart}.</li>
     *   <li>Linearly interpolates to {@code minDamageMultiplier} as dist approaches {@code range}.</li>
     *   <li>Clamps at {@code minDamageMultiplier} beyond max range.</li>
     * </ul>
     *
     * @param gun  The weapon data.
     * @param dist Distance the projectile has travelled (blocks).
     * @return Damage multiplier in {@code [minDamageMultiplier, 1.0]}.
     */
    public static double falloffMultiplier(GunData gun, double dist) {
        if (dist <= gun.getDamageFalloffStart()) return 1.0;
        double window   = Math.max(0.5, gun.getRange() - gun.getDamageFalloffStart());
        double progress = Math.min(1.0, (dist - gun.getDamageFalloffStart()) / window);
        return 1.0 - ((1.0 - gun.getMinDamageMultiplier()) * progress);
    }

    /**
     * Returns the headshot multiplier: {@code gun.getHeadshotMultiplier()} if {@code headshot}
     * is true, otherwise {@code 1.0}.
     */
    public static double headshotMultiplier(GunData gun, boolean headshot) {
        return headshot ? gun.getHeadshotMultiplier() : 1.0;
    }

    /**
     * Computes the full final damage value after all three pipeline steps.
     *
     * @param gun             The weapon data.
     * @param distanceTravelled Distance the projectile has travelled (blocks).
     * @param headshot        Whether the hit was a headshot.
     * @param eventMultiplier Damage multiplier from {@code GunShootEvent}.
     * @return Final damage to deal (always positive).
     */
    public static double finalDamage(GunData gun,
                                     double  distanceTravelled,
                                     boolean headshot,
                                     double  eventMultiplier) {
        double base    = baseDamage(gun, eventMultiplier);
        double falloff = falloffMultiplier(gun, distanceTravelled);
        double hs      = headshotMultiplier(gun, headshot);
        return base * falloff * hs;
    }

    // ── Headshot detection ────────────────────────────────────────────────────

    /**
     * Returns {@code true} when the ray-trace hit position is in the "head zone"
     * of the entity — the top {@code HEAD_ZONE_FRACTION} of its hitbox.
     *
     * <p>Crouching players receive a reduced hitbox height correction
     * ({@code ×CROUCH_FACTOR}) to match the actual bounding-box shrinkage.</p>
     *
     * @param hitY    Y-coordinate of the ray-trace hit position.
     * @param baseY   Y-coordinate of the entity's feet (entity.getLocation().getY()).
     * @param height  Normal stand-up hitbox height (entity.getHeight()).
     * @param crouching Whether the entity is currently crouching / sneaking.
     * @return {@code true} if the hit is in the head zone.
     */
    public static boolean isHeadshot(double hitY,
                                     double baseY,
                                     double height,
                                     boolean crouching) {
        double effectiveHeight = crouching ? height * CROUCH_FACTOR : height;
        double headThreshold   = baseY + effectiveHeight * HEAD_ZONE_START;
        return hitY >= headThreshold;
    }

    // ── Spread ────────────────────────────────────────────────────────────────

    /**
     * Computes the effective spread value for a single shot, taking ADS state and
     * movement into account.
     *
     * @param gun             The weapon data.
     * @param scopeSpread     Hard-coded scope spread value (used when scoped).
     * @param scoped          Whether the player is in full-scope mode.
     * @param ads             Whether ADS mode is active.
     * @param movementSpreadMult Movement spread multiplier (1.0 if stationary, gun.getMovementSpreadMultiplier() otherwise).
     * @return Effective spread to pass into {@code applySpread()}.
     */
    public static double effectiveSpread(GunData gun,
                                         double  scopeSpread,
                                         boolean scoped,
                                         boolean ads,
                                         double  movementSpreadMult) {
        if (scoped) {
            return scopeSpread;
        }
        if (ads && gun.isAdsEnabled()) {
            return gun.getRecoil().getSpread() * gun.getAdsSpreadMultiplier() * movementSpreadMult;
        }
        return gun.getRecoil().getSpread() * movementSpreadMult;
    }

    // ── Pellet spread ─────────────────────────────────────────────────────────

    /**
     * Returns the spread value for a single shotgun pellet, which is widened
     * compared to single-projectile weapons.
     *
     * @param baseSpread   The effective spread from {@link #effectiveSpread}.
     * @param multiPellet  Whether the gun fires multiple projectiles per shot.
     * @return Per-pellet spread value.
     */
    public static double pelletSpread(double baseSpread, boolean multiPellet) {
        return multiPellet ? baseSpread * SHOTGUN_SPREAD_FACTOR : baseSpread;
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    /** Top fraction of hitbox considered the "head zone". */
    public static final double HEAD_ZONE_START     = 0.80;

    /** Crouching reduces effective hitbox height by this factor. */
    public static final double CROUCH_FACTOR       = 0.85;

    /** Pellet spread is widened by this factor for multi-projectile weapons. */
    public static final double SHOTGUN_SPREAD_FACTOR = 1.45;
}
