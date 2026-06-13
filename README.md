<div align="center">

# 🎣 Hooked

**木筏求生 — 漂浮物收集系统**

在 Minecraft 海洋中漂流收集资源的沉浸式体验

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21-blue?logo=minecraft)](https://www.minecraft.net)
[![Paper](https://img.shields.io/badge/Paper-1.21-yellow?logo=paper)](https://papermc.io)
[![Folia](https://img.shields.io/badge/Folia-✓-brightgreen)](https://papermc.io/software/folia)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

</div>

---
> *~~在海水中打捞浮木~~* ( bushi

## 📖 简介

**Hooked** 是一款 Paper 1.21+ 服务器插件，在无尽海洋中持续生成可漂流的方块型资源实体。玩家手持钓鱼竿抛钩，将漂浮的木板、木桶、树叶团等资源钩取到面前，获得生存物资。完美还原《木筏求生》(Raft) 的漂浮物收集体验。

## ✨ 特性

- **无尽海面，资源涌现** — 海洋表面持续生成漂流资源，永不枯竭
- **动态漂流系统** — 漂浮物随海浪漂流、随机转向，遇到障碍自动绕行
- **真实钩取手感** — 瞄准抛竿 → 命中判定 → 拉回动画，清晰的操作反馈链
- **多人互不干扰** — 独立归属权，杜绝抢资源纠纷；掉线自动释放
- **高性能架构** — BlockDisplay 轻量实体 + 空间索引分桶，百级漂浮物不影响 TPS
- **丰富配置** — 可自定义漂浮物种类、权重、掉落表、漂流速度等
- **插件集成** — 支持 ItemsAdder（自定义模型）、WorldGuard（区域保护）、BentoBox（海岛）
- **事件 API** — 第三方插件可干涉生成、钩取、掉落全过程

## 🎮 命令

| 命令 | 功能 | 权限 |
|:---|:---|:---:|
| `/hooked reload` | 重载配置文件 | `hooked.admin` |
| `/hooked debug` | 切换个人调试模式 | `hooked.debug` |
| `/hooked stats` | 查看插件统计数据 | `hooked.admin` |
| `/hooked test hook` | 在准星处生成测试漂浮物 | `hooked.admin` |

## 🔐 权限

| 权限节点 | 描述 | 默认 |
|:---|:---|:---:|
| `hooked.admin` | 管理权限（重载/统计/测试） | `op` |
| `hooked.debug` | 调试权限 | `op` |

## ⚙️ 配置文件

插件在首次运行时自动生成 `config.yml`，所有选项均有安全默认值。

### 主设置

```yaml
# 调试模式开关
debug: false
```

### 漂浮物参数

```yaml
debris:
  max_per_player: 25        # 每玩家最大漂浮物数量
  spawn_interval_seconds: 5 # 生成检测间隔
  spawn_distance_min: 16    # 最小生成距离
  spawn_distance_max: 48    # 最大生成距离
  despawn_distance: 128     # 自动消失距离
  drift_speed: 1.25         # 漂流速度（格/秒）
```

### 钩取参数

```yaml
hook:
  cooldown_seconds: 1.5  # 钩取冷却
  hit_radius: 2.0        # 判定半径
  grab_animation_speed: 0.5  # 拉回速度
```

### 自定义漂浮物类型

支持自定义方块外观、权重和掉落表：

```yaml
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
```

## 🔌 软依赖

| 插件 | 用途 |
|:---|:---|
| [ItemsAdder](https://www.spigotmc.org/resources/73355/) | 自定义模型支持 |
| [WorldGuard](https://dev.bukkit.org/projects/worldguard) | 区域保护集成 |
| [BentoBox](https://github.com/BentoBoxWorld/BentoBox) | 海岛插件集成 |
| [ProtocolLib](https://www.spigotmc.org/resources/19978/) | 数据包扩展 |

## 🛠 安装

1. 从 [Releases](https://github.com/AdLamb/Hooked/releases) 下载最新版本
2. 将 `Hooked.jar` 放入服务器的 `plugins/` 目录
3. 重启服务器或使用 Plugman 加载
4. 编辑 `plugins/Hooked/config.yml` 按需调整配置
5. 使用 `/hooked reload` 重载配置

## 🧑‍💻 开发者 API

```java
// 获取漂浮物管理器
IDebrisManager manager = HookedAPI.getDebrisManager();

// 监听事件
@EventHandler
public void onDebrisCollect(DebrisCollectEvent event) {
    // 修改掉落奖励
}
```

可用事件：

| 事件 | 触发时机 | 可取消 |
|:---|:---|:---:|
| `DebrisSpawnEvent` | 漂浮物生成前 | ✅ |
| `DebrisHookAttemptEvent` | 钩取尝试时 | ✅ |
| `DebrisHookSuccessEvent` | 钩取成功时 | ❌ |
| `DebrisCollectEvent` | 收集完成前 | ✅ |
| `DebrisRemoveEvent` | 漂浮物移除时 | ❌ |

## 📜 协议

本项目基于 MIT 协议开源。
