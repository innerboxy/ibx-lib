# Item tooltips

[简体中文](TOOLTIP.md) | **English**

[← Back to README](../README/README_en.md)

---

## 1. Overview

Adds your own lines to item tooltips (the box that pops up when hovering an item): registered per item and inserted right under the item name; long lines wrap automatically and every piece keeps its color; and a part can be split off as details shown only while Shift is held. There is one shared way to build lines and color them: body text is cream, and only keywords such as numbers, effect names and keys get their own colors.

| What you want to do | Class to use | See |
| --- | --- | --- |
| Build tooltip lines and colored fragments (passive, active, cooldown, numbers, effect names...) | `ibx.lib.tooltip.TooltipLines` (common code) | [4](#4-tooltiplines-reference) |
| Register tooltips per item | `ibx.lib.client.tooltip.ItemTooltips` (client only) | [2](#2-registering-tooltips) |
| Details shown only while Shift is held | `ItemTooltips.Registrar#add(item, lines, details)` | [3](#3-shift-folded-details) |
| Another part shown only while Ctrl is held (formulas, backstory…) | `ItemTooltips.Registrar#add(item, lines, details, extra)` | [3](#3-shift-folded-details) |

- `TooltipLines` only builds `Component`s and can be called on both sides; an item's own `appendHoverText` can use it too, so its colors match the registered tooltips.
- `ItemTooltips` uses the client's font and key state, so call it only from your client entry.

---

## 2. Registering tooltips

Call `ItemTooltips.register` with a registration callback in the constructor of your client mod entry (`@Mod(value = ..., dist = Dist.CLIENT)`); in the callback, call `registrar.add(item, lines)` once per item. `lines` receives the item stack and a `Consumer<Component>`, and `accept`s each line in order:

```java
@Mod(value = "examplemod", dist = Dist.CLIENT)
public class ExampleModClient {
    public ExampleModClient(ModContainer container) {
        ExampleTooltips.register();
    }
}
```

```java
public final class ExampleTooltips {
    private static final String KEY = "tooltip.examplemod.";

    public static void register() {
        ItemTooltips.register(registrar -> {
            // Passive: "Passive" + "Attacks have a 15% chance to give the target 5 seconds of Slowness II"
            registrar.add(ModItems.RUBY_SWORD, (stack, tooltip) -> {
                tooltip.accept(TooltipLines.passive());
                tooltip.accept(TooltipLines.body(KEY + "ruby_sword.passive",
                        TooltipLines.percent(0.15F),
                        TooltipLines.seconds(100),
                        TooltipLines.effect(MobEffects.SLOWNESS, 1)));
            });

            // Active: "Active · [Right-click]" + "Cooldown 8 seconds" + "Shoots 3 fireballs forward, each dealing 40% of your ⚔Attack Damage" (the attribute name comes with its icon)
            registrar.add(ModItems.RUBY_STAFF, (stack, tooltip) -> {
                tooltip.accept(TooltipLines.active(TooltipLines.RIGHT_CLICK));
                tooltip.accept(TooltipLines.cooldown(160));
                tooltip.accept(TooltipLines.body(KEY + "ruby_staff.active",
                        TooltipLines.value(3),
                        TooltipLines.percent(0.4F),
                        TooltipLines.attribute(Attributes.ATTACK_DAMAGE)));
            });
        });
    }
}
```

`zh_cn.json` and `en_us.json`:

```json
{
  "tooltip.examplemod.ruby_sword.passive": "攻击有%s概率给目标加持续%s秒的%s",
  "tooltip.examplemod.ruby_staff.active": "朝前方放出%s颗造成自身%s%s的火球"
}
```

```json
{
  "tooltip.examplemod.ruby_sword.passive": "Attacks have a %s chance to give the target %s seconds of %s",
  "tooltip.examplemod.ruby_staff.active": "Shoots %s fireballs forward, each dealing %s of your %s"
}
```

- **The callback runs late**: `register` only stores the callback; it is called the first time a tooltip is shown (after all items are registered), so the callback can read items directly.
- **Each item is registered once**: counted across all mods, and a duplicate throws when the table is built. `register` itself can be called several times, for example once from each class handling one kind of item.
- `add` takes an `ItemLike`, so a `DeferredItem`, an `Item` or a block all work. Numbers are passed as arguments, so changing them doesn't touch the lang files.

---

## 3. Shift-folded details

`registrar.add(item, lines, details)`: `lines` is always shown, `details` only while Shift is held. Replace the staff's registration from the previous section with:

```java
registrar.add(ModItems.RUBY_STAFF, (stack, tooltip) -> {
    tooltip.accept(TooltipLines.active(TooltipLines.RIGHT_CLICK));
    tooltip.accept(TooltipLines.cooldown(160));
    tooltip.accept(TooltipLines.body(KEY + "ruby_staff.active", TooltipLines.value(3), TooltipLines.percent(0.4F),
            TooltipLines.attribute(Attributes.ATTACK_DAMAGE)));
}, (stack, tooltip) -> {
    // Shown only while Shift is held
    tooltip.accept(TooltipLines.muted(KEY + "ruby_staff.detail", TooltipLines.key("key.sneak")));
    tooltip.accept(TooltipLines.flavor(KEY + "ruby_staff.flavor"));
});
```

- While Shift is not held, a "Hold [Shift] for details" line is added at the end of the tooltip (`TooltipLines.foldHint()`, lang key `tooltip.ibx_lib.fold_hint`); while it is held, that line is gone and the contents of `details` follow `lines`.
- If `details` adds no lines (for example, it checked the stack's state and wrote nothing), no hint line is shown.
- When the lines use fragments with icons (`attribute`, `effect`, `spell`, `item`, `block`, see [4](#4-tooltiplines-reference)), they fold too: without Shift only the icons are shown (effects keep their level, "🛡II") and the hint line is added as well; with Shift, icon + name. In that case the hint line appears even without `details`. Only the parts actually shown count: icons that are only in a hidden part (`details`, or `extra` while Ctrl is not held) don't. Fragments that found no icon and show only the name don't fold.
- It checks the Shift key on the keyboard, not whatever the sneak key is rebound to.

There can be a third part, shown only while Ctrl is held: `registrar.add(item, lines, details, extra)`; pass `ItemTooltips.NONE` for a part you don't need (e.g. Ctrl part only: `add(item, lines, ItemTooltips.NONE, extra)`).

- `extra` comes after the other two parts; while Ctrl is not held, a "Hold [Ctrl] for more" line is added at the end of the tooltip (`TooltipLines.ctrlHint()`, lang key `tooltip.ibx_lib.ctrl_hint`), which is left out if `extra` adds no lines.
- Shift and Ctrl work independently: hold both and both parts show. Icon fragments fold with Shift only. Both hint lines come at the end of the tooltip, the Shift one first.
- It checks the Ctrl keys on the keyboard (left or right).

---

## 4. TooltipLines reference

The methods of `ibx.lib.tooltip.TooltipLines` come in two kinds: **whole lines** go straight to `tooltip.accept`; **fragments** are arguments of a whole line and fill the `%s` in its lang text. All colors come from `ibx.lib.client.UiPalette`.

| Method | Produces | Color | Kind |
| --- | --- | --- | --- |
| `passive()` | "Passive", the title line of a passive trait | Accent (amber, `ACCENT`) | Line |
| `active(trigger)` | "Active · [Right-click]", the title line of an active ability | Accent | Line |
| `cooldown(ticks)` | "Cooldown 8 seconds", ticks converted to seconds | Muted (old wood, `MUTED`), the seconds in vanilla blue | Line |
| `foldHint()` | "Hold [Shift] for details", added by `ItemTooltips` automatically | Disabled (`DISABLED`), the key in the key color | Line |
| `ctrlHint()` | "Hold [Ctrl] for more", added by `ItemTooltips` automatically | Disabled, the key in the key color | Line |
| `blank()` | An empty line to split the tooltip into blocks | — | Line |
| `body(key, args...)` | A line of body text | Body (cream, `BODY`) | Line |
| `muted(key, args...)` | A line of secondary notes: counts, conditions and the like | Muted | Line |
| `flavor(key, args...)` | A line of flavor text: backstory and other words that explain no mechanics | Muted, italic | Line |
| `value(number)` | A number with at most one decimal, integers without a decimal point | Vanilla blue | Fragment |
| `percent(ratio)` | A ratio written as a percentage: `0.06` → `6%` | Vanilla blue | Fragment |
| `seconds(ticks)` | Ticks converted to seconds: `100` → `5`, `30` → `1.5`; the word "seconds" goes in the lang file | Vanilla blue | Fragment |
| `effect(effect, amplifier)` | Effect icon + effect name + level: "🛡Absorption II" | Positive for beneficial, negative for harmful, body for neutral | Fragment |
| `spell(spell)` | Spell icon + spell name | Spell (`SPELL`) | Fragment |
| `item(item)` / `item(stack)` / `item(id)` | Item icon + item name: pass the item, an item stack or the registry id (`Identifier` or string) | The item's rarity color, same as the tooltip's first line | Fragment |
| `block(block)` / `block(id)` | Block icon + block name: pass the block or the registry id | Same as items (the block item's rarity); blocks without an item (fire, portals) in vanilla white | Fragment |
| `entity(type)` / `entity(id)` | An entity name: pass the `EntityType` or the registry id | Negative for monsters, positive for animals / water / ambient mobs, body for the rest | Fragment |
| `attribute(attribute)` / `attribute(id)` | Attribute icon + attribute name: "❤Max Health" | Icon in its own colors, name positive (`POSITIVE`) | Fragment |
| `biome(key)` / `biome(id)` | A biome name: "Plains" | Body | Fragment |
| `dimension(key)` / `dimension(id)` | A dimension name: "The Nether" | Body | Fragment |
| `colored(text, color)` / `colored(key, color, args...)` | Any text in the given color | The `UiPalette` semantic color passed in | Fragment |
| `key(keybind)` | A key in square brackets: "[Shift]" | Key (`KEY`) | Fragment |
| `keyName(name)` | A fixed key name in square brackets | Key | Fragment |

- **Lang keys**: `body`, `muted` and `flavor` take the full lang key (`tooltip.examplemod.ruby_sword.passive`). Text in the lang string that isn't an argument takes the line's color; arguments keep their own colors.
- **Numbers**: vanilla attribute blue (`ChatFormatting.BLUE`), the same color as vanilla's "+8 Armor".
- **Effects**: `amplifier` is the amplifier (starting at 0), and the level is always written (level I too); up to level 10 it uses vanilla roman numerals (`enchantment.level.N`), above that arabic numbers.
- **Items**: `item(Items.ROTTEN_FLESH)` uses the same color as the item name on the tooltip's first line (by rarity); with an item stack, enchanted stacks go up one rarity and renamed stacks show the new name (italic). To refer to another mod's item without depending on it, pass the id: `item("othermod:ruby")`; if that mod isn't installed and the id isn't found, the id itself is written (disabled color).
- **Entities / attributes**: `entity(EntityType.ZOMBIE)` and `attribute(Attributes.ATTACK_DAMAGE)` also take ids (`entity("othermod:wraith")`, `attribute("othermod:mana")`); an id that isn't found is written as is (disabled color), same as items. The entity color follows its category (`MobCategory`): `MONSTER` negative, `MISC` (villagers, iron golems, projectiles and the like) body, everything else positive.
- **Small icons**: `attribute`, `effect`, `spell`, `item` and `block` put a small icon before the name, on the client only: always white with no shadow (not tinted by the line), bottom-aligned with the text, followed by a 1-pixel gap. In lines registered through `ItemTooltips`, only the icon shows until Shift is held (see [3](#3-shift-folded-details)). In plain text (JEI's tooltip search, the narrator) an icon leaves no text of its own; a folded icon stands in as the name it replaced, so items can still be found by names like "Max Health" without Shift.

  | Fragment | Icon | Drawn size | If missing |
  | --- | --- | --- | --- |
  | `attribute` | The attribute panel's icon (looked up the same way as the [attribute panel](ATTRIBUTE_en.md)) | Native 9×9 | Placeholder icon |
  | `effect` | Vanilla mob effect icon `textures/mob_effect/<path>.png` (18×18) | Halved, 9×9 | Name only |
  | `spell` | Spell icon `textures/spell/<path>.png` (16×16, same as the spell wheel) | Halved, 8×8 | Spell placeholder icon |
  | `item` / `block` | Item texture `textures/item/<path>.png`, else `textures/block/<path>.png` | Halved, 8×8 | Name only (3D models and items whose texture name differs from the id get no icon) |

  Icons use vanilla's inline atlas sprites (`Component.object`), which vanilla always draws at 8×8; lib redraws sprites under the paths `hud/icon/`, `mob_effect/`, `spell/`, `item/` and `block/` scaled down by a whole factor with a 1-pixel gap after them, so pixels stay even at any GUI scale. Other inline sprites of these kinds (from other mods, `/tellraw`) are drawn the same way. Spell icons aren't in any atlas by themselves; lib's `assets/minecraft/atlases/gui.json` adds every mod's `textures/spell` to the gui atlas (sprite id `<namespace>:spell/<path>`).
- **Blocks**: `block(Blocks.OBSIDIAN)`, `block("othermod:ruby_ore")`; an id that isn't found is written as is (disabled color), same as items.
- **Biomes / dimensions**: `biome(Biomes.PLAINS)`, `dimension(Level.NETHER)`, ids work too. Biome lang keys are vanilla's `biome.<namespace>.<path>`; vanilla has no dimension translations, so the lang key is `dimension.<namespace>.<path>` — lib provides the three vanilla dimensions, add your own dimensions to your own lang file. Without a translation, an English name is built from the path (`crimson_forest` → `Crimson Forest`). Biomes and dimensions are registered by datapacks, so their existence isn't checked.
- **Custom colors**: for keywords without a dedicated method, use `colored`, e.g. "Lifesteal" `colored(KEY + "lifesteal", UiPalette.HEAL)`; pass only `UiPalette` semantic names, never raw color values.
- **Keys**: `key("key.sneak")` takes a key binding name (vanilla or registered by a mod) and shows the key the player has bound right now, following any rebinding; `keyName("Shift")` is for fixed keys that don't go through key bindings.
- **Triggers**: `TooltipLines.RIGHT_CLICK` ([Right-click]), `TooltipLines.LEFT_CLICK` ([Left-click]), `TooltipLines.HOLD_RIGHT_CLICK` (Hold [Right-click], active while held), `TooltipLines.HOLD_LEFT_CLICK` (Hold [Left-click], active while held), `TooltipLines.SNEAK` (Press [Shift], fires once), `TooltipLines.HOLD_SNEAK` (Hold [Shift], active while held), `TooltipLines.SNEAK_LEFT_CLICK` ([Shift] + [Left-click]) and `TooltipLines.SNEAK_RIGHT_CLICK` ([Shift] + [Right-click]), with lang keys `tooltip.ibx_lib.trigger.<trigger>`, where every key is key-colored and bracketed: `%1$s` is "[Shift]", `%2$s` is "[Left-click]", `%3$s` is "[Right-click]" (key names in `tooltip.ibx_lib.key.*`). If none fits, build the trigger part yourself with `active(Component)`; key colors inside it are kept.

---

## 5. Notes

- Tooltip lines wider than 180 GUI pixels (about 20 Chinese characters) wrap automatically, split the vanilla way: at spaces where possible, and between any two Chinese characters. No need to break lines yourself in lang files.
- Registered lines go right under the item name, before vanilla attribute lines; vanilla lines and lines added by the item's own `appendHoverText` are left alone and come after the registered lines.
- Lang keys are best named `tooltip.<modid>.<item id>.passive`, with `passive_2` for a second passive and `active` for an active ability.
- In Chinese lang strings, put no space between `%s` and the surrounding text ("攻击有%s概率"); in English, use spaces as usual.
- Put an attribute's value before its name: "8 Max Health", "40% of your Attack Damage", not "Max Health 8"; without Shift, when only the icon remains, it reads "8 ❤". Write sentences around icon fragments as usual; when folded, the icon simply takes the name's place.
