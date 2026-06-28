# 无尽纪元核心 (EECore)

**无尽纪元核心**

一个核心API模组，适用于Minecraft NeoForge 1.21.1，提供Ω（欧米伽）能量系统——一个基于BigInteger的，12级电压能量框架。

## 概述

EECore 定义了**Ω 能量标准** (`1 Ω = 2 FE`)，具有从 ELV 到 QV 的 12 个电压等级。它提供了 API、存储实现和能力注册——是车轮，而不是汽车。

其他 Mod 通过 EECore 接入Ω能量系统，无需硬依赖 EECore 类，只需使用 Capability 查询。

## 特性 / 特性

- **12 电压等级**：超高压，高压，中压，高压，特高压，超特高压，特高压，特高压，超高压，中压，低压，超低压，特低压
- **BigInteger 算术**：生产使用没有上限（硬性上限为 10¹⁰⁰⁰）
- **每等级能量跟踪**：机器按等级储存能量，需要时从高等级提取
- **Voltage step-down**: automatic with configurable loss factor
- **Capability integration**: `EECoreCapabilities.OMEGA_ENERGY` for cross-mod access
- **Event-driven**: `EnergyTransferEvent` fires on every receive/extract
- **FE compatibility**: full conversion (1 Ω = 2 FE) with BigInteger precision
- **MachineSpec API**: one-line machine spec declaration for other mods

## Documentation / 文档

- [Getting Started / 快速开始](api/getting-started.md)
- [Energy System API / 能量系统 API](api/energy-system.md)

## Dependency / 依赖

NeoForge: `21.1.234+`
