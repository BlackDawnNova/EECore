# Endless Epoch Core (EECore)

**无尽纪元核心**

A core API mod for Minecraft NeoForge 1.21.1 providing the Ω (Omega) energy system — a BigInteger-based, 12-tier voltage energy framework for Minecraft mod developers.

## Overview / 概述

EECore defines the **Ω energy standard** (`1 Ω = 2 FE`) with 12 voltage tiers from ELV to QV. It provides the API, storage implementation, and capability registration — the wheel, not the car.

其他 Mod 通过 EECore 接入 Ω 能量系统，无需硬依赖 EECore 类，只需使用 Capability 查询。

## Features / 特性

- **12 voltage tiers**: ELV, LV, MV, HV, EHV, UHV, PHV, XHV, PLV, SV, BV, QV
- **BigInteger arithmetic**: no upper limit for production use (hard cap at 10¹⁰⁰⁰)
- **Per-tier energy tracking**: machines store energy by tier, extract from higher tiers when needed
- **Voltage step-down**: automatic with configurable loss factor
- **Capability integration**: `EECoreCapabilities.OMEGA_ENERGY` for cross-mod access
- **Event-driven**: `EnergyTransferEvent` fires on every receive/extract
- **FE compatibility**: full conversion (1 Ω = 2 FE) with BigInteger precision
- **MachineSpec API**: one-line machine spec declaration for other mods
- **NovaNet wireless network**: transmitter/receiver nodes, laser links, distance attenuation
- **Multiblock structure system**: scanner tool, 3D visualizer, click-to-inspect, controller highlight
- **Animated text & items**: rainbow/blink/gradient tooltips for other mods
- **Pluggable team system**: EECore built-in + FTB Teams bridge

## Documentation / 文档

- [Getting Started / 快速开始](api/getting-started.md)
- [Energy System API / 能量系统 API](api/energy-system.md)
- [NovaNet Energy Network / 能量网络](api/novanet.md)
- [Multiblock Structure System / 多方块结构](api/multiblock.md)
- [Team System / 队伍系统](api/team.md)
- [Animation API / 动画工具](api/animation.md)

## Dependency / 依赖

NeoForge: `21.1.234+`
