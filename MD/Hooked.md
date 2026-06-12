# Hooked - 木筏求生“漂浮物”收集系统插件开发文档 (v2.1)

> 本文档专为 AI 编程助手 (AIDE) 设计，包含架构、流程、代码规范、未来扩展与效果描述。请严格遵循所有 “AI 生成规范” 小节的指示生成代码。

## 效果预览（插件实现后游戏内表现）

- **无尽海面，资源涌现**：玩家置身于全海洋世界，向四周眺望，每隔数十格便能发现缓慢漂流的木板、木桶、树叶团等漂浮物。它们随着海浪轻轻起伏，方向随机，仿佛真实的海洋垃圾。
- **动态漂流，身临其境**：漂浮物并非静止物品展示框，而是实实在在浮在水面上的方块实体。它们会缓缓移动，偶尔因水流改变方向，碰到玩家的木筏边缘还会自动转向绕开，绝不卡墙。
- **挥竿钩取，手感扎实**：玩家手持钓鱼竿瞄准漂浮物抛竿，鱼钩带着水花飞出。当鱼钩落点靠近漂浮物时，命中瞬间发出清脆音效，漂浮物被“钩住”冻结，随后被快速拉回，划过一道弧线至玩家面前。若未命中，钩子空返也有冷却提示，形成清晰的技巧反馈。
- **多人同服，互不干扰**：数名玩家在同一海域各自收集资源时，不会出现抢不到或重复拾取的问题。一名玩家成功钩取的漂浮物会立即标记为已归属，其他人无法再次钩取，杜绝抢资源纠纷。掉线等异常情况会自动释放归属权，等待下一位有缘人。
- **性能稳如磐石**：即便全服上百个漂浮物同时存在，由于使用了轻量级 BlockDisplay 实体与空间索引分桶，服务器 TPS 几乎不受影响。方块实体不存档、自动清理远距离残骸，即使服务器重启也毫秒级恢复。
- **扩展与定制，一应俱全**：服主可通过配置文件轻松修改漂浮物种类、刷新密度、漂流速度、掉落表，甚至接入 ItemsAdder 将木板替换为自己绘制的精致模型。第三方插件可通过事件 API 干涉生成、钩取、掉落全过程，创造出完全贴合主题的独特循环。

---

## 1. 项目简介

**Hooked** 是一款为 Paper 1.20+ 服务器设计的插件，在无尽海洋中生成可漂流的方块型资源实体。玩家使用钓鱼竿（钩子）收集它们，获得生存物资。目的是在纯插件环境下高度还原《木筏求生》的漂浮物收集体验。插件名取自“被钩住”的双关——既指物理上的钩取，也指玩家对玩法的沉迷。

## 2. 技术栈与环境

- **目标平台**：Paper 1.20.1+ (API 版本 1.20+)
- **语言**：Java 17+
- **构建工具**：Gradle 或 Maven
- **必需前置**：无
- **可选软依赖**（建议在代码中通过 `Bukkit.getPluginManager().getPlugin("...")` 判断）：
  - **ProtocolLib**：若需要发送精细数据包或兼容旧版，可用来创建更平滑的实体移动或自定义交互。
  - **ItemsAdder**：若服主想使用自定义模型代替原版方块外观，可在配置中指定 ItemsAdder 物品 ID，插件通过 IA API 获取外观。
  - **PlaceholderAPI**：为未来可能的需求预留，如显示玩家钩子冷却时间。

## 3. AI 代码生成总体规范

> **所有生成的代码必须遵守本节规定。**

### 3.1 日志规范

- 使用 `java.util.logging.Logger`（通过 `plugin.getLogger()` 获取）。
- 级别使用：
  - `info`：插件启用/停用、重载配置成功。
  - `warning`：配置文件缺失项使用默认值、软依赖未找到。
  - `severe`：关键异常导致功能失效。
  - `debug`：所有需要跟踪的细节（生成位置、漂移计算、钩取判定、实体移除）。**debug 日志必须用 `if (config.isDebug())` 包裹，避免默认刷屏。**
- 每条日志需包含关键上下文，例如：`"生成漂浮物 [PLANK] 在 (x, y, z) 实体UUID: xxx"`。

### 3.2 关键处必须添加 DEBUG 日志的点

以下位置必须加入 `if (debug) log.fine(...)` 级别的日志：

- 每个漂浮物生成成功时。
- 漂流移动计算的新旧位置与方向。
- 钩取判定命中/未命中时，包括距离和漂浮物ID。
- 拉回动画开始、每步移动、结束。
- 漂浮物因超时、区块卸载、钩取被移除时。
- 空间索引更新（进出区块桶）。
- 多玩家互斥冲突（尝试钩取已被钩取的漂浮物）。

### 3.3 异常处理

- 所有与 Bukkit API 交互（生成实体、Teleport、检查方块）必须捕获可能的 `Exception`，并记录 warning，不得导致循环中断。
- 定时任务内部必须使用 try-catch 包裹，防止单个错误停止整个调度。
- 配置读取时，每个值都提供默认值，并记录使用了默认值的情况。

### 3.4 代码风格

- 遵循标准 Java 命名规范（驼峰式）。
- 每个类、公共方法必须有 Javadoc 注释，说明用途、参数和返回值。
- 使用 `final` 修饰不该变的局部变量。
- 避免魔法数字，统一放在 `Constants` 类或配置中。
- 单例模式（如 Manager 类）通过插件实例传递，避免使用静态字段。

### 3.5 可扩展性要求

- 所有主要模块（生成、漂移、钩取、掉落）必须基于接口设计，提供默认实现，并允许服主通过配置指定实现类（如果需要）或通过 Java 的 SPI 加载。**当前版本至少为未来扩展预留 getter/setter 和事件。**
- 关键操作（生成前、钩取前、掉落前、移除后）必须触发自定义 `Event`（见第9章），以便其他插件介入。

## 4. 架构总览

插件主类为 `HookedPlugin`，持有各模块实例并负责初始化。

模块划分与职责：

| 接口 | 默认实现类 | 职责 |
|------|-----------|------|
| `IDebrisManager` | `DebrisManagerImpl` | 漂浮物实体的内存管理、空间索引、生命周期 |
| `ISpawnController` | `SpawnControllerImpl` | 按规则定时刷新漂浮物 |
| `IDriftAI` | `DefaultDriftAI` | 控制漂浮物移动、漂流、避障 |
| `IHookHandler` | `HookHandlerImpl` | 监听钓鱼竿事件，进行钩取判定与处理 |
| `ILootGenerator` | `ConfigLootGenerator` | 读取掉落表，计算奖励 |

所有实现类通过构造函数接受 `HookedPlugin` 实例，以便访问配置和其他服务。

## 5. 数据模型 (含扩展点)

### 5.1 `Debris` 类

```java
public class Debris {
    private final UUID entityId;          // BlockDisplay 实体 UUID
    private final DebrisType type;        // 枚举，定义外观和掉落
    private final Location spawnLocation; // 生成时的水面位置
    private Vector driftDirection;        // 当前漂流方向，单位向量
    private boolean isHooked;             // 是否已被钩住
    private UUID hookedBy;                // 钩取者 UUID (用于互斥)
    private long lastUpdateTime;          // 上次移动时间戳 (ms)
    private long hookedTimestamp;         // 被钩住时的时间，用于超时释放
    private final long createdAt;         // 生成时间戳
    private final int debugId;            // 唯一自增ID（用于日志追踪）
    private final Map<String, Object> customData = new HashMap<>(); // 扩展数据
    
    // 方法: tryHook(), releaseHook(), updateMovement(), ...
}
```
### 5.2 `DebrisType` 枚举
```
public enum DebrisType {
    PLANK(Material.OAK_PLANKS, null, 50, Arrays.asList(
        new LootEntry(Material.OAK_PLANKS, 1, 3, 100.0)
    )),
    BARREL(Material.BARREL, null, 20, Arrays.asList(
        new LootEntry(Material.STICK, 1, 4, 80.0),
        new LootEntry(Material.STRING, 1, 1, 30.0)
    )),
    // 自定义模型示例（第二个参数为 ItemsAdder 物品 ID）
    DRIFTWOOD(null, "myitems:driftwood", 10, Arrays.asList(...));
    
    private final Material blockMaterial; // 原版方块
    private final String itemsAdderId;    // ItemsAdder 模型ID
    private final int weight;
    private final List<LootEntry> lootTable;
    // ...
}
```
`LootEntry` 包含物品类型、数量范围、掉落几率。
### 5.3 空间索引

`DebrisManagerImpl` 内部维护：
```
private final Map<ChunkKey, List<Debris>> chunkMap = new ConcurrentHashMap<>();

public record ChunkKey(int x, int z) {
    public static ChunkKey fromLocation(Location loc) {
        return new ChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }
}
```
## 6. 核心流程详细设计

### 6.1 生成逻辑 (`SpawnControllerImpl`)

**触发器**：每 `spawn_interval_seconds` 秒执行一次。
**步骤**：
1. 遍历在线玩家。
2. 获取玩家位置，计算半径范围。
3. 统计玩家周围已存在的 Debris 数量。
4. 计算可生成数量 = `max_per_player - current`，按权重选取 `DebrisType`。
5. 随机生成候选水面坐标（y = 海平面 + `y_level_offset`）。
6. **区域检查**：若开启 WorldGuard/BentoBox 集成，调用对应 API 判断区域权限；不满足则跳过并记录 debug。
7. 要求候选方块为 `Material.WATER`，上方为 `AIR`；周围3格内无玩家放置的非水非空气方块（防止卡木筏）。
8. 在主线程生成 `BlockDisplay` 实体，设置方块数据、标签 `"hooked_debris"`、`setPersistent(false)`。
9. 创建 `Debris` 对象，设置随机漂流方向，加入 `DebrisManager`。
10. 触发 `DebrisSpawnEvent`，若被取消则移除实体。
11. 输出 debug 日志。

### 6.2 漂流移动 (`DefaultDriftAI`)

**触发器**：每 1 秒 (20 tick) 执行。
**单次更新逻辑**：
1. 计算 deltaTime。
2. 对 `driftDirection` 平滑旋转扰动（每5秒随机偏转小角度）。
3. 候选新坐标 = 当前位置 + 方向 * 速度 * deltaTime。
4. 检查候选点下方为水、上方为空气、前方无固体方块：
	- 是 → teleport。
	- 否 → 随机旋转 120°~180°，再次尝试；仍失败则本次跳过。
5. 更新 `driftDirection` 和 `lastUpdateTime`。
6. 每步移动记录 debug 新旧坐标与方向。

### 6.3 钩取交互 (`HookHandlerImpl`)

**监听**：`PlayerFishEvent`，优先级 `HIGHEST`，忽略取消。
**流程**：
1. 若状态为 `FISHING`，记录钩子实体位置到映射表 `hookLocationMap`（玩家UUID → Location）。
2. 若状态为 `REEL_IN` 或 `CAUGHT_FISH`：
	- 取消事件，移除钩子实体。
	- 检查冷却，若在冷却中返回。
	- 从映射表取出鱼钩位置，移除记录。
	- 调用 `debrisManager.findNearestHookable(hookLocation, hit_radius)` 查找最近的未钩取 Debris。
	- 若没有，播放失败音效，设置 1 秒冷却。
	- 若有，调用 `debris.tryHook(playerId)`，成功则：
		- 播放成功音效，设置 1.5 秒冷却。
		- 启动拉回动画任务。
		- 触发 `DebrisHookAttemptEvent`（成功），`DebrisHookSuccessEvent`。
	- 记录 debug 信息（命中/未命中、距离、漂浮物ID、玩家名）。
	- 互斥冲突时记录 debug。

### 6.4 拉回动画

`BukkitRunnable` 每 tick 执行：
1. 计算漂浮物与玩家前方1格的距离。
2. 若距离 < 0.5 格：结束动画，调用 `lootGenerator.giveLoot(player, type)`，移除 Debris，触发 `DebrisCollectEvent`，记录 debug。
3. 否则，朝玩家方向移动 `grab_animation_speed` 格/tick，播放气泡粒子，记录每步位置。

### 6.5 掉落计算 (`ConfigLootGenerator`)

```
public void giveLoot(Player player, DebrisType type) {
    for (LootEntry entry : type.getLootTable()) {
        if (Math.random() * 100 < entry.getChance()) {
            int amount = random(entry.getMin(), entry.getMax());
            ItemStack item = new ItemStack(entry.getMaterial(), amount);
            // 支持 ItemsAdder 物品
            if (entry.getMaterial() == null && entry.getItemsAdderId() != null) {
                // 调用 ItemsAdder API 获取物品
            }
            player.getInventory().addItem(item).values().forEach(excess ->
                player.getWorld().dropItem(player.getLocation(), excess)
            );
        }
    }
}
```
### 6.6 超时释放与清理

`DebrisManagerImpl` 维护定时任务，每5秒检查所有 `isHooked` 的 Debris，若 `now - hookedTimestamp > 5000`，调用 `releaseHook()` 重置状态。监听 `EntityRemoveEvent`，若移除的实体属于漂浮物，同步清理 Debris 对象并触发 `DebrisRemoveEvent`。
## 7. 配置设计 (`config.yml`)
```
# Hooked 插件配置文件

debug: false

debris:
  max_per_player: 25
  spawn_interval_seconds: 5
  spawn_distance_min: 16
  spawn_distance_max: 48
  despawn_distance: 128
  drift_speed: 0.04
  y_level_offset: 0.2

hook:
  cooldown_seconds: 1.5
  hit_radius: 2.0
  grab_animation_speed: 0.5

integrations:
  itemsadder: true
  worldguard: true
  bentobox: true

# 自定义漂流 AI 实现类（留空使用默认）
drift_ai_class: ""

types:
  plank:
    block: OAK_PLANKS
    itemsadder_id: ""
    weight: 50
    loot:
      - item: OAK_PLANKS
        min: 1
        max: 3
        chance: 100.0
  barrel:
    block: BARREL
    itemsadder_id: ""
    weight: 20
    loot:
      - item: STICK
        min: 1
        max: 4
        chance: 80.0
      - item: STRING
        min: 1
        max: 1
        chance: 30.0
  leaves:
    block: OAK_LEAVES
    itemsadder_id: ""
    weight: 30
    loot:
      - item: STICK
        min: 1
        max: 2
        chance: 70.0
      - item: APPLE
        min: 1
        max: 1
        chance: 15.0
```
## 8. 命令与权限


|命令|功能|权限|
|:-:|:-:|:-:|
|`/hooked reload`|重载配置|`hooked.admin`|
|`/hooked debug`|切换个人 debug 模式|`hooked.debug` （且服务器 `debug: true`）|
|`/hooked stats`|显示全局漂浮物数量、性能数据|`hooked.admin`|
|`/hooked test hook`|在准星处生成一个静止的测试漂浮物|`hooked.admin`|
## 9. 未来扩展设计 (事件与 API)

### 9.1 事件

所有事件位于 `com.hooked.events` 包，均继承 `Event`，部分为 `Cancellable`。


|事件类|触发时机|可取消|用途|
|:-:|:-:|:-:|:-:|
|`DebrisSpawnEvent`|漂浮物即将生成|是|修改位置、类型或阻止生成|
|`DebrisHookAttemptEvent`|玩家收竿尝试钩取（无论成败）|是|取消本次钩取尝试|
|`DebrisHookSuccessEvent`|钩取成功，即将开始拉回|否|修改拉回速度、额外处理|
|`DebrisCollectEvent`|拉回完成，物品给予前|是|修改最终奖励或阻止给予|
|`DebrisRemoveEvent`|漂浮物移除（任何原因）|否|清理逻辑、记录统计|
### 9.2 公共 API
```
public final class HookedAPI {
    public static IDebrisManager getDebrisManager();
    public static void setLootModifier(Player player, Function<LootEntry, LootEntry> modifier);
}
```
## 10. 测试与调试建议

- 使用 `/hooked test hook` 命令快速在面前生成一个测试漂浮物，便于验证钩取逻辑和动画。
- 在配置中开启 `debug: true` 后，玩家可使用 `/hooked debug` 开启个人调试，所有相关日志将输出到控制台并可选择发送粒子效果标识漂浮物。
- 性能测试时，关注 `/hooked stats` 输出的实体数量与 TPS 影响，确保在 1000 个漂浮物下服务器仍稳定。


----
>此文档为 Hooked 插件的完整开发蓝图，涵盖了从效果预览到架构、规范、扩展的全部内容。AI 助手请严格按照上述要求生成代码，确保所有关键逻辑处包含调试日志，模块解耦，并优雅处理可选前置插件。项目源码根包应为 `com.hooked`。