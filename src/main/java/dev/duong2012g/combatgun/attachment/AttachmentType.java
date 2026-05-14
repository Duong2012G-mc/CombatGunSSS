package dev.duong2012g.combatgun.attachment;

/**
 * Defines every attachment slot available in CombatGunSSS.
 * <p>
 * Each gun can hold at most one attachment per type. Attaching a second
 * attachment of the same type replaces the first.
 *
 * <table>
 *   <tr><th>Type</th><th>Effect</th></tr>
 *   <tr><td>SILENCER</td><td>Reduces gunshot sound radius; slight damage reduction</td></tr>
 *   <tr><td>SCOPE</td><td>Enables sniper-level scope spread; marks gun as scopeable</td></tr>
 *   <tr><td>EXTENDED_MAG</td><td>Increases magazine size</td></tr>
 *   <tr><td>GRIP</td><td>Reduces recoil pitch and spread</td></tr>
 * </table>
 */
public enum AttachmentType {

    /** Suppresses gunshot sound (shorter play radius). Slight damage penalty. */
    SILENCER,

    /** Enables precision scope mode (very low spread when scoped). */
    SCOPE,

    /** Increases magazine size by a flat amount. */
    EXTENDED_MAG,

    /** Reduces pitch recoil and bullet spread. */
    GRIP
}
