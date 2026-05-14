package dev.duong2012g.combatgun.attachment;

/**
 * Immutable configuration record for one attachment definition.
 * <p>
 * Attachment definitions live in {@code config.yml} under
 * {@code combatgun.attachments.<id>}:
 * <pre>
 * combatgun:
 *   attachments:
 *     silencer:
 *       type: SILENCER
 *       display_name: "Silencer"
 *       sound_radius_multiplier: 0.3   # 70% quieter
 *       damage_multiplier: 0.9         # 10% less damage
 *       custom_model_data: 7001
 *
 *     extended_mag_ar:
 *       type: EXTENDED_MAG
 *       display_name: "Extended AR Mag"
 *       magazine_bonus: 15
 *       custom_model_data: 7002
 *
 *     tactical_grip:
 *       type: GRIP
 *       display_name: "Tactical Grip"
 *       recoil_pitch_multiplier: 0.6
 *       spread_multiplier: 0.75
 *       custom_model_data: 7003
 *
 *     acog_scope:
 *       type: SCOPE
 *       display_name: "ACOG Scope"
 *       custom_model_data: 7004
 * </pre>
 */
public record AttachmentData(
    /** Internal ID, e.g. {@code "silencer"} or {@code "extended_mag_ar"}. */
    String id,
    /** Attachment category determining which stat modifiers apply. */
    AttachmentType type,
    /** Display name shown in item lore. */
    String displayName,
    /** Resource-pack model data integer (0 = no CMD). */
    int customModelData,

    // ── Type-specific modifiers ────────────────────────────────────────────
    /** SILENCER — multiplier on sound play radius (default 1.0 = no change). */
    double soundRadiusMultiplier,
    /** SILENCER — damage multiplier (default 1.0 = no change). */
    double damageMultiplier,
    /** EXTENDED_MAG — extra rounds added to magazine (default 0). */
    int magazineBonus,
    /** GRIP — multiplier on pitch recoil per shot (default 1.0 = no change). */
    double recoilPitchMultiplier,
    /** GRIP — multiplier on bullet spread (default 1.0 = no change). */
    double spreadMultiplier
) {
    /** Returns a sensible default instance for unknown / unconfigured attachments. */
    public static AttachmentData defaults(String id, AttachmentType type, String displayName, int cmd) {
        return new AttachmentData(id, type, displayName, cmd,
            1.0, 1.0, 0, 1.0, 1.0);
    }
}
