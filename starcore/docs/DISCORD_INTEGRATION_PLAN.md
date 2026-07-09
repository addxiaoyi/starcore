# StarCore Discord 集成计划

基于 TownsAndNations-DiscordSrv 参考分析，为 StarCore 设计完整的 Discord 集成方案。

---

## 1. 模块架构设计

### 1.1 核心组件

```
starcore/src/main/java/dev/starcore/starcore/integration/discord/
├── DiscordIntegrationModule.java     # 模块入口（可选，模块化）
├── DiscordService.java              # 核心服务类
├── DiscordConfig.java               # 配置类
├── DiscordEventListener.java        # StarCore 事件监听
├── DiscordMessageFormatter.java      # 消息格式化器
├── channel/                        # 频道管理
│   ├── ChannelManager.java
│   └── ChannelConfig.java
├── embed/                          # Embed 构建
│   ├── EmbedBuilder.java
│   └── EmbedColor.java
├── command/                        # 命令同步
│   └── DiscordCommandHandler.java
└── placeholder/                    # 占位符系统
    └── PlaceholderManager.java
```

### 1.2 依赖配置 (pom.xml)

```xml
<!-- DiscordSRV API - provided 运行时依赖 -->
<dependency>
    <groupId>github.scarsz</groupId>
    <artifactId>DiscordSRV</artifactId>
    <version>1.26.4</version>
    <scope>provided</scope>
</dependency>

<!-- JDA (DiscordSRV 已包含，可选直接使用) -->
<!-- 通过 DiscordSRV API 已足够，无需额外依赖 -->
```

---

## 2. 配置设计

### 2.1 config.yml 扩展

```yaml
# Discord 集成配置
discord:
  # 是否启用 Discord 集成
  enabled: false
  # 是否在 DiscordSRV 不可用时警告
  warn-if-discordsrv-missing: true

# 频道配置
channels:
  # 主广播频道（用于大多数事件通知）
  broadcast: "starcore"
  # 战争专用频道
  war: "starcore-war"
  # 外交专用频道
  diplomacy: "starcore-diplomacy"
  # 经济专用频道
  economy: "starcore-economy"
  # 领土专用频道
  territory: "starcore-territory"
  # 军队专用频道
  army: "starcore-army"

# 消息格式
format:
  # Embed 颜色（十六进制）
  embed-color: "#3498db"
  # 是否显示时间戳
  show-timestamp: true
  # 页脚文本
  footer: "StarCore - Minecraft 国家系统"
  # 玩家头像 URL（支持占位符 {player}）
  player-avatar-url: "https://crafatar.com/avatars/{uuid}?size=128"

# 通知开关（按类别）
notifications:
  # 国家系统
  nation:
    created: true
    deleted: true
    member-joined: true
    member-left: true
    level-up: true
  # 领土系统
  territory:
    claimed: true
    unclaimed: true
    disputed: true
  # 外交系统
  diplomacy:
    alliance-formed: true
    alliance-broken: true
    war-declared: true
    peace-signed: true
  # 战争系统
  war:
    started: true
    ended: true
    battle-result: true
    surrender: true
  # 国库系统
  treasury:
    deposit: false  # 可能过于频繁
    large-transaction: true
    daily-income: true
  # 军事系统
  army:
    siege-started: true
    siege-ended: true
    raid-alert: true
    spy-detected: true
  # 科技/政策
  technology:
    unlocked: true
    research-completed: true

# 命令同步配置
command-sync:
  # 是否启用命令同步（Discord -> Minecraft）
  enabled: false
  # 命令前缀
  prefix: "!"
  # 允许的命令列表
  allowed-commands:
    - "sc nation info"
    - "sc war status"
    - "sc diplomacy list"
    # 管理员命令
    - "sc reload"
  # Discord 频道限制（只有这些频道可以发送命令）
  allowed-channels: ["starcore-commands"]

# Webhook 配置（可选高级功能）
webhooks:
  # 是否使用 Webhook 发送消息（更高自定义）
  use-webhook: false
  # Webhook URL（通过 Discord 应用获取）
  webhook-url: ""
  # Webhook 名称
  webhook-name: "StarCore"
```

### 2.2 配置加载

```java
public class DiscordConfig {
    private boolean enabled;
    private Map<String, String> channels;
    private NotificationSettings notifications;
    private CommandSyncSettings commandSync;
    private WebhookSettings webhooks;
    
    public static DiscordConfig load(FileConfiguration config);
}
```

---

## 3. 通知类型设计

### 3.1 通知分类

| 分类 | 事件 | 频道 | 优先级 |
|------|------|------|--------|
| **国家** | NationCreatedEvent, NationDeletedEvent | broadcast | 高 |
| **成员** | MemberJoinEvent, MemberLeaveEvent | broadcast | 中 |
| **领土** | TerritoryClaimEvent, TerritoryDisputeEvent | territory | 高 |
| **外交** | AllianceFormedEvent, WarDeclaredEvent | diplomacy/war | 高 |
| **战争** | WarStartEvent, WarEndEvent, BattleResultEvent | war | 高 |
| **国库** | TreasuryDepositEvent, DailyIncomeEvent | economy | 低 |
| **军事** | SiegeStartedEvent, RaidAlertEvent | army | 高 |
| **科技** | TechnologyUnlockedEvent | broadcast | 中 |

### 3.2 事件映射（高优先级事件）

```java
// 国家系统
NationCreatedEvent -> "国家创建"
NationDeletedEvent -> "国家解散"
NationLevelUpEvent -> "国家升级"

// 领土系统
TerritoryClaimEvent -> "领土占领"
TerritoryLostEvent -> "领土丢失"

// 外交系统
AllianceFormedEvent -> "联盟建立"
AllianceBrokenEvent -> "联盟破裂"
WarDeclaredEvent -> "宣战"
PeaceSignedEvent -> "和平协议"

// 战争系统
WarStartEvent -> "战争开始"
WarEndEvent -> "战争结束"
BattleResultEvent -> "战斗结果"
SurrenderEvent -> "投降"

// 军事系统
SiegeStartedEvent -> "围城开始"
SiegeEndedEvent -> "围城结束"
RaidAlertEvent -> "突袭警报"
SpyDetectedEvent -> "间谍发现"

// 国库系统
LargeTransactionEvent -> "大额交易"
DailyIncomeEvent -> "日常收入"

// 科技系统
TechnologyUnlockedEvent -> "科技解锁"
ResearchCompletedEvent -> "研究完成"
```

---

## 4. 消息格式设计

### 4.1 Embed 结构

```java
// 基础 Embed 构建
EmbedBuilder embed = new EmbedBuilder()
    .setTitle(eventTitle)                           // 事件标题
    .setDescription(eventDescription)               // 事件描述
    .setColor(getCategoryColor())                  // 分类颜色
    .setTimestamp(Instant.now())                    // 时间戳
    .setFooter(config.footer(), null)              // 页脚
    .setThumbnail(playerAvatarUrl)                  // 玩家头像
    .addField("国家", nationName, true)           // 额外字段
    .addField("领土", territoryCount, true);
```

### 4.2 颜色方案

```java
public enum EmbedColor {
    NATION("#3498db"),        // 蓝色 - 国家
    TERRITORY("#27ae60"),     // 绿色 - 领土
    DIPLOMACY("#9b59b6"),    // 紫色 - 外交
    WAR("#e74c3c"),          // 红色 - 战争
    TREASURY("#f39c12"),     // 金色 - 国库
    ARMY("#e67e22"),         // 橙色 - 军事
    TECHNOLOGY("#1abc9c"),   // 青色 - 科技
    INFO("#95a5a6");         // 灰色 - 通用
}
```

### 4.3 消息模板

```yaml
# lang/messages_zh_cn.yml 扩展

discord:
  # 国家
  nation_created_title: "🏰 新国家建立"
  nation_created_desc: "**{leader}** 建立了 **{nation}**"
  nation_deleted_title: "🏰 国家解散"
  nation_deleted_desc: "**{nation}** 已解散"
  
  # 领土
  territory_claimed_title: "🗺️ 领土占领"
  territory_claimed_desc: "**{nation}** 占领了 ({x}, {z})"
  
  # 外交
  alliance_formed_title: "🤝 联盟建立"
  alliance_formed_desc: "**{nation1}** 与 **{nation2}** 结成联盟"
  war_declared_title: "⚔️ 战争宣告"
  war_declared_desc: "**{attacker}** 向 **{defender}** 宣战！"
  
  # 战争
  war_started_title: "🔥 战争开始"
  war_ended_title: "🏆 战争结束"
  battle_result_title: "⚔️ 战斗结果"
  
  # 军事
  siege_started_title: "🏰 围城开始"
  siege_started_desc: "**{attacker}** 开始围攻 **{defender}** 的 {fortress}"
  raid_alert_title: "⚠️ 突袭警报"
  raid_alert_desc: "**{nation}** 遭受突袭！"
  
  # 国库
  daily_income_title: "💰 日常收入"
  daily_income_desc: "**{nation}** 获得 {amount} 金币收入"
```

---

## 5. 命令同步设计

### 5.1 架构

```
Discord 用户消息
      ↓
命令解析 (!sc ...)
      ↓
权限验证 (允许频道/用户)
      ↓
命令转换 (Discord -> Minecraft 命令)
      ↓
执行并捕获输出
      ↓
格式化返回消息
      ↓
发送到 Discord
```

### 5.2 命令处理器

```java
public class DiscordCommandHandler {
    
    // 处理 Discord 命令
    public void handleCommand(MessageReceivedEvent event, String command);
    
    // 转换并执行 Minecraft 命令
    public String executeMinecraftCommand(String command);
    
    // 格式化返回结果
    public void sendResponse(MessageChannel channel, String response);
}
```

### 5.3 安全控制

```java
// 配置白名单
commandSync:
  allowed-commands:
    - "sc nation info"
    - "sc nation list"
    - "sc war status"
    - "sc diplomacy list"
    - "sc treasury balance"
  allowed-channels:
    - "starcore-commands"
  # 管理员 Discord ID 白名单
  admin-ids: []
```

---

## 6. Webhook 配置（可选）

### 6.1 Webhook vs DiscordSRV API

| 特性 | DiscordSRV API | Webhook |
|------|----------------|---------|
| 头像自定义 | 受限 | 完全自定义 |
| 用户名自定义 | 受限 | 完全自定义 |
| 速率限制 | 无（通过 API） | 有（每渠道 5/秒） |
| 交互支持 | 无 | 无 |
| 实现复杂度 | 低 | 中 |

### 6.2 Webhook 消息格式

```json
{
  "content": "消息内容（可选）",
  "embeds": [{
    "title": "事件标题",
    "description": "事件描述",
    "color": 2303784,
    "timestamp": "2024-01-01T00:00:00Z",
    "footer": {
      "text": "StarCore - Minecraft 国家系统"
    },
    "thumbnail": {
      "url": "https://..."
    }
  }],
  "username": "StarCore",
  "avatar_url": "https://..."
}
```

---

## 7. 实现步骤

### Phase 1: 核心功能
1. **依赖添加** - pom.xml 添加 DiscordSRV 依赖
2. **配置类** - DiscordConfig 实现配置加载
3. **服务类** - DiscordService 实现基本发送功能
4. **事件监听** - DiscordEventListener 订阅 StarCore 事件

### Phase 2: 通知扩展
5. **事件映射** - 添加更多事件监听
6. **国际化** - 消息模板集成到 lang 文件
7. **频道管理** - 按类别发送到不同频道

### Phase 3: 高级功能（可选）
8. **命令同步** - Discord -> Minecraft 命令转发
9. **Webhook 支持** - 更高自定义选项
10. **管理员命令** - Discord 管理 Minecraft

---

## 8. 代码示例

### 8.1 事件监听器

```java
public class DiscordEventListener {
    private final DiscordService discord;
    private final I18nManager i18n;
    
    public DiscordEventListener(DiscordService discord, I18nManager i18n) {
        this.discord = discord;
        this.i18n = i18n;
        
        // 订阅 StarCore 事件
        StarCoreEventBus bus = ...;
        
        // 国家创建
        bus.subscribe(NationCreatedEvent.class, event -> {
            String title = i18n.getMessage("discord.nation_created_title");
            String desc = i18n.getMessage("discord.nation_created_desc",
                event.getLeaderName(), event.getNationName());
            discord.sendEmbed("broadcast", title, desc, EmbedColor.NATION);
        });
        
        // 宣战
        bus.subscribe(WarDeclaredEvent.class, event -> {
            String title = i18n.getMessage("discord.war_declared_title");
            String desc = i18n.getMessage("discord.war_declared_desc",
                event.getAttackerName(), event.getDefenderName());
            discord.sendEmbed("war", title, desc, EmbedColor.WAR);
        });
        
        // 更多事件...
    }
}
```

### 8.2 DiscordService

```java
public class DiscordService {
    private DiscordSRV discordSRV;
    private DiscordConfig config;
    
    public void sendEmbed(String channelKey, String title, String description, 
                         EmbedColor color) {
        if (!config.isEnabled()) return;
        
        String channelName = config.getChannel(channelKey);
        TextChannel channel = discordSRV.getOptionalTextChannel(channelName);
        
        if (channel == null) {
            // 回退到默认频道
            channel = discordSRV.getOptionalTextChannel(
                config.getChannel("broadcast"));
        }
        
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle(title)
            .setDescription(description)
            .setColor(color.getColor())
            .setTimestamp(Instant.now())
            .setFooter(config.getFooter(), null);
        
        channel.sendMessageEmbeds(embed.build()).queue();
    }
}
```

---

## 9. 配置示例

### 9.1 完整配置

```yaml
# starcore/config.yml 新增部分
discord:
  enabled: true
  warn-if-discordsrv-missing: true

discord:
  channels:
    broadcast: "starcore"
    war: "starcore-war"
    diplomacy: "starcore-diplomacy"
    economy: "starcore-economy"
    territory: "starcore-territory"
    army: "starcore-army"

  format:
    embed-color: "#3498db"
    show-timestamp: true
    footer: "StarCore - Minecraft 国家系统"

  notifications:
    nation:
      created: true
      deleted: true
      member-joined: true
      level-up: true
    territory:
      claimed: true
      unclaimed: true
    diplomacy:
      alliance-formed: true
      alliance-broken: true
      war-declared: true
    war:
      started: true
      ended: true
      battle-result: true
    treasury:
      daily-income: true
    army:
      siege-started: true
      siege-ended: true
      raid-alert: true
    technology:
      unlocked: true

  command-sync:
    enabled: false
    prefix: "!"
    allowed-commands:
      - "sc nation info"
      - "sc nation list"
      - "sc war status"
    allowed-channels:
      - "starcore-commands"
```

---

## 10. 注意事项

1. **异步处理** - 所有 Discord 消息发送必须在异步线程执行
2. **速率限制** - 避免高频事件导致 Discord 限流
3. **配置热重载** - 支持 `/starcore reload` 重载 Discord 配置
4. **优雅降级** - DiscordSRV 不可用时不应崩溃
5. **日志记录** - 记录发送失败等错误信息
