package ibx.lib.gearset;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 一个套装：一组「件位」加上几档效果。穿戴者身上在位的件位达到某一档的件数，这一档就生效，低档一起保留。
 *
 * <pre>{@code
 * public static final GearSet HUNTER = GearSets.register(GearSet.builder(Identifier.fromNamespaceAndPath(MODID, "hunter"))
 *         .piece(HUNTER_HELMET).piece(HUNTER_CHESTPLATE)   // 每个件位算 1 件
 *         .anyOf(HUNTER_BOW, HUNTER_CROSSBOW)              // 这几样里有任意一样在位就算 1 件，多拿不多算
 *         .piece(HUNTER_RING)
 *         .bonus(2, b -> b.attribute(Attributes.MAX_HEALTH, 4, AttributeModifier.Operation.ADD_VALUE))
 *         .bonus(4, b -> b.effect(MobEffects.NIGHT_VISION).triggers(new HunterFourPiece()))
 *         .build());
 * }</pre>
 *
 * <ul>
 *   <li>套装由任意物品组成，不要求有盔甲；一件物品可以同时属于几个套装，但同一个套装里只能出现在一个件位上。</li>
 *   <li>物品在哪里才算在位，按物品自己定，见 {@link GearSets}。</li>
 *   <li>套装名的语言键是 {@code gearset.<命名空间>.<路径>}。</li>
 *   <li>物品可以直接传还没注册完的 {@code DeferredItem}：第一次用到时才取物品。</li>
 * </ul>
 */
public final class GearSet {
    private final Identifier id;
    private final List<Piece> pieces;
    private final List<Bonus> bonuses;

    private GearSet(Identifier id, List<List<ItemLike>> pieceItems, List<Bonus> bonuses) {
        this.id = id;
        List<Piece> built = new ArrayList<>();
        for (int i = 0; i < pieceItems.size(); i++) {
            built.add(new Piece(this, i, pieceItems.get(i)));
        }
        this.pieces = List.copyOf(built);
        this.bonuses = List.copyOf(bonuses);
    }

    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    public Identifier id() {
        return id;
    }

    /** 套装名，语言键 {@code gearset.<命名空间>.<路径>}。 */
    public Component name() {
        return Component.translatable(Util.makeDescriptionId("gearset", id));
    }

    /** 全部件位，按声明顺序；个数就是这个套装的总件数。 */
    public List<Piece> pieces() {
        return pieces;
    }

    /** 各档效果，按件数从少到多。 */
    public List<Bonus> bonuses() {
        return bonuses;
    }

    @Override
    public String toString() {
        return "GearSet[" + id + "]";
    }

    // ---------------------------------------------------------------- 件位

    /** 一个件位：最多算 1 件。只有一个物品，或者几样任选一。 */
    public static final class Piece {
        private final GearSet set;
        private final int index;
        private final List<ItemLike> sources;
        private @Nullable List<Item> items;

        private Piece(GearSet set, int index, List<ItemLike> sources) {
            this.set = set;
            this.index = index;
            this.sources = List.copyOf(sources);
        }

        public GearSet set() {
            return set;
        }

        /** 在套装里的序号，从 0 起。 */
        public int index() {
            return index;
        }

        /** 这个件位认的物品，有任意一样在位就算。物品注册完之后才能调。 */
        public List<Item> items() {
            List<Item> resolved = items;
            if (resolved == null) {
                resolved = sources.stream().map(ItemLike::asItem).toList();
                items = resolved;
            }
            return resolved;
        }
    }

    // ---------------------------------------------------------------- 档位

    /** 一条属性加成；{@code id} 是挂到穿戴者身上的修饰符 id。 */
    public record AttributeBonus(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation, Identifier id) {
        public AttributeModifier modifier() {
            return new AttributeModifier(id, amount, operation);
        }
    }

    /** 一个常驻效果（{@code amplifier} 同 {@code MobEffectInstance}，0 是一级）。 */
    public record EffectBonus(Holder<MobEffect> effect, int amplifier) {}

    /** 一档效果：在位件数达到 {@link #pieces()} 就生效。 */
    public static final class Bonus {
        private final int pieces;
        private final List<AttributeBonus> attributes;
        private final List<EffectBonus> effects;
        private final List<SetTriggers> triggers;

        private Bonus(int pieces, List<AttributeBonus> attributes, List<EffectBonus> effects, List<SetTriggers> triggers) {
            this.pieces = pieces;
            this.attributes = List.copyOf(attributes);
            this.effects = List.copyOf(effects);
            this.triggers = List.copyOf(triggers);
        }

        /** 要几件才生效。 */
        public int pieces() {
            return pieces;
        }

        public List<AttributeBonus> attributes() {
            return attributes;
        }

        public List<EffectBonus> effects() {
            return effects;
        }

        public List<SetTriggers> triggers() {
            return triggers;
        }
    }

    /** 配一档效果，{@link Builder#bonus} 里用。 */
    public static final class BonusBuilder {
        private final Identifier setId;
        private final int pieces;
        private final List<AttributeBonus> attributes = new ArrayList<>();
        private final List<EffectBonus> effects = new ArrayList<>();
        private final List<SetTriggers> triggers = new ArrayList<>();

        private BonusBuilder(Identifier setId, int pieces) {
            this.setId = setId;
            this.pieces = pieces;
        }

        /**
         * 这一档生效期间给穿戴者挂一条属性修饰符，掉档就摘掉。开关属性（如「法术可暴击」）照样挂 +1。
         * 修饰符 id 是 {@code <套装命名空间>:gearset/<套装路径>/<件数>/<这一档里的第几条>}。
         */
        public BonusBuilder attribute(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
            Identifier modifierId = Identifier.fromNamespaceAndPath(setId.getNamespace(),
                    "gearset/" + setId.getPath() + "/" + pieces + "/" + attributes.size());
            attributes.add(new AttributeBonus(attribute, amount, operation, modifierId));
            return this;
        }

        /** 这一档生效期间一直有这个效果（一级）。 */
        public BonusBuilder effect(Holder<MobEffect> effect) {
            return effect(effect, 0);
        }

        /**
         * 这一档生效期间一直有这个效果：无限时长，不闪、不冒粒子、显示图标；掉档就去掉。
         * 同一效果的药水：等级更高的生效期间不动它，到期后再补上；等级不比它高的会被盖住。
         * 掉档时只去掉套装给的那个（无限时长、不显示粒子、等级相同），别处来的留着。
         *
         * @param amplifier 效果等级减 1，0 是一级
         */
        public BonusBuilder effect(Holder<MobEffect> effect, int amplifier) {
            effects.add(new EffectBonus(effect, amplifier));
            return this;
        }

        /** 这一档生效期间的触发钩子（命中、挨打、击杀、施法……），见 {@link SetTriggers}。可以调多次。 */
        public BonusBuilder triggers(SetTriggers triggers) {
            this.triggers.add(triggers);
            return this;
        }

        private Bonus build() {
            return new Bonus(pieces, attributes, effects, triggers);
        }
    }

    // ---------------------------------------------------------------- 构建

    public static final class Builder {
        private final Identifier id;
        private final List<List<ItemLike>> pieces = new ArrayList<>();
        private final List<Bonus> bonuses = new ArrayList<>();

        private Builder(Identifier id) {
            this.id = id;
        }

        /** 加一个件位：这件物品在位就算 1 件。 */
        public Builder piece(ItemLike item) {
            pieces.add(List.of(item));
            return this;
        }

        /** 加一个件位：列出的几样里有任意一样在位就算 1 件，同时有几样也只算 1 件。 */
        public Builder anyOf(ItemLike... items) {
            if (items.length == 0) {
                throw new IllegalArgumentException("GearSet " + id + ": anyOf needs at least one item");
            }
            pieces.add(List.of(items));
            return this;
        }

        /** 加一档效果：在位件数达到 pieces 就生效。每个件数只能配一档，先把件位都加完再调。 */
        public Builder bonus(int pieces, Consumer<BonusBuilder> config) {
            if (pieces < 1 || pieces > this.pieces.size()) {
                throw new IllegalArgumentException("GearSet " + id + ": bonus needs " + pieces
                        + " pieces but the set has " + this.pieces.size());
            }
            for (Bonus bonus : bonuses) {
                if (bonus.pieces == pieces) {
                    throw new IllegalArgumentException("GearSet " + id + ": duplicate bonus for " + pieces + " pieces");
                }
            }
            BonusBuilder builder = new BonusBuilder(id, pieces);
            config.accept(builder);
            bonuses.add(builder.build());
            return this;
        }

        public GearSet build() {
            if (pieces.isEmpty()) {
                throw new IllegalStateException("GearSet " + id + " has no pieces");
            }
            List<Bonus> sorted = new ArrayList<>(bonuses);
            sorted.sort((a, b) -> Integer.compare(a.pieces, b.pieces));
            return new GearSet(id, pieces, sorted);
        }
    }
}
