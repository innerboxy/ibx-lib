# ibx_lib

**简体中文** | [English](README/README_en.md)

---

## 简介

本模组是一个前置库:只放框架,不放具体内容。法术、召唤物、目标选择、装备基类、属性、特效和创造栏子栏都在这里,其他模组在它上面注册自己的法术、武器和生物。

### 法术

法术是一个注册表条目,施法物品(法杖、法典)带着它;玩家用法术轮盘选、右键放,生物和 Boss 走同一套入口。瞬发、吟唱、引导、蓄力四种施法方式,伤害在施法瞬间算成快照、命中时结算,法力、冷却、法术抗性和暴击都由框架处理;还带法术弹射物、范围效果、射线三个实体基类。

详见 [法术系统](TUTORIAL/SPELL.md)。

### 召唤物

召唤物按名额算:主人的「召唤物上限」是总名额,每只占几个由法术定,名额不够时最早召唤的先消散。主人死亡、下线时全部消散;友伤保护、标记目标、击杀归属主人都是自动的,还带一组跟随、协同攻击的 AI 目标。

详见 [召唤物](TUTORIAL/SUMMON.md)。

### 目标选择

通用的「打谁、打哪」模块:指向一个生物、选一个落点、朝一个方向,读条时在目标脚下和地上画出提示。敌我判断全模组一套(队伍、PvP、宠物和召唤物跟主人),法术和物品都能用。

详见 [目标选择](TUTORIAL/TARGETING.md)。

### 装备基类

近战武器、弓、弩、法杖、法典、单手盾、双手盾、3D 盔甲和饰品的物品基类,继承后只写自己的数值和钩子。弓弩走属性结算(远程伤害、拉弦时间),双手盾带盾击,3D 盔甲由 GeckoLib 画、一套共用一个模型,原版弓弩和几套原版盔甲也可以一并接管,原版里只能叠 16 个的物品可以改成叠 64 个。

详见 [装备基类](TUTORIAL/EQUIPMENT.md)。

### 属性

18 个属性:远程伤害、拉弦、暴击、护甲穿透、回复效率、反甲、法力、法术强度、法术抗性与穿透、召唤物上限……全部生物都有、全部同步到客户端。背包左边可以打开属性面板,列出玩家身上的全部属性。

详见 [属性](TUTORIAL/ATTRIBUTE.md)。

### 特效

只管画面、不带结算的三样:地面预警(地上标一块圆或长条)、地面起伏(地表方块像浪一样弹起来)、换颜色的火(实体身上和屏幕上的火换贴图)。服务端一行调用,客户端自己画。

详见 [特效](TUTORIAL/EFFECT.md)。

### 创造栏子栏

把一个创造栏的物品分成几组:创造模式背包左边多一列图标,点一组只显示这一组的物品,再点一次回到整个创造栏。注册创造栏时把 `displayItems` 换成 `CreativeSubTabs`,一组一个 `add`;搜索栏和 JEI 拿到的还是全部物品。

详见 [创造栏子栏](TUTORIAL/CREATIVE_TAB.md)。

---

## 文档

| 文档 | 内容 |
| --- | --- |
| [法术系统](TUTORIAL/SPELL.md) | 注册法术、施法流程、法术容器、法术伤害、法力、法术实体基类、事件 |
| [召唤物](TUTORIAL/SUMMON.md) | `SummonApi`、名额与寿命、`Summoned` 接口、AI 目标、事件 |
| [目标选择](TUTORIAL/TARGETING.md) | `TargetApi`、阵营、瞄准方式、选取时机、读条提示、法术和物品里怎么用 |
| [装备基类](TUTORIAL/EQUIPMENT.md) | 近战、弓弩、法器、盾牌、3D 盔甲、饰品的基类,`RangedWeaponApi`、`ShieldApi`,自动物品标签 |
| [属性](TUTORIAL/ATTRIBUTE.md) | 属性一览、伤害与治疗的结算顺序、`CriticalStrikeApi`、属性类型、属性面板 |
| [特效](TUTORIAL/EFFECT.md) | `GroundWarnings`、`BlockWave`、`CustomFire` |
| [创造栏子栏](TUTORIAL/CREATIVE_TAB.md) | `CreativeSubTabs`、分组规则 |
| [配置](TUTORIAL/CONFIG.md) | 服务端配置、启动配置、客户端配置 |
| [指令](TUTORIAL/COMMAND.md) | `/taverntales mana`、`spell`、`summon` |

---

## 依赖

| 模组 | 类型 | 说明 |
| --- | --- | --- |
| NeoForge `26.1.2.112+` | **必需** | — |
| Minecraft `26.1.2` | **必需** | — |
| [GeckoLib](https://modrinth.com/mod/geckolib) `5.5.2+` | 可选 | 只有法术弹射物基类 `SpellProjectile`、3D 盔甲 `IbxGeoArmorItem` 和它们的渲染器用到;注册法术弹射物或 3D 盔甲的模组自己要把它设成必需 |
| [Player Animation Library](https://modrinth.com/mod/player-animation-library) `1.2.6+` | 可选(客户端) | 装了才有施法、格挡和盾击时的玩家动作,没装只是手臂不动;它播第一人称动作时,3D 盔甲照它对原版盔甲的规矩藏起来 |
| [Curios API](https://modrinth.com/mod/curios) `15.0.0+` | 可选 | 饰品基类、饰品的触发钩子、饰品栏里的施法物品、饰品栏界面上的属性面板按钮 |
| [Jade](https://modrinth.com/mod/jade) | 可选(客户端) | 提示框里在护甲后面显示法术抗性;第一次进世界时关掉一次 Jade 的「显示Boss」,免得和 Boss 血条重复 |
| [MmmMmmMmmMmm](https://modrinth.com/mod/mmmmmmmmmmmm)(试验假人) | 可选 | 远程、法术暴击显示成暴击数字,法术伤害的数字用专属的蓝色 |
| [JEI](https://modrinth.com/mod/jei) | 可选(客户端) | 属性面板、饰品槽、创造栏子栏显示时让 JEI 的书签栏让开 |

---

## 许可证

本模组以 [MIT 许可证](LICENSE)开源。你可以自由地使用、修改、分发本模组及其源码,或将其打包进整合包(含商业整合包),只需保留版权与许可声明。
