package dev.duong2012g.combatgun.data;

import java.util.Objects;

/**
 * Immutable data class for a single weapon (gun or melee).
 * Construct via {@link Builder} — never call the package-private constructor directly.
 */
public final class GunData {

    // ── Core identity ─────────────────────────────────────────────────────────
    private final String id;
    private final String name;
    private final String category;
    private final String ammoType;
    private final boolean melee;        // derived, not parsed separately

    // ── Combat stats ──────────────────────────────────────────────────────────
    private final int    damage;
    private final double reloadTime;
    private final int    magazineSize;
    private final double fireRate;
    private final RecoilData recoil;
    private final double headshotMultiplier;

    // ── Advanced ballistics ───────────────────────────────────────────────────
    private final double range;
    private final int    projectilesPerShot;
    private final double projectileDamageMultiplier;
    private final int    burstCount;
    private final long   burstIntervalTicks;
    private final double blockPenetration;
    private final int    entityPenetration;
    private final double damageFalloffStart;
    private final double minDamageMultiplier;
    private final double movementSpreadMultiplier;
    private final double knockbackStrength;
    private final int    maxDurability;
    private final boolean scopeable;

    // ── ADS (Aim Down Sights) ─────────────────────────────────────────────────
    private final boolean adsEnabled;
    private final double  adsSpreadMultiplier;
    private final double  adsMovementPenalty;

    // ── Presentation ─────────────────────────────────────────────────────────
    private final String rarity;
    private final String sound;
    private final int    customModelData;
    private final RecipeData recipe;

    /** Package-private — use {@link Builder}. */
    GunData(Builder b) {
        this.id                        = Objects.requireNonNull(b.id, "id");
        this.name                      = b.name != null ? b.name : b.id.toUpperCase();
        this.category                  = b.category != null ? b.category : "unknown";
        this.ammoType                  = b.ammoType != null ? b.ammoType : "none";
        // A weapon is melee if its category is "melee" OR its ammo type is absent/none.
        this.melee                     = "melee".equalsIgnoreCase(this.category)
                                         || "none".equalsIgnoreCase(this.ammoType)
                                         || this.ammoType.isBlank();
        this.damage                    = b.damage;
        this.reloadTime                = b.reloadTime;
        this.magazineSize              = b.magazineSize;
        this.fireRate                  = b.fireRate;
        this.recoil                    = b.recoil != null ? b.recoil : new RecoilData(0.5, 0.1, 0.15, 0.5);
        this.headshotMultiplier        = b.headshotMultiplier;
        this.range                     = b.range;
        this.projectilesPerShot        = Math.max(1, b.projectilesPerShot);
        this.projectileDamageMultiplier= b.projectileDamageMultiplier;
        this.burstCount                = Math.max(1, b.burstCount);
        this.burstIntervalTicks        = b.burstIntervalTicks;
        this.blockPenetration          = b.blockPenetration;
        this.entityPenetration         = b.entityPenetration;
        this.damageFalloffStart        = b.damageFalloffStart;
        this.minDamageMultiplier       = b.minDamageMultiplier;
        this.movementSpreadMultiplier  = b.movementSpreadMultiplier;
        this.knockbackStrength         = b.knockbackStrength;
        this.maxDurability             = b.maxDurability;
        this.scopeable                 = b.scopeable;
        this.adsEnabled                = b.adsEnabled;
        this.adsSpreadMultiplier       = b.adsSpreadMultiplier;
        this.adsMovementPenalty        = b.adsMovementPenalty;
        this.rarity                    = b.rarity != null ? b.rarity : "common";
        this.sound                     = b.sound != null ? b.sound : "ENTITY_FIREWORK_ROCKET_BLAST";
        this.customModelData           = b.customModelData;
        this.recipe                    = b.recipe;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String     getId()                        { return id; }
    public String     getName()                      { return name; }
    public String     getCategory()                  { return category; }
    public String     getAmmoType()                  { return ammoType; }
    /** True when this weapon is melee (category=melee OR ammo_type=none/blank). */
    public boolean    isMelee()                      { return melee; }
    public int        getDamage()                    { return damage; }
    public double     getReloadTime()                { return reloadTime; }
    public int        getMagazineSize()              { return magazineSize; }
    public double     getFireRate()                  { return fireRate; }
    public RecoilData getRecoil()                    { return recoil; }
    public double     getHeadshotMultiplier()        { return headshotMultiplier; }
    public double     getRange()                     { return range; }
    public int        getProjectilesPerShot()        { return projectilesPerShot; }
    public double     getProjectileDamageMultiplier(){ return projectileDamageMultiplier; }
    public int        getBurstCount()                { return burstCount; }
    public long       getBurstIntervalTicks()        { return burstIntervalTicks; }
    public double     getBlockPenetration()          { return blockPenetration; }
    public int        getEntityPenetration()         { return entityPenetration; }
    public double     getDamageFalloffStart()        { return damageFalloffStart; }
    public double     getMinDamageMultiplier()       { return minDamageMultiplier; }
    public double     getMovementSpreadMultiplier()  { return movementSpreadMultiplier; }
    public double     getKnockbackStrength()         { return knockbackStrength; }
    public int        getMaxDurability()             { return maxDurability; }
    public boolean    isScopeable()                  { return scopeable; }
    public boolean    isAdsEnabled()                 { return adsEnabled; }
    public double     getAdsSpreadMultiplier()        { return adsSpreadMultiplier; }
    public double     getAdsMovementPenalty()         { return adsMovementPenalty; }
    public String     getRarity()                    { return rarity; }
    public String     getSound()                     { return sound; }
    public int        getCustomModelData()           { return customModelData; }
    public RecipeData getRecipe()                    { return recipe; }

    /** Fire-rate cooldown in milliseconds. Returns Long.MAX_VALUE for unusable weapons. */
    public long getShotCooldownMs() {
        if (fireRate <= 0) return Long.MAX_VALUE;
        return (long) (1000.0 / fireRate);
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static final class Builder {
        // Required
        String id;
        // Optional with sensible defaults
        String     name;
        String     category               = "unknown";
        String     ammoType               = "none";
        int        damage                 = 10;
        double     reloadTime             = 2.5;
        int        magazineSize           = 30;
        double     fireRate               = 10.0;
        RecoilData recoil                 = null;
        double     headshotMultiplier     = 1.5;
        double     range                  = 60.0;
        int        projectilesPerShot     = 1;
        double     projectileDamageMultiplier = 1.0;
        int        burstCount             = 1;
        long       burstIntervalTicks     = 0;
        double     blockPenetration       = 0.0;
        int        entityPenetration      = 0;
        double     damageFalloffStart     = 30.0;
        double     minDamageMultiplier    = 0.5;
        double     movementSpreadMultiplier = 1.5;
        double     knockbackStrength      = 0.0;
        int        maxDurability          = 0;
        boolean    scopeable              = false;
        boolean    adsEnabled             = false;
        double     adsSpreadMultiplier    = 0.35;
        double     adsMovementPenalty     = 0.6;
        String     rarity                 = "common";
        String     sound                  = "ENTITY_FIREWORK_ROCKET_BLAST";
        int        customModelData        = 0;
        RecipeData recipe                 = null;

        public Builder id(String v)                          { this.id = v; return this; }
        public Builder name(String v)                        { this.name = v; return this; }
        public Builder category(String v)                    { this.category = v; return this; }
        public Builder ammoType(String v)                    { this.ammoType = v; return this; }
        public Builder damage(int v)                         { this.damage = v; return this; }
        public Builder reloadTime(double v)                  { this.reloadTime = v; return this; }
        public Builder magazineSize(int v)                   { this.magazineSize = v; return this; }
        public Builder fireRate(double v)                    { this.fireRate = v; return this; }
        public Builder recoil(RecoilData v)                  { this.recoil = v; return this; }
        public Builder headshotMultiplier(double v)          { this.headshotMultiplier = v; return this; }
        public Builder range(double v)                       { this.range = v; return this; }
        public Builder projectilesPerShot(int v)             { this.projectilesPerShot = v; return this; }
        public Builder projectileDamageMultiplier(double v)  { this.projectileDamageMultiplier = v; return this; }
        public Builder burstCount(int v)                     { this.burstCount = v; return this; }
        public Builder burstIntervalTicks(long v)            { this.burstIntervalTicks = v; return this; }
        public Builder blockPenetration(double v)            { this.blockPenetration = v; return this; }
        public Builder entityPenetration(int v)              { this.entityPenetration = v; return this; }
        public Builder damageFalloffStart(double v)          { this.damageFalloffStart = v; return this; }
        public Builder minDamageMultiplier(double v)         { this.minDamageMultiplier = v; return this; }
        public Builder movementSpreadMultiplier(double v)    { this.movementSpreadMultiplier = v; return this; }
        public Builder knockbackStrength(double v)           { this.knockbackStrength = v; return this; }
        public Builder maxDurability(int v)                  { this.maxDurability = v; return this; }
        public Builder scopeable(boolean v)                  { this.scopeable = v; return this; }
        public Builder adsEnabled(boolean v)                 { this.adsEnabled = v; return this; }
        public Builder adsSpreadMultiplier(double v)          { this.adsSpreadMultiplier = v; return this; }
        public Builder adsMovementPenalty(double v)           { this.adsMovementPenalty = v; return this; }
        public Builder rarity(String v)                      { this.rarity = v; return this; }
        public Builder sound(String v)                       { this.sound = v; return this; }
        public Builder customModelData(int v)                { this.customModelData = v; return this; }
        public Builder recipe(RecipeData v)                  { this.recipe = v; return this; }

        public GunData build() {
            Objects.requireNonNull(id, "GunData id must not be null");
            return new GunData(this);
        }
    }
}
