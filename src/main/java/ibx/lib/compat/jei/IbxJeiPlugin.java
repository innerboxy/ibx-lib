package ibx.lib.compat.jei;

import ibx.lib.IbxLib;
import ibx.lib.client.attribute.AttributePanel;
import ibx.lib.client.creativetab.CreativeSubTabStrip;
import ibx.lib.compat.curios.client.CuriosScreens;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import ibx.lib.registry.TagRegistry;
import ibx.lib.spell.SpellContainer;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI 联动（装了 JEI 才加载，JEI 按 {@link JeiPlugin} 注解找到它）。
 *
 * <p>信息页：重盾标签 {@code #c:tools/heavy_shield} 里的物品（lib 的双手盾自动进这个标签）加一页「盾击」说明，
 * 语言键 {@code jei.ibx_lib.info.heavy_shield.*}；施法物品和法术容器加一页施法说明（右键施法、法术轮盘、按住 / 切换），
 * 语言键 {@code jei.ibx_lib.info.spells.*}。</p>
 *
 * <p>界面让位：把背包框外面额外占的地方报给 JEI，让左边的书签栏让开。</p>
 * <ul>
 *   <li>背包 / 创造模式背包左边的属性面板（开着时）；</li>
 *   <li>创造模式背包左边的创造栏子栏（选中的创造栏有子栏时）；</li>
 *   <li>Curios 饰品栏界面左边的饰品槽：Curios 自己给 JEI 的注册是注释掉的，书签栏会盖住饰品槽，这里替它报。</li>
 * </ul>
 */
@JeiPlugin
public final class IbxJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.fromNamespaceAndPath(IbxLib.MODID, "jei_plugin");
    private static final String INFO = "jei." + IbxLib.MODID + ".info.";

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    /** 重盾（{@code #c:tools/heavy_shield}）里的物品加一页信息，讲盾击怎么用。 */
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<ItemStack> heavyShields = new ArrayList<>();
        BuiltInRegistries.ITEM.getTagOrEmpty(TagRegistry.TOOLS_HEAVY_SHIELD).forEach(item -> heavyShields.add(item.value().getDefaultInstance()));
        if (!heavyShields.isEmpty()) {
            registration.addItemStackInfo(heavyShields,
                    Component.translatable(INFO + "heavy_shield.bash"),
                    Component.translatable(INFO + "heavy_shield.cooldown"));
        }
        registerSpellInfo(registration);
    }

    /**
     * 法术相关的物品加一页信息，讲怎么施法、法术轮盘怎么用，按物品分三种：
     * 能施法又自带法术的（法杖、法典）、只能施法的（{@code #ibx_lib:can_cast_spell} 里但默认不带法术）、
     * 只提供法术的（带法术但不能拿来施法，比如提供法术的饰品、盔甲）。按物品的默认物品栈判断。
     */
    private static void registerSpellInfo(IRecipeRegistration registration) {
        List<ItemStack> castingContainers = new ArrayList<>();
        List<ItemStack> castingOnly = new ArrayList<>();
        List<ItemStack> containersOnly = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = item.getDefaultInstance();
            boolean casts = stack.is(TagRegistry.CAN_CAST_SPELL);
            boolean provides = !SpellContainer.spellsOf(stack).isEmpty();
            if (casts && provides) {
                castingContainers.add(stack);
            } else if (casts) {
                castingOnly.add(stack);
            } else if (provides) {
                containersOnly.add(stack);
            }
        }
        Component provide = Component.translatable(INFO + "spells.provide");
        Component wheel = Component.translatable(INFO + "spells.wheel", Component.literal("[").append(Component.keybind("key.ibx_lib.spell_wheel")).append("]"));
        Component cast = Component.translatable(INFO + "spells.cast");
        Component modes = Component.translatable(INFO + "spells.modes");
        if (!castingContainers.isEmpty()) {
            registration.addItemStackInfo(castingContainers, provide, wheel, cast, modes);
        }
        if (!castingOnly.isEmpty()) {
            registration.addItemStackInfo(castingOnly, cast, modes, wheel);
        }
        if (!containersOnly.isEmpty()) {
            registration.addItemStackInfo(containersOnly, provide, wheel, Component.translatable(INFO + "spells.need_caster"));
        }
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGenericGuiContainerHandler(AbstractContainerScreen.class, new IGuiContainerHandler<AbstractContainerScreen<?>>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(AbstractContainerScreen<?> screen) {
                List<Rect2i> areas = new ArrayList<>(CuriosScreens.slotAreas(screen));
                AttributePanel.extraArea(screen).ifPresent(areas::add);
                CreativeSubTabStrip.extraArea(screen).ifPresent(areas::add);
                return areas;
            }
        });
    }
}
