package ibx.lib.entity;

/**
 * 分阶段的实体（BOSS 半血进二阶段这类）。别的模组不认识具体的实体类，按实体 id 认出是谁之后，借这个接口读它现在几阶段：
 *
 * <pre>{@code
 * caster instanceof PhasedEntity boss && boss.getPhase() >= 1
 * }</pre>
 */
public interface PhasedEntity {
    /** 当前阶段：一阶段为 0，之后依次加一。两端都能读（实现方应同步给客户端）。 */
    int getPhase();
}
