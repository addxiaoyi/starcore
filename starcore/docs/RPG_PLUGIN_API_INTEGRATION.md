# STARCORE RPG 插件 API 接入说明

本文面向任务、副本、活动、RPG 职业等外部插件作者。目标是让外部插件直接把奖励发到 STARCORE 国家金库，并写入独立账本类型 `treasury.reward`，而不是模拟玩家执行命令。

## 依赖方式

外部插件的 `plugin.yml` 建议声明软依赖，确保 STARCORE 先加载时可以直接取到 API：

```yaml
softdepend: [STARCORE]
```

如果你的插件必须依赖 STARCORE 才能启动，可以改成：

```yaml
depend: [STARCORE]
```

构建时把 STARCORE jar 作为 `provided` 依赖使用，不要把 STARCORE 打进你的插件 jar。实际运行时由服务器 `plugins` 目录里的 STARCORE 提供 API 类。

## 获取 STARCORE API

STARCORE 启动后会通过 Bukkit `ServicesManager` 注册 `dev.starcore.starcore.api.StarCoreApi`。

```java
import dev.starcore.starcore.api.StarCoreApi;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;

public final class StarCoreHook {
    public Optional<StarCoreApi> api() {
        RegisteredServiceProvider<StarCoreApi> registration =
            Bukkit.getServicesManager().getRegistration(StarCoreApi.class);
        if (registration == null) {
            return Optional.empty();
        }
        return Optional.of(registration.getProvider());
    }
}
```

## 给国家发 RPG 奖励

推荐流程：

1. 通过 `StarCoreApi.nationService()` 按国家名查国家。
2. 通过 `StarCoreApi.treasuryRewardService()` 获取奖励服务。
3. 调用 `reward(nation.id(), amount, actor, reason)`。

```java
import dev.starcore.starcore.api.StarCoreApi;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.treasury.TreasuryRewardResult;
import dev.starcore.starcore.module.treasury.TreasuryRewardService;

import java.math.BigDecimal;
import java.util.Optional;

public final class DungeonRewardBridge {
    private final StarCoreHook hook;

    public DungeonRewardBridge(StarCoreHook hook) {
        this.hook = hook;
    }

    public boolean rewardNationForDungeon(String nationName, BigDecimal amount, String dungeonName) {
        Optional<StarCoreApi> apiOptional = hook.api();
        if (apiOptional.isEmpty()) {
            return false;
        }

        StarCoreApi api = apiOptional.get();
        Optional<NationService> nationService = api.nationService();
        Optional<TreasuryRewardService> rewardService = api.treasuryRewardService();
        if (nationService.isEmpty() || rewardService.isEmpty()) {
            return false;
        }

        Optional<Nation> nation = nationService.get().nationByName(nationName);
        if (nation.isEmpty()) {
            return false;
        }

        TreasuryRewardResult result = rewardService.get().reward(
            nation.get().id(),
            amount,
            "DungeonPlugin",
            "副本通关: " + dungeonName
        );

        return result.balance().signum() >= 0;
    }
}
```

## 结果字段

`TreasuryRewardResult` 包含：

- `nationId`: 收到奖励的国家 ID。
- `amount`: 实际入账金额，STARCORE 会按 2 位小数向下规范化。
- `balance`: 入账后的国家金库余额。
- `eventRecorded`: 是否成功写入 `treasury.reward` 账本事件。

`eventRecorded=false` 不代表入账失败。它表示当前没有可用的 `EventService`，STARCORE 仍会完成金库入账。外部插件可以把这个状态写进自己的日志，方便服主排查模块配置。

## 推荐 reason 格式

`reason` 会进入国家账本 context。建议写成玩家和系统都能看懂的短文本：

```text
副本通关: Ancient Ruins
任务奖励: 主线-王国边境
活动奖励: 周末世界 Boss
```

避免传入超长 JSON、大量换行或包含敏感数据的文本。STARCORE 会清理 `;` 和换行，防止账本 context 被破坏。

## 常见失败情况

- `StarCoreApi` 为空：STARCORE 未安装、未启用，或你的插件在 STARCORE 注册 API 前尝试获取。
- `nationService()` 为空：国家模块被配置关闭。
- `treasuryRewardService()` 为空：金库模块被配置关闭，或 STARCORE 版本过旧。
- `nationByName(...)` 为空：国家名不存在，或传入的是显示名/别名而非 STARCORE 国家名。
- `amount` 非正数：STARCORE 会抛出 `IllegalArgumentException`，外部插件应在调用前校验金额。

## 最低兼容建议

如果你的插件想兼容旧版 STARCORE：

- 优先调用 `treasuryRewardService()`。
- 服务不存在时提示服主升级 STARCORE。
- 不建议 fallback 到执行 `/sc tr rw`，因为命令权限、语言、控制台上下文和未来命令结构都可能变化。
