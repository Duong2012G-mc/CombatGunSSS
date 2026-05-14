package dev.duong2012g.combatgun.throwable;

/**
 * Categories of throwable items in CombatGunSSS.
 *
 * <ul>
 *   <li>{@link #FRAG}      — Explosive grenade, damages entities in radius.</li>
 *   <li>{@link #SMOKE}     — Smoke cloud blocking vision for N seconds.</li>
 *   <li>{@link #FLASHBANG} — Blinds and deafens players caught in the burst.</li>
 * </ul>
 */
public enum ThrowableType {
    FRAG,
    SMOKE,
    FLASHBANG
}
