# AntiEnchants

Paper plugin that **blocks unwanted enchantments server-wide**. Successor to the old `AntiMending`,
generalised from a single hardcoded enchantment to a **configurable blocklist** plus level caps,
compensation, permission bypasses, villager book-trade control, per-world exclusions and a hot reload.

## What it does

- **Global enchantment purge** (default: `mending`) from any item: inventories (on join / open /
  click), picked-up items, fishing, generated loot and mob drops.
- **Blocks at the source**: removes banned enchantments (and applies caps) on the enchanting-table
  result and the anvil result preview, instead of letting the player pay and purging afterwards.
- **Level caps** (`level-caps`): cap an enchantment instead of banning it — `sharpness: 3` turns
  Sharpness V into Sharpness III automatically, everywhere the purge runs.
- **Item whitelist** (`exempt-items`): item types the plugin never touches. Material names with
  `*` wildcards (e.g. `"*_SWORD"`, `"BOW"`).
- **Compensation** (`compensation`, off by default): when a banned enchantment is stripped from a
  player's item, grant configurable items back (e.g. XP bottles, emeralds), globally or per
  enchantment. Player-context strips only, never in creative (farmable otherwise).
- **Permission bypasses**: `antienchants.bypass.<key>` (e.g. `antienchants.bypass.mending`) lets
  donor ranks / staff keep specific enchantments; `antienchants.bypass.*` bypasses everything.
  Nodes are registered with default `false`, so ops don't bypass by accident.
- **Player feedback** (`messages`, MiniMessage): players are told when their items are stripped,
  capped or compensated (empty string = silent, like before).
- **Empty-book conversion**: a stripped enchanted book with nothing left becomes a normal book.
- **Blocks mending's effect** (`PlayerItemMendEvent`): XP never repairs, even if an item slips through.
- **Curses**: `ban-all-curses` blocks every curse at once (vanilla + plugins).
- **Villager trades**: cancels trades whose result is a book (any type) or carries a banned /
  over-cap enchantment.
- **Per-world**: `disabled-worlds` excludes worlds.
- **Commands** (permission `antienchants.admin`): `/antienchants reload | list | check | add <key>
  | remove <key>` — `check` inspects the held item, `add`/`remove` edit the blocklist live and save it.
- **Config auto-update**: new options are merged into your existing `config.yml` (with comments)
  on startup without touching values you edited.

## Build

Gradle + Java 25, Paper 26.1.2.

```
./gradlew.bat jar    # -> build/libs/AntiEnchants-1.1.0.jar
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
- Every action is config-gated; nothing is hardcoded.
