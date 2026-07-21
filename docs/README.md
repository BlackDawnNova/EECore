# Endless Epoch Core (EECore) / 无尽纪元核心

A core API mod for Minecraft NeoForge 1.21.1 providing the Ω (Omega) energy system — a BigInteger-based, 12-tier voltage energy framework for Minecraft mod developers.

Minecraft NeoForge 1.21.1 核心 API 模组，提供 Ω（Omega）能量系统——基于 BigInteger 的 12 级电压能源框架。

## Overview / 概述

EECore defines the **Ω energy standard** (`1 Ω = 2 FE`) with 12 voltage tiers from ELV to QV. It provides the API, storage implementation, capability registration, multiblock machine registration, voltage-tier casing blocks, and visual effects — the wheel, not the car. Other mods integrate via capabilities without hard depending on EECore classes.

EECore 定义了 **Ω 能量标准**（`1 Ω = 2 FE`），12 级电压 ELV~QV。提供 API、存储实现、Capability 注册、多方块机器注册、电压外壳方块、视觉特效——轮子，不造车。其他 Mod 通过 Capability 接入，无需硬依赖 EECore 类。

## Features / 特性

- **12 voltage tiers / 12 级电压**: ELV (Steam), LV, MV, HV, EHV, UHV, PHV, XHV, PLV, SV, BV, QV
- **BigInteger arithmetic / 大数运算**: no upper limit for production use (hard cap at 10¹⁰⁰⁰ / 硬上限 10¹⁰⁰⁰)
- **Per-tier energy tracking / 分层能量追踪**: machines store energy by tier, extract from higher tiers / 按电压等级存储和提取
- **Voltage step-down / 电压降压**: automatic with configurable loss factor / 自动降压，可配置损耗
- **Capability integration / Capability 集成**: `EECoreCapabilities.OMEGA_ENERGY` for cross-mod access / 跨 Mod 能量访问
- **Event-driven / 事件驱动**: `EnergyTransferEvent` fires on every receive/extract / 每次收发触发事件
- **FE compatibility / FE 兼容**: full conversion (1 Ω = 2 FE) with BigInteger precision / 完整换算
- **Machine registration / 机器注册**: `MultiblockLoader` builder API — load `.ecs` structure, define tier & name, auto-generate controller item & block model / 一行注册机器
- **Voltage-tier casing blocks / 电压外壳方块**: 12 tier-colored casing blocks (side/top/bottom textures), auto-generated from tier hex colors / 每级不同颜色的外壳
- **Machine controller model / 机器控制器模型**: `ee_base_12_front_emissive` composite — tier casing body + machine overlay + emissive layer / 外壳贴图+机器面板+发光层
- **Directory-based textures / 目录制贴图**: `textures/block/machines/<id>/overlay_front.png` + `overlay_front_e.png`, auto-detected / 放贴图即用
- **3 creative tabs / 三创造标签**: Machines / 机器, Blocks / 方块, Items / 物品
- **Pluggable machine effects / 可插拔机器特效**: `IMachineEffect` + `MachineEffectRegistry`, built-in `eecore:celestial` / 内置日月星辰特效
- **NovaNet wireless network / NovaNet 无线网络**: transmitter/receiver nodes, laser links, distance attenuation
- **Multiblock structure system / 多方块结构系统**: scanner tool, 3D visualizer, back-face culling, fluid rendering, layer view
- **Animated text & items / 动画文字物品**: rainbow/blink/gradient tooltips
- **Pluggable team system / 可插拔队伍系统**: EECore built-in + FTB Teams bridge / 内置+FTB桥接
- **Creative tabs open for addon mods / 创造栏开放给附属 Mod**: `BuildCreativeModeTabContentsEvent` — addon items appear in EECore tabs / 附属物品进 EECore 标签
- **Ore system / 矿石系统**: one-line registration `new Material(...)`, auto-generates textures+models+tags+lang, 5 random spot variants per ore, dynamic stone-base rendering from block below / 一行Material定义全自动生成贴图+模型+标签+翻译，5种随机矿斑变体，石底动态渲染

## Quick Start / 快速开始

**Use as energy API / 作为能量 API 使用:**
```java
// Read energy from any block entity via capability
IOmegaEnergyStorage storage = level.getCapability(
    EECoreCapabilities.OMEGA_ENERGY, pos, side);
OmegaValue energy = storage.getEnergyStored(VoltageTier.LV);
```

**Register a multiblock machine / 注册多方块机器:**
```java
// Fixed format / 固定式
MultiblockLoader.load(ResourceLocation.parse("mymod:my_machine"))
    .name("My Machine", "我的机器")
    .tier(1)  // LV casing
    .where("busTag", ModBlocks.INPUT_BUS.get())
        .or(ModBlocks.OUTPUT_BUS.get())
    .limit("busTag", ModBlocks.INPUT_BUS.get(), 2)
    .limit("busTag", ModBlocks.OUTPUT_BUS.get(), 1)
    .register(ResourceLocation.parse("mymod:my_machine"));

// Frame-based / 框架式
FrameMachineLoader.load(ResourceLocation.parse("mymod:my_frame_machine"))
    .name("My Frame Machine", "我的框架机")
    .tier(1)
    .frame("CASING", 3, 3, 3)               // required: TAG + inner W×H×D (shell = inner+2) / 必调：标签+内部宽高深
    .where("CASING", ModBlocks.MY_CASING.get())
    .where("CORE", ModBlocks.MY_CORE.get())
    .limit("CORE", ModBlocks.MY_CORE.get(), 2)
    .register(ResourceLocation.parse("mymod:my_frame_machine"));
```

**Place machine textures / 放置机器贴图:**
```
assets/<modid>/textures/block/machines/<machine_id>/
  overlay_front.png       ← front panel design / 面板图案
  overlay_front_e.png     ← emissive version (optional) / 发光版（可选）
```

## Documentation / 文档

- [Getting Started / 快速开始](api/getting-started.md)
- [Energy System API / 能量系统 API](api/energy-system.md)
- [Multiblock System / 多方块系统](api/multiblock.md)
- [NovaNet Energy Network / 能量网络](api/novanet.md)
- [Emissive API / 发光渲染](api/emissive.md)
- [Team System / 队伍系统](api/team.md)
- [Ore System / 矿石系统](api/ore.md)
- [Animation API / 动画工具](api/animation.md)

## Dependency / 依赖

NeoForge: `21.1.234+`
