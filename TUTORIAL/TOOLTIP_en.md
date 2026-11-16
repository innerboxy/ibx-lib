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

            // Active: "Active · [Right-click]" + "Cooldown 8 seconds" + "Shoots 3 fireballs forward, each dealing 40% of your attack damage"
            registrar.add(ModItems.RUBY_STAFF, (stack, tooltip) -> {
                tooltip.accept(TooltipLines.active(TooltipLines.RIGHT_CLICK));
                tooltip.accept(TooltipLines.cooldown(160));
                tooltip.accept(TooltipLines.body(KEY + "ruby_staff.active",
                        TooltipLines.value(3),
                        TooltipLines.percent(0.4F)));
            });
        });
    }
}
```

`zh_cn.json` and `en_us.json`:

```json
{
  "tooltip.examplemod.ruby_sword.passive": "攻击有%s概率给目标加持续%s秒的%s",
  "tooltip.examplemod.ruby_staff.active": "朝前方放出%s颗造成自身攻击伤害%s的火球"
}
```

```json
{
  "tooltip.examplemod.ruby_sword.passive": "Attacks have a %s chance to give the target %s seconds of %s",
  "tooltip.examplemod.ruby_staff.active": "Shoots %s fireballs forward, each dealing %s of your attack damage"
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
    tooltip.accept(TooltipLines.body(KEY + "ruby_staff.active", TooltipLines.value(3), TooltipLines.percent(0.4F)));
}, (stack, tooltip) -> {
    // Shown only while Shift is held
    tooltip.accept(TooltipLines.muted(KEY + "ruby_staff.detail", TooltipLines.key("key.sneak")));
    tooltip.accept(TooltipLines.flavor(KEY + "ruby_staff.flavor"));
});
```

- While Shift is not held, a "Hold [Shift] for details" line follows `lines` (`TooltipLines.foldHint()`, lang key `tooltip.ibx_lib.fold_hint`); while it is held, that line is replaced by the contents of `details`.
- If `details` adds no lines (for example, it checked the stack's state and wrote nothing), no hint line is shown.
- It checks the Shift key on the keyboard, not whatever the sneak key is rebound to.

---

## 4. TooltipLines reference

The methods of `ibx.lib.tooltip.TooltipLines` come in two kinds: **whole lines** go straight to `tooltip.accept`; **fragments** are arguments of a whole line and fill the `%s` in its lang text. All colors come from `ibx.lib.client.UiPalette`.

| Method | Produces | Color | Kind |
| --- | --- | --- | --- |
| `passive()` | "Passive", the title line of a passive trait | Accent (amber, `ACCENT`) | Line |
| `active(trigger)` | "Active · [Right-click]", the title line of an active ability | Accent | Line |
| `cooldown(ticks)` | "Cooldown 8 seconds", ticks converted to seconds | Muted (old wood, `MUTED`), the seconds in vanilla blue | Line |
| `foldHint()` | "Hold [Shift] for details", added by `ItemTooltips` automatically | Disabled (`DISABLED`), the key in the key color | Line |
| `body(key, args...)` | A line of body text | Body (cream, `BODY`) | Line |
| `muted(key, args...)` | A line of secondary notes: counts, conditions and the like | Muted | Line |
| `flavor(key, args...)` | A line of flavor text: backstory and other words that explain no mechanics | Muted, italic | Line |
| `value(number)` | A number with at most one decimal, integers without a decimal point | Vanilla blue | Fragment |
| `percent(ratio)` | A ratio written as a percentage: `0.06` → `6%` | Vanilla blue | Fragment |
| `seconds(ticks)` | Ticks converted to seconds: `100` → `5`, `30` → `1.5`; the word "seconds" goes in the lang file | Vanilla blue | Fragment |
| `effect(effect, amplifier)` | Effect name + level: "Slowness II" | Positive for beneficial, negative for harmful, body for neutral | Fragment |
| `spell(spell)` | A spell name | Spell (`SPELL`) | Fragment |
| `item(item)` / `item(stack)` / `item(id)` | An item name: pass the item, an item stack or the registry id (`Identifier` or string) | The item's rarity color, same as the tooltip's first line | Fragment |
| `key(keybind)` | A key in square brackets: "[Shift]" | Key (`KEY`) | Fragment |
| `keyName(name)` | A fixed key name in square brackets | Key | Fragment |

- **Lang keys**: `body`, `muted` and `flavor` take the full lang key (`tooltip.examplemod.ruby_sword.passive`). Text in the lang string that isn't an argument takes the line's color; arguments keep their own colors.
- **Numbers**: vanilla attribute blue (`ChatFormatting.BLUE`), the same color as vanilla's "+8 Armor".
- **Effects**: `amplifier` is the amplifier (starting at 0), and the level is always written (level I too); up to level 10 it uses vanilla roman numerals (`enchantment.level.N`), above that arabic numbers.
- **Items**: `item(Items.ROTTEN_FLESH)` uses the same color as the item name on the tooltip's first line (by rarity); with an item stack, enchanted stacks go up one rarity and renamed stacks show the new name (italic). To refer to another mod's item without depending on it, pass the id: `item("othermod:ruby")`; if that mod isn't installed and the id isn't found, the id itself is written (disabled color).
- **Keys**: `key("key.sneak")` takes a key binding name (vanilla or registered by a mod) and shows the key the player has bound right now, following any rebinding; `keyName("Shift")` is for fixed keys that don't go through key bindings.
- **Triggers**: `TooltipLines.RIGHT_CLICK` ([Right-click]), `TooltipLines.LEFT_CLICK` ([Left-click]), `TooltipLines.SNEAK` (Press [Shift], fires once), `TooltipLines.HOLD_SNEAK` (Hold [Shift], active while held), `TooltipLines.SNEAK_LEFT_CLICK` ([Shift] + [Left-click]) and `TooltipLines.SNEAK_RIGHT_CLICK` ([Shift] + [Right-click]), with lang keys `tooltip.ibx_lib.trigger.<trigger>`, where every key is key-colored and bracketed: `%1$s` is "[Shift]", `%2$s` is "[Left-click]", `%3$s` is "[Right-click]" (key names in `tooltip.ibx_lib.key.*`). If none fits, build the trigger part yourself with `active(Component)`; key colors inside it are kept.

---

## 5. Notes

- Tooltip lines wider than 180 GUI pixels (about 20 Chinese characters) wrap automatically, split the vanilla way: at spaces where possible, and between any two Chinese characters. No need to break lines yourself in lang files.
- Registered lines go right under the item name, before vanilla attribute lines; vanilla lines and lines added by the item's own `appendHoverText` are left alone and come after the registered lines.
- Lang keys are best named `tooltip.<modid>.<item id>.passive`, with `passive_2` for a second passive and `active` for an active ability.
- In Chinese lang strings, put no space between `%s` and the surrounding text ("攻击有%s概率"); in English, use spaces as usual.
