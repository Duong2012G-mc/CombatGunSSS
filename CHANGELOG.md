# Changelog

All notable changes to CombatGunSSS will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.6] - 2026-05-14

### Fixed

#### 🔴 `GunListener.isFriendlyFire()` — Scoreboard team detection using wrong scoreboard (Critical)
- **Root cause:** `shooter.getScoreboard()` and `targetPlayer.getScoreboard()` return each player's *personal* scoreboard view, which may differ between players when a scoreboard plugin assigns individual views. Team membership is defined on the **main** scoreboard, so using a personal scoreboard could return `null` even when both players are on the same team — or vice versa, allowing players to bypass friendly-fire restrictions.
- **Fix:** Replaced both `player.getScoreboard()` calls with a single `Bukkit.getScoreboardManager().getMainScoreboard()` lookup. Team membership is now always evaluated against the authoritative server scoreboard.

#### 🔴 `GunListener` — Memory leak in `recentDamage` map (Critical)
- **Root cause:** `recentDamage` entries were only evicted by `pruneRecentDamage()`, which runs *after each shot*. Players who disconnected (crash, `/kick`, network drop) or died without triggering a subsequent shot by another player left stale `DamageRecord` entries in the map indefinitely, growing unbounded on long-running servers.
- **Fix:** Added `recentDamage.remove(p.getUniqueId())` to both `onQuit()` and `onDeath()` event handlers, ensuring entries are cleaned up immediately when a player leaves or dies — in addition to the existing TTL-based pruning.

#### 🟡 `GunListener` — Shared `static Random` causes thread contention and predictable spread (Medium)
- **Root cause:** `private static final Random RANDOM = new Random()` is shared across all usages of `GunListener`. Java's `Random` is thread-safe via internal synchronization, which causes lock contention when multiple players fire simultaneously, and its LCG algorithm produces correlated sequences when called rapidly — making bullet spread slightly predictable.
- **Fix:** Replaced the shared field with a private helper `rng()` that returns `ThreadLocalRandom.current()`. Each thread gets its own independent, higher-quality RNG with zero contention. All three `RANDOM.nextDouble()` call sites updated to `rng().nextDouble()`.

#### 🟡 `StatsManager` — Unsynchronized async DB access on shared connection (Medium)
- **Root cause:** `flushBuffer()` is `synchronized` on the `StatsManager` instance, but `incrementGunKill()` and `getGunKills()` — both called from async tasks — are not. All three methods share the same JDBC `Connection`. Concurrent execution of `flushBuffer()` and `incrementGunKill()` could interleave SQL transactions, causing `SQLITE_BUSY` errors, corrupted auto-commit state, or inconsistent read results in `getGunKills()`.
- **Fix:** Marked both `incrementGunKill()` and `getGunKills()` as `synchronized`. All three DB-touching methods now synchronize on the same monitor (the `StatsManager` instance), serializing access to the shared `connection` without requiring a connection pool.

---

## [2.0.5] - 2026-04-26

### Added

#### `AttachmentApplyEvent` and `AttachmentRemoveEvent` — Developer API
- Two new cancellable events fire when a player attaches or removes an attachment from a gun.
- **`AttachmentApplyEvent`** — fired before `fitAttachment()` writes the PDC key. Provides `getPlayer()`, `getGunData()`, `getIncoming()` (attachment being fitted), and `getReplacedAttachment()` (previously occupied slot, or `null`). Cancellable — cancel to block the attachment action.
- **`AttachmentRemoveEvent`** — fired before `removeAttachment()` clears the PDC key. Provides `getPlayer()`, `getGunData()`, `getSlot()`, and `getRemoved()` (the attachment being stripped). Cancellable — cancel to block the removal.
- `AttachmentManager.fitAttachment(Player, ItemStack, GunData, String)` — new overload that fires the apply event. The no-player overload is retained for internal use.
- `AttachmentManager.removeAttachment(Player, ItemStack, GunData, AttachmentType)` — new overload that fires the remove event.
- Example use-cases: locking legendary weapons from modification, auto-returning detached attachments to the player's inventory, logging equipment changes, restricting attachments in specific regions.

#### `DamageCalculator` — Centralised Damage Math Utility
- New `util/DamageCalculator.java` extracts all damage-pipeline calculations from `GunListener`, making them independently testable and accessible to add-on plugins.
- **Static methods**:
  - `baseDamage(GunData, double eventMult)` — gun damage × projectile multiplier × event multiplier.
  - `falloffMultiplier(GunData, double dist)` — linear range-based damage reduction from `damageFalloffStart` to min floor.
  - `headshotMultiplier(GunData, boolean headshot)` — returns `headshotMultiplier` or `1.0`.
  - `finalDamage(GunData, double dist, boolean headshot, double eventMult)` — full pipeline in one call.
  - `isHeadshot(double hitY, double baseY, double height, boolean crouching)` — single source of truth for headshot detection, accounting for crouch state.
  - `effectiveSpread(GunData, double scopeSpread, boolean scoped, boolean ads, double moveMult)` — spread value for all fire modes.
  - `pelletSpread(double baseSpread, boolean multiPellet)` — per-pellet spread for shotguns.
- **Named constants**: `HEAD_ZONE_START` (0.80), `CROUCH_FACTOR` (0.85), `SHOTGUN_SPREAD_FACTOR` (1.45) — previously magic numbers scattered across `GunListener`.
- `GunListener` now delegates all damage math and spread calculations to `DamageCalculator`. The private `computeFalloffMultiplier()` method has been removed (logic moved to `DamageCalculator.falloffMultiplier()`).

#### `StatsManager` — Per-Kill Detail Log (`kills_by_gun_detail` table)
- New SQLite table `kills_by_gun_detail` added via schema migration v3. Stores one row per kill with `uuid`, `gun_id`, `headshot` flag, and Unix `timestamp` (ms).
- Enables future features: per-weapon leaderboards, session-based kill analytics, and season resets that preserve historical totals in the detail log while zeroing the aggregate counters.
- Two covering indexes (`idx_kbgd_uuid`, `idx_kbgd_gun`) ensure per-player and per-weapon queries stay fast as the table grows.
- Schema migration runs automatically on first startup of 2.0.5 — no manual SQL required.

#### `GunCommand` — Season Reset and Per-Player Stats Reset
- **`/gun statsreset <player>`** — resets kills, deaths, headshots, gun kills, and detail records for a single online player. Wipes the in-memory buffer and schedules async DB deletes across all three stat tables.
- **`/gun seasonreset confirm`** — resets ALL players' stats (requires the literal `confirm` argument as a safeguard against accidental use). Clears the in-memory buffer and truncates `player_stats`, `gun_kills`, and `kills_by_gun_detail` in a single transaction. Prints a gold confirmation message on success.
- Both commands require `combatgun.admin` permission.
- Both commands fail gracefully with a clear error if `StatsManager` is disabled (SQLite unavailable).

### Improved

#### `GunListener` — God Class Reduction
- `performShot()` spread logic (previously a three-branch `if`/`else if`/`else` block) replaced with a single `DamageCalculator.effectiveSpread()` call.
- `applyEntityDamage()` damage calculation (previously inline) replaced with `DamageCalculator.finalDamage()` and `DamageCalculator.baseDamage()`.
- `isHeadshot()` private method now delegates entirely to `DamageCalculator.isHeadshot()` — single source of truth for headshot geometry.
- Removed `computeFalloffMultiplier()` private method (~7 lines) — logic now lives in `DamageCalculator`.

#### `StatsManager` — Schema Version Bumped to 3
- `SCHEMA_VERSION` constant updated from `2` to `3`.
- `recordKill()` now passes the `headshot` flag to `incrementGunKill()` so both the aggregate `gun_kills` table and the new `kills_by_gun_detail` table are updated in one async task.
- `resetStats(UUID)` now also deletes from `kills_by_gun_detail` in addition to `player_stats` and `gun_kills`.
- `resetAllStats()` truncates all three stat tables in a single transaction.

---

## [2.0.4] - 2026-04-24

### Fixed

#### 🔴 `StatsManager` — Thread-safety data race on the write buffer (Critical)
- **Root cause:** `buffer` was a plain `HashMap` modified by main-thread event handlers (`recordKill`, `recordDeath`) while the async flush scheduler simultaneously iterated and cleared it. This is an undefined-behaviour data race that can cause `ConcurrentModificationException` or silently corrupt stat counts.
- **Fix:** Replaced `HashMap` with `ConcurrentHashMap`. All `computeIfAbsent` calls are inherently atomic on `ConcurrentHashMap`, eliminating the race. The redundant nested `synchronized (buffer)` block inside `flushBuffer()` was also removed since the outer `synchronized` on the method and the concurrent map together provide correct guarantees.

#### 🟡 `GunManager` — Invalid gun configs loaded silently (Medium)
- **Root cause:** If a gun YAML had `damage: 0`, `fire_rate: 0`, `magazine_size: 0`, or a blank `name`, the gun was still loaded and registered. This caused division-by-zero style issues at shoot time (e.g., `shotCooldownMs = 1000 / fireRate` → `Infinity`) and guns that could never run out of ammo.
- **Fix:** Added `validateGunConfig()` called before `parseGun()`. It checks all critical numeric fields (`damage > 0`, `fire_rate > 0`, `magazine_size > 0`, `reload_time > 0`, `range > 0`, `burst_count >= 1`) and logs a clear per-field error list. Invalid guns are skipped entirely with a warning instead of loading in a broken state.

#### 🟡 `StatsManager` — Leaderboard query does a full table scan (Medium)
- **Root cause:** `SELECT … ORDER BY kills DESC LIMIT ?` had no index on the `kills` column. As the `player_stats` table grows, this query became progressively slower — on a server with thousands of entries, it could block the async thread for hundreds of milliseconds.
- **Fix:** Added `CREATE INDEX IF NOT EXISTS idx_player_stats_kills ON player_stats(kills DESC)` via a schema migration. The migration runs automatically on first startup of 2.0.4 and takes less than a millisecond on existing databases.

#### 🟡 `StatsManager` — No schema migration system (Medium)
- **Root cause:** Any future change to `player_stats` or `gun_kills` tables would require manual SQL on the server's SQLite file. Admins who missed this step would get crashes or silently wrong data.
- **Fix:** Introduced a `schema_version` table and a `runMigrations()` method. Each migration is guarded by a version check (`if (currentVersion < N)`). New migrations can be added as additional `if` blocks — existing databases are upgraded incrementally on startup. Current schema is version 2.

### Improved

#### `VaultHook` — Shop prices now cached in memory
- `getPrice(gunId)` previously re-read `config.yml` on every `/gun buy` call. On servers with many price entries, YAML key traversal on every purchase was wasteful.
- Prices are now cached in a `HashMap` on first access using `computeIfAbsent`. The cache is invalidated by `invalidateCache()`, which is called automatically after `/gun reload` so updated prices are picked up immediately.

#### `CombatGunSSSPlugin` — Config version detection
- Added `config-version` key to the default `config.yml` (currently version `2`).
- On startup, `checkConfigVersion()` compares the value in the server's config against the expected version and logs a prominent warning if they do not match. This tells admins when their config is outdated after a plugin update rather than silently using wrong defaults.
- No hard failure — the plugin loads normally and uses defaults for any missing keys.

---

## [2.0.3] - 2026-04-18

### Fixed

#### 🔴 `AmmoPouchManager.unpackPouch()` — Pouch lost when inventory is full (Critical)
- **Root cause:** When partially unpacking (only a few slots left in inventory), the code always called `pouch.setAmount(0)` regardless of how many bullets were actually transferred. The entire pouch was destroyed even if bullets remained inside.
- **Fix:** If `remaining > 0` (unpack not completed), update the PDC `pouch_quantity` to the remaining amount and refresh the lore instead of destroying the pouch. Only call `setAmount(0)` when all bullets have been successfully transferred.

#### 🔴 `ReloadManager` — Reload task running every 1 tick causing high CPU usage (Critical)
- **Root cause:** `runTaskTimer(plugin, 0L, 1L)` scheduled 20 times per second for every player reloading. With 10 players reloading simultaneously, this resulted in 200 task calls per second just for the progress bar.
- **Fix:** Changed to `runTaskTimer(plugin, 0L, 2L)` and adjusted `totalTicks = reloadTime * 10.0` so reload duration remains unchanged. This reduces reload system CPU load by approximately 50%.

#### 🔴 `GunListener.isHeadshot()` — Inaccurate headshots while player is crouching (Critical)
- **Root cause:** Used fixed `entity.getHeight() * 0.8` without accounting for crouching players (hitbox shrinks to ~1.5 blocks instead of 1.8).
- **Fix:** Detect `player.isSneaking()` and multiply `height *= 0.85` before calculating headY threshold. Headshot detection is now accurate for both standing and crouching states.

#### 🔴 `CraftingManager.craftFromStation()` — No rollback when crafting fails (Critical)
- **Root cause:** `consumeIngredients()` ran before `createResultItem()`. If `createResultItem()` returned `null` (invalid gun ID, config error, etc.), ingredients were consumed but no item was given → permanent item loss.
- **Fix:** Snapshot all input slots before consuming. If `createResultItem()` returns `null`, restore ingredients from the snapshot.

#### 🟡 `GunListener.applyRecoil()` — Potential NPE when AntiCheatHook is disabled (Medium)
- **Root cause:** `if (hm2 != null) hm2.getAntiCheatHook().exempt(player)` — checked `hm2` but not `getAntiCheatHook()`. If the hook was disabled, `getAntiCheatHook()` could return an uninitialized instance.
- **Fix:** Added full guard: `hm2 != null && hm2.getAntiCheatHook() != null && hm2.getAntiCheatHook().isActive()` before calling `exempt()`.

#### 🟡 `BleedingManager` — Incorrect damage during server lag (Medium)
- **Root cause:** Task used `elapsed += 20` to count ticks, but when the server lagged (>50ms per tick), actual damage was lower than the configured `damage_per_second`.
- **Fix:** Switched to `System.currentTimeMillis()` for real-time tracking. `lastDamageTime` is now updated only when damage is actually applied, ensuring exactly 1 second between damage ticks regardless of server lag.

#### 🟡 `CraftingManager.consumeIngredients()` — Incorrect null-check order (Medium)
- **Root cause:** `identifyIngredient(item)` was called before checking `item == null`, causing potential NPE with empty inventory slots in some edge cases.
- **Fix:** Moved `if (item == null || item.getType().isAir()) continue;` before calling `identifyIngredient()`.

#### 🟡 `GunListener.onItemHeldChange()` — Race condition when switching slots rapidly (Medium)
- **Root cause:** Task with 0-tick delay read `getItemInMainHand()` — if the player switched slots again before the task ran, it would process the wrong slot's item.
- **Fix:** Store `newSlot = event.getNewSlot()` before scheduling the task. Inside the task, check `player.getInventory().getHeldItemSlot() != newSlot` and abort if the player has switched slots again.

---

## [2.0.2] - 2026-04-14

### Fixed

#### `NullPointerException: HudManager.sendNow()` when shooting any gun
- **Root cause**: `GunListener` cached `plugin.getHudManager()` into a `final HudManager hudManager` field inside its constructor. However, `GunListener` is instantiated in `registerListeners()` which runs **before** `startServices()` where `HudManager` is created. The field was always `null` at the time of assignment.
- **Fix**: Removed the cached `hudManager` field entirely. All two call-sites now use `plugin.getHudManager()` inline with a null-guard: `if (plugin.getHudManager() != null) plugin.getHudManager().sendNow(player)`. This makes the call lazy — it resolves the reference at the moment of use rather than at construction time, so startup order no longer matters.

---

## [2.0.1] - 2026-04-14


### Fixed

#### Plugin crash on startup (`onEnable` exception → plugin disabled)
- **Root cause**: Any uncaught exception inside `onEnable()` caused Bukkit to call `disablePlugin()` immediately, leaving the plugin in a broken state where commands responded with _"plugin is disabled"_ instead of a useful error message.
- **Fix**: Refactored `onEnable()` into isolated private methods (`initManagers()`, `registerListeners()`, `startServices()`, `registerCommands()`). Each step is now wrapped in a top-level try-catch. A detailed stack trace is printed to console before the plugin gracefully self-disables — making the actual root cause visible instead of a cryptic command error.

#### `StatsManager` (SQLite) failure no longer kills the plugin
- `statsManager.init()` is now wrapped in its own try-catch. If SQLite initialisation fails (missing driver, file permission error, corrupted DB), the plugin logs a warning, sets `statsManager = null`, and continues loading. All stats-dependent features (`/gun stats`, `/gun leaderboard`, PAPI `%combatgun_kills%` etc.) degrade gracefully with a user-friendly error message.

#### `HookManager` failure no longer kills the plugin
- `hookManager.initAll()` is wrapped in a try-catch. If any soft-depend integration throws an unexpected exception, the hook manager is set to `null` and the plugin runs without integrations rather than crashing entirely.

#### Null pointer exceptions when `hookManager` or `statsManager` are null
- `GunListener.isAllowed()` — WorldGuard check now guards with `hm != null` before calling `canShoot()`.
- `GunListener.applyRecoil()` — AntiCheat exempt call guarded with `hm2 != null`.
- `GunCommand.handleBuy()` — Vault access guarded; returns a friendly error if hook manager is unavailable.
- `GunCommand.handleStats()` / `handleLeaderboard()` — Guarded with `statsManager != null` check; returns a friendly error if stats are disabled.
- `EntityDeathListener` — Stats recording guarded with `statsManager != null`.

#### `Material.GREY_DYE` compile error
- `ThrowableManager` used the pre-1.13 material name `GREY_DYE` which no longer exists. Corrected to `GRAY_DYE` (American English naming introduced in the 1.13 flattening).

### Changed

- `onEnable()` restructured into `initManagers()`, `registerListeners()`, `startServices()`, `registerCommands()`, `scheduleCleanup()`, `printStartupBanner()` for clarity and fault isolation.
- `onDisable()` wraps each cleanup call in its own try-catch so a failure in one manager never prevents the others from shutting down cleanly.
- Startup banner now correctly reads version from `plugin.yml` at runtime via `getDescription().getVersion()` — no hardcoded version strings in Java code.

---

## [2.0.0] - 2026-04-12


### Added

#### Weapon Attachment System
- Four attachment types with stackable stat modifiers:
  - `SILENCER` — reduces sound radius (config: `sound_radius_multiplier`) with optional damage penalty.
  - `EXTENDED_MAG` — adds flat magazine capacity (config: `magazine_bonus`).
  - `GRIP` — reduces pitch recoil and bullet spread (config: `recoil_pitch_multiplier`, `spread_multiplier`).
  - `SCOPE` — enables precision scope spread at runtime, even on guns where `scopeable: false`.
- Each gun holds at most one attachment per slot. Attaching a second of the same type replaces the first.
- Attachments persist on the gun item via PersistentDataContainer keys (`combatgun:attach_silencer`, etc.).
- **Commands**: `/gun attach <id>`, `/gun detach <TYPE>`, `/gun attachments` (inspect held gun).
- **Config**: Defined under `combatgun.attachments.<id>` in `config.yml`. Five built-in definitions included (silencer, extended_mag_ar, extended_mag_smg, tactical_grip, acog_scope).
- `AttachmentManager` exposes stat modifier helpers used at shoot time: `soundMultiplier()`, `damageMultiplier()`, `magBonus()`, `spreadMultiplier()`, `recoilPitchMultiplier()`, `hasScopeAttachment()`.

#### Throwable Items (Grenades)
- Three throwable types, all thrown as Snowball projectiles and detonated on `ProjectileHitEvent` or after fuse:
  - `FRAG` — Creates a visual explosion and deals falloff damage to nearby entities.
  - `SMOKE` — Spawns a timed smoke particle cloud (campfire smoke particles for 10 seconds by default).
  - `FLASHBANG` — Applies Blindness and Slowness (simulated deafness) to nearby players, scaled by distance.
- **Command**: `/gun givethrowable <id> [player]`.
- **Config**: Defined under `combatgun.throwables.<id>` with full control over fuse, radius, damage, duration, and effect ticks. Three built-in throwables included.
- `ThrowableListener` handles right-click throw and projectile landing. Registered as a separate listener from `GunListener`.
- Projectiles are tracked in `ThrowableManager.liveProjectiles` and auto-detonate after `fuse_ticks` if they don't land.

#### Kill Statistics & Leaderboard
- New `StatsManager` backed by SQLite (`plugins/CombatGunSSS/stats.db`). No external database required.
- Tracks per-player: kills, deaths, headshots, and per-gun kill counts.
- Write buffer flushes to DB every 5 minutes and on plugin disable — minimises disk writes while keeping data safe.
- **Commands**: `/gun stats [player]` (view stats), `/gun leaderboard` (top 10 by kills).
- **New PlaceholderAPI placeholders**: `%combatgun_kills%`, `%combatgun_deaths%`, `%combatgun_headshots%`, `%combatgun_kd%`.
- `EntityDeathListener` now calls `StatsManager.recordKill()` and `recordDeath()` on every gun kill.
- SQLite JDBC shaded into the jar via `maven-shade-plugin` with relocation to `dev.duong2012g.combatgun.libs.sqlite` — no external jar needed.

### Changed

- `CombatGunSSSPlugin` — registers `AttachmentManager`, `ThrowableManager`, `StatsManager`; registers `ThrowableListener`; `onDisable()` calls `statsManager.shutdown()`; startup log prints attachment/throwable counts and stats DB status.
- `GunCommand` — added `attach`, `detach`, `attachments`, `givethrowable`, `stats`, `leaderboard` to `SUB_COMMANDS`, switch, tab completion, and help.
- `PlaceholderHook` — added `kills`, `deaths`, `headshots`, `kd` placeholders (total 12 placeholders now).
- `EntityDeathListener` — records kill/death to `StatsManager` on every confirmed gun kill.
- `plugin.yml` — version bumped to `2.0.0`.
- `pom.xml` — version bumped to `2.0.0`; added `sqlite-jdbc 3.45.1.0` as compile dependency; added `maven-shade-plugin` to shade and relocate SQLite.
- `config.yml` — added `combatgun.attachments` and `combatgun.throwables` sections with default definitions.

---

## [1.2.0] - 2026-04-11


### Added

#### ADS — Aim Down Sights
- New per-gun ADS system. Configured in the gun's YAML under an `ads` block:
  ```yaml
  ads:
    enabled: true
    spread_multiplier: 0.35   # fraction of normal spread while ADS
    movement_penalty: 0.6     # speed fraction while ADS active (0–1)
  ```
- **Controls**: Shift+Right-click to toggle ADS on/off. Exit happens automatically on item switch, teleport, world change, or death.
- While ADS is active, bullet spread is multiplied by `spread_multiplier` (default 0.35 — 65% tighter groups). Movement penalty is applied via a Slowness potion effect. Action bar shows `🎯 ADS [spread ×0.35]`.
- ADS guns do **not** respond to the vanilla sneak-to-scope mechanic — only the Shift+Right-click toggle.
- New `GunData` fields: `isAdsEnabled()`, `getAdsSpreadMultiplier()`, `getAdsMovementPenalty()`. Builder setters: `.adsEnabled()`, `.adsSpreadMultiplier()`, `.adsMovementPenalty()`.

#### Ammo Pouch
- New compressed ammo bag item. Stores ammo type and quantity in PersistentDataContainer.
- **Usage**: Shift+Right-click to unpack all rounds directly into the player's inventory. Inventory-full message shown if not all rounds fit.
- Admin command: `/gun givepouch <ammo_id> <amount> [player]`
- API: `plugin.getAmmoPouchManager().createPouch(ammoId, amount)`
- Configurable material (`BUNDLE` default), `custom_model_data`, and enable toggle in `config.yml`.
- PDC layout: `combatgun:custom_item_type = "ammo_pouch"`, `combatgun:pouch_ammo_type`, `combatgun:pouch_quantity`.

#### Bleeding — Damage Over Time
- New optional DoT system. When a bullet hits a **player**, there is a configurable chance to apply bleeding.
- A bleeding player loses `damage_per_second` HP every second until the duration expires or they cure it.
- **Cure**: Hold a bandage (configurable component ID) and press Swap Hand `[F]` to consume one and stop bleeding.
- Config (`config.yml`):
  ```yaml
  combatgun:
    bleeding:
      enabled: false
      chance: 0.15
      damage_per_second: 1.0
      duration_seconds: 10
      cure_item: bandage
  ```
- Bleeding is cancelled on player death, quit, and `BleedingManager.cure()`.
- API: `plugin.getBleedingManager().tryApply(player)`, `isBleeding(player)`, `cure(player, notify)`.

#### Multi-Language (i18n)
- New `LangManager` loads all player-facing messages from `lang/<code>.yml`.
- Bundled languages: `en` (English) and `vi` (Tiếng Việt).
- Configure via `combatgun.language: en` in `config.yml`. Running `/gun reload` hot-reloads the language file.
- Adding a custom language: copy `lang/en.yml` from the plugin data folder to `lang/xx.yml` and translate.
- All messages in `GunListener`, `ReloadManager`, shop, bleeding, and ammo pouch now route through `LangManager.get(key)` and `LangManager.format(key, args...)`.
- Messages support `{0}`, `{1}` ... positional placeholders.

### Changed

- `GunData` — added `adsEnabled`, `adsSpreadMultiplier`, `adsMovementPenalty` fields, getters, and Builder setters.
- `GunManager` — parses `ads.enabled`, `ads.spread_multiplier`, `ads.movement_penalty` from gun YAML.
- `GunListener` — added `adsPlayers` set, `toggleAds()`, `clearAds()` methods; ADS state factored into spread calculation in `performShot()`; all hardcoded message strings replaced with `lang()` calls; ammo pouch Shift+Right-click detection in `onInteract()`; bleeding applied to player targets in `applyEntityDamage()`; bandage cure via Swap Hand in `onSwapHand()`; `onQuit()` and `onDeath()` now call `bleedingManager.cure()`.
- `CombatGunSSSPlugin` — registers `LangManager`, `BleedingManager`, `AmmoPouchManager`; `onDisable()` calls `bleedingManager.cancelAll()`; startup log prints `Lang`, `Bleeding` status lines.
- `GunCommand` — added `givepouch` to `SUB_COMMANDS`, switch, tab completion, help, and `handleGivePouch()` method.
- `plugin.yml` — version bumped to `1.2.0`; command usage updated; description updated.
- `pom.xml` — version bumped to `1.2.0`.
- `config.yml` — added `combatgun.language`, `combatgun.bleeding`, `combatgun.ammo_pouch` sections.

---

## [1.1.0] - 2026-04-10


### Added

#### PlaceholderAPI Integration
- New soft-depend on PlaceholderAPI. When the plugin is present, CombatGunSSS registers a `%combatgun_*%` expansion with 8 real-time placeholders:

| Placeholder | Example | Description |
| :--- | :--- | :--- |
| `%combatgun_gun_name%` | `AK47` | Display name of held gun |
| `%combatgun_gun_id%` | `ak47` | Internal ID of held gun |
| `%combatgun_gun_rarity%` | `epic` | Rarity of held gun |
| `%combatgun_ammo%` | `24` | Current magazine ammo |
| `%combatgun_ammo_max%` | `30` | Magazine capacity |
| `%combatgun_ammo_reserve%` | `90` | Reserve ammo in inventory |
| `%combatgun_is_reloading%` | `true` | Whether player is reloading |
| `%combatgun_is_gun%` | `true` | Whether held item is a gun |

- All placeholders return safe empty-string or `0` defaults when the player holds no gun.
- Compatible with scoreboard plugins (AdvancedHud, TAB, AnimatedScoreboard, etc).

#### Vault Economy / Shop
- New soft-depend on Vault. When an economy provider is found and `combatgun.shop.enabled: true`, players can purchase weapons via `/gun buy <gun_id>`.
- Prices are configured per gun in `config.yml` under `combatgun.shop.guns`. All 45 built-in weapons have default prices included in the generated config.
- Admin shortcut: `/gun buy <gun_id> free` gives the weapon at no cost (requires `combatgun.admin`).
- Clear failure messages for insufficient funds, missing price entry, and unavailable economy.
- `/gun buy` added to tab completion and `/gun` help output.
- `VaultHook` is a standalone class — `VaultHook.buyGun()` can be called from third-party plugins that depend on CombatGunSSS.

#### WorldGuard Region Protection
- New soft-depend on WorldGuard. When present, a custom flag `gun-shooting` is registered during `onLoad()` (before WorldGuard reads region data).
- Shooting is blocked in any region where `gun-shooting` is set to `deny`:
  ```
  /rg flag <region> gun-shooting deny    # block all gun use
  /rg flag <region> gun-shooting allow   # explicitly allow
  ```
- Falls back to WorldGuard's built-in `PVP` flag when `gun-shooting` is not configured on a region.
- Applies to all ranged shots and is checked inside `isAllowed()` — same path as world and permission checks. Players receive an action-bar message: `⛔ Guns are disabled in this region.`
- Fails open on unexpected errors to avoid breaking gameplay.

#### Anti-Cheat Exemptions (Vulcan & Matrix)
- New soft-depend detection for Vulcan and Matrix anti-cheat plugins.
- When either plugin is detected, `AntiCheatHook.exempt(player)` is called immediately before `applyRecoil()` — preventing false-positive flags on the camera pitch/yaw changes that recoil applies.
- Exemption is automatically removed after `combatgun.anticheat.exempt_ticks` ticks (default `3`).
- Both exemption and un-exemption calls are fully wrapped in try-catch — an API version mismatch in either anti-cheat never crashes CombatGunSSS.

#### Central `HookManager`
- New `HookManager` class owns all hook instances (`PlaceholderHook`, `VaultHook`, `WorldGuardHook`, `AntiCheatHook`).
- Initialized once in `onEnable()`. Each hook initializes independently — a failure in one never prevents others from loading.
- Accessible via `plugin.getHookManager()` for third-party plugins that want to query hook state.
- Startup log now prints `Shop: ON (Vault)` and `WGuard: ON` lines based on detected integrations.

### Changed

- `pom.xml` — added `provided` dependencies for PlaceholderAPI `2.11.6`, VaultAPI `1.7`, and WorldGuard `7.0.9`. All three are compile-time only (never shaded into the jar).
- `plugin.yml` — version bumped to `1.1.0`; added `softdepend` list: `[PlaceholderAPI, Vault, WorldGuard, Vulcan, Matrix]`; updated command description to include `buy`; updated plugin description.
- `config.yml` — added `combatgun.shop` section with `enabled`, `currency_symbol`, and per-gun price list for all 45 built-in weapons; added `combatgun.anticheat.exempt_ticks` key; added inline documentation comments for WorldGuard.
- `GunCommand` — added `buy` to `SUB_COMMANDS` list, switch statement, tab completion, and help text; added `handleBuy()` method.
- `GunListener.isAllowed()` — added WorldGuard region check between world check and permission check.
- `GunListener.applyRecoil()` — calls `antiCheatHook.exempt(player)` before applying camera rotation.
- `CombatGunSSSPlugin.onLoad()` — added `WorldGuardHook.registerFlags()` call (required before WorldGuard reads region data).
- `CombatGunSSSPlugin.onEnable()` — initializes `HookManager` after all managers are ready; startup log prints hook status lines.

---

## [1.0.3] - 2026-04-09

### Added

#### Action Bar HUD
- New persistent ammo HUD displayed on the action bar while holding any ranged weapon.
- Format: `🔫 <GunName>  ▐████████████░░░░▌  22 / 30  •  90`
  - Gun name is colored by rarity: white (common) → aqua (rare) → light purple (epic) → gold (legendary).
  - Visual bar transitions from green (full) to gold (≤ 25%) to red (≤ 10%) as ammo depletes.
  - Reserve count turns red when the player has no reserve ammo left.
- HUD yields automatically to the reload progress bar during active reloads — no visual conflict.
- Immediate update on every shot (no delay waiting for the next tick).
- Configurable via new `combatgun.hud` config block:
  - `enabled` (boolean, default `true`) — toggle the HUD entirely.
  - `update_interval_ticks` (integer, default `5`) — refresh rate in ticks. Lower = smoother, minimum 1.

#### Auto-Reload When Empty
- New config key `combatgun.auto_reload_when_empty` (boolean, default `true`).
- When enabled, CombatGunSSS automatically starts a reload after a gun fires its last round, saving the player from manually pressing `[F]`.
- Auto-reload respects the existing `GunReloadEvent` — other plugins can still cancel it.
- Set to `false` for hardcore or manual-reload server modes.

#### Developer API — `GunHitEvent`
- New informational event `GunHitEvent`, fired immediately after a bullet successfully damages a living entity.
- Provides the complete damage pipeline in a single event:
  - `getBaseDamage()` — damage before falloff and headshot multiplier.
  - `getFinalDamage()` — damage actually applied after all multipliers.
  - `isHeadshot()` — whether the hit registered as a headshot.
  - `getDistance()` — distance in blocks from shooter eye to hit position.
  - `getShooter()`, `getTarget()`, `getGun()` — full context.
- Intended for stat tracking, cosmetic side-effects, or per-hit reward systems. Not cancellable (damage is already done by the time the event fires).

#### Developer API — `GunShootEvent.getBaseDamage()`
- New method on the existing `GunShootEvent`, available pre-shot before any multipliers are applied.
- Returns `gun.getDamage() * gun.getProjectileDamageMultiplier()`.
- Useful for pre-shot checks such as arena minimum-damage caps or logging without recalculating manually.

### Changed

- `CombatGunAPI` — added `GunHitEvent` to the `CUSTOM_EVENTS` documentation array and updated Javadoc to list all four custom events with descriptions.
- `CombatGunSSSPlugin` — registers and stops `HudManager`; logs `HUD: ON/OFF` to console on startup.
- `GunListener` — removed private `sendAmmoBar()` helper (replaced by `HudManager`); calls `hudManager.sendNow()` after each shot for an instant HUD update; applies `auto_reload_when_empty` config check before triggering reload on empty magazine.

### Fixed

- **`GunHitEvent` damage transparency** — previously, third-party plugins had no way to observe the actual damage applied by a bullet (only the pre-shot `GunShootEvent` existed). Final damage, falloff, and headshot calculations were completely opaque after `v1.0.2`. `GunHitEvent` closes this gap.

---

## [1.0.2] - 2026-04-07

### Added

#### Developer API — Custom Events
- **`GunShootEvent`** (cancellable) — fired before every ranged shot. Other plugins can cancel the shot entirely or modify damage via `setDamageMultiplier(double)`. Useful for anti-cheat integration, zone-based restrictions, or temporary power-up effects.
- **`GunReloadEvent`** (cancellable) — fired when a player starts a reload. Cancel to block reloading in specific zones or game states (e.g., "no reload during objective capture").
- **`GunHeadshotEvent`** (informational) — fired on every confirmed headshot. Provides shooter, target, gun data, and final damage for kill-feed plugins, stat trackers, or custom rewards.

#### Friendly Fire Control
- New config keys `combatgun.friendly_fire` (boolean, default `true`) and `combatgun.team_provider` (string).
  - `scoreboard` mode — uses vanilla Minecraft scoreboard teams (`/team add …`). Players sharing a team cannot shoot each other when friendly fire is off.
  - `permission_group` mode — players sharing any `combatgun.team.<n>` permission node are considered teammates.
  - Both ranged shots and melee attacks respect the friendly-fire setting.

#### Toggleable Effects
- New `combatgun.effects` config block. Every visual and audio effect can now be enabled or disabled individually without recompiling:
  - `bullet_trail`, `muzzle_flash`, `hit_particles`, `block_impact`, `headshot_particles`
  - `gun_sound`, `melee_sound`, `melee_particles`
  - `sound_volume` — master volume multiplier for all gun sounds (0.0 – 2.0, default 1.5)
- Disabling heavy effects (bullet trails, muzzle flash) can meaningfully reduce particle load on high-population servers.

#### Kill / Death Messages
- Gun kills now override the vanilla death message with a formatted line showing killer name, weapon name (colored by rarity), and a **☠ HEADSHOT** prefix when applicable.
- Implemented via a short-lived `DamageRecord` map (TTL 5 seconds) that links the last gun shot to the subsequent death event — no false attribution on delayed deaths.

### Fixed

#### Memory Leak in `AmmoManager`
- `lastShot` and `lastMeleeAttack` were plain `HashMap` entries that were only removed when `clearPlayer()` was called on a clean quit or death. Players who disconnected via server crash, network drop, or `/kick` left stale entries that accumulated indefinitely.
- **Fix:** Added `AmmoManager.pruneStale(long olderThan)` and a periodic async scheduler in `onEnable()` that calls it every 2 minutes, removing any entry whose timestamp is older than 60 seconds.

#### `getMeleeCooldownRemaining` Always Returned Wrong Value
- The zero-argument overload `getMeleeCooldownRemaining(UUID)` contained a hardcoded `cooldownMs = 1000` with a comment acknowledging the bug. It would always calculate the remaining cooldown against 1 second regardless of the actual weapon's fire rate.
- **Fix:** Removed the broken zero-argument overload entirely. Only the correct `getMeleeCooldownRemaining(UUID, long weaponCooldownMs)` overload is retained. All callers already passed the weapon cooldown.

#### Reload Not Cancelled on Teleport / World Change
- When a player teleported or changed worlds while a reload was in progress, the reload `BukkitRunnable` continued executing in the background. If the reload completed, ammo was consumed and applied to the gun even though the player was now in a different (possibly gun-banned) world.
- **Fix:** Added `cancelReload(player, false)` calls to both `onTeleport` and `onChangedWorld` event handlers, matching the existing behaviour of `onDeath` and `onQuit`.

#### `isMelee()` Could Throw `NullPointerException`
- `GunData.isMelee()` evaluated `"none".equalsIgnoreCase(ammoType)`. If a gun's YAML omitted `ammo_type` entirely, `ammoType` was `null` and this call threw an NPE silently swallowed by the YAML loader, causing the weapon to not load.
- **Fix:** `isMelee()` is now a stored boolean field derived at construction time. It is `true` when `category == "melee"` OR `ammoType` is null, blank, or `"none"`.

### Refactored

#### `GunData` — Builder Pattern
- Replaced the 27-parameter constructor with a fluent `GunData.Builder`. Every field has a sensible default so callers only set what differs. This prevents silent argument-order bugs (two adjacent `double` fields are indistinguishable to the compiler in a positional constructor).
- `GunManager.parseGun()` updated to use the builder; all other call sites remain source-compatible.

#### Bullet Trail — Adaptive Density + Lighter Particle
- Trail step count now scales with distance: `steps = distance < 20 ? distance × 2 : distance × 0.8`, capped at 40. Previously always `min(60, distance × 2)` which produced up to 80 particles on sniper shots.
- Switched from `Particle.SMOKE` to `Particle.DUST` (small grey, 0.3f radius) — lighter on client render without a visible quality difference at combat distances.

---

## [1.0.1] - 2026-04-05

### Fixed

#### Crafting System
- **`registerVanillaRecipes()` was never called** — component recipes were not registered with Bukkit on startup or after `/gun reload`, so the vanilla Recipe Book showed no entries. Fixed by calling `registerVanillaRecipes()` in `onEnable()` and `handleReload()`.
- **Best-match recipe resolution** — `ar_ammo`, `smg_ammo`, and `hg_ammo` share the same four ingredient types (`brass_casing`, `gunpowder`, `steel_scrap`, `primer_cap`). The old first-match algorithm could produce the wrong ammo type when a player placed slightly more ingredients than the minimum required. Replaced with a lowest-surplus algorithm that always selects the recipe whose ingredient amounts are closest to what the player put in.
- **`break` only exited the inner loop in `registerVanillaRecipes()`** — when a component recipe required more than 9 items total (the Bukkit `ShapelessRecipe` cap), the warning was logged but the outer ingredient loop continued, resulting in a partially-built recipe being registered silently. Changed to a labeled `break outer` so the entire recipe is skipped on overflow.
- **Double-click (COLLECT) could pull ingredients out of the Mechanical Crafting Table** — Bukkit's COLLECT click type sweeps the entire open inventory for matching items, bypassing the station's input-slot guard and corrupting the crafting preview. Added an explicit `ClickType.DOUBLE_CLICK` cancel at the top of `onInventoryClick`.
- **Recipe book identified by display name instead of PDC tag** — any `written_book` with "Recipe Guide" in its name would hijack the recipe GUI. The book now carries a `recipe_book_marker` PDC key set at creation time; `RecipeBookListener` checks this key instead.
- **`RecipeCategory.valueOf` could throw uncaught `IllegalArgumentException`** — if PDC data was corrupt or written by a different plugin version, the category click handler would crash with an unhandled exception. Wrapped in try-catch with a warning log.

#### Combat
- **Removed 5 `[DEBUG]` log statements from `GunListener.traceProjectile()`** — these were left in from development and spammed the server console with 4–5 lines per bullet fired.

---

## [1.0.0] - 2026-04-02

### Added

#### Weapons
- **39 Ranged Weapons** across 5 categories:
  - Assault Rifles: AK47, M4A1, SCAR, AUG, FAMAS, G36, Groza, AN94, M14, ParaFAL, XM8, Kingfisher
  - SMGs: MP5, P90, Vector, Bizon, UMP, Thompson, MAC-10, MP40, VSS, CG15
  - Snipers: AWM, M24, Kar98k, M82B, M107, VSK94
  - Shotguns: M1014, SPAS-12, MAG-7, M1887, M590, Trogon
  - Pistols: Desert Eagle, G18, USP, M1917, M1873, M500
- **6 Melee Weapons**: Bat, Knife, Pan, Parang, Katana, Scythe

#### Combat System
- Hitscan shooting mechanics with ray-tracing
- Per-weapon recoil (pitch/yaw), spread, and recovery
- Damage falloff based on distance traveled
- Headshot detection and multipliers
- Block penetration through soft materials
- Entity penetration for multi-target shots
- Knockback system for both ranged and melee
- Movement spread penalty while sprinting/jumping
- Burst fire support with configurable delays
- Shotgun pellet spread mechanics

#### Melee System
- Left-click attack mechanics (EntityDamageByEntityEvent)
- Range check (4 blocks default)
- Separate cooldown system from ranged weapons
- Knockback on melee hits
- Sound effects at target location
- Cooldown feedback in action bar

#### Crafting System
- Mechanical Crafting Table (21-slot GUI)
- Vanilla Crafting Table component recipes
- Recipe Book with visual browser
- Per-item ingredient requirements
- Non-grid-based crafting interface
- Output preview with weapon stats

#### Ammo System
- 5 Ammo Types: AR, SMG, Sniper, Shotgun, Handgun
- Magazine system with reload mechanics
- Swap-hand [F] key reload
- Durability system for weapons
- Auto-reload when empty

#### Configuration
- Per-weapon YAML configuration files
- Separated folder structure: `guns/` and `melees/`
- Custom weapon support (add your own YAML files)
- World whitelist/blacklist support
- Mob drop configuration
- Custom Model Data support for resource packs

#### Developer API
- Public API class: `CombatGunAPI`
- Query gun data and recipes
- Create gun/ammo/component items programmatically
- Check item types and player state
- World permission checks

#### Commands
- `/gun give <id> [player]` - Give weapons
- `/gun giveammo <id> [amount] [player]` - Give ammo
- `/gun givepart <id> [amount] [player]` - Give components
- `/gun station [player]` - Give crafting table
- `/gun book [player]` - Give recipe book
- `/gun list [category]` - List all weapons
- `/gun recipe <id>` - Show crafting chain
- `/gun inspect [player]` - Inspect held weapon
- `/gun reload` - Reload configurations

#### Permissions
- `combatgun.admin` - Access to all admin commands
- `combatgun.use` - Allow using guns (default: true)
- `combatgun.use.<gun_id>` - Per-weapon permissions

#### Visual Effects
- Bullet trail particles
- Muzzle flash effects
- Hit particles and sound effects
- Headshot particle effects
- Block impact particles
- Sweep attack particles for melee
- Action bar ammo display
- Action bar cooldown feedback

### Technical
- Paper/Spigot 1.21+ API support
- Java 21 requirement
- Persistent data container for item storage
- Tab completion for all commands
- Optimized ray-tracing with entity filtering

### Notes
- Initial release after extensive testing
- All 45 weapons balanced and configured
- Full documentation and examples provided
- Ready for production servers

---

## Future Roadmap

### Planned for 2.1.0
- NPC Gunsmith for weapon repair
- Per-player stats season reset
- Attachment crafting recipes
- Custom throwable effects API

### Planned for 2.0.0
- Weapon attachment system (silencer, scope, extended mag, grip)
- Throwable items (frag grenade, smoke grenade, flashbang)

---

[2.0.6]: https://github.com/duong2012g/CombatGunSSS/releases/tag/v2.0.6
[2.0.5]: https://github.com/duong2012g/CombatGunSSS/releases/tag/v2.0.5
[2.0.4]: https://github.com/duong2012g/CombatGunSSS/releases/tag/v2.0.4
[2.0.3]: https://github.com/duong2012g/CombatGunSSS/releases/tag/v2.0.3
[2.0.2]: https://github.com/duong2012g/CombatGunSSS/releases/tag/v2.0.2
[2.0.1]: https://github.com/duong2012g/CombatGunSSS/releases/tag/v2.0.1
[2.0.0]: https://github.com/duong2012g/CombatGunSSS/releases/tag/v2.0.0
[1.2.0]: https://github.com/duong2012g/CombatGunSSS/releases/tag/v1.2.0
[1.1.0]: https://github.com/duong2012g/CombatGunSSS/releases/tag/v1.1.0
[1.0.3]: https://github.com/duong2012g/CombatGunSSS/releases/tag/v1.0.3
[1.0.2]: https://github.com/duong2012g/CombatGunSSS/releases/tag/v1.0.2
[1.0.1]: https://github.com/duong2012g/CombatGunSSS/releases/tag/v1.0.1
[1.0.0]: https://github.com/duong2012g/CombatGunSSS/releases/tag/v1.0.0
