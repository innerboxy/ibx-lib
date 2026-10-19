package ibx.lib.target;

/**
 * 瞄准方式的声明：法术、物品在类里写一次，lib 按它选目标、锁定、校验、画提示。
 *
 * <pre>{@code
 * Targeting.self()                                          // 不选（默认）
 * Targeting.unit(Faction.ALLY).range(16.0).orSelf()          // 准星锁一个友方，没指着就对自己
 * Targeting.unit(Faction.ENEMY).range(16.0)                  // 准星锁一个敌方，没指着放不了
 * Targeting.point().range(24.0).circle(3.5F, Faction.ENEMY)  // 准星选落点，读条时地上画半径 3.5 的红圈
 * Targeting.direction()                                     // 不锁定，生效时朝准星；生物施法时 AI 可以直接给对象
 * Targeting.self().fan(2.5F, 45.0F, Faction.ENEMY)           // 自身前方的扇形，读条时画出来
 * Targeting.self().rect(4.0F, 12.0F, 0.8F, Faction.ENEMY)     // 面前 4 格处横着一条 12 × 0.8 的长条（火墙），读条时画出来
 * }</pre>
 *
 * <p>什么时候选（开始时锁定 / 生效时选取）是玩家的设置，不在这里声明，见 {@link TargetMode}。
 * 选取规则（不穿墙、瞄准辅助、排除盔甲架……）见 {@link TargetApi}。</p>
 *
 * <p>每次调工厂方法都是一个新对象，链式方法改的是它自己；声明完就别再改。</p>
 */
public final class Targeting {
    /** 怎么瞄。 */
    public enum Kind {
        /** 不选，作用于施法者 / 使用者自己或自身周围。 */
        SELF,
        /** 指向：锁一个生物。 */
        UNIT,
        /** 地点：选一个落点。 */
        POINT,
        /** 方向：不锁定，生效时朝准星。 */
        DIRECTION
    }

    /** 读条时地上画的范围形状（地点型、自身范围）；指向型画在目标脚下，不用写。 */
    public enum Area {
        NONE,
        /** 圆：地点型以落点为圆心，自身型以自己为圆心。 */
        CIRCLE,
        /** 扇形：以自己为圆心、朝面朝的方向。 */
        FAN,
        /** 长条（只用于自身型）：面前一段距离处，横在面朝方向的左右。 */
        RECT
    }

    /** 保持距离默认是射程的几倍。 */
    public static final double KEEP_RANGE_RATIO = 1.5;

    private final Kind kind;
    private final Faction faction;
    private double range;
    private double keepRange = -1.0;
    private boolean orSelf;
    private boolean blockedByOthers;
    private Area area = Area.NONE;
    private float areaRadius;
    private float areaHalfAngle;
    private float areaAhead;
    private float areaWidth;
    private Faction areaFaction = Faction.ALL;
    private boolean hidden;

    private Targeting(Kind kind, Faction faction) {
        this.kind = kind;
        this.faction = faction;
    }

    /** 不选（默认）。 */
    public static Targeting self() {
        return new Targeting(Kind.SELF, Faction.ALL);
    }

    /** 指向：准星锁一个 {@code faction} 的生物（敌方 / 友方）。 */
    public static Targeting unit(Faction faction) {
        if (faction != Faction.ENEMY && faction != Faction.ALLY) {
            throw new IllegalArgumentException("指向型只能锁敌方或友方：" + faction);
        }
        return new Targeting(Kind.UNIT, faction);
    }

    /** 地点：准星选一个落点。 */
    public static Targeting point() {
        return new Targeting(Kind.POINT, Faction.ALL);
    }

    /** 方向：不锁定，生效时朝准星。 */
    public static Targeting direction() {
        return new Targeting(Kind.DIRECTION, Faction.ALL);
    }

    // ---------------------------------------------------------------- 链式声明

    /** 射程（格）：指向型找人、地点型找落点的距离；方向型只用来校验 AI 直接给的对象够不够得着（0 = 不查）。 */
    public Targeting range(double range) {
        this.range = Math.max(0.0, range);
        return this;
    }

    /** 保持距离（格）：锁定后离开它就算失效。不写 = 射程 × {@value #KEEP_RANGE_RATIO}。 */
    public Targeting keepRange(double keepRange) {
        this.keepRange = Math.max(0.0, keepRange);
        return this;
    }

    /** 指向型没指着时对自己，而不是放不了。 */
    public Targeting orSelf() {
        this.orSelf = true;
        return this;
    }

    /** 指向型：准星和目标之间挡着阵营不对的生物时当没指着。不写 = 穿过去找后面阵营对的。 */
    public Targeting blockedByOthers() {
        this.blockedByOthers = true;
        return this;
    }

    /** 读条时地上画的圆：地点型以落点为圆心，自身型以自己为圆心。{@code faction} 是范围作用于谁，决定颜色。 */
    public Targeting circle(float radius, Faction faction) {
        this.area = Area.CIRCLE;
        this.areaRadius = Math.max(0.0F, radius);
        this.areaFaction = faction;
        return this;
    }

    /** 读条时地上画的扇形（自身型）：前方 {@code range} 格、左右各 {@code halfAngle} 度。 */
    public Targeting fan(float range, float halfAngle, Faction faction) {
        this.area = Area.FAN;
        this.areaRadius = Math.max(0.0F, range);
        this.areaHalfAngle = halfAngle;
        this.areaFaction = faction;
        return this;
    }

    /**
     * 读条时地上画的长条（自身型）：中心在面前 {@code ahead} 格处，横在面朝方向的左右，长 {@code length}、宽 {@code width}（沿面朝方向）。
     * 跟着施法者的朝向转。
     */
    public Targeting rect(float ahead, float length, float width, Faction faction) {
        this.area = Area.RECT;
        this.areaAhead = ahead;
        this.areaRadius = Math.max(0.0F, length);
        this.areaWidth = Math.max(0.0F, width);
        this.areaFaction = faction;
        return this;
    }

    /** 不显示提示：Boss 想偷袭的招式用。 */
    public Targeting hidden() {
        this.hidden = true;
        return this;
    }

    // ---------------------------------------------------------------- 读

    public Kind kind() {
        return this.kind;
    }

    /** 指向型锁的阵营；其他类型为 {@link Faction#ALL}。 */
    public Faction faction() {
        return this.faction;
    }

    public double range() {
        return this.range;
    }

    public double keepRange() {
        return this.keepRange >= 0.0 ? this.keepRange : this.range * KEEP_RANGE_RATIO;
    }

    /** 没指着时对自己（{@link #orSelf()}）。 */
    public boolean fallsBackToSelf() {
        return this.orSelf;
    }

    /** 挡在中间阵营不对的算挡住（{@link #blockedByOthers()}）。 */
    public boolean isBlockedByOthers() {
        return this.blockedByOthers;
    }

    public Area area() {
        return this.area;
    }

    /** 圆半径、扇形长度、长条长度。 */
    public float areaRadius() {
        return this.areaRadius;
    }

    public float areaHalfAngle() {
        return this.areaHalfAngle;
    }

    /** 长条中心在面前几格。 */
    public float areaAhead() {
        return this.areaAhead;
    }

    /** 长条的宽（沿面朝方向）。长度见 {@link #areaRadius()}。 */
    public float areaWidth() {
        return this.areaWidth;
    }

    public Faction areaFaction() {
        return this.areaFaction;
    }

    public boolean isHidden() {
        return this.hidden;
    }

    /** 开始时要不要选：指向、地点要；方向、自身不用。 */
    public boolean picks() {
        return this.kind == Kind.UNIT || this.kind == Kind.POINT;
    }

    /** 需不需要「当前锁定」：要选的、要画范围的，以及 AI 直接给了对象的（由调用方另判）。 */
    public boolean needsLock() {
        return this.picks() || this.area != Area.NONE;
    }
}
