# Creative sub-tabs

[简体中文](CREATIVE_TAB.md) | **English**

[← Back to README](../README/README_en.md)

---

## 1. Overview

Splits the items of one creative tab into groups. When that tab is open, a column of icons appears on the left of the creative inventory; click a group to show only its items, and click it again to go back to the whole tab. Hovering an icon shows the group's name.

- **Grouping only**: the creative tab itself is registered as usual. The search tab, JEI and anything else that reads items by creative tab still get every item, in the order the groups list them.
- **Remembers the group**: the selected group is kept per creative tab, so switching to another tab and back, or closing and reopening the inventory, keeps it; after restarting the game it goes back to the whole tab.
- **Up to 6 per column**: with more, scroll the wheel over the column; small arrows show there are more above or below.
- **Empty groups are hidden**: a group with no items (for example, disabled by a feature flag) shows no icon, and the column is hidden when every group is empty.
- **Hidden when it doesn't fit**: if the screen is too narrow to fit the column left of the inventory, it isn't shown and the item grid goes back to the whole tab.
- Clicking the column while holding an item doesn't drop the item; with JEI installed, its bookmark panel keeps clear of the column.

---

## 2. Usage

`ibx.lib.creativetab.CreativeSubTabs` implements `CreativeModeTab.DisplayItemsGenerator` and is used directly as the tab's `displayItems`. Register the tab with the overload of `DeferredRegister#register` that passes the id, hand the id to `CreativeSubTabs.of`, then `add` the groups in display order:

| Method | Description |
| --- | --- |
| `CreativeSubTabs.of(tabId)` | Creates the sub-tabs of the creative tab with id `tabId` and returns them for chaining `add`. Call it while registering that tab, once per tab. |
| `add(title, icon, items)` | Adds a group after the existing ones. `title` is the name shown when hovering the icon (`Component`); `icon` is the icon (`Supplier<ItemStack>`, read the first time it is drawn); `items` are the group's items, written the same way as a tab's `displayItems`. |
| `style(style)` | Look of the sub-tab column: `CreativeSubTabs.Style.WOOD` (default, this mod's wooden UI) or `Style.VANILLA` (light gray panel and sunken item slots like vanilla container screens; the selected cell is raised). |
| `CreativeSubTabs.get(tabId)` | The sub-tabs of a creative tab, or `null` if it has none. |
| `subTabs()` | All groups in `add` order; each has `title()`, `icon()` and `items()` (the items the group put in the last time the tab's contents were built). |

```java
public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "examplemod");

// One main creative tab split into "Weapons" and "Materials"
public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", id -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.examplemod.main"))
        .icon(() -> new ItemStack(ModItems.RUBY_STAFF.get()))
        .displayItems(CreativeSubTabs.of(id)
                .add(Component.translatable("itemGroup.examplemod.main.weapons"), () -> new ItemStack(ModItems.RUBY_SWORD.get()), (parameters, output) -> {
                    output.accept(ModItems.RUBY_STAFF.get());
                    output.accept(ModItems.RUBY_SWORD.get());
                })
                .add(Component.translatable("itemGroup.examplemod.main.materials"), () -> new ItemStack(ModItems.RUBY.get()), (parameters, output) -> {
                    output.accept(ModItems.RUBY.get());
                }))
        .build());
```

Nothing else needs registering: when the selected creative tab has sub-tabs, the lib's client draws the column by itself.

---

## 3. Notes

- Items other mods add to this tab with `BuildCreativeModeTabContentsEvent` belong to no group and only show in the whole tab; items removed with that event disappear from their group too.
- An item can only go into one group: as in vanilla, a duplicate item anywhere in the tab throws an error.
- Items added with `TabVisibility.SEARCH_TAB_ONLY` only go to the search tab and don't count toward a group.
- Only works on ordinary item category tabs; the search, inventory and hotbar pages never show sub-tabs.
