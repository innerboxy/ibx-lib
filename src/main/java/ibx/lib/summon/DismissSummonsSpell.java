package ibx.lib.summon;

import ibx.lib.IbxLib;
import ibx.lib.api.SummonApi;
import ibx.lib.registry.AttachmentRegistry;
import ibx.lib.spell.CastContext;
import ibx.lib.spell.Spell;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/**
 * 解散召唤物 Dismiss Summons：让自己的召唤物全部离场。不来自物品——玩家身上有召唤物时由框架自动加进法术列表
 * （见 {@link SummonHandler}），没有召唤物就不出现。
 */
public class DismissSummonsSpell extends Spell {
    public DismissSummonsSpell(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canCast(CastContext ctx) {
        LivingEntity caster = ctx.caster();
        if (!caster.hasData(AttachmentRegistry.OWNER_SUMMONS) || caster.getData(AttachmentRegistry.OWNER_SUMMONS).isEmpty()) {
            ctx.fail(Component.translatable("message." + IbxLib.MODID + ".cast.no_summons"));
            return false;
        }
        return true;
    }

    @Override
    public void onCast(CastContext ctx) {
        SummonApi.dismissAll(ctx.caster(), UnsummonReason.DISMISSED);
    }
}
