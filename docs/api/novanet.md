# NovaNet 能量网络 / NovaNet Wireless Energy Network

NovaNet 是一个无线能量传输框架。节点（发送端/接收端）通过 push 模式注册到中央注册表，通过激光链路传输能量。

---

## 核心接口 / Core Interfaces

### INovaNode

任何参与 NovaNet 的方块实体实现此接口：

```java
public interface INovaNode {
    UUID getNodeId();       // 持久化节点 ID
    BlockPos getBlockPos(); // 世界坐标
    NodeType getNodeType(); // TRANSMITTER / RECEIVER / HUB / RELAY
    VoltageTier getTier();  // 电压等级（决定范围）
    int getRange();         // 覆盖半径（格）
    long getBufferEnergy(); // 当前缓存能量
    long getBufferCapacity();// 最大缓存
    UUID getTeamId();       // 所属队伍
}
```

### NodeType

```java
TRANSMITTER  // 收集附近发电机的能量 → 缓存 → 激光发送
RECEIVER     // 激光接收 → 缓存 → 分发到附近机器
HUB          // （Phase 2）跨维度枢纽
RELAY        // （Phase 2）跨维度中继
```

---

## 节点注册 / Node Registration

Push 模式：节点放置时注册，破坏时注销，无需每 tick 扫描。

```java
// 在 BlockEntity.onLoad() 或成形回调中：
NovaNodeRegistration.register(this);

// 在 setRemoved() 中：
NovaNodeRegistration.unregister(this);
```

注册表线程安全（`ConcurrentHashMap`）。

---

## 距离范围 / Range

范围 = `4 << Math.min(tier.ordinal(), 10)`：

| 等级 | 范围（格） |
|------|-----------|
| ELV  | 4 |
| LV   | 8 |
| MV   | 16 |
| HV   | 32 |
| EHV  | 64 |
| UHV  | 128+ |

可通过实现 `IRangeProvider` 替换范围计算。

---

## 距离衰减 / Attenuation

传输效率公式：

```
efficiency = 1 / (1 + α × distance²)
```

默认 α = 0.0005。可通过 `TransmitterRangeScanner.setAttenuationAlpha(alpha)` 配置。

---

## 激光链路 / Laser Link

使用 `LaserLinkCardItem` 绑定节点：

1. Shift+右键点击发送端 → 绑定
2. Shift+右键点击接收端 → 建立连接
3. Shift+右键空气 → 清除绑定

激光束渲染特性：
- 颜色 = 发送端电压等级色
- 粗细 = 当前功率
- 有能量流动时脉冲

通过 `LaserRenderConfig` 控制渲染距离和粒子密度。

---

## 策略注入 / Strategy Injection

通过 `INovaNetRegistry` 替换 NovaNet 的核心模块：

```java
registry.registerRouter(myRouter);          // 替换路径发现
registry.registerSecurityProvider(mySec);   // 替换安全认证
registry.registerCreditSystem(myCredit);    // 替换信用系统
registry.registerTeamProvider(myTeam);      // 替换队伍系统
```

当前默认实现均为 NOOP（Phase 3 实现）。

---

## 示例：附属 Mod 添加发送端 / Add a Transmitter

```java
public class MyTransmitterBE extends BlockEntity 
        implements INovaNode, IOmegaEnergyStorage {
    
    private UUID nodeId = UUID.randomUUID();
    private TransmitterEnergyBuffer buffer;
    private boolean formed;
    
    public MyTransmitterBE(BlockPos pos, BlockState state) {
        super(MY_TX_BE.get(), pos, state);
        this.buffer = new TransmitterEnergyBuffer(VoltageTier.MV);
    }
    
    // 成形后注册节点
    public void onFormed() {
        formed = true;
        NovaNodeRegistration.register(this);
    }
    
    // 破坏时注销
    @Override
    public void setRemoved() {
        if (formed) NovaNodeRegistration.unregister(this);
        super.setRemoved();
    }
    
    // === INovaNode ===
    @Override public UUID getNodeId() { return nodeId; }
    @Override public BlockPos getBlockPos() { return worldPosition; }
    @Override public NodeType getNodeType() { return NodeType.TRANSMITTER; }
    @Override public VoltageTier getTier() { return VoltageTier.MV; }
    @Override public int getRange() { return 16; }
    @Override public long getBufferEnergy() { return buffer.getStored().toLong(); }
    @Override public long getBufferCapacity() { return buffer.getCapacity().toLong(); }
    @Override public UUID getTeamId() { return null; }
}
```
