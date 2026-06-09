# AntiEnchants

Paper plugin that **blocks unwanted enchantments server-wide**. Successor to the old `AntiMending`,
generalised from a single hardcoded enchantment to a **configurable blocklist** plus villager
book-trade control, per-world exclusions and a hot reload.

## What it does

- **Global enchantment purge** (default: `mending`) from any item: inventories (on join / open /
  click), picked-up items, fishing, generated loot and mob drops.
- **Blocks mending's effect** (`PlayerItemMendEvent`): XP never repairs, even if an item slips through.
- **Curses**: `ban-all-curses` blocks every curse at once (vanilla + plugins).
- **Villager trades**: cancels trades whose result is a book (any type) or carries a banned enchantment.
- **Per-world**: `disabled-worlds` excludes worlds.
- **Hot reload**: `/antienchants reload` (permission `antienchants.admin`).

## Build

Gradle + Java 25, Paper 26.1.2.

```
./gradlew.bat jar    # -> build/libs/AntiEnchants-1.0.0.jar
```

## Config

See [`src/main/resources/config.yml`](src/main/resources/config.yml). Keys accept short form
(`mending` -> `minecraft:mending`) or full form (`namespace:key`, including other plugins' keys).

## Design notes

- The click/creative strip is **deferred 1 tick**: mutating items mid-click breaks Bukkit's item
  tracking and can dupe (especially in creative).
- Every action is config-gated; nothing is hardcoded.
