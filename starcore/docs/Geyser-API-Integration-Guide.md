# Geyser API 深度集成指南

> 适用于 StarCore v0.1.0 | GeyserMC 2.x / Floodgate 2.x

---

## 目录

1. [核心架构概览](#1-核心架构概览)
2. [Geyser API 核心类](#2-geyser-api-核心类)
3. [GeyserConnection 接口](#3-geyserconnection-接口)
4. [事件系统](#4-事件系统)
5. [玩家平台检测](#5-玩家平台检测)
6. [Floodgate API](#6-floodgate-api)
7. [基岩版特定功能](#7-基岩版特定功能)
8. [扩展开发接口](#8-扩展开发接口)
9. [依赖配置](#9-依赖配置)
10. [StarCore 集成示例](#10-starcore-集成示例)

---

## 1. 核心架构概览

Geyser 生态由两个核心组件组成:

```
┌─────────────────────────────────────────────────────────────┐
│                    Minecraft: Bedrock Edition                 │
│                         (玩家客户端)                          │
└─────────────────────────┬───────────────────────────────────┘
                          │ Bedrock 协议
┌─────────────────────────▼───────────────────────────────────┐
│                     Geyser-Spigot                            │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────────────┐  │
│  │ GeyserApi   │  │ EventBus   │  │ Session Management   │  │
│  │ (中央入口)   │  │ (事件系统)  │  │ (GeyserConnection)  │  │
│  └─────────────┘  └─────────────┘  └──────────────────────┘  │
└─────────────────────────┬───────────────────────────────────┘
                          │ 转发为 Java 协议
┌─────────────────────────▼───────────────────────────────────┐
│                     Spigot/Paper Server                       │
│                         + Floodgate                           │
│              (提供基岩版账号绑定/离线认证)                     │
└─────────────────────────────────────────────────────────────┘
```

### 两种集成方式

| 方式 | 说明 | 适用场景 |
|------|------|----------|
| **反射调用** | 运行时检测，无编译依赖 | StarCore 当前方案，兼容性强 |
| **API 依赖** | 编译时引入，提供完整类型安全 | 新开发项目，推荐使用 |

---

## 2. Geyser API 核心类

### 2.1 GeyserApi (主入口)

```java
// 获取 API 实例 (所有功能的入口)
GeyserApi api = GeyserApi.api();
```

**核心方法:**

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `api()` | `GeyserApi` | 获取 API 单例 |
| `isBedrockPlayer(UUID)` | `boolean` | 检查是否为基岩版玩家 |
| `connectionByUuid(UUID)` | `GeyserConnection` | 获取玩家连接 (可能为 null) |
| `sendForm(UUID, Form)` | `void` | 向基岩版玩家发送表单 |
| `onlineConnectionsCount()` | `int` | 获取在线基岩版玩家数 |
| `eventBus()` | `EventBus` | 获取事件总线 |

### 2.2 Base API (玩家信息接口)

`GeyserApi` 继承自 `BaseApi`，提供玩家级别的通用操作:

```java
// 继承关系
public interface GeyserApi extends BaseApi {
    // ...
}

public interface BaseApi {
    boolean isBedrockPlayer(UUID uuid);
    GeyserConnection connectionByUuid(UUID uuid);
    int onlineConnectionsCount();
    // ...
}
```

---

## 3. GeyserConnection 接口

`GeyserConnection` 代表一个基岩版玩家的连接会话:

```java
GeyserConnection connection = GeyserApi.api().connectionByUuid(player.getUniqueId());

if (connection != null) {
    // 玩家在线且为基岩版
}
```

### 3.1 可用属性 (通过反射获取)

```java
// 获取连接后，可通过反射获取以下属性
connection.getClass().getMethod("geyserPlayerName")      // 基岩版用户名
connection.getClass().getMethod("xuid")                 // Xbox 用户 ID
connection.getClass().getMethod("clientUuid")           // 基岩版客户端 UUID
connection.getClass().getMethod("locale")              // 语言设置
connection.getClass().getMethod("protocolVersion")     // 协议版本
connection.getClass().getMethod("serverAddress")        // 连接的服务器地址
connection.getClass().getMethod("deviceOs")            // 设备类型
```

### 3.2 GeyserConnection 核心能力

```java
// 发送基岩版原生表单
connection.sendForm(form);

// 获取关联的 Java 玩家 (如果已绑定)
UUID javaUuid = connection.getPlayerUuid();
```

---

## 4. 事件系统

### 4.1 事件分类

| 类别 | 说明 | 示例 |
|------|------|------|
| **Bedrock** | 基岩版客户端特定动作 | 表单响应 |
| **Java** | 服务器端动作 | 玩家加入/离开 |
| **Connection** | 网络相关事件 | MOTD 查询 |
| **Lifecycle** | Geyser 生命周期 | 初始化完成 |

### 4.2 Spigot/Paper 插件事件监听

需要实现 `EventRegistrar` 接口:

```java
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.Subscribe;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class ExamplePlugin extends JavaPlugin implements EventRegistrar {
    
    @Override
    public void onEnable() {
        // 注册事件监听器
        GeyserApi.api().eventBus().register(this, this);
    }

    // 监听 Geyser 初始化完成事件
    @Subscribe
    public void onGeyserPostInitializeEvent(GeyserPostInitializeEvent event) {
        getLogger().info("Geyser 已完成初始化!");
    }
}
```

### 4.3 手动订阅事件

```java
// 方式1: 在 onEnable 中手动订阅
GeyserApi.api().eventBus().subscribe(
    this,                                    // 监听器拥有者
    GeyserPostInitializeEvent.class,          // 事件类型
    this::onGeyserPostInitializeEvent        // 处理方法
);

// 方式2: 使用 @Subscribe 注解 (需要先注册)
```

### 4.4 常见事件类型

```java
// 生命周期事件
GeyserPreInitializeEvent       // Geyser 预初始化
GeyserLoadResourcePacksEvent   // 资源包加载
GeyserPostInitializeEvent      // Geyser 初始化完成

// 玩家事件 (通过 Bukkit 事件监听)
// PlayerJoinEvent / PlayerQuitEvent - 标准 Bukkit 事件
// 使用 GeyserApi.isBedrockPlayer() 区分平台
```

---

## 5. 玩家平台检测

### 5.1 GeyserApi 检测 (推荐)

```java
// 检测在线玩家是否为基岩版
public boolean isBedrockPlayer(Player player) {
    UUID uuid = player.getUniqueId();
    
    // 方式1: 使用 GeyserApi
    return GeyserApi.api().isBedrockPlayer(uuid);
    
    // 方式2: 获取连接
    return GeyserApi.api().connectionByUuid(uuid) != null;
}
```

### 5.2 UUID 特征检测 (无 API 依赖)

基岩版玩家通过 Floodgate 连接的 UUID 有特定格式:

```java
public boolean isFloodgateUuid(UUID uuid) {
    // Floodgate UUID 格式: xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx
    // 其中 '4' 是固定值，表示这是 Floodgate 生成的 UUID
    String uuidStr = uuid.toString();
    return uuidStr.charAt(14) == '4';
}

public boolean isBedrockByUuidFormat(UUID uuid) {
    // 更精确的检测: 检查特定位
    // 基岩版 UUID 的 version nibble 是 4，variant nibble 是 8/9/a/b
    long mostSigBits = uuid.getMostSignificantBits();
    int version = (int) ((mostSigBits >> 48) & 0xF);
    return version == 4;
}
```

### 5.3 插件检测

```java
// 检测 Geyser 是否安装
Plugin geyser = server.getPluginManager().getPlugin("Geyser");
boolean hasGeyser = geyser != null && geyser.isEnabled();

// 检测 Floodgate 是否安装
Plugin floodgate = server.getPluginManager().getPlugin("Floodgate");
boolean hasFloodgate = floodgate != null && floodgate.isEnabled();
```

---

## 6. Floodgate API

Floodgate 提供更丰富的基岩版玩家信息 API。

### 6.1 Maven 依赖

```xml
<dependency>
    <groupId>org.geysermc.floodgate</groupId>
    <artifactId>api</artifactId>
    <version>2.2.2-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

### 6.2 FloodgateApi 核心方法

```java
FloodgateApi api = FloodgateApi.getInstance();

// 玩家检测
api.isFloodgatePlayer(player);           // 检查是否为基岩版
api.isFloodgatePlayer(uuid);             // 通过 UUID 检查

// 获取玩家信息
FloodgatePlayer player = api.getPlayer(uuid);
if (player != null) {
    player.getUsername();      // 基岩版用户名
    player.getDeviceOs();      // 设备类型
    player.getInputMode();     // 输入模式
    player.getXuid();          // Xbox User ID
    player.isLinked();         // 是否绑定 Java 账号
}

// 绑定账号操作
api.createJavaPlayerId(xuid);  // 从 XUID 生成 Java UUID
```

### 6.3 FloodgatePlayer 属性

```java
FloodgatePlayer bedrockPlayer = FloodgateApi.getInstance().getPlayer(uuid);

// 获取用户名
String username = bedrockPlayer.getUsername();

// 获取设备类型 (DeviceOS)
DeviceOS deviceOs = bedrockPlayer.getDeviceOs();
// DeviceOS enum: ANDROID, IOS, OSX, WINDOWS, PLAYSTATION, NINTENDO, etc.

// 获取输入模式
InputMode inputMode = bedrockPlayer.getInputMode();
// InputMode enum: KEYBOARD_MOUSE, TOUCH, GAMEPAD, MOTION_CONTROLLER

// 获取 Xbox User ID
long xuid = bedrockPlayer.getXuid();

// 检查是否已绑定 Java 账号
boolean linked = bedrockPlayer.isLinked();

// 获取绑定的 Java 玩家信息
if (linked) {
    LinkedPlayer linkedPlayer = bedrockPlayer.getLinkedPlayer();
    String javaUsername = linkedPlayer.getJavaUsername();
    UUID javaUuid = linkedPlayer.getJavaUuid();
}
```

---

## 7. 基岩版特定功能

### 7.1 Bedrock 表单系统

基岩版支持三种原生表单类型:

#### ModalForm (模态表单)

```java
ModalForm form = ModalForm.builder()
    .title("确认操作")
    .content("你确定要执行此操作吗？")
    .button1("确认")
    .button2("取消")
    .build();
```

#### SimpleForm (简单表单)

```java
SimpleForm form = SimpleForm.builder()
    .title("选择一个选项")
    .content("请从下方选择一个选项")
    .button("选项 1")
    .button("选项 2")
    .button("选项 3", FormImage.Type.URL, "https://example.com/icon.png")
    .build();
```

#### CustomForm (自定义表单)

```java
CustomForm form = CustomForm.builder()
    .title("设置")
    .input("name", "请输入名称", "默认名称")
    .toggle("enabled", "启用功能", true)
    .slider("volume", "音量", 0, 100, 5, 50)
    .dropdown("difficulty", "难度", Arrays.asList("简单", "普通", "困难"))
    .build();
```

### 7.2 发送表单

```java
// 通过 GeyserApi 发送
UUID uuid = player.getUniqueId();
GeyserApi.api().sendForm(uuid, form);

// 通过 FloodgateApi 发送
FloodgateApi.getInstance().sendForm(uuid, form);

// 注册表单响应回调
form.responseHandler((player, responseData) -> {
    if (responseData == null) {
        // 玩家关闭了表单
        return;
    }
    
    // 根据表单类型解析响应
    ModalFormResponseData response = form.parseResponse(responseData);
    int clickedButton = response.getClickedButtonId();
});
```

### 7.3 基岩版限制与适配

| 功能 | 基岩版限制 | 适配方案 |
|------|-----------|----------|
| **聊天输入** | 无法直接发送斜杠命令 | 使用表单收集输入 |
| **某些 NBT 数据** | 支持受限 | 简化数据结构 |
| **书与笔** | 支持有限 | 替代为表单输入 |
| **重命名物品** | 字符限制更严格 | 截断或使用符号 |
| **着色文本** | 符号代码不同 | 使用 JSON 文本组件 |
| **鞘翅飞行** | 不支持 | 检测平台并提示 |

---

## 8. 扩展开发接口

### 8.1 Geyser Extension (独立扩展)

创建独立的 Geyser 扩展插件:

#### 项目结构

```
src/
└── main/
    ├── java/
    │   └── com/example/extension/
    │       └── ExampleExtension.java
    └── resources/
        └── extension.yml
```

#### extension.yml 配置

```yaml
id: starcore-geyser
name: StarCore Integration
main: com.example.extension.StarCoreExtension
api: 2.9.0
version: 1.0.0
authors: [StarCoreTeam]
description: StarCore plugin integration for Geyser

dependencies:
  geyser:
    load: BEFORE
    required: true
```

#### Extension 主类

```java
package com.example.extension;

import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.api.event.Subscribe;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;

public class StarCoreExtension implements Extension {
    
    @Override
    public void onGeyserEnable() {
        getLogger().info("StarCore Extension 已加载!");
        
        // 注册事件监听
        GeyserApi.api().eventBus().register(this, this);
    }
    
    @Subscribe
    public void onPostInitialize(GeyserPostInitializeEvent event) {
        // Geyser 完全初始化后执行
        getLogger().info("Geyser 初始化完成，可以开始注册自定义内容");
    }
}
```

### 8.2 Spigot 插件集成 (非 Extension)

作为独立 Spigot 插件与 Geyser 协同工作:

```java
public class StarCoreGeyserPlugin extends JavaPlugin implements EventRegistrar {
    
    @Override
    public void onEnable() {
        // 检查 Geyser 是否可用
        if (!isGeyserAvailable()) {
            getLogger().warning("Geyser 未安装，基岩版支持将被禁用");
            return;
        }
        
        // 注册事件总线
        GeyserApi.api().eventBus().register(this, this);
        
        // 注册命令
        getCommand("bedrockinfo").setExecutor(this);
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, 
                           String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("仅玩家可用");
            return true;
        }
        
        if (GeyserApi.api().isBedrockPlayer(player.getUniqueId())) {
            player.sendMessage("你正在使用基岩版游玩!");
            
            // 获取设备信息
            FloodgatePlayer fp = FloodgateApi.getInstance()
                .getPlayer(player.getUniqueId());
            player.sendMessage("设备: " + fp.getDeviceOs());
        } else {
            player.sendMessage("你正在使用 Java 版游玩");
        }
        
        return true;
    }
    
    @Subscribe
    public void onGeyserPostInitialize(GeyserPostInitializeEvent event) {
        getLogger().info("Geyser 已初始化，扩展功能就绪");
    }
}
```

---

## 9. 依赖配置

### 9.1 Maven pom.xml 配置

#### Geyser API (最新)

```xml
<repositories>
    <repository>
        <id>geysermc</id>
        <url>https://download.geysermc.org/repo/maven</url>
    </repository>
</repositories>

<dependencies>
    <!-- Geyser API -->
    <dependency>
        <groupId>org.geysermc.geyser</groupId>
        <artifactId>api</artifactId>
        <version>2.10.0-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
    
    <!-- Floodgate API -->
    <dependency>
        <groupId>org.geysermc.floodgate</groupId>
        <artifactId>api</artifactId>
        <version>2.2.2-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

#### StarCore 当前方案 (反射调用)

当前 StarCore 使用反射方式，无需编译依赖:

```xml
<!-- pom.xml 中已注释说明 -->
<!-- 
========== Platform Integration (运行时插件，可选) ==========
以下依赖通过反射调用，无需编译时依赖:
- Geyser/Geyser-Spigot - 基岩版支持
- Floodgate - 基岩版玩家识别
-->
```

### 9.2 plugin.yml 依赖声明

```yaml
name: StarCore
version: 0.1.0
main: dev.starcore.starcore.StarCorePlugin
api-version: '1.21'

# 软依赖 (可选)
softdepend:
  - Geyser-Spigot
  - Floodgate

# 建议依赖 (如果需要强制依赖)
depend:
  - PlaceholderAPI
```

---

## 10. StarCore 集成示例

### 10.1 现有集成分析

StarCore 当前使用**反射调用**方式集成 Geyser/Floodgate:

```java
// GeyserIntegration.java 核心逻辑
public class GeyserIntegration {
    
    // 检测插件是否可用
    private void checkGeyserAvailability() {
        Plugin floodgate = plugin.getServer()
            .getPluginManager().getPlugin("Floodgate");
        Plugin geyser = plugin.getServer()
            .getPluginManager().getPlugin("Geyser");
        
        floodgateAvailable = floodgate != null && floodgate.isEnabled();
        geyserAvailable = geyser != null && geyser.isEnabled();
    }
    
    // 反射初始化 Floodgate API
    private void initFloodgate反射() {
        // 尝试新版 API
        Class<?> managerClass = Class.forName(
            "net.geysermc.geyserapi.FloodgateApi");
        // ...
        
        // 尝试旧版 API (兼容性)
        Class<?> floodgateClass = Class.forName(
            "com.github.william278.floodgateapi.FloodgateApi");
        // ...
    }
    
    // 反射调用方法
    public boolean isBedrockPlayer(UUID uuid) {
        try {
            Object result = isBedrockMethod.invoke(null, uuid);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }
}
```

### 10.2 增强建议

#### 升级为使用完整 API 依赖

```xml
<!-- 在 pom.xml 中添加 -->
<dependencies>
    <dependency>
        <groupId>org.geysermc.geyser</groupId>
        <artifactId>api</artifactId>
        <version>2.10.0-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>org.geysermc.floodgate</groupId>
        <artifactId>api</artifactId>
        <version>2.2.2-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

#### 现代化集成代码

```java
package dev.starcore.starcore.integration.geyser;

import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.geysermc.geyser.api.GeyserApi;

import java.util.Optional;
import java.util.UUID;

/**
 * 现代化 Geyser/Floodgate 集成
 */
public class ModernGeyserIntegration {
    
    private final boolean geyserAvailable;
    private final boolean floodgateAvailable;
    
    public ModernGeyserIntegration() {
        this.geyserAvailable = isPluginAvailable("Geyser");
        this.floodgateAvailable = isPluginAvailable("Floodgate");
    }
    
    private boolean isPluginAvailable(String name) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        return plugin != null && plugin.isEnabled();
    }
    
    /**
     * 检查玩家是否为基岩版
     */
    public boolean isBedrockPlayer(Player player) {
        return isBedrockPlayer(player.getUniqueId());
    }
    
    public boolean isBedrockPlayer(UUID uuid) {
        if (!geyserAvailable) return false;
        return GeyserApi.api().isBedrockPlayer(uuid);
    }
    
    /**
     * 获取 FloodgatePlayer (如果可用)
     */
    public Optional<FloodgatePlayer> getFloodgatePlayer(UUID uuid) {
        if (!floodgateAvailable) return Optional.empty();
        
        FloodgatePlayer player = FloodgateApi.getInstance().getPlayer(uuid);
        return Optional.ofNullable(player);
    }
    
    /**
     * 获取设备信息
     */
    public String getDeviceInfo(Player player) {
        return getFloodgatePlayer(player.getUniqueId())
            .map(fp -> "基岩版 (" + fp.getDeviceOs() + ")")
            .orElse("Java版");
    }
    
    /**
     * 获取基岩版玩家用户名
     */
    public Optional<String> getBedrockUsername(UUID uuid) {
        return getFloodgatePlayer(uuid)
            .map(FloodgatePlayer::getUsername);
    }
    
    /**
     * 检查是否已绑定 Java 账号
     */
    public boolean isLinked(Player player) {
        return getFloodgatePlayer(player.getUniqueId())
            .map(FloodgatePlayer::isLinked)
            .orElse(false);
    }
    
    /**
     * 发送基岩版表单
     */
    public void sendForm(UUID uuid, Object form) {
        if (geyserAvailable) {
            GeyserApi.api().sendForm(uuid, (Form) form);
        }
    }
    
    /**
     * 基岩版特定处理检查
     */
    public boolean needsBedrockAdaptation(Player player) {
        return isBedrockPlayer(player);
    }
}
```

### 10.3 使用示例

```java
// 在其他服务中使用
public class PlayerService {
    
    private final ModernGeyserIntegration geyser;
    
    public void onPlayerJoin(Player player) {
        // 检测平台
        if (geyser.isBedrockPlayer(player)) {
            log.info("基岩版玩家 {} 加入游戏", 
                geyser.getBedrockUsername(player.getUniqueId())
                    .orElse(player.getName()));
            
            // 检查绑定状态
            if (!geyser.isLinked(player)) {
                sendLinkPrompt(player);
            }
            
            // 获取设备信息
            String device = geyser.getDeviceInfo(player);
            log.info("设备: {}", device);
        }
        
        // 通用处理
        initializePlayer(player);
    }
    
    private void sendLinkPrompt(Player player) {
        // 为未绑定玩家发送提示
        if (geyser.needsBedrockAdaptation(player)) {
            // 发送基岩版表单
            sendLinkForm(player);
        } else {
            // 发送 Java 版消息
            player.sendMessage("请使用 /link 命令绑定账号");
        }
    }
}
```

---

## 附录 A: API 版本参考

| Geyser 版本 | API 版本 | 关键变化 |
|-------------|----------|----------|
| 2.0.x | 2.0.0 | 初始 API 重构 |
| 2.1.x | 2.1.0 | 事件系统改进 |
| 2.2.x | 2.2.0 | 表单 API 增强 |
| 2.3.x | 2.3.0 | 生命周期事件 |
| 2.9.x | 2.9.0 | Extension 支持 |
| 2.10.x | 2.10.0 | 最新稳定版 |

## 附录 B: Floodgate 版本

| Floodgate 版本 | API 包名 | 备注 |
|----------------|----------|------|
| 2.0.x | `org.geysermc.floodgate` | 主流版本 |
| 2.1.x | `org.geysermc.floodgate` | 性能优化 |
| 2.2.x | `org.geysermc.floodgate` | 跨平台改进 |

---

## 附录 C: 相关资源

- [GeyserMC Wiki](https://wiki.geysermc.org/geyser/)
- [Geyser API 文档](https://github.com/GeyserMC/api)
- [Floodgate Wiki](https://wiki.geysermc.org/floodgate/)
- [Geyser Discord](https://discord.gg/geysermc)

---

*本文档基于 GeyserMC 官方文档和 StarCore 项目分析生成*
*最后更新: 2026/07/02*
