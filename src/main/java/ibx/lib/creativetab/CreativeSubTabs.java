package ibx.lib.creativetab;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 创造栏子栏：把一个创造栏的物品分成几组。打开这个创造栏时，创造模式背包左边多一列图标，点一组只显示这一组，再点一次回到整个创造栏
 * （界面在客户端的 {@code ibx.lib.client.creativetab.CreativeSubTabStrip}）。
 *
 * <p>用法：创造栏的 {@code displayItems} 换成这个类，按显示顺序 {@link #add} 每一组。整个创造栏（搜索栏、JEI 这些按创造栏取物品的）
 * 照样是全部物品，顺序就是各组依次排下来。</p>
 * <pre>{@code
 * TABS.register("equipment", id -> CreativeModeTab.builder()
 *         .title(...)
 *         .icon(...)
 *         .displayItems(CreativeSubTabs.of(id)
 *                 .add(Component.translatable("..."), () -> new ItemStack(...), (parameters, output) -> {
 *                     output.accept(...);
 *                 })
 *                 .add(...))
 *         .build());
 * }</pre>
 * <ul>
 *   <li>别的模组用 {@code BuildCreativeModeTabContentsEvent} 往这个创造栏加的物品不属于任何一组，只在整个创造栏里看得到。</li>
 *   <li>一组里没有物品（被功能开关关掉之类）时不显示它的图标。</li>
 * </ul>
 */
public final class CreativeSubTabs implements CreativeModeTab.DisplayItemsGenerator {
    private static final Map<Identifier, CreativeSubTabs> BY_TAB = new ConcurrentHashMap<>();

    private final List<SubTab> subTabs = new ArrayList<>();

    private CreativeSubTabs() {}

    /** 给 id 为 {@code tabId} 的创造栏建子栏，在注册这个创造栏时调用。 */
    public static CreativeSubTabs of(Identifier tabId) {
        CreativeSubTabs subTabs = new CreativeSubTabs();
        BY_TAB.put(tabId, subTabs);
        return subTabs;
    }

    /** 创造栏的子栏，没建就是 null。 */
    public static @Nullable CreativeSubTabs get(Identifier tabId) {
        return BY_TAB.get(tabId);
    }

    /**
     * 加一组，排在已有的后面。
     *
     * @param title 指着图标时显示的名字
     * @param icon  图标（第一次画的时候才取）
     * @param items 这一组的物品，写法同创造栏的 {@code displayItems}
     */
    public CreativeSubTabs add(Component title, Supplier<ItemStack> icon, CreativeModeTab.DisplayItemsGenerator items) {
        this.subTabs.add(new SubTab(title, icon, items));
        return this;
    }

    public List<SubTab> subTabs() {
        return Collections.unmodifiableList(this.subTabs);
    }

    @Override
    public void accept(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output) {
        for (SubTab subTab : this.subTabs) {
            subTab.build(parameters, output);
        }
    }

    /** 一组：名字、图标，和上次生成创造栏内容时放进去的物品。 */
    public static final class SubTab {
        private final Component title;
        private final Supplier<ItemStack> iconSupplier;
        private final CreativeModeTab.DisplayItemsGenerator generator;
        private @Nullable ItemStack icon;
        private volatile List<ItemStack> items = List.of();

        private SubTab(Component title, Supplier<ItemStack> icon, CreativeModeTab.DisplayItemsGenerator generator) {
            this.title = title;
            this.iconSupplier = icon;
            this.generator = generator;
        }

        public Component title() {
            return this.title;
        }

        public ItemStack icon() {
            if (this.icon == null) {
                this.icon = this.iconSupplier.get();
            }
            return this.icon;
        }

        /** 上次生成创造栏内容时这一组放进去的物品，按放的顺序；只进搜索栏的、被功能开关关掉的不算。 */
        public List<ItemStack> items() {
            return this.items;
        }

        /** 物品照常交给创造栏，同时记下这一组有哪些。 */
        private void build(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output) {
            List<ItemStack> collected = new ArrayList<>();
            this.generator.accept(parameters, (stack, visibility) -> {
                output.accept(stack, visibility);
                if (visibility != CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY && stack.getItem().isEnabled(parameters.enabledFeatures())) {
                    collected.add(stack);
                }
            });
            this.items = List.copyOf(collected);
        }
    }
}
