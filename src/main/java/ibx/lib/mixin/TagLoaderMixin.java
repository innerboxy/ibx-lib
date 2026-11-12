package ibx.lib.mixin;

import ibx.lib.IbxLib;
import ibx.lib.registry.AutoItemTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagLoader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 按物品类自动补物品标签（规则见 {@link AutoItemTags}）：读完数据包里的物品标签后，把对应物品插到各标签的最前面。
 * 是真的标签成员（同步到客户端，指令、JEI、别的模组都认得），各子模组的 data 里不用再写。
 *
 * <p>插在最前面是为了让数据包还能用 {@code "remove"} 把某件物品去掉；{@code "replace": true} 去不掉（替换发生在这之前）。</p>
 */
@Mixin(TagLoader.class)
public abstract class TagLoaderMixin {
    @Shadow
    @Final
    private String directory;

    @Inject(method = "load", at = @At("RETURN"))
    private void ibx_lib$addAutoItemTags(ResourceManager resourceManager,
                                             CallbackInfoReturnable<Map<Identifier, List<TagLoader.EntryWithSource>>> cir) {
        if (!this.directory.equals(Registries.tagsDirPath(Registries.ITEM))) {
            return;
        }
        Map<Identifier, List<TagLoader.EntryWithSource>> added = new HashMap<>();
        BuiltInRegistries.ITEM.forEach(item -> AutoItemTags.forEach(item, tag ->
                added.computeIfAbsent(tag.location(), id -> new ArrayList<>())
                        .add(new TagLoader.EntryWithSource(TagEntry.element(BuiltInRegistries.ITEM.getKey(item)), IbxLib.MODID))));
        added.forEach((tag, entries) -> cir.getReturnValue().computeIfAbsent(tag, id -> new ArrayList<>()).addAll(0, entries));
    }
}
