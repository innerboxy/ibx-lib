# 药水效果

**简体中文** | [English](MOB_EFFECT_en.md)

[← 返回首页](../README.md)

---

## 1. 简介

原版给生物加药水效果用 `LivingEntity#addEffect`:同一个效果已经在身上时,只有新给的等级更高、或者同级但更久,才会替换。本模组给 `addEffect` 加了带「是否叠加」「上限」两个参数的版本,用来做「每次命中中毒加一级」这类效果。

- **叠加**:在目标身上现有的等级上往上加,加几级看这次给的等级(给 I 级加 1 级、给 II 级加 2 级)。
- **上限**:用放大等级(和 `MobEffectInstance` 的 amplifier 一样,从 0 起,4 = V 级);不填就是不封顶。
- 原版的 `addEffect(效果)`、`addEffect(效果, 来源)` 行为完全不变。

---

## 2. 用法

lib 用 mixin 把下面几个方法加到 `LivingEntity` 上(接口 `ibx.lib.effect.IbxEffectTarget`):

| 方法 | 说明 |
| --- | --- |
| `addEffect(effect, source, stack, maxAmplifier)` | 给效果。`stack` 是否叠加,`maxAmplifier` 上限(放大等级),返回值同原版:效果有没有加上或变化。 |
| `addEffect(effect, source, stack)` | 同上,不封顶。 |
| `addEffect(effect, stack, maxAmplifier)` | 同上,没有来源。 |
| `addEffect(effect, stack)` | 同上,没有来源、不封顶。 |
| `IbxEffectTarget.NO_LIMIT` | 不封顶(`-1`),和不填一样。 |

```java
// 每次命中叠加一级中毒,时长 5 秒,最高 V 级
target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0), attacker, true, 4);

// 不叠加、只截上限:给多高都最多 III 级,其余同原版
target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 5), attacker, false, 2);
```

### 叠加的规则

`stack = true`,目标身上已有同一个效果时:

- **等级**:原来的放大等级 + (这次给的放大等级 + 1),不超过上限。
- **时长**:原来剩下的和这次给的取较长的;有一边是无限时长就是无限。
- **环境效果、粒子、图标**:照原版,用这次给的。

目标身上没有这个效果时,按给的等级直接加。`stack = false` 时同原版。两种情况下给的等级超过上限,都先截到上限。

算好等级和时长后交给原版的 `addEffect(效果, 来源)`,所以能不能生效的判断(亡灵不中毒、NeoForge 的 `MobEffectEvent.Applicable`)、`MobEffectEvent.Added` 事件、属性刷新、同步到客户端都照原版。

---

## 3. 在自己的模组里调用

`entity.addEffect(效果, 来源, true, 4)` 能直接写,是靠 ModDevGradle 的接口注入:编译时让 `LivingEntity` 带上 `IbxEffectTarget`。lib 自己的 `build.gradle` 配了这份数据,依赖 lib 的模组要在自己的 `build.gradle` 里也配一份(内容同 lib 的 `interfaces.json`):

```json
{
  "net/minecraft/world/entity/LivingEntity": [
    "ibx/lib/effect/IbxEffectTarget"
  ]
}
```

```groovy
neoForge {
    interfaceInjectionData {
        from(file('interfaces.json'))
    }
}
```

不想配的话,先强转再调用,游戏里效果一样:

```java
((IbxEffectTarget) target).addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0), attacker, true, 4);
```

---

## 4. 注意

- 「叠加」「上限」只在给效果的那一下起作用,不存进效果里。药水、药箭、滞留药水云这些带着效果的东西,里面存的还是原版效果,不会叠加。
- 原版的 `forceAddEffect` 直接替换效果,不认叠加。
