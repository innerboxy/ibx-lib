# 配置

**简体中文** | [English](CONFIG_en.md)

[← 返回首页](../README.md)

---

## 1. 服务端配置

配置类型为 **SERVER**,随存档保存在 `<存档>/serverconfig/ibx_lib-server.toml`,会同步给客户端。

```toml
#是否保留原版跳劈暴击（下落攻击必定暴击）。
#false = 暴击完全由 critical_chance 属性决定。
#true  = 跳劈仍必定暴击，平地攻击另按 critical_chance 判定。
#两种情况下暴击倍率都取 critical_damage，而非原版写死的 1.5 倍。
enableVanillaCritical = false
```

| 选项 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `enableVanillaCritical` | boolean | `false` | 是否保留原版的跳劈必暴。开启后跳劈仍必定暴击,平地攻击另按暴击率判定(两者取或)。无论开关如何,暴击倍率都取 `critical_damage`(见 [属性 3](ATTRIBUTE.md#3-伤害与治疗的结算顺序))。 |

> SERVER 配置在存档加载后才可用。开发者在代码里读 `Config` 时只能在游戏运行中读,不要在注册期或类初始化时读,否则会抛异常。

---

## 2. 启动配置

配置类型为 **STARTUP**,文件位于 `config/ibx_lib-startup.toml`。这几项要在物品注册时就定下来(原版物品的默认属性、堆叠上限在那时烤进物品),所以放在启动配置里。

```toml
#是否接管原版弓与弩，让它们按本模组的远程属性结算。
takeOverVanillaRangedWeapons = true
#是否调整原版盔甲套装的属性。
adjustVanillaArmors = true
#是否把原版里堆叠上限为 16 的物品改成 64。
raiseVanillaStackSizes = true
```

| 选项 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `takeOverVanillaRangedWeapons` | boolean | `true` | 是否接管原版弓与弩。开启后原版弓弩带上远程伤害 / 拉弦时间属性,伤害、蓄力、装填、暴击与本模组的弓弩一致,副手不能用;手持原版弓弩的生物(骷髅、掠夺者等)同样按属性结算。关闭则保持原版行为(见 [装备基类](EQUIPMENT.md))。 |
| `adjustVanillaArmors` | boolean | `true` | 是否调整原版盔甲套装的属性:下界合金套装额外加上等同于护甲值的法术抗性;金套装的护甲换成钻石套装护甲值的法术抗性(头 3、胸 8、腿 6、脚 3);铜套装的护甲换成等量的法术抗性(头 2、胸 4、腿 3、脚 1)。 |
| `raiseVanillaStackSizes` | boolean | `true` | 是否把原版里堆叠上限为 16 的物品改成 64:告示牌、悬挂式告示牌、旗帜、雪球、鸡蛋、末影珍珠、铁桶、盔甲架、蜂蜜瓶。成书仍是 16(见 [装备基类](EQUIPMENT.md))。 |

- **改完要重启游戏**才生效。
- **不会同步**:服务端和客户端各读各的文件。两边要改成一致,否则客户端显示的数值(tooltip、拉弓动画、堆叠上限)会和服务端实际结算对不上。多人游戏时,整合包要让服务器和玩家用同一份文件。

---

## 3. 客户端配置

每个玩家自己的显示和操作设置,文件位于 `config/ibx_lib-client.toml`,不同步。也可以在模组列表里点本模组的「配置」改;施法相关的五项还在 **选项 → 控制** 里,插在原版「使用:切换 / 按住」后面。都是现读的,游戏里改了立即生效。

### HUD 与画面

| 选项 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `vanillaHud` | boolean | `true` | 是否使用原版的生命、饥饿、护甲、氧气、等级显示。`false` 时心形 / 鸡腿换成生命条、饥饿条(氧气画在生命条外框上),隐藏原版护甲行,等级数字挪到经验条正中。吟唱条、蓝条不受这一项影响,两种都显示,叠在生命 / 饥饿那几行上方。 |
| `alwaysShowManaBar` | boolean | `false` | 蓝条是否常驻。`false` 时蓝不满才显示,回满 3 秒后淡出。 |
| `replaceFireOverlay` | boolean | `true` | 第一人称着火时,`true` 换成屏幕四角的火焰边框(中间不挡视线);`false` 为原版镜头前的两大片火(见 [特效 4](EFFECT.md#4-换颜色的火))。 |
| `spellWheelScale` | double(1.0~4.0) | `2.3` | 法术轮盘的大小倍数,以「刚好放得下 16×16 图标」为 1。屏幕放不下时自动缩小,但不小于 1。 |

### 施法操作(也在 选项 → 控制 里)

| 选项 | 控制界面里的名字 | 默认 | 说明 |
| --- | --- | --- | --- |
| `toggleCastSpell` | 吟唱法术 | `true`(切换) | 切换:按一下右键开始,读完自动放出,读条中再按一下取消。按住:按住右键读条,松开取消。 |
| `toggleChannelSpell` | 引导法术 | `false`(按住) | 切换:按一下右键开始,蓝耗尽或再按一下结束。按住:按住右键持续引导,松开结束。 |
| `spellWheelSelectOnRelease` | 法术轮盘选择 | `true`(可以不点击) | 可以不点击:松开轮盘键时选中鼠标指着的那格(指着中间空白不改),左键点选也照常有效。必须点击:只有左键点一格才选中。 |
| `targetUnitLockAtStart` | 指向目标 | `false`(生效时选取) | 指向型(锁一个生物)什么时候选目标。生效时选取:开始时选中的只当后备,读完 / 松手时按当时的准星重新选。开始时锁定:开始时选中就锁死,读条中目标死了、走远了就取消。 |
| `targetPointLockAtStart` | 地点目标 | `false`(生效时选取) | 地点型(选落点)什么时候选落点。生效时选取:读完 / 松手时按当时的准星重新选。开始时锁定:开始时选中就定下。 |

后两项会告诉服务端,改了马上生效(见 [目标选择](TARGETING.md))。

### 目标提示

读条时地上的提示(锁定目标脚下、范围的圆 / 扇形),见 [目标选择](TARGETING.md)。

| 选项 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `showTargetSelf` | boolean | `true` | 是否显示自己的。 |
| `showTargetPlayerAlly` | boolean | `true` | 是否显示友方玩家的(友方 / 敌方相对你来说,按服务器的 PvP 规则和队伍算)。 |
| `showTargetPlayerEnemy` | boolean | `true` | 是否显示敌方玩家的。 |
| `showTargetMobAlly` | boolean | `true` | 是否显示友方生物(你的宠物、召唤物……)的。 |
| `showTargetMobEnemy` | boolean | `true` | 是否显示敌方生物(怪物、Boss……)的。 |
| `targetColorAlly` | `#RRGGBB` | `#55D16A` | 作用于友方的(治疗、护盾……)颜色。 |
| `targetColorEnemy` | `#RRGGBB` | `#E5484D` | 作用于敌方的(伤害、减益……)颜色。 |
| `targetColorAll` | `#RRGGBB` | `#F2C230` | 敌我都作用的,以及只标落点的颜色。 |

三个颜色也用在地面预警上(见 [特效 2](EFFECT.md#2-地面预警));地面预警不跟上面的显示开关走。颜色写错时按默认值算。

### 属性面板

背包左边的属性面板(见 [属性 7](ATTRIBUTE.md#7-属性面板)),一般在面板的设置界面里改。

| 选项 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `attributePanelOpen` | boolean | `false` | 面板是否开着(点卷轴按钮开关,记住上次的状态)。 |
| `attributePanelShowIcons` | boolean | `true` | 每行是否画图标。 |
| `attributePanelShowNames` | boolean | `true` | 每行是否写属性名。 |
| `attributePanelGroupByMod` | boolean | `false` | 是否按模组分组(带组名);`false` 不分组排成一列。 |
| `attributePanelHideDefault` | boolean | `false` | 是否不列出还是默认值的属性(只列被装备、效果等改过的)。 |
| `attributePanelSort` | `DEFAULT` / `NAME` / `CUSTOM` | `DEFAULT` | 默认顺序 / 按名称 / 自定义(设置界面里用 ▲▼ 调的顺序)。 |
| `attributePanelOrder` | 属性 id 列表 | 空 | 自定义顺序,排序选 `CUSTOM` 时用;没列出的按默认顺序接在后面。 |
| `attributePanelHidden` | 属性 id 列表 | 见说明 | 面板里不显示的属性。默认不显示镜头距离、路径点传输 / 接收距离、名称标签渲染距离、创造模式飞行。 |

### 其他

| 选项 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `jadeBossesHiddenOnce` | boolean | `false` | 内部记录,一般不用改:是否已经替你关掉过 Jade 的「显示Boss」(装了 Jade 才有用)。Jade 准星对着 Boss 时会再显示一遍血量,和 Boss 血条重复,所以第一次进世界时自动关一次;之后在 Jade 里重新打开就不会再关。改回 `false`,下次进世界会再关一次。 |
