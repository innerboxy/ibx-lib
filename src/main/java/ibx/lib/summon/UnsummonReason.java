package ibx.lib.summon;

/** 召唤物为什么离场。 */
public enum UnsummonReason {
    /** 限时召唤物到点了。 */
    EXPIRED,
    /** 名额不够，被新召的挤掉（最早召唤的先走）。 */
    REPLACED,
    /** 主动解散（指令、法术、Boss 自己花掉）。 */
    DISMISSED,
    /** 主人死了。 */
    OWNER_DIED,
    /** 主人下线。 */
    OWNER_LEFT,
    /** 找不到主人，或者主人的名单里已经没有它（主人被移除、读档后对不上……）。 */
    OWNER_GONE,
    /** 被打死。框架不处理它的消散（原版死亡流程照走），只从主人名单里划掉并发事件。 */
    KILLED
}
