# StarCore Geyser 集成增强计划

> 版本: 0.1.0 | 更新: 2026/07/02 | 状态: 规划中

---

## 1. 现状分析

### 1.1 现有实现

| 组件 | 状态 | 说明 |
|------|------|------|
| GeyserIntegration | 已实现 | 使用反射调用，兼容双 API 版本 |
| IntegrationManager | 已集成 | 在启动时初始化 |
| 基岩版检测 | 基础 | 仅 `isBedrockPlayer()` 方法 |
| 使用范围 | 有限 | 仅 GeyserIntegration 内部使用 |

### 1.2 当前架构

```
IntegrationManager
    └── GeyserIntegration (反射调用)
            ├── isBedrockPlayer() [未在其他模块使用]
            ├── getPlayerType()
            └── getDeviceInfo()
```

### 1.3 已知限制

| 功能 | 基岩版限制 | 影响范围 |
|------|-----------|----------|
| 聊天输入 | 无法直接发送斜杠命令 | 命令系统 |
| NBT 数据 | 支持受限 | 物品/容器系统 |
| 书与笔 | 支持有限 | 邮件/写书功能 |
| 重命名物品 | 字符限制 | 领地命名等 |
| 着色文本 | 符号代码不同 | 消息显示 |
| 鞘翅飞行 | 不支持 |  ElytraFlight |

---

## 2. 集成目标

### 2.1 短期目标 (v0.1.1)

- [ ] Maven 依赖配置
- [ ] 增强 GeyserIntegration
- [ ] 基岩版检测服务
- [ ] 平台适配监听器

### 2.2 中期目标 (v0.2.0)

- [ ] 基岩版表单系统
- [ ] 兼容层设计
- [ ] 配置化适配规则
- [ ] 开发者 API

### 2.3 长期目标 (v1.0.0)

- [ ] 完整功能适配
- [ ] 性能优化
- [ ] 测试覆盖

---

## 3. 详细实现计划

### 3.1 Maven 依赖配置

**文件**: `starcore/pom.xml`

```xml
<!-- 在 <repositories> 添加 -->
<repository>
    <id>geysermc</id>
    <url>https://download.geysermc.org/repo/maven</url>
</repository>

<!-- 在 <dependencies> 添加 -->
<!-- Geyser API - 类型安全集成 (可选, 使用 provided 避免硬依赖) -->
<!--
<dependency>
    <groupId>org.geysermc.geyser</groupId>
    <artifactId>api</artifactId>
    <version>2.10.0-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
-->

<!-- 推荐: 保持反射调用方式，添加依赖说明 -->
<!-- 
    当前使用反射方式集成 Geyser/Floodgate，无需编译依赖。
    如需升级为类型安全 API:
    1. 取消上方注释
    2. 修改 GeyserIntegration 使用 GeyserApi.api()
    3. 修改 FloodgatePlayerApi 使用 FloodgateApi.getInstance()
-->
```

### 3.2 增强 GeyserIntegration

**文件**: `starcore/src/main/java/dev/starcore/starcore/integration/geyser/GeyserIntegration.java`

```java
// 需要添加的方法:

// 1. 获取 GeyserConnection (通过反射)
public GeyserConnection getConnection(Player player)
public GeyserConnection getConnection(UUID uuid)

// 2. 获取基岩版原生用户名
public String getBedrockUsername(Player player)
public String getBedrockUsername(UUID uuid)

// 3. 获取设备信息 (详细)
public DeviceInfo getDeviceInfoDetailed(Player player)

// 4. 获取 XUID
public long getXuid(Player player)
public long getXuid(UUID uuid)

// 5. 检查账号绑定状态
public boolean isLinkedToJava(Player player)

// 6. 获取语言设置
public String getLocale(Player player)

// 7. 获取协议版本
public int getProtocolVersion(Player player)

// 8. 发送基岩版原生表单
public void sendModalForm(Player player, ModalForm form)
public void sendSimpleForm(Player player, SimpleForm form)
public void sendCustomForm(Player player, CustomForm form)

// 9. 平台特定消息
public void sendPlatformMessage(Player player, String javaMsg, String bedrockMsg)
```

### 3.3 基岩版检测服务

**新文件**: `starcore/src/main/java/dev/starcore/starcore/integration/geyser/BedrockPlayerService.java`

```java
/**
 * 基岩版玩家检测服务
 * 提供统一的平台检测接口
 */
public class BedrockPlayerService {
    
    private final GeyserIntegration geyser;
    
    // 缓存基岩版玩家 UUID
    private final Cache<UUID, Boolean> bedrockCache;
    
    /**
     * 检查玩家是否为基岩版
     */
    public boolean isBedrock(Player player) {
        UUID uuid = player.getUniqueId();
        
        // 检查缓存
        Boolean cached = bedrockCache.getIfPresent(uuid);
        if (cached != null) return cached;
        
        // 检测并缓存
        boolean result = geyser.isBedrockPlayer(uuid);
        bedrockCache.put(uuid, result);
        return result;
    }
    
    /**
     * 获取平台类型
     */
    public PlatformType getPlatform(Player player) {
        if (isBedrock(player)) {
            return PlatformType.BEDROCK;
        }
        return PlatformType.JAVA;
    }
    
    /**
     * 获取显示名称 (考虑基岩版用户名)
     */
    public String getDisplayName(Player player) {
        if (isBedrock(player)) {
            String bedrockName = geyser.getBedrockUsername(player);
            if (bedrockName != null) {
                return bedrockName + " (基岩版)";
            }
        }
        return player.getName();
    }
}

/**
 * 平台类型枚举
 */
public enum PlatformType {
    JAVA,
    BEDROCK
}

/**
 * 设备信息
 */
public record DeviceInfo(
    String deviceOs,      // ANDROID, IOS, WINDOWS, etc.
    String inputMode,     // KEYBOARD_MOUSE, TOUCH, GAMEPAD
    String locale,        // zh_CN, en_US
    long xuid,
    boolean linked
) {}
```

### 3.4 平台适配监听器

**新文件**: `starcore/src/main/java/dev/starcore/starcore/integration/geyser/listener/PlatformAdaptationListener.java`

```java
/**
 * 平台适配监听器
 * 自动处理基岩版兼容性问题
 */
public class PlatformAdaptationListener implements Listener {
    
    private final BedrockPlayerService bedrockService;
    private final StarCorePlugin plugin;
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        if (bedrockService.isBedrock(player)) {
            // 基岩版玩家加入处理
            handleBedrockJoin(player);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        
        if (bedrockService.isBedrock(player)) {
            // 基岩版无法直接使用命令
            event.setCancelled(true);
            
            // 发送提示使用表单
            sendCommandHelp(player, event.getMessage());
        }
    }
    
    private void handleBedrockJoin(Player player) {
        // 检查是否绑定 Java 账号
        if (!geyser.isLinkedToJava(player)) {
            // 发送绑定提示
            sendLinkPrompt(player);
        }
        
        // 发送欢迎消息 (基岩版友好)
        player.sendMessage("§a=== 欢迎来到 StarCore ===");
        player.sendMessage("§e检测到您正在使用基岩版客户端");
        player.sendMessage("§7部分功能可能需要使用表单交互");
    }
    
    private void sendCommandHelp(Player player, String command) {
        // 为基岩版玩家提供替代交互方式
        player.sendMessage("§c基岩版不支持直接输入命令");
        player.sendMessage("§e请使用菜单按钮或联系管理员");
    }
}
```

### 3.5 兼容层设计

**新文件**: `starcore/src/main/java/dev/starcore/starcore/integration/geyser/compat/PlatformCompatLayer.java`

```java
/**
 * 平台兼容层
 * 根据平台类型提供不同的实现
 */
public class PlatformCompatLayer {
    
    private final BedrockPlayerService platformService;
    
    /**
     * 获取适合平台的文本
     */
    public Text getPlatformText(Player player, String javaText, String bedrockText) {
        if (platformService.isBedrock(player)) {
            // 基岩版不支持 ANSI 颜色代码
            return Text.of(convertToLegacy(bedrockText));
        }
        return Text.of(javaText);
    }
    
    /**
     * 获取适合平台的标题
     */
    public Component getPlatformTitle(Player player, String javaTitle, String bedrockTitle) {
        if (platformService.isBedrock(player)) {
            // 基岩版标题长度限制
            String truncated = truncate(bedrockTitle, 64);
            return Component.text(truncated);
        }
        return Component.text(javaTitle);
    }
    
    /**
     * 获取适合平台的物品名称
     */
    public String getPlatformItemName(Player player, String name) {
        if (platformService.isBedrock(player)) {
            // 基岩版物品名称长度限制
            return truncate(name, 48);
        }
        return name;
    }
    
    /**
     * 转换 ANSI 颜色代码为基岩版格式
     */
    private String convertToLegacy(String text) {
        // 移除 § 符号，保留基本文本
        return text.replaceAll("§[0-9a-fk-or]", "");
    }
    
    /**
     * 截断文本
     */
    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}
```

### 3.6 配置化适配规则

**新文件**: `starcore/src/main/resources/bedrock-compat.yml`

```yaml
# 基岩版兼容配置

# 启用/禁用基岩版支持
enabled: true

# 平台检测
detection:
  # 缓存超时 (秒)
  cache-ttl: 300
  # 使用反射检测 (false=使用API依赖)
  use-reflection: true

# 功能适配
adaptations:
  # 聊天/命令
  commands:
    # 基岩版玩家是否可以使用命令
    allow-direct-commands: false
    # 替代交互方式
    fallback: "form"  # form | message | disabled
  
  # 文本/消息
  text:
    # 移除颜色代码
    strip-colors: true
    # 截断过长文本
    truncate: true
    # 最大标题长度
    max-title-length: 64
    # 最大物品名称长度
    max-item-name-length: 48
  
  # 物品/NBT
  items:
    # 简化 NBT 数据
    simplify-nbt: true
    # 限制特殊字符
    restrict-special-chars: true
  
  # 表单交互
  forms:
    # 启用原生表单
    enable-native-forms: true
    # 表单超时 (秒)
    timeout: 30

# 消息配置
messages:
  join:
    # 基岩版欢迎消息
    welcome: "&a=== &eStarCore &a===\n&7欢迎加入服务器!"
  link:
    # 账号绑定提示
    prompt: "&e请使用 &b/link &e绑定 Java 账号"
  commands:
    # 命令不可用提示
    disabled: "&c基岩版不支持此命令\n&e请使用菜单交互"

# 设备类型处理
devices:
  # 移动端特殊处理
  mobile:
    # 增大点击区域
    enlarge-buttons: true
    # 简化界面
    simplify-ui: false
  
  # 游戏主机
  console:
    # 使用控制器优化
    controller-optimized: true
```

### 3.7 开发者 API

**新文件**: `starcore/src/main/java/dev/starcore/starcore/api/BedrockApi.java`

```java
/**
 * StarCore 基岩版支持公共 API
 * 供其他插件或模块使用
 */
public interface BedrockApi {
    
    /**
     * 检查玩家是否为基岩版
     */
    boolean isBedrockPlayer(Player player);
    
    /**
     * 获取玩家平台
     */
    PlatformType getPlatform(Player player);
    
    /**
     * 获取设备信息
     */
    Optional<DeviceInfo> getDeviceInfo(Player player);
    
    /**
     * 获取基岩版原生用户名
     */
    Optional<String> getBedrockUsername(Player player);
    
    /**
     * 检查是否绑定 Java 账号
     */
    boolean isLinkedToJava(Player player);
    
    /**
     * 获取平台特定文本
     */
    String getPlatformText(Player player, String javaText, String bedrockText);
    
    /**
     * 发送模态表单
     */
    void sendModalForm(Player player, ModalForm form, FormResponseHandler handler);
    
    /**
     * 发送简单表单
     */
    void sendSimpleForm(Player player, SimpleForm form, FormResponseHandler handler);
}
```

---

## 4. 实现优先级

### 阶段 1: 基础集成 (1-2 天)

| 任务 | 优先级 | 工作量 |
|------|--------|--------|
| 更新 pom.xml 依赖 | P0 | 10 分钟 |
| 增强 GeyserIntegration | P0 | 2 小时 |
| 创建 BedrockPlayerService | P0 | 2 小时 |
| 创建 PlatformAdaptationListener | P1 | 3 小时 |

### 阶段 2: 兼容层 (2-3 天)

| 任务 | 优先级 | 工作量 |
|------|--------|--------|
| 创建 PlatformCompatLayer | P1 | 3 小时 |
| 创建 bedrock-compat.yml | P1 | 1 小时 |
| 实现配置加载 | P1 | 2 小时 |
| 添加 PlaceholderAPI 支持 | P2 | 2 小时 |

### 阶段 3: 表单系统 (3-5 天)

| 任务 | 优先级 | 工作量 |
|------|--------|--------|
| 实现 ModalForm 支持 | P2 | 4 小时 |
| 实现 SimpleForm 支持 | P2 | 4 小时 |
| 实现 CustomForm 支持 | P2 | 4 小时 |
| 表单响应处理 | P2 | 3 小时 |

### 阶段 4: 完善与测试 (5-7 天)

| 任务 | 优先级 | 工作量 |
|------|--------|--------|
| 创建 BedrockApi | P2 | 2 小时 |
| 添加单元测试 | P1 | 4 小时 |
| 集成测试 | P1 | 6 小时 |
| 文档更新 | P2 | 3 小时 |

---

## 5. 已知限制与处理

### 5.1 基岩版限制清单

| 限制 | 严重程度 | 处理方案 |
|------|----------|----------|
| 无法直接输入命令 | 高 | 使用表单替代，菜单按钮 |
| NBT 支持受限 | 中 | 简化数据结构，使用基础 NBT |
| 书与笔功能有限 | 中 | 使用表单输入替代 |
| 物品名称长度限制 | 低 | 截断或使用符号替代 |
| 某些颜色代码不支持 | 低 | 转换或移除 |
| 鞘翅飞行不支持 | 中 | 检测平台并提示 |
| 潜影盒无法打开 | 高 | 禁用或使用替代方案 |

### 5.2 兼容性矩阵

| 功能模块 | Java 版 | 基岩版 | 需要适配 |
|----------|---------|--------|----------|
| 国家系统 | 完整 | 完整 | 部分 |
| 领地系统 | 完整 | 完整 | 无 |
| 外交系统 | 完整 | 完整 | 无 |
| 军队系统 | 完整 | 基础 | 表单 |
| 交易系统 | 完整 | 基础 | 表单 |
| 邮件系统 | 完整 | 限制 | 简化 |
| 成就系统 | 完整 | 完整 | 无 |
| 社交系统 | 完整 | 基础 | 部分 |

---

## 6. 测试计划

### 6.1 单元测试

```java
@Test
public void testBedrockDetection() {
    GeyserIntegration geyser = new GeyserIntegration(mockPlugin);
    
    UUID javaUuid = UUID.fromString("a1b2c3d4-1234-5678-90ab-cdef00000001");
    UUID bedrockUuid = UUID.fromString("a1b2c3d4-1234-4678-90ab-cdef00000002");
    
    // 模拟基岩版 UUID
    when(mockFloodgateApi.isBedrockPlayer(javaUuid)).thenReturn(false);
    when(mockFloodgateApi.isBedrockPlayer(bedrockUuid)).thenReturn(true);
    
    assertFalse(geyser.isBedrockPlayer(javaUuid));
    assertTrue(geyser.isBedrockPlayer(bedrockUuid));
}
```

### 6.2 集成测试

```java
@Test
public void testPlatformAdaptation() {
    // 测试基岩版玩家获得正确适配
    Player bedrockPlayer = mockBedrockPlayer();
    when(bedrockService.isBedrock(bedrockPlayer)).thenReturn(true);
    
    String result = compatLayer.getPlatformText(
        bedrockPlayer,
        "§aHello Java!",
        "Hello Bedrock!"
    );
    
    // 颜色代码应该被移除
    assertEquals("Hello Bedrock!", result);
}
```

### 6.3 手动测试清单

- [ ] 基岩版玩家加入服务器
- [ ] 基岩版玩家查看国家信息
- [ ] 基岩版玩家使用领地命令
- [ ] 基岩版玩家打开菜单
- [ ] 基岩版玩家发送邮件
- [ ] 基岩版玩家参与战争
- [ ] 混合服务器 (Java + 基岩版)

---

## 7. 风险评估

| 风险 | 可能性 | 影响 | 缓解措施 |
|------|--------|------|----------|
| API 版本变化 | 中 | 中 | 使用反射，保持兼容性 |
| 基岩版协议更新 | 低 | 高 | 跟进 Geyser 更新 |
| 性能影响 | 低 | 低 | 缓存机制 |
| 配置复杂 | 中 | 低 | 简化默认值 |

---

## 8. 文档更新

- [ ] 更新 CLAUDE.md 添加基岩版支持说明
- [ ] 更新 docs/MODULE_INDEX.md
- [ ] 添加 docs/BEDROCK_COMPAT.md
- [ ] 添加开发者 API 文档
- [ ] 添加故障排除指南

---

## 9. 版本路线图

```
v0.1.1 (1-2 周)
├── GeyserIntegration 增强
├── BedrockPlayerService
├── PlatformAdaptationListener
└── 基础兼容层

v0.2.0 (3-4 周)
├── 表单系统
├── 配置化适配
├── PlaceholderAPI 增强
└── 开发者 API

v1.0.0 (发布前)
├── 完整功能适配
├── 性能优化
├── 测试覆盖
└── 文档完善
```

---

*本计划基于 GeyserMC 官方文档和 StarCore 代码分析生成*
*如有问题，请参考 `docs/Geyser-API-Integration-Guide.md`*
