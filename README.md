# AntiEnchants

Plugin de Paper que **bloquea encantamientos no deseados en todo el servidor**. Sucesor del antiguo
`AntiMending`, generalizado de un solo encantamiento hardcodeado a una **lista configurable** + control
de tradeos de aldeano, exclusión de mundos y recarga en caliente.

## Qué hace

- **Purga global de encantamientos** (default: `mending`) de cualquier ítem: inventarios (al entrar /
  abrir / clickear), ítems recogidos, pesca, loot generado y drops de mobs.
- **Bloquea el efecto de mending** (`PlayerItemMendEvent`): la XP nunca repara, aunque algo se cuele.
- **Maldiciones**: `ban-all-curses` veta todas las maldiciones de golpe (vanilla + plugins).
- **Tradeos de aldeano**: cancela trades cuyo resultado sea un libro (cualquier tipo) o que lleve un
  encantamiento bloqueado.
- **Por mundo**: `disabled-worlds` excluye mundos.
- **Recarga en caliente**: `/antienchants reload` (perm. `antienchants.admin`).

## Build

Gradle + Java 25, Paper 26.1.2.

```
./gradlew.bat jar    # → build/libs/AntiEnchants-1.0.0.jar
```

## Config

Ver [`src/main/resources/config.yml`](src/main/resources/config.yml). Las keys admiten forma corta
(`mending` → `minecraft:mending`) o completa (`namespace:key`, incluidas las de otros plugins).

## Notas de diseño

- El strip en click/creativo se **difiere 1 tick**: mutar ítems mid-click rompe el tracking de Bukkit
  y puede dupear (sobre todo en creativo).
- Toda acción está gateada por config; nada está hardcodeado.
