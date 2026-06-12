# AntiEnchants

Paper plugin that **blocks unwanted enchantments server-wide**. Successor to the old `AntiMending`,
generalised from a single hardcoded enchantment to a configurable blocklist plus level caps,
per-world rules, compensation, permission bypasses, villager trade control and an audit log.

## What it does

- **Global enchantment purge** (default: `mending`) from any item: inventories (on join / open /
  click), picked-up items, fishing, generated loot and mob drops.
- **Blocks at the source**: removes banned enchantments (and applies caps) on the enchanting-table
  result and the anvil/grindstone/smithing-table result previews, instead of letting the player pay
  and purging afterwards (the smithing case covers netherite upgrades, which carry every
  enchantment onto the result).
- **Level caps** (`level-caps`): cap an enchantment instead of banning it — `sharpness: 3` turns
  Sharpness V into Sharpness III automatically, everywhere the purge runs.
- **Per-world rules** (`per-world`): extra banned keys and caps for specific worlds (e.g. ban
  `fortune` only in the resource world), merged on top of the global rules.
- **Item whitelist** (`exempt-items`): item types the plugin never touches. Material names with
  `*` wildcards (e.g. `"*_SWORD"`, `"BOW"`).
- **Compensation** (`compensation`, off by default): when a banned enchantment is stripped from a
  player's item, grant configurable items back (e.g. XP bottles, emeralds), globally or per
  enchantment. Player-context strips only, never in creative (farmable otherwise).
- **Permission bypasses**: `antienchants.bypass.<key>` (e.g. `antienchants.bypass.mending`) lets
  donor ranks / staff keep specific enchantments; `antienchants.bypass.*` bypasses everything.
  Nodes are registered with default `false`, so ops don't bypass by accident.
- **Player feedback** (`messages`, MiniMessage): players are told when their items are stripped,
  capped or compensated (empty string = silent).
- **Audit log** (`audit-log`, off by default): every player-context strip is appended to
  `plugins/AntiEnchants/strips.log` (when, who, where, what) — handy when someone claims the
  plugin "stole" their item. Async writes, size-based rotation.
- **Empty-book conversion**: a stripped enchanted book with nothing left becomes a normal book.
- **Blocks mending's effect** (`PlayerItemMendEvent`): XP never repairs, even if an item slips through.
- **Curses**: `ban-all-curses` blocks every curse at once (vanilla + plugins).
- **Dry-run mode** (`dry-run`, off by default): detect and log what *would* be stripped/capped
  (console + `strips.log` if the audit log is on) without modifying a single item — preview the
  impact of new rules on a live server before enforcing them. Non-destructive interventions
  (result previews, mend cancel, trade cancels) stand down too. Loud warning on startup/reload,
  banner in `/antienchants list` and a reminder to joining admins, so it's never left on by accident.
- **On-demand purge** (`/antienchants purge <player|all>`): clean online inventories immediately
  after changing the rules, instead of waiting for each player's next join/click. Same pipeline
  as the automatic strips (bypass, messages, compensation, audit) and honours dry-run.
- **Villager trades**: cancels trades whose result is a book (any type) or carries a banned /
  over-cap enchantment.
- **Per-world**: `disabled-worlds` excludes worlds entirely.
- **Commands** (permission `antienchants.admin`): `/antienchants reload | list | check [player]
  | add <key> | remove <key> | cap <key> <level|off> | purge <player|all>` — `check` inspects the
  held item (or, with a name, every slot of another player's inventory, console included); `add`,
  `remove` and `cap` edit the config live and save it; `purge` cleans online inventories on demand.
- **Config auto-update**: new options are merged into your existing `config.yml` (with comments)
  on startup without touching values you edited.
- **bStats**: anonymous usage stats (opt-out in `plugins/bStats/config.yml`).

## Build

Gradle + Java 25, Paper 26.1.2.

```
./gradlew.bat shadowJar    # -> build/libs/AntiEnchants-1.2.0.jar (bStats shaded + relocated)
```

## Config

See [`src/main/resources/config.yml`](src/main/resources/config.yml). Keys accept short form
(`mending` -> `minecraft:mending`) or full form (`namespace:key`, including other plugins' keys).

## Design notes

- The click/creative strip is **deferred 1 tick**: mutating items mid-click breaks Bukkit's item
  tracking and can dupe (especially in creative).
- Bypass permission nodes are **registered explicitly with default `false`** — Bukkit defaults
  undefined permissions to OP, which would let every op silently bypass everything.
- Compensation is **player-context only** (inventory purge, pickup, fishing) and skipped in
  creative: loot/mob-drop strips have no owner and creative can spawn banned books, both farmable.
- The audit line is built on the main thread (it reads the player) and written async.
- Every action is config-gated; nothing is hardcoded.
