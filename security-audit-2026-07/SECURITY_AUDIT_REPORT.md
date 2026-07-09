# StarCore 安全审计报告

**审计时间**: 2026-07-10  
**扫描文件数**: 677+ Java 文件  
**审计工具**: Claude Code Security Scanner

---

## 漏洞统计

| 严重级别 | 数量 | 状态 |
|---------|------|------|
| CRITICAL | 2 | 待修复 |
| HIGH | 3 | 待修复 |
| MEDIUM | 15 | 待修复 |
| LOW | 8 | 待修复 |
| **总计** | **28** | |

---

## 严重级别: CRITICAL

### V-001: 硬编码默认密钥

**文件**: `src/main/java/dev/starcore/starcore/core/config/ConfigurationService.java`  
**行号**: 25-26

**问题描述**:
```java
private static final String DEFAULT_MAP_WEB_ACCESS_SECRET = "change-this-secret";
private static final String DEFAULT_REST_API_SIGNING_SECRET = "change-this-secret";
```

**CWE**: CWE-798 (使用硬编码凭证)  
**风险**: 如果服务器管理员未修改默认密钥，攻击者可以伪造 Web API 访问令牌

**修复建议**:
1. 在首次启动时生成随机密钥（已有 `generateSecret()` 方法）
2. 检测到默认密钥时拒绝启动服务
3. 在 `ensureMapWebAccessSecretConfigured()` 返回 true 时阻止 Web 服务启动

---

## 严重级别: HIGH

### V-002: 不安全的反序列化

**文件**: `src/main/java/dev/starcore/starcore/module/army/prisoner/PrisonerServiceImpl.java`  
**行号**: 435-437

**问题描述**:
```java
try (ObjectInputStream ois = new ObjectInputStream(
    new BufferedInputStream(Files.newInputStream(file)))) {
    List<PrisonerOfWar> loaded = (List<PrisonerOfWar>) ois.readObject();
```

**CWE**: CWE-502 (不安全的反序列化)  
**风险**: 攻击者可构造恶意 `prisoners.dat` 文件执行任意代码

**修复建议**:
1. 使用自定义 ObjectInputStream 重写 `resolveClass()` 方法
2. 添加类白名单验证
3. 考虑迁移到 JSON/YAML 格式存储

---

### V-003: SQL 错误日志泄露数据

**文件**: `src/main/java/dev/starcore/starcore/pvp/duel/DuelService.java`  
**行号**: 1511

**问题描述**:
```java
logger.warning("SQL error loading stats for " + playerId + ": " + e.getMessage());
```

**CWE**: CWE-532 (通过日志的信息泄露)  
**风险**: 错误信息可能包含 SQL 语句、数据库结构等敏感信息

**修复建议**:
```java
logger.warning("SQL error loading stats for player: " + playerId);
logger.debug("SQL error details: " + e.getMessage());
```

---

## 严重级别: MEDIUM

### V-004 到 V-012: Integer.parseInt 无异常处理

**影响文件**: 9 个命令文件

| 文件 | 行号 |
|------|------|
| MigrationCommand.java | 129 |
| EssentialsGuiCommand.java | 80, 103, 121 |
| EconomyCommand.java | 129 |
| ParliamentCommand.java | 161 |
| CourtCommand.java | 105, 330, 551, 674, 850, 988, 1034 |
| RankingCommand.java | 210, 353 |
| DonationCommand.java | 296 |
| TerritoryUpgradeCommand.java | 244 |
| QuestCommand.java | 81, 148 |

**问题描述**:
```java
int page = Integer.parseInt(args[0]);  // 如果 args[0] 不是数字会抛出 NumberFormatException
```

**CWE**: CWE-396 (未捕获的异常情况)  
**风险**: 玩家输入非数字参数导致命令崩溃

**修复建议**:
```java
if (args.length > 0) {
    try {
        int page = Integer.parseInt(args[0]);
        // 使用 page
    } catch (NumberFormatException e) {
        player.sendMessage("请输入有效的数字");
        return;
    }
}
```

---

### V-013 到 V-018: Broad Exception 捕获

**影响文件**: 6 个服务文件

**问题描述**:
```java
} catch (Exception e) {
    // 吞掉所有异常
}
```

**CWE**: CWE-391 (未检查的错误)  
**风险**: 关键错误被静默忽略，问题难以排查

**修复建议**:
```java
} catch (SpecificException e) {
    handleSpecificError(e);
} catch (Exception e) {
    logger.log(Level.WARNING, "Unexpected error", e);
    throw e; // 或重新抛出
}
```

---

### V-019: PlayerMoveEvent 性能问题

**影响文件**: 26 个监听器

**问题描述**: 高频事件未优化可能导致服务器卡顿

**修复建议**:
1. 使用 `ignoreCancelled = true`
2. 使用 `priority = EventPriority.MONITOR`
3. 添加节流机制（已有 OptimizedMoveListener 示例）

---

## 严重级别: LOW

### V-020 到 V-028: 其他问题

| ID | 类型 | 文件 | 描述 |
|----|------|------|------|
| V-020 | 命名规范 | PrisonerServiceImpl.java:221 | `public String password = ""` 字段 |
| V-021 | 命名规范 | DatabasePool.java:221 | 密码字段命名 |
| V-022 | 代码质量 | 多个 Listener | 事件处理方法命名不规范 |
| V-023 | 性能 | 133 个 @EventHandler | 事件处理可能过于频繁 |
| V-024 | 配置 | config.yml | 默认配置安全性检查 |
| V-025 | 注释 | 多处 TODO | 遗留的 TODO 未完成 |
| V-026 | 依赖 | pom.xml | 依赖版本检查 |
| V-027 | 日志 | 多个文件 | 日志级别不当 |
| V-028 | 错误处理 | 命令执行 | 缺少错误消息国际化 |

---

## 修复优先级

### 第一优先级 (立即修复)
1. V-001: 硬编码密钥 - 阻止使用默认密钥
2. V-002: 不安全反序列化 - 防止 RCE 攻击

### 第二优先级 (本周内)
3. V-003: SQL 日志泄露
4. V-004 到 V-012: parseInt 异常处理
5. V-013 到 V-018: Broad Exception 处理

### 第三优先级 (计划中)
6. V-019: PlayerMoveEvent 优化
7. V-020 到 V-028: 其他改进

---

## 附录: CWE 参考

| CWE | 名称 |
|-----|------|
| CWE-78 | OS 命令注入 |
| CWE-79 | 跨站脚本 (XSS) |
| CWE-89 | SQL 注入 |
| CWE-94 | 代码注入 |
| CWE-502 | 不安全的反序列化 |
| CWE-532 | 通过日志的信息泄露 |
| CWE-798 | 使用硬编码凭证 |
| CWE-835 | 无限循环 |

---

*报告生成时间: 2026-07-10*
