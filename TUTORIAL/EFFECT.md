# 特效

**简体中文** | [English](EFFECT_en.md)

[← 返回首页](../README.md)

---

## 1. 简介

三样只管画面、不带结算的特效,都在 `ibx.lib.api` 包里,服务端一行调用:

| 特效 | 类 | 画什么 |
| --- | --- | --- |
| 地面预警 | `GroundWarnings` | 地上标一块圆或长条,提醒这里马上要挨打(见 [2](#2-地面预警))。 |
| 地面起伏 | `BlockWave` | 地表方块从一点往外像浪一样弹起来再落回去(见 [3](#3-地面起伏))。 |
| 换颜色的火 | `CustomFire` | 实体身上和第一人称屏幕上的火换成别的贴图(见 [4](#4-换颜色的火))。 |

- **只是画面**:不伤害、不改地形、不生成实体。到点该打谁、扣多少血,由调用的一方自己算,节奏和这里对齐就行。
- **发包给附近的玩家**:地面预警和地面起伏只发一个包给 64 格内的玩家,客户端按同样的节奏自己算、自己画;发出去以后才走进范围的玩家看不到这一次。
- **不用注册**:三样都是 lib 自带的,直接调就行。

---

## 2. 地面预警

**`GroundWarnings`**:地上标一块范围一段时间。画法和目标选择读条时地上的提示一样(中间填色、边框稍深、贴着地形起伏、自发光),见 [目标选择](TARGETING.md)。

| 方法 | 说明 |
| --- | --- |
| `circle(level, center, radius, duration)` | 以 `center` 为圆心、半径 `radius` 格的圆,标 `duration` tick,敌方颜色。返回编号。 |
| `circle(level, center, radius, duration, color)` | 同上,`color` 是 `Faction`,决定用哪个阵营的颜色。 |
| `line(level, from, dir, length, width, duration)` | 从 `from` 沿 `dir`(水平单位向量)长 `length`、宽 `width` 的长条,敌方颜色。返回编号。 |
| `line(level, from, dir, length, width, duration, color)` | 同上,指定颜色。 |
| `cancel(level, id)` | 提前撤掉编号 `id` 那一块(已经到点没了的也没关系),比如 Boss 死了提前收掉。 |

- `level` 是 `ServerLevel`;`center` / `from` 是贴在地面上的点。
- 颜色取看的那个玩家客户端配置里的阵营颜色:`ENEMY` 用 `targetColorEnemy`(默认红),`ALLY` 用 `targetColorAlly`(默认绿),`ALL` / `NONE` 用 `targetColorAll`(默认黄),见 [配置](CONFIG.md)。
- 不跟客户端的「显示敌方生物的提示」开关走,关了也照样显示。
- 客户端按收到那一刻的世界时间到点自己删,`duration` 最少 1 tick。

```java
// Boss 抬手:脚下 4 格的圆亮 1 秒,到点再结算
ServerLevel level = (ServerLevel) boss.level();
GroundWarnings.circle(level, boss.position(), 4.0F, 20);
// 20 tick 后由 Boss 自己的招式逻辑对圆内的目标造成伤害

// 朝前方 12 格、宽 3 格的冲锋路线
Vec3 dir = boss.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
int id = GroundWarnings.line(level, boss.position(), dir, 12.0F, 3.0F, 30);
// 招式被打断时
GroundWarnings.cancel(level, id);
```

---

## 3. 地面起伏

**`BlockWave`**:从一点往外推一道扇形的浪,浪经过的地表方块往上弹一下再落回去,像地底下有东西顶过去。真方块一块不动、碰撞不变。

| 方法 | 说明 |
| --- | --- |
| `fan(level, from, dir, halfAngle, startRadius, range, speed, height)` | 扇形浪:从 `from` 往 `dir`(水平单位向量)推,左右各 `halfAngle` 度(0~180),从离起点 `startRadius` 格推到 `range` 格,每 tick 推 `speed` 格;离起点最近的方块弹 `height` 格高,越远越矮。 |
| `fan(..., block)` | 同上;`block` 不为 `null` 时弹起来的方块画成这种方块(比如岩浆块),上面的草、花不跟着动。 |
| `ring(level, center, startRadius, range, speed, height)` | 整圈往外扩的浪,等于 `halfAngle` 为 180 的 `fan`。 |
| `ring(..., block)` | 同上,指定画成的方块。 |

- 弹的是地上本来的方块,上面的草、花、雪层一起弹。
- 最远处的方块弹起的高度约为最近处的 60%,每一列还有一点随机高低。
- **对齐伤害**:浪在第 `t` tick 离起点 `startRadius + speed × t` 格,每一列弹到最高的那一刻正好是浪推到它的时候。要「浪推到哪、伤到哪」,按这个距离每 tick 找一圈目标即可。

```java
// 砸地:半径 1 格起、推到 8 格,每 tick 0.5 格,最高弹 0.6 格
BlockWave.ring(level, boss.position(), 1.0F, 8.0F, 0.5F, 0.6F);

// 往前方 90° 扇形推一道岩浆浪
BlockWave.fan(level, boss.position(), dir, 45.0F, 1.0F, 10.0F, 0.6F, 0.8F,
        Blocks.MAGMA_BLOCK.defaultBlockState());
```

---

## 4. 换颜色的火

**`CustomFire`**:让实体身上烧的火换成别的贴图(第三人称身上的火苗、第一人称屏幕上的火)。着火本身还是原版的:着火时间、伤害、水能浇灭、防火的实体不着都不变;这里只在实体上记「烧的是哪种火」,存盘、同步给看得见它的玩家,火灭了自动清掉。

| 方法 | 说明 |
| --- | --- |
| `ignite(entity, look, seconds)` | 点燃 `seconds` 秒(同原版 `igniteForSeconds`,不缩短已有的),并记下烧的是 `look` 这种火。防火的实体不着,也不记。只在服务端调。 |
| `look(entity)` | 这个实体烧的是哪种火;原版火或者没着火为 `null`。两端都能问。 |

着着换色的火又走进原版火里,还是显示换色的火,直到整个火灭掉。

**贴图**:`look` 是贴图的基础名,比如 `examplemod:frost_fire`:

| 贴图 | 位置 | 说明 |
| --- | --- | --- |
| 身上的火苗 | `assets/examplemod/textures/block/frost_fire_0.png`、`frost_fire_1.png` | 同原版的 `fire_0` / `fire_1`,在方块图集里,可以带 `.mcmeta` 动画。 |
| 第一人称屏幕边框 | `assets/examplemod/textures/misc/frost_fire_outline.png` | 格式同 lib 的 `ibx_lib:textures/misc/fire_outline.png`:16 帧竖着排,每帧 320×180,每帧 2 tick 循环。没有就用原版火色的那张。 |

```java
// 冰霜弹命中:点燃 4 秒,烧的是蓝色的火
CustomFire.ignite(target, Identifier.fromNamespaceAndPath("examplemod", "frost_fire"), 4.0F);
```

**第一人称的火**:lib 把原版镜头前那两大片挡视线的火换成了屏幕四角的火焰边框,着火 / 熄灭时淡入淡出,原版火和换色的火都这样画。玩家可以在客户端配置 `replaceFireOverlay` 里换回原版的两大片(见 [配置](CONFIG.md)),这时换色的火在原版的位置用换色的贴图画。
