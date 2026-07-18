# 队伍系统 / Team System

能量网络所有权和共享的插拔式队伍系统。

---

## ITeamProvider

```java
public interface ITeamProvider {
    Optional<TeamData> getTeam(UUID playerId);      // 玩家所属队伍
    Optional<TeamData> getTeamById(UUID teamId);    // 按 ID 查队伍
    TeamData createTeam(UUID ownerId, String name); // 创建队伍
    boolean disbandTeam(UUID teamId, UUID requesterId);
    boolean invitePlayer(UUID teamId, UUID inviterId, UUID targetId);
    boolean kickPlayer(UUID teamId, UUID kickerId, UUID targetId);
    boolean transferOwnership(UUID teamId, UUID currentOwnerId, UUID newOwnerId);
    boolean setAlly(UUID teamIdA, UUID teamIdB, boolean ally);
    boolean shareNetwork(UUID playerA, UUID playerB); // 是否共享网络
    String getProviderName();
}
```

## 默认实现 / Default Implementation

`EECoreTeamProvider` — 内存中的简单队伍系统，适用于无 FTB Teams 时。

`FTBTeamsBridge` — 自动检测 FTB Teams 是否加载，加载时桥接。

## TeamData

```java
record TeamData(UUID teamId, String name, UUID owner, 
                Set<UUID> members, Set<UUID> allies) { }
```

不可变记录。使用 `withMember`、`withoutMember`、`withAlly` 创建变体。
