package ibx.lib.gearset;

import ibx.lib.compat.CuriosCompat;
import ibx.lib.registry.AttachmentRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 套装的注册表和查询入口。套装怎么写见 {@link GearSet}，件数的统计和效果的挂摘见 {@link GearSetHandler}。
 *
 * <p><b>物品在哪里才算在位</b>（不用在套装里另外配）：</p>
 * <ul>
 *   <li>盔甲槽（头、胸、腿、脚）里的都算。</li>
 *   <li>Curios 饰品栏里的都算（化妆栏不算；没装 Curios 就没有这一条）。</li>
 *   <li>主手、副手里的只算「手持物」：能穿进盔甲槽的物品、带 {@code curios} 命名空间物品标签的饰品拿在手上不算。</li>
 * </ul>
 *
 * <p>套装在模组加载期间注册（静态字段或模组构造函数里），之后不再变。</p>
 */
public final class GearSets {
    private GearSets() {}

    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final EquipmentSlot[] HAND_SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    private static final Map<Identifier, GearSet> SETS = new LinkedHashMap<>();
    /** 全部套装按注册顺序的快照，注册时换新。 */
    private static volatile List<GearSet> ordered = List.of();
    /** 物品 → 认它的件位。物品注册完才能取到物品，所以第一次查的时候再建。 */
    private static volatile @Nullable Map<Item, List<GearSet.Piece>> byItem;

    // ---------------------------------------------------------------- 注册

    /** 注册一个套装，原样返回方便存成字段。id 重复会抛异常。 */
    public static synchronized GearSet register(GearSet set) {
        if (SETS.putIfAbsent(set.id(), set) != null) {
            throw new IllegalStateException("Duplicate gear set " + set.id());
        }
        ordered = List.copyOf(SETS.values());
        byItem = null;
        return set;
    }

    /** 全部套装，按注册顺序。 */
    public static List<GearSet> all() {
        return ordered;
    }

    public static synchronized @Nullable GearSet get(Identifier id) {
        return SETS.get(id);
    }

    private static Map<Item, List<GearSet.Piece>> index() {
        Map<Item, List<GearSet.Piece>> index = byItem;
        if (index == null) {
            synchronized (GearSets.class) {
                index = byItem;
                if (index == null) {
                    index = buildIndex();
                    byItem = index;
                }
            }
        }
        return index;
    }

    private static Map<Item, List<GearSet.Piece>> buildIndex() {
        Map<Item, List<GearSet.Piece>> map = new IdentityHashMap<>();
        for (GearSet set : SETS.values()) {
            Set<Item> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            for (GearSet.Piece piece : set.pieces()) {
                for (Item item : piece.items()) {
                    if (!seen.add(item)) {
                        throw new IllegalStateException("GearSet " + set.id() + ": " + item + " appears in more than one piece");
                    }
                    map.computeIfAbsent(item, key -> new ArrayList<>()).add(piece);
                }
            }
        }
        map.replaceAll((item, pieces) -> List.copyOf(pieces));
        return map;
    }

    // ---------------------------------------------------------------- 按物品查

    /** 认这件物品的件位（每个套装最多一个）；不属于任何套装就是空表。 */
    public static List<GearSet.Piece> piecesOf(Item item) {
        return index().getOrDefault(item, List.of());
    }

    /** 这件物品属于哪些套装，按注册顺序。 */
    public static List<GearSet> setsOf(Item item) {
        return piecesOf(item).stream().map(GearSet.Piece::set).toList();
    }

    // ---------------------------------------------------------------- 按穿戴者查

    /**
     * 这个套装现在算几件。服务端是生效中的件数（手持的件离手后还保留 {@link GearSetHandler#HAND_GRACE_TICKS} tick）；
     * 客户端按眼下的装备现数，没有离手保留。
     */
    public static int count(LivingEntity entity, GearSet set) {
        if (entity.level().isClientSide()) {
            return countWorn(entity, set);
        }
        GearSetState state = entity.getExistingDataOrNull(AttachmentRegistry.GEAR_SETS);
        return state == null ? 0 : state.count(set);
    }

    /** 这个套装的某一档现在有没有生效：件数达到 pieces。 */
    public static boolean isActive(LivingEntity entity, GearSet set, int pieces) {
        return count(entity, set) >= pieces;
    }

    /** 按眼下的装备数这个套装有几个件位在位，不算离手保留。两端都能用。 */
    public static int countWorn(LivingEntity entity, GearSet set) {
        Set<GearSet.Piece> worn = Collections.newSetFromMap(new IdentityHashMap<>());
        scan(entity, (piece, viaHand) -> {
            if (piece.set() == set) {
                worn.add(piece);
            }
        });
        return worn.size();
    }

    /** 这个件位眼下在不在位，不算离手保留。两端都能用。 */
    public static boolean isWorn(LivingEntity entity, GearSet.Piece piece) {
        boolean[] found = {false};
        scan(entity, (worn, viaHand) -> found[0] |= worn == piece);
        return found[0];
    }

    // ---------------------------------------------------------------- 扫装备

    /** 扫到一个在位的件位；viaHand 是不是靠手上拿着的物品算上的。同一个件位可能报多次。 */
    @FunctionalInterface
    interface PieceVisitor {
        void visit(GearSet.Piece piece, boolean viaHand);
    }

    /** 把穿戴者身上在位的件位逐个报给 visitor：盔甲槽、饰品栏、双手。 */
    static void scan(LivingEntity entity, PieceVisitor visitor) {
        Map<Item, List<GearSet.Piece>> index = index();
        if (index.isEmpty()) {
            return;
        }
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            visit(index, entity.getItemBySlot(slot), false, visitor);
        }
        if (CuriosCompat.isLoaded()) {
            CuriosCompat.forEachCurio(entity, stack -> visit(index, stack, false, visitor));
        }
        for (EquipmentSlot slot : HAND_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (index.containsKey(stack.getItem()) && isHandGear(stack)) {
                visit(index, stack, true, visitor);
            }
        }
    }

    private static void visit(Map<Item, List<GearSet.Piece>> index, ItemStack stack, boolean viaHand, PieceVisitor visitor) {
        if (stack.isEmpty()) {
            return;
        }
        List<GearSet.Piece> pieces = index.get(stack.getItem());
        if (pieces != null) {
            for (GearSet.Piece piece : pieces) {
                visitor.visit(piece, viaHand);
            }
        }
    }

    /** 拿在手上算不算数：能穿进盔甲槽的、带 curios 命名空间标签的饰品不算。 */
    private static boolean isHandGear(ItemStack stack) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable != null && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
            return false;
        }
        return stack.typeHolder().tags().noneMatch(tag -> tag.location().getNamespace().equals(CuriosCompat.MODID));
    }
}
