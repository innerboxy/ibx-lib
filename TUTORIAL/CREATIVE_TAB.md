# 创造栏子栏

**简体中文** | [English](CREATIVE_TAB_en.md)

[← 返回首页](../README.md)

---

## 1. 简介

把一个创造栏的物品分成几组:打开这个创造栏时,创造模式背包左边多一列图标,点一组只显示这一组的物品,再点一次回到整个创造栏。指着图标会显示组名。

- **只是分组**:创造栏本身照常注册。搜索栏、JEI 这些按创造栏取物品的,拿到的还是全部物品,顺序就是各组依次排下来。
- **记住选的组**:每个创造栏选了哪一组分别记着,换到别的创造栏再回来、关掉背包再开,都还是那一组;重开游戏后回到整个创造栏。
- **一列最多 6 个**:多了用滚轮在这一列上翻,上下有小三角提示还有。
- **没物品的组不显示**:一组里没有物品(比如被功能开关关掉)时不显示它的图标,所有组都没物品时整列不显示。
- **放不下就不显示**:屏幕太窄、界面左边放不下这一列时不显示,物品格子回到整个创造栏。
- 手上拿着物品点在这一列上不会把物品扔出去;装了 JEI 时,书签栏会让开这一列。

---

## 2. 用法

`ibx.lib.creativetab.CreativeSubTabs` 实现了 `CreativeModeTab.DisplayItemsGenerator`,直接当创造栏的 `displayItems` 用。注册创造栏时用 `DeferredRegister#register` 带 id 的那个重载,把 id 交给 `CreativeSubTabs.of`,再按显示顺序一组一组 `add`:

| 方法 | 说明 |
| --- | --- |
| `CreativeSubTabs.of(tabId)` | 给 id 为 `tabId` 的创造栏建子栏,返回它自己,接着 `add`。在注册这个创造栏时调用,一个创造栏调一次。 |
| `add(title, icon, items)` | 加一组,排在已有的后面。`title` 是指着图标时显示的名字(`Component`);`icon` 是图标(`Supplier<ItemStack>`,第一次画的时候才取);`items` 是这一组的物品,写法和创造栏的 `displayItems` 一样。 |
| `CreativeSubTabs.get(tabId)` | 某个创造栏的子栏,没建就是 `null`。 |
| `subTabs()` | 全部组,按 `add` 的顺序;每组有 `title()`、`icon()`、`items()`(上次生成创造栏内容时这一组放进去的物品)。 |

```java
public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "examplemod");

// 一个总创造栏,分成「武器」「材料」两组
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

不用另外注册什么:lib 的客户端看到选中的创造栏建了子栏,就自己画出那一列。

---

## 3. 注意

- 别的模组用 `BuildCreativeModeTabContentsEvent` 往这个创造栏加的物品不属于任何一组,只在整个创造栏里看得到;用这个事件删掉的物品,组里也跟着不显示。
- 一件物品只能放进一组:同原版,整个创造栏里出现重复的物品会报错。
- `TabVisibility.SEARCH_TAB_ONLY` 的物品只进搜索栏,不算进组里。
- 只对普通的物品分类创造栏有效;搜索、背包、快捷栏这几页不显示子栏。
