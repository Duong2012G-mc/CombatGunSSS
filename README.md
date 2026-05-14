# 🔫 CombatGunSSS

![Version](https://img.shields.io/badge/Version-2.0.6-blue.svg)
![Platform](https://img.shields.io/badge/Platform-Paper%20/%20Spigot-green.svg)
![Java](https://img.shields.io/badge/Java-21-red.svg)

**CombatGunSSS** is a high-performance, feature-rich Minecraft gun plugin designed for modern servers (1.21+). It brings a tactical, realistic combat experience with 45 uniquely configured weapons (39 firearms + 6 melee), advanced crafting mechanics, optimized hitscan technology, a full custom events API, and a growing ecosystem of plugin integrations.

---

## 🌟 Key Features

- **🎯 Tactical Combat**: Realistic hitscan mechanics with accurate recoil, bullet spread, and headshot multipliers.
- **⚔️ Melee System**: Left-click melee combat with range check, knockback, and separate cooldown system.
- **🛡️ Advanced Penetration**: Projectiles can pass through "soft" blocks and multiple entities based on individual gun stats.
- **🩸 Progressive Damage**: Distance-based **Damage Falloff** and configurable **Knockback** intensities.
- **🛠️ Two-Tier Crafting System**:
    - **Vanilla Workbench**: Craft basic components with official **Recipe Book** support.
    - **Mechanical Crafting Table**: A custom 21-slot industrial GUI for assembling advanced firearms.
- **📂 Interactive Recipe Browser**: Built-in visual guide inside the Mechanical Crafting Table — no commands needed!
- **📦 45 Built-in Weapons**: 39 ranged guns + 6 melee weapons with burst-fire, shotgun pellets, and melee logic.
- **📉 Durability System**: Weapons can have limited usage and require repairs (fully configurable).
- **📊 Persistent HUD**: Real-time action bar showing gun name (rarity-colored), visual ammo bar, current/max ammo, and reserve count.
- **🔄 Auto-Reload**: Automatically reloads after firing the last round (toggleable).
- **🗺️ WorldGuard Integration**: Block gun use inside protected regions with a custom `gun-shooting` flag.
- **💰 Vault Shop**: Players purchase weapons via `/gun buy <id>` using server economy.
- **📋 PlaceholderAPI**: 8 real-time placeholders for scoreboards, HUDs, and TAB plugins.
- **🎯 ADS (Aim Down Sights)**: Per-gun aim-down-sights system. Shift+Right-click to toggle — reduces spread and applies movement penalty.
- **🩸 Bleeding**: Optional damage-over-time on bullet hit. Cured by holding a bandage and pressing `[F]`.
- **🎒 Ammo Pouch**: Compressed ammo bag. Shift+Right-click to unpack into inventory. Give via `/gun givepouch`.
- **🌐 Multi-Language (i18n)**: All messages live in `lang/en.yml` or `lang/vi.yml`. Add your own translation file.
- **🛡️ Anti-Cheat**: Automatic exemptions for Vulcan and Matrix to prevent recoil false-positives.
- **🔌 Developer API**: Full custom events — `GunShootEvent`, `GunReloadEvent`, `GunHitEvent`, `GunHeadshotEvent`.
- **🤝 Friendly Fire Control**: Toggle friendly fire on/off with scoreboard team or permission-group detection.
- **🎨 Toggleable Effects**: Every particle and sound effect can be individually enabled/disabled. Includes a master `sound_volume` control.
- **💀 Custom Kill Messages**: Death messages show killer name, weapon name (colored by rarity), and headshot indicator.

---

## 🎮 Player Controls

| Action | Control |
| :--- | :--- |
| **Shoot** | `Right Click` |
| **Reload** | `Swap Hand Key [F]` |
| **Melee Attack** | `Left Click` |
| **Buy a weapon** | `/gun buy <id>` |
| **Crafting** | `Right Click` on Mechanical Crafting Table |

---

## 🔫 Weapon Categories

### Ranged Weapons (39)
- **Assault Rifles**: AK47, M4A1, SCAR, AUG, FAMAS, G36, Groza, AN94, M14, ParaFAL, XM8, Kingfisher
- **SMGs**: MP5, P90, Vector, Bizon, UMP, Thompson, MAC-10, MP40, VSS, CG15
- **Snipers**: AWM, M24, Kar98k, M82B, M107, VSK94
- **Shotguns**: M1014, SPAS-12, MAG-7, M1887, M590, Trogon
- **Pistols**: Desert Eagle, G18, USP, M1917, M1873, M500

### Melee Weapons (6)
- **Bat**: Common, fast swing
- **Knife**: Common, highest attack speed
- **Pan**: Rare, defensive knockback
- **Parang**: Rare, balanced damage/speed
- **Katana**: Epic, high damage
- **Scythe**: Legendary, maximum damage

---

## 🔫 Technical Weapon Stats

Every weapon in CombatGunSSS features a deep set of configurable properties:

- **Damage Falloff**: Define exactly at what block distance damage begins to drop (`damage_falloff_start`) and the minimum damage floor (`min_damage_multiplier`).
- **Recoil & Spread**: Independent **Pitch** (vertical) and **Yaw** (horizontal) kick per shot, plus **Movement Spread** penalties while moving or jumping.
- **Burst Fire**: Configure weapons to fire multiple rounds per trigger pull (`burst_count`) with custom delays.
- **Shotgun Logic**: Support for multiple projectiles per shot (`projectiles_per_shot`) with per-pellet damage calculation.
- **Durability**: Set a maximum usage limit (`max_durability`). Weapons will break and become unusable until repaired.
- **Penetration**: Define how many blocks (`block_penetration`) or entities (`entity_penetration`) a single bullet can pass through.
- **Melee Range**: Maximum attack distance for melee weapons (default: 4 blocks).
- **Knockback**: Push strength for both ranged and melee weapons.

---

## 📁 Folder Structure

```
plugins/CombatGunSSS/
├── config.yml              # Main configuration
├── guns/                   # Ranged weapon configs (39 built-in + custom)
│   ├── ak47.yml
│   ├── m4a1.yml
│   └── ...
├── melees/                 # Melee weapon configs (6 built-in + custom)
│   ├── katana.yml
│   ├── knife.yml
│   └── ...
└── README.md
```

**Custom Weapons**: Create your own YAML files in `guns/` or `melees/` folders and run `/gun reload`.

---

## ⚒️ Crafting Mechanics

### **1. Components (The Basics)**
Craft raw materials like **Steel Ingots**, **Gun Barrels**, and **Springs** at a standard Crafting Table. These recipes are automatically unlocked in your **Vanilla Recipe Book**.

### **2. Assembly (The Advanced)**
Place your components into the **Mechanical Crafting Table**.
- **Non-grid-based**: Just throw the ingredients into the 21-slot input area.
- **Visual Preview**: The output slot shows your gun stats before you build it.
- **Recipe Guide**: Click the **Glowing Book** icon in the station to browse all weapon requirements.

---

## 📊 Action Bar HUD

While holding any ranged weapon, CombatGunSSS displays a persistent HUD on your action bar:

```
🔫 AK47  ▐████████████░░░░▌  22 / 30  •  90
```

- **Gun name** is colored by rarity: white (common) → aqua (rare) → light purple (epic) → gold (legendary).
- **Visual bar** transitions green → gold (≤25%) → red (≤10%) as ammo depletes.
- **Reserve count** turns red when you have no backup ammo left.
- The HUD automatically yields to the reload progress bar during active reloads.

```yaml
combatgun:
  hud:
    enabled: true
    update_interval_ticks: 5
```


## 🎯 ADS — Aim Down Sights

Enable ADS per gun in its YAML config:

```yaml
ads:
  enabled: true
  spread_multiplier: 0.35   # 65% tighter accuracy while ADS
  movement_penalty: 0.6     # movement speed while ADS active
```

**Control**: Shift+Right-click to toggle ADS on/off. Action bar shows `🎯 ADS [spread ×0.35]`.

ADS exits automatically when you switch items, teleport, or die. Scopeable sniper rifles use the traditional sneak-to-scope mechanic instead of ADS.

---

## 🩸 Bleeding

An optional damage-over-time system. When enabled, bullets hitting players have a configurable chance to cause bleeding.

```yaml
combatgun:
  bleeding:
    enabled: true
    chance: 0.15
    damage_per_second: 1.0
    duration_seconds: 10
    cure_item: bandage
```

Hold a **bandage** (crafting component) and press `[F]` to cure. Bleeding stops automatically on death.

---

## 🎒 Ammo Pouch

A compressed ammo container that stores hundreds of rounds in a single slot.

```
/gun givepouch ar_ammo 300 PlayerName
```

Shift+Right-click to unpack all rounds into your inventory. Surplus rounds are left if the inventory is full.

---

## 🌐 Multi-Language

Set the active language in `config.yml`:

```yaml
combatgun:
  language: vi   # en | vi | (any lang/xx.yml)
```

Bundled: **English** (`en`) and **Tiếng Việt** (`vi`). Add a custom translation by placing `lang/xx.yml` in the plugin data folder and running `/gun reload`.

---
## 🔌 Plugin Integrations

### PlaceholderAPI

8 real-time placeholders for use in scoreboards, TAB, AdvancedHud, and similar plugins:

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

### Vault Shop

Enable the shop in `config.yml`, set prices per gun, then players can buy weapons in-game:

```yaml
combatgun:
  shop:
    enabled: true
    currency_symbol: "$"
    guns:
      ak47:  700.0
      awm:  2500.0
      knife: 100.0
```

```
/gun buy ak47          # purchase for configured price
/gun buy ak47 free     # admin free-give (requires combatgun.admin)
```

### WorldGuard

The custom flag `gun-shooting` is automatically registered when WorldGuard is present:

```
/rg flag <region> gun-shooting deny    # block all guns in region
/rg flag <region> gun-shooting allow   # explicitly allow
/rg flag <region> gun-shooting -g      # remove flag (inherit parent)
```

Falls back to WorldGuard's built-in `PVP` flag if `gun-shooting` is not set on a region.

### Anti-Cheat (Vulcan / Matrix)

Auto-detected. When present, players are exempted from motion checks for `anticheat.exempt_ticks` ticks after each shot to prevent recoil from triggering false positives.

```yaml
combatgun:
  anticheat:
    exempt_ticks: 3
```

---

## 📜 Commands & Permissions

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/gun statsreset <player>` | Reset a specific player's stats | `combatgun.admin` |
| `/gun seasonreset confirm` | Wipe ALL player stats (season reset) | `combatgun.admin` |
| `/gun give <id> [player]` | Give a specific weapon | `combatgun.admin` |
| `/gun giveammo <id> [amt] [player]` | Give custom ammunition | `combatgun.admin` |
| `/gun givepart <id> [amt] [player]` | Give crafting components | `combatgun.admin` |
| `/gun station [player]` | Give the Mechanical Crafting Table | `combatgun.admin` |
| `/gun book [player]` | Give the Recipe Guide Book | `combatgun.admin` |
| `/gun recipe <id>` | Show the full crafting chain in chat | `combatgun.admin` |
| `/gun list [category]` | View all loaded weapons | `combatgun.admin` |
| `/gun inspect [player]` | View deep stats of the held weapon | `combatgun.admin` |
| `/gun reload` | Reload all configurations | `combatgun.admin` |
| `/gun buy <id>` | Purchase a weapon (requires Vault) | `combatgun.use` |
| `/gun givepouch <ammo_id> <amount> [player]` | Give an ammo pouch | `combatgun.admin` |

### Player Permissions
- `combatgun.use` — Allows shooting and using guns (default: `true`)
- `combatgun.use.<gun_id>` — Per-gun permission node (e.g. `combatgun.use.awm`)

---

## 🔧 Configuration Examples

### Ranged Weapon (AK47)
```yaml
name: AK47
category: assault_rifles
ammo_type: ar_ammo
damage: 12
fire_rate: 10.0
magazine_size: 30
reload_time: 2.6
headshot_multiplier: 1.8
damage_falloff_start: 35.0
min_damage_multiplier: 0.5
range: 72.0
block_penetration: 0.8
entity_penetration: 0
recoil:
  pitch: 1.45
  yaw: 0.42
  spread: 0.24
  recovery: 0.80
rarity: rare
custom_model_data: 1001
sound: ENTITY_FIREWORK_ROCKET_BLAST
recipe:
  station: mechanical_crafting_table
  ingredients:
    steel_ingot: 6
    gun_barrel: 1
    spring: 2
    hardwood: 3
```

### Melee Weapon (Katana)
```yaml
name: Katana
category: melee
ammo_type: none
rarity: epic
damage: 10
fire_rate: 1.35
range: 4.0
knockback: 0.3
sound: ENTITY_PLAYER_ATTACK_CRIT
custom_model_data: 26002
recipe:
  station: mechanical_crafting_table
  ingredients:
    steel_ingot: 10
    carbon_fiber: 2
    leather_strip: 3
    blade_core: 1
```

---

## 🔌 Developer API

### Custom Events

| Event | Cancellable | When it fires |
| :--- | :---: | :--- |
| `GunShootEvent` | ✅ | Before each shot — cancel or set damage multiplier |
| `GunReloadEvent` | ✅ | When a player starts reloading |
| `GunHitEvent` | ❌ | After damage is applied — full damage pipeline |
| `GunHeadshotEvent` | ❌ | When a shot lands in the head zone |
| `AttachmentApplyEvent` | ✅ | Before an attachment is fitted to a gun |
| `AttachmentRemoveEvent` | ✅ | Before an attachment is removed from a gun |

```java
@EventHandler
public void onShoot(GunShootEvent event) {
    if (isInsideArena(event.getShooter()))
        event.setDamageMultiplier(2.0);
}

@EventHandler
public void onReload(GunReloadEvent event) {
    if (isCapturingObjective(event.getPlayer()))
        event.setCancelled(true);
}

@EventHandler
public void onHit(GunHitEvent event) {
    // base damage, final damage, headshot flag, distance
    plugin.getStats().record(
        event.getShooter(), event.getGun().getId(),
        event.getFinalDamage(), event.isHeadshot(), event.getDistance());
}

@EventHandler
public void onHeadshot(GunHeadshotEvent event) {
    event.getShooter().giveExp(5);
}
```

### API Instance

```java
CombatGunAPI api = CombatGunAPI.getInstance();

// Query guns
GunData gun              = api.getGun("ak47");
Collection<GunData> all  = api.getAllGuns();

// Create items
ItemStack gunItem         = api.createGunItem("ak47");
ItemStack ammo            = api.createAmmoItem("ar_ammo", 30);

// Check items
boolean isGun             = api.isGun(item);
String  gunId             = api.getGunId(item);

// Player state
boolean isReloading       = api.isReloading(player.getUniqueId());

// Hook state
boolean shopEnabled       = plugin.getHookManager().getVaultHook().isEnabled();
boolean wgActive          = plugin.getHookManager().getWorldGuardHook().isAvailable();
```

---

## 🚀 Installation

1. Download the `CombatGunSSS-2.0.6.jar`.
2. Drop it into your server's `plugins` folder.
3. (Optional) Install any soft-depend plugins you want: PlaceholderAPI, Vault, WorldGuard, Vulcan, Matrix.
4. Restart the server to generate default configurations.
5. (Optional) Add a Resource Pack to see 3D gun models.

### Requirements
- **Server**: Paper/Spigot 1.21+
- **Java**: 21 or higher
- **Required dependencies**: None (standalone plugin)
- **Optional dependencies**: PlaceholderAPI, Vault + economy plugin, WorldGuard, Vulcan, Matrix

---

## 📋 Changelog

See [CHANGELOG.md](CHANGELOG.md) for the full version history.

**2.0.6** — Bug-fix release: 4 bugs fixed
- Fixed friendly-fire scoreboard team detection using player scoreboard instead of main scoreboard — could allow friendly-fire bypass
- Fixed `recentDamage` memory leak: entries now removed on player quit and death events
- Fixed shared `static Random` contention: replaced with `ThreadLocalRandom.current()` (per-thread, no lock, better entropy)
- Fixed async SQLite connection safety: `incrementGunKill()` and `getGunKills()` now synchronized alongside `flushBuffer()`

**2.0.5** — Developer API & architecture release: 4 new additions, 1 improvement
- New `AttachmentApplyEvent` and `AttachmentRemoveEvent` (cancellable) — fired on `/gun attach` and `/gun detach`
- New `DamageCalculator` utility class — centralises all damage math extracted from `GunListener` (headshots, falloff, spread, pellets)
- New `kills_by_gun_detail` SQLite table (schema v3) — per-kill log with timestamp, weapon, headshot flag
- New commands: `/gun statsreset <player>` and `/gun seasonreset confirm`
- `GunListener` God Class partially refactored — 4 methods delegate to `DamageCalculator`

**2.0.4** — Stability & performance release: 2 bug-fixes, 3 improvements
- Fixed thread-safety data race in `StatsManager` stat buffer (ConcurrentHashMap)
- Fixed silent loading of invalid gun configs (YAML validation with clear per-field errors)
- `StatsManager` leaderboard query optimized with a `kills DESC` index (schema migration v2)
- Shop price reads now cached in memory; invalidated on `/gun reload`
- Config version detection warns admins when `config.yml` is outdated after an update

**2.0.3** — Bug-fix release: 7 bugs fixed
- Ammo Pouch no longer loses items when the inventory is full
- Reload task CPU usage reduced by 50% (changed from 1 tick to 2 tick interval)
- `isHeadshot()` now works accurately when the player is crouching
- `craftFromStation()` properly rolls back ingredients if crafting fails
- `BleedingManager` now uses wall-clock time for accurate damage even during server lag
- `applyRecoil()` is now fully null-safe
- Fixed race condition in `onItemHeldChange`

**2.0.2** — Hotfix: Fixed NPE when shooting caused by GunListener trying to cache HudManager before it was initialized — resolved using lazy access.

**2.0.1** — Hotfix: plugin crash on startup isolated; `StatsManager`/`HookManager` failures degrade gracefully; `Material.GRAY_DYE` compile fix; null-safety throughout.

**2.0.0** — Weapon attachments (silencer, scope, extended mag, grip); throwable items (frag, smoke, flashbang); kill stats & leaderboard (SQLite); 4 new PlaceholderAPI placeholders.

**1.2.0** — ADS (Aim Down Sights) per-gun toggle; Ammo Pouch compressed item (`/gun givepouch`); Bleeding DoT on player hit with bandage cure; Multi-language i18n (`lang/en.yml`, `lang/vi.yml`).

**1.1.0** — PlaceholderAPI expansion (8 placeholders); Vault shop (`/gun buy`); WorldGuard `gun-shooting` custom flag for region protection; Vulcan & Matrix anti-cheat exemptions on recoil; central `HookManager`; `softdepend` in `plugin.yml`.

**1.0.3** — Persistent action bar HUD (rarity-colored name, visual ammo bar, reserve count); auto-reload when empty (toggleable); new `GunHitEvent` exposing full damage pipeline; `GunShootEvent.getBaseDamage()` for pre-shot checks.

**1.0.2** — Custom events API; friendly fire toggle; toggleable effects + `sound_volume`; custom kill messages; `AmmoManager` memory leak fix; reload cancel on teleport/world change; `GunData` Builder pattern.

**1.0.1** — Recipe Book registration fix; ammo type disambiguation fix; double-click exploit fix; debug spam removed.

---

## 🔍 Troubleshooting

<details>
<summary><b>Friendly fire still occurring despite being disabled</b></summary>

* Fixed in **2.0.6** — team detection now uses the main scoreboard instead of each player's personal view
* Ensure teams are set up with `/team add <name>` and players added with `/team join <name> <player>`
* Test with `combatgun.team_provider: scoreboard` in `config.yml`

</details>

<details>
<summary><b>Guns not working / can't shoot</b></summary>

* Check `combatgun.use` permission is granted to players
* Make sure the world is not blacklisted in `config.yml`
* If using WorldGuard, check if the region has `gun-shooting deny`
* Try `/gun give ak47` and test in a clean area
* Use Paper — Spigot is not supported

</details>

<details>
<summary><b>Ammo not found / can't reload</b></summary>

* Make sure you have the correct ammo type in your inventory (e.g. AR ammo for assault rifles)
* Craft ammo at a Vanilla Crafting Table or Mechanical Crafting Table
* Check `ammo_type` in the gun's YAML matches a defined ammo type in `config.yml`
* Run `/gun reload` after editing configs

</details>

<details>
<summary><b>Recipes not showing in crafting table</b></summary>

* Run `/gun reload` to re-register vanilla recipes
* Give yourself the Recipe Guide Book: `/gun book`
* Ensure the ingredient IDs in the gun YAML match those defined in `config.yml`

</details>

<details>
<summary><b>Console warning: "config.yml is OUTDATED"</b></summary>

* This warning appears when your `config.yml` has a lower `config-version` than the plugin expects.
* Back up your current `config.yml`, delete it, and restart the server to regenerate a fresh one.
* Then manually copy over your custom settings (world lists, shop prices, effects, etc.) from the backup.
* The plugin loads normally even with an outdated config — default values are used for missing keys.

</details>

<details>
<summary><b>Gun config not loading / "config validation failed" warning</b></summary>

* Added in **2.0.4** — guns with invalid configs now log a clear per-field error instead of loading silently.
* Check the listed fields: `damage`, `fire_rate`, `magazine_size`, `reload_time` must all be greater than 0.
* `burst_count` must be at least 1. `name` cannot be blank.
* Fix the values in the gun's YAML and run `/gun reload`.

</details>

<details>
<summary><b>Leaderboard (`/gun leaderboard`) is slow on large servers</b></summary>

* Fixed in **2.0.4** — a `kills DESC` index is now created automatically on the SQLite database.
* The migration runs once on first startup of 2.0.4. If you still experience slowness, ensure the server was fully restarted (not `/reload`).

</details>

<details>
<summary><b>Ammo Pouch destroying items when inventory is full</b></summary>

* Fixed in **2.0.3** — update the plugin
* Partial unpacks now save remaining ammo back into the pouch instead of destroying it

</details>

<details>
<summary><b>Reload taking wrong amount of time / inconsistent</b></summary>

* Fixed in **2.0.3** — reload task now runs every 2 ticks instead of 1 (50% less CPU, same reload duration)
* If still wrong, check `reload_time` in the gun's YAML (in seconds)

</details>

<details>
<summary><b>Headshots not registering correctly</b></summary>

* Fixed in **2.0.3** — crouching player hitbox now calculated correctly
* Headshot detection uses the top 20% of the entity hitbox, adjusted for crouch state

</details>

<details>
<summary><b>Bleeding damage lower than configured</b></summary>

* Fixed in **2.0.3** — `BleedingManager` now uses wall-clock time instead of tick counting
* Damage is now accurate even under server lag

</details>

<details>
<summary><b>Plugin crashes on startup</b></summary>

* Fixed in **2.0.1** — startup errors are now isolated per manager
* Check console for `[CombatGunSSS]` error lines indicating which manager failed
* Common cause: corrupted `config.yml` or invalid gun YAML syntax
* Fix the config, then restart (do not use `/reload`)

</details>

<details>
<summary><b>NullPointerException when shooting</b></summary>

* Fixed in **2.0.2** — lazy HudManager access prevents NPE at startup
* If persisting: check that `HookManager` is not null in the stack trace and update to latest version

</details>

<details>
<summary><b>Anti-cheat flagging recoil movement</b></summary>

* Ensure Vulcan or Matrix is listed in `softdepend` (already done automatically)
* Increase `combatgun.anticheat.exempt_ticks` in `config.yml` (default: `3`)
* The plugin auto-detects and registers exemptions via reflection — no API jar needed

</details>

<details>
<summary><b>PlaceholderAPI placeholders showing as raw text</b></summary>

* Install PlaceholderAPI and run `/papi reload`
* Ensure PlaceholderAPI is loaded before CombatGunSSS (restart, not `/reload`)
* Test with `/papi parse me %combatgun_gun_name%`

</details>

<details>
<summary><b>Vault shop not working</b></summary>

* Make sure an economy plugin (EssentialsX, CMI, etc.) is installed alongside Vault
* Set `combatgun.shop.enabled: true` in `config.yml`
* Confirm the gun ID exists in `combatgun.shop.guns` price list
* Use `/gun buy <id> free` (admin) to test without balance requirement

</details>

<details>
<summary><b>/reload breaks the plugin</b></summary>

* Never use `/reload` — always do a full server restart
* Use `/gun reload` to hot-reload gun configs, language files, and settings without restarting

</details>

<details>
<summary><b>Custom gun YAML not loading</b></summary>

* Place the file inside `plugins/CombatGunSSS/guns/` (ranged) or `melees/` (melee)
* Run `/gun reload` — check console for warnings about your file
* Ensure required fields are present: `name`, `category`, `ammo_type`, `damage`, `magazine_size`
* YAML is whitespace-sensitive — use a validator like [yaml.org/start.html](https://yaml.org/start.html)

</details>

---

## ❓ FAQ

### Does this work on Spigot?

No — CombatGunSSS requires **Paper 1.21+**. Spigot lacks several API features used internally.

### Can I add my own guns?

Yes. Create a new `.yml` file in `plugins/CombatGunSSS/guns/` following the same format as the built-in weapon files, then run `/gun reload`.

### Can I disable specific guns?

Simply delete or remove the gun's YAML file from the `guns/` folder and run `/gun reload`. Players holding that gun will keep the item but it won't function.

### Does it support multiple worlds?

Yes. Configure a world whitelist or blacklist under `combatgun.worlds` in `config.yml`.

### Is it laggy on large servers?

CombatGunSSS uses hitscan (ray-trace, not projectile entities), so it is significantly lighter than projectile-based gun plugins. Heavy effects like bullet trails can be toggled off in `config.yml` under `combatgun.effects`.

### Can I disable the crafting system?

Yes — simply don't give players the Mechanical Crafting Table or Recipe Book. Use `/gun give` for admin distribution. Vanilla workbench recipes can also be disabled per-item.

### Do I need a Resource Pack?

No. The plugin works without a resource pack. However, each gun has a `custom_model_data` value pre-configured for you to link 3D models if you have a pack.

### Can I disable bleeding or the Ammo Pouch?

Yes. Set `combatgun.bleeding.enabled: false` or `combatgun.ammo_pouch.enabled: false` in `config.yml`.

### Does it support ZombieApocalypseSSS?

Yes — CombatGunSSS is the recommended gun companion for [ZombieApocalypseSSS](https://modrinth.com/plugin/zombieapocalypsesss). Guns deal full damage to all zombie types and trigger the noise / aggro system automatically.

### Can other plugins listen to gun events?

Yes. Four custom events are exposed via the API: `GunShootEvent` (cancellable), `GunReloadEvent` (cancellable), `GunHitEvent`, and `GunHeadshotEvent`. See the [Developer API](#-developer-api) section.

### How do I give guns without the shop?

Use `/gun give <id> [player]` (requires `combatgun.admin`). Example: `/gun give awm Steve`.

---



Developed with ❤️ by **Duong2012G**.

---

> [!NOTE]
> This plugin is built on the **Paper/Spigot 1.21** API and requires **Java 21** or higher.

> [!TIP]
> For support and updates, check the repository or contact the developer.
