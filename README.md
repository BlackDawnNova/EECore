# EECore — 无尽纪元核心

**无尽纪元核心模组**

[![许可证](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.234-green)](https://neoforged.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-orange)](https://minecraft.net/)

EECore 是一个 Minecraft NeoForge 底层开发框架，核心提供 **Ω（Omega）能量系统**——基于 BigInteger、12 级电压体系的能量 API。


---


## 功能特性 / 功能特性

- **12 级电压体系**：ELV → QV（超低压到普朗克级） 
- **BigInteger 运算**：超大数值，理论上限 10¹⁰⁰⁰
- **能量存储 API**：`IOmegaEnergyStorage` + `OmegaStorage` 完整实现
- **跨模组接入**：`EECoreCapabilities.OMEGA_ENERGY` 能力
- **能量事件通知**：`EnergyTransferEvent` 每次收/发触发
- **机器规格声明**：`MachineSpec` 一行代码创建能量存储
- **FE 兼容**：1 Ω = 2 FE，BigInteger 精度无损
- **中英双语 UI**：translatable 组件，自动适配客户端语言

---

## 快速开始 / Quick Start

附属 Mod 接入 Ω 能量只需三步：

### 声明机器规格 + 实现接口

```java
// 一行创建能量存储
OmegaStorage storage = MachineSpec.simple(VoltageTier.MV, 10_000, 128)
        .createStorage();

// BE 实现接口并委托给 storage
public class MyMachineBE extends BlockEntity implements IOmegaEnergyStorage {
    // 委托所有方法给 storage...
}
```

### 注册 Capability

```java
event.registerBlockEntity(EECoreCapabilities.OMEGA_ENERGY,
        MY_BE.get(), (be, side) -> be);
```

### 收发能量

```java
var cap = level.getCapability(EECoreCapabilities.OMEGA_ENERGY, pos, side);
if (cap != null) cap.receivePacket(new EnergyPacket(VoltageTier.MV, 1, 128), false);
```

> 完整文档见 [docs/api/getting-started.md](docs/api/getting-started.md)（中英双语）

---

## NovaNet 能量网络

无线能量传输框架。节点通过 Push 模式注册，支持发送端/接收端、激光链路、距离衰减。

→ [docs/api/novanet.md](docs/api/novanet.md)

## 多方块结构系统

扫描仪标记两角 → Shift+右键扫描生成 Pattern。3D Visualizer 支持旋转/缩放/点击选块。多控制器时穿透渲染红光柱。

→ [docs/api/multiblock.md](docs/api/multiblock.md)

## 文档 / Documentation

| 文档 | 说明 |
|------|------|
| [docs/README.md](docs/README.md) | 文档索引 |
| [docs/api/getting-started.md](docs/api/getting-started.md) | Ω 能量快速接入 |
| [docs/api/energy-system.md](docs/api/energy-system.md) | Ω 能量 API 全参 |
| [docs/api/novanet.md](docs/api/novanet.md) | NovaNet 无线网络 |
| [docs/api/multiblock.md](docs/api/multiblock.md) | 多方块结构系统 |
| [docs/api/team.md](docs/api/team.md) | 队伍系统 |
| [docs/api/animation.md](docs/api/animation.md) | 动画文字/物品 API |

---

## 技术栈 / Tech Stack

| 项目 | 版本 |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.234 |
| Java | 21 |
| Gradle | NeoGradle 2.0.141 |

---

## 许可 / License

[MIT](LICENSE) © 2026 ForiLusa
