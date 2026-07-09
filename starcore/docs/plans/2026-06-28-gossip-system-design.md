# 八卦传播系统 - 详细设计方案

## 概述
玩家八卦（私聊内容）有概率被"传播"给第三方。传播准确度随玩家社交技能提升，增加游戏戏剧性和社交深度。

---

## 1. 数据库设计

### 八卦记录表: gossip_records
```sql
CREATE TABLE gossip_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    gossip_id CHAR(36) NOT NULL UNIQUE,
    
    -- 八卦内容
    original_content TEXT NOT NULL,
    transformed_content TEXT,                     -- 变形后的内容
    content_type ENUM('ROMANCE', 'POLITICS', 'WEALTH', 'CRIME', 'GENERAL') NOT NULL,
    sensitivity_level INT DEFAULT 50,             -- 敏感度 0-100
    
    -- 来源
    source_player_uuid CHAR(36) NOT NULL,         -- 八卦来源
    source_context VARCHAR(255),                  -- 场景描述
    
    -- 传播路径
    spread_count INT DEFAULT 0,                   -- 传播次数
    max_spread INT DEFAULT 5,                     -- 最大传播次数
    spread_path JSON,                             -- 传播路径 [{player, time, accuracy}]
    
    -- 准确性
    base_accuracy DECIMAL(5,2) DEFAULT 0.80,      -- 基础准确度
    final_accuracy DECIMAL(5,2),                  -- 最终准确度
    distortion_count INT DEFAULT 0,               -- 内容变形次数
    
    -- 时间
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_spread_time TIMESTAMP,
    expiration_time TIMESTAMP,                    -- 过期时间
    
    -- 状态
    status ENUM('ACTIVE', 'FADING', 'CONFIRMED', 'DENIED', 'EXPIRED') DEFAULT 'ACTIVE',
    verification_status ENUM('UNVERIFIED', 'TRUE', 'FALSE', 'MIXED') DEFAULT 'UNVERIFIED',
    
    INDEX idx_source (source_player_uuid),
    INDEX idx_type (content_type),
    INDEX idx_status (status),
    INDEX idx_created (created_time DESC)
);
```

### 八卦关系表: gossip_relationships
```sql
CREATE TABLE gossip_relationships (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    player_uuid CHAR(36) NOT NULL,
    target_uuid CHAR(36) NOT NULL,
    
    -- 关系值
    trust_level INT DEFAULT 50,                   -- 信任度 0-100
    intimacy_level INT DEFAULT 0,                 -- 亲密程度
    betrayal_count INT DEFAULT 0,                 -- 背叛次数
    gossip_received_count INT DEFAULT 0,          -- 收到的八卦数
    gossip_shared_count INT DEFAULT 0,            -- 分享的八卦数
    
    -- 社交技能
    social_skill_level INT DEFAULT 1,             -- 社交技能等级
    charisma INT DEFAULT 10,                      -- 魅力值
    discretion INT DEFAULT 10,                    -- 保密能力
    
    -- 时间戳
    first_interaction TIMESTAMP,
    last_interaction TIMESTAMP,
    
    UNIQUE KEY uk_pair (player_uuid, target_uuid),
    INDEX idx_player (player_uuid)
);
```

### 八卦订阅表: gossip_subscriptions
```sql
CREATE TABLE gossip_subscriptions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    subscriber_uuid CHAR(36) NOT NULL,            -- 订阅者
    target_uuid CHAR(36) NOT NULL,                -- 被订阅者
    subscription_type ENUM('FOLLOW', 'SPY', 'FAN') DEFAULT 'FOLLOW',
    
    -- 订阅偏好
    interested_topics JSON,                       -- 感兴趣的八卦类型
    notification_enabled BOOLEAN DEFAULT TRUE,
    
    -- 状态
    is_active BOOLEAN DEFAULT TRUE,
    subscription_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_subscription (subscriber_uuid, target_uuid),
    INDEX idx_target (target_uuid)
);
```

### 八卦验证表: gossip_verifications
```sql
CREATE TABLE gossip_verifications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    verification_id CHAR(36) NOT NULL UNIQUE,
    gossip_id CHAR(36) NOT NULL,
    
    -- 验证者
    verifier_uuid CHAR(36) NOT NULL,
    verification_type ENUM('CONFIRM', 'DENY', 'CLARIFY') NOT NULL,
    verification_content TEXT,
    
    -- 结果
    result_accuracy DECIMAL(5,2),
    
    -- 时间
    verification_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (gossip_id) REFERENCES gossip_records(gossip_id) ON DELETE CASCADE
);
```

---

## 2. 核心类设计

### 接口: GossipService
```java
package dev.starcore.starcore.module.social.gossip;

import java.util.List;
import java.util.UUID;

public interface GossipService {
    
    // ===== 八卦记录 =====
    /**
     * 记录八卦
     * @param source 八卦来源玩家
     * @param target 被八卦的目标玩家
     * @param content 八卦内容
     * @param type 八卦类型
     * @return 八卦记录
     */
    GossipRecord recordGossip(Player source, Player target, String content, GossipType type);
    
    /**
     * 传播八卦
     * @param gossipId 八卦ID
     * @param spreader 传播者
     * @param recipient 接收者
     */
    void spreadGossip(UUID gossipId, Player spreader, Player recipient);
    
    /**
     * 获取八卦列表
     */
    List<GossipRecord> getGossips(Player player, int limit);
    
    /**
     * 获取关于某玩家的八卦
     */
    List<GossipRecord> getGossipsAbout(UUID targetId, int limit);
    
    // ===== 关系管理 =====
    /**
     * 获取玩家间的关系
     */
    GossipRelationship getRelationship(UUID player1, UUID player2);
    
    /**
     * 更新关系值
     */
    void updateRelationship(UUID player1, UUID player2, RelationshipChange change);
    
    /**
     * 建立八卦关系
     */
    void establishRelationship(UUID player1, UUID player2);
    
    // ===== 订阅系统 =====
    /**
     * 订阅玩家八卦
     */
    void subscribe(UUID subscriber, UUID target, SubscriptionType type);
    
    /**
     * 取消订阅
     */
    void unsubscribe(UUID subscriber, UUID target);
    
    /**
     * 获取订阅通知
     */
    List<GossipNotification> getSubscriptions(UUID playerId);
    
    // ===== 验证 =====
    /**
     * 验证八卦真假
     */
    VerificationResult verifyGossip(UUID gossipId, Player verifier, 
                                    boolean isTrue, String clarification);
    
    // ===== 社交技能 =====
    /**
     * 提升社交技能
     */
    void improveSocialSkill(UUID playerId, int points);
    
    /**
     * 获取社交技能加成
     */
    double getSocialBonus(UUID playerId);
}
```

### 模型类
```java
// GossipRecord.java
public record GossipRecord(
    UUID gossipId,
    String originalContent,
    String transformedContent,
    GossipType type,
    int sensitivityLevel,
    UUID sourcePlayerId,
    String sourceContext,
    int spreadCount,
    int maxSpread,
    List<SpreadNode> spreadPath,
    double baseAccuracy,
    double finalAccuracy,
    int distortionCount,
    long createdTime,
    long lastSpreadTime,
    long expirationTime,
    GossipStatus status,
    VerificationStatus verification
) {}

// SpreadNode.java
public record SpreadNode(
    UUID playerId,
    long spreadTime,
    double accuracy,
    String transformation // 本次变形后的内容
) {}

// GossipRelationship.java
public record GossipRelationship(
    UUID player1Id,
    UUID player2Id,
    int trustLevel,
    int intimacyLevel,
    int betrayalCount,
    int gossipReceived,
    int gossipShared,
    int socialSkillLevel,
    int charisma,
    int discretion,
    long lastInteraction
) {}

// GossipNotification.java
public record GossipNotification(
    UUID gossipId,
    UUID subscriberId,
    UUID sourcePlayerId,
    GossipType type,
    String preview,
    long timestamp
) {}
```

### 核心实现: GossipServiceImpl
```java
public class GossipServiceImpl implements GossipService, Listener {
    
    private final JavaPlugin plugin;
    private final DatabaseService databaseService;
    private final SocialSkillService socialSkillService;
    private final StarCoreEventBus eventBus;
    private final StarCoreScheduler scheduler;
    
    // 配置
    private GossipConfig config;
    
    // 八卦传播黑话库
    private static final Map<String, String> TRANSFORMATION_PATTERNS = Map.of(
        "非常", "超级",
        "好像", "据说",
        "据说", "听说",
        "可能", "八成",
        "据说", "有人看到",
        "我听说", "悄悄告诉你",
        "朋友说", "好基友告诉我"
    );
    
    @Override
    public GossipRecord recordGossip(Player source, Player target, 
                                     String content, GossipType type) {
        // 计算敏感度
        int sensitivity = calculateSensitivity(content, type);
        
        // 计算基础准确度
        double baseAccuracy = calculateBaseAccuracy(source, target);
        
        // 创建八卦记录
        GossipRecord gossip = new GossipRecord(
            UUID.randomUUID(),
            content,
            content,
            type,
            sensitivity,
            source.getUniqueId(),
            determineContext(source.getLocation()),
            0,
            config.maxSpread(),
            List.of(),
            baseAccuracy,
            baseAccuracy,
            0,
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            System.currentTimeMillis() + config.gossipLifetime() * 3600000L,
            GossipStatus.ACTIVE,
            VerificationStatus.UNVERIFIED
        );
        
        // 保存
        saveGossip(gossip);
        
        // 更新关系
        updateRelationship(source.getUniqueId(), target.getUniqueId(), 
            RelationshipChange.builder()
                .gossipShared(1)
                .intimacyChange(5)
                .build()
        );
        
        // 触发事件
        eventBus.publish(new GossipRecordedEvent(gossip, source, target));
        
        // 随机触发传播
        if (Math.random() < config.initialSpreadChance()) {
            triggerInitialSpread(gossip, source);
        }
        
        return gossip;
    }
    
    @Override
    public void spreadGossip(UUID gossipId, Player spreader, Player recipient) {
        GossipRecord gossip = getGossip(gossipId)
            .orElseThrow(() -> new IllegalArgumentException("Gossip not found"));
        
        if (gossip.status() != GossipStatus.ACTIVE) {
            throw new IllegalStateException("Gossip is no longer active");
        }
        
        if (gossip.spreadCount() >= gossip.maxSpread()) {
            throw new IllegalStateException("Gossip has reached max spread");
        }
        
        // 检查是否已经传播给该玩家
        if (gossip.spreadPath().stream().anyMatch(n -> n.playerId().equals(recipient.getUniqueId()))) {
            return; // 已经听过
        }
        
        // 计算传播准确度
        double spreadAccuracy = calculateSpreadAccuracy(spreader, gossip);
        
        // 内容变形
        String transformedContent = transformContent(gossip.originalContent(), spreadAccuracy);
        
        // 创建传播节点
        SpreadNode node = new SpreadNode(
            recipient.getUniqueId(),
            System.currentTimeMillis(),
            spreadAccuracy,
            transformedContent
        );
        
        // 更新八卦记录
        GossipRecord updated = new GossipRecord(
            gossip.gossipId(),
            gossip.originalContent(),
            transformedContent,
            gossip.type(),
            gossip.sensitivityLevel(),
            gossip.sourcePlayerId(),
            gossip.sourceContext(),
            gossip.spreadCount() + 1,
            gossip.maxSpread(),
            appendToPath(gossip.spreadPath(), node),
            gossip.baseAccuracy(),
            calculateFinalAccuracy(spreadAccuracy, gossip),
            gossip.distortionCount() + (spreadAccuracy < 0.8 ? 1 : 0),
            gossip.createdTime(),
            System.currentTimeMillis(),
            gossip.expirationTime(),
            gossip.spreadCount() + 1 >= gossip.maxSpread() ? GossipStatus.FADING : GossipStatus.ACTIVE,
            gossip.verification()
        );
        
        saveGossip(updated);
        
        // 通知接收者
        notifyRecipient(recipient, updated, transformedContent, spreader);
        
        // 更新关系
        updateRelationship(spreader.getUniqueId(), recipient.getUniqueId(),
            RelationshipChange.builder()
                .gossipShared(1)
                .build()
        );
        
        // 触发事件
        eventBus.publish(new GossipSpreadEvent(updated, spreader, recipient, spreadAccuracy));
    }
    
    private double calculateSpreadAccuracy(Player spreader, GossipRecord gossip) {
        // 基础准确度
        double accuracy = gossip.baseAccuracy();
        
        // 传播者社交技能加成
        GossipRelationship relationship = getRelationship(gossip.sourcePlayerId(), spreader.getUniqueId());
        if (relationship != null) {
            // 亲密朋友准确度更高
            accuracy += relationship.intimacyLevel() * 0.002; // 最高+10%
            // 保密能力强准确度高
            accuracy += relationship.discretion() * 0.003;    // 最高+15%
        }
        
        // 社交技能加成
        double socialBonus = getSocialBonus(spreader.getUniqueId());
        accuracy *= socialBonus;
        
        // 传播次数越多，准确度越低
        accuracy -= gossip.spreadCount() * 0.05;
        
        // 敏感内容更容易变形
        if (gossip.sensitivityLevel() > 70) {
            accuracy -= 0.1;
        }
        
        return Math.max(0.1, Math.min(1.0, accuracy));
    }
    
    private String transformContent(String content, double accuracy) {
        String transformed = content;
        
        // 准确度越低，变形越多
        int transformationsNeeded = (int)((1 - accuracy) * 10);
        
        for (int i = 0; i < transformationsNeeded; i++) {
            // 随机应用一种变形
            String pattern = getRandomTransformationPattern();
            String replacement = TRANSFORMATION_PATTERNS.getOrDefault(pattern, pattern);
            
            // 50%概率应用词汇变形
            if (Math.random() < 0.5) {
                transformed = transformed.replaceFirst(pattern, replacement);
            }
            
            // 30%概率添加模糊词
            if (Math.random() < 0.3) {
                String[] fillers = {"好像", "据说", "可能", "不太确定"};
                transformed = fillers[random.nextInt(fillers.length)] + transformed;
            }
            
            // 20%概率改变数字
            if (transformed.matches(".*\\d+.*") && Math.random() < 0.2) {
                transformed = transformed.replaceAll("\\d+", 
                    String.valueOf(random.nextInt(100) + 1));
            }
        }
        
        return transformed;
    }
    
    private void notifyRecipient(Player recipient, GossipRecord gossip, 
                                String content, Player spreader) {
        // 构建通知
        Component notification = Component.text()
            .append(Component.text("[八卦] ", NamedTextColor.LIGHT_PURPLE))
            .append(Component.text(spreader.getName(), NamedTextColor.YELLOW))
            .append(Component.text(" 悄悄告诉你: ", NamedTextColor.GRAY))
            .append(Component.text(content, NamedTextColor.WHITE))
            .build();
        
        recipient.sendMessage(notification);
        
        // 高敏感度八卦添加提示
        if (gossip.sensitivityLevel() > 70) {
            recipient.sendMessage(Component.text()
                .append(Component.text("⚠️ ", NamedTextColor.YELLOW))
                .append(Component.text("这条八卦很敏感，传播需谨慎", NamedTextColor.DARK_GRAY))
                .build()
            );
        }
        
        // 记录接收
        updateRelationship(spreader.getUniqueId(), recipient.getUniqueId(),
            RelationshipChange.builder()
                .gossipReceived(1)
                .build()
        );
    }
    
    @Override
    public VerificationResult verifyGossip(UUID gossipId, Player verifier, 
                                           boolean isTrue, String clarification) {
        GossipRecord gossip = getGossip(gossipId)
            .orElseThrow(() -> new IllegalArgumentException("Gossip not found"));
        
        // 计算验证准确度
        double accuracy = 1.0;
        if (!isTrue) {
            accuracy = 1.0 - Math.abs(gossip.finalAccuracy() - 0.5) * 2;
        }
        
        // 更新八卦状态
        GossipStatus newStatus = isTrue ? GossipStatus.CONFIRMED : GossipStatus.DENIED;
        GossipRecord updated = new GossipRecord(
            gossip.gossipId(),
            gossip.originalContent(),
            gossip.transformedContent(),
            gossip.type(),
            gossip.sensitivityLevel(),
            gossip.sourcePlayerId(),
            gossip.sourceContext(),
            gossip.spreadCount(),
            gossip.maxSpread(),
            gossip.spreadPath(),
            gossip.baseAccuracy(),
            accuracy,
            gossip.distortionCount(),
            gossip.createdTime(),
            gossip.lastSpreadTime(),
            gossip.expirationTime(),
            newStatus,
            isTrue ? VerificationStatus.TRUE : VerificationStatus.FALSE
        );
        
        saveGossip(updated);
        
        // 更新来源玩家关系
        updateRelationship(gossip.sourcePlayerId(), verifier.getUniqueId(),
            RelationshipChange.builder()
                .betrayalCount(isTrue ? 0 : 1)
                .trustChange(isTrue ? 10 : -10)
                .build()
        );
        
        // 事件
        eventBus.publish(new GossipVerifiedEvent(updated, verifier, isTrue));
        
        return new VerificationResult(updated, verifier.getUniqueId(), isTrue, accuracy);
    }
    
    // ===== 社交技能系统 =====
    @Override
    public double getSocialBonus(UUID playerId) {
        // 基于社交技能等级计算加成
        int skillLevel = getSocialSkillLevel(playerId);
        return 1.0 + (skillLevel * 0.05); // 每级+5%
    }
    
    private int getSocialSkillLevel(UUID playerId) {
        // 从数据库获取或默认
        return databaseService.query(
            "SELECT social_skill_level FROM gossip_relationships WHERE player_uuid = ?",
            rs -> rs.next() ? rs.getInt("social_skill_level") : 1,
            playerId.toString()
        ).orElse(1);
    }
    
    @Override
    public void improveSocialSkill(UUID playerId, int points) {
        int currentLevel = getSocialSkillLevel(playerId);
        int newLevel = currentLevel + points;
        
        databaseService.update(
            "UPDATE gossip_relationships SET social_skill_level = ? WHERE player_uuid = ?",
            newLevel, playerId.toString()
        );
        
        // 检查升级
        if (newLevel % 10 == 0) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendTitle(
                    "§6§l社交技能提升!",
                    "§7等级: " + newLevel,
                    10, 60, 10
                );
            }
        }
    }
    
    // ===== 监听器: 监听私聊 =====
    @EventHandler(priority = EventPriority.LOW)
    public void onPrivateMessage(PrivateMessageEvent event) {
        Player sender = event.getSender();
        Player recipient = event.getRecipient();
        
        // 记录互动
        establishRelationship(sender.getUniqueId(), recipient.getUniqueId());
        updateRelationship(sender.getUniqueId(), recipient.getUniqueId(),
            RelationshipChange.builder()
                .intimacyChange(1)
                .lastInteraction(true)
                .build()
        );
        
        // 检查是否触发八卦记录
        if (shouldRecordAsGossip(event.getMessage())) {
            GossipType type = classifyGossip(event.getMessage());
            
            // 敏感内容有更高传播概率
            int sensitivity = calculateSensitivity(event.getMessage(), type);
            if (Math.random() < config.gossipChance() * (1 + sensitivity / 200.0)) {
                recordGossip(sender, recipient, event.getMessage(), type);
            }
        }
    }
    
    private boolean shouldRecordAsGossip(String message) {
        // 检查是否包含八卦关键词
        for (String keyword : config.gossipKeywords()) {
            if (message.contains(keyword)) {
                return true;
            }
        }
        
        // 检查消息长度
        if (message.length() > 100) {
            return Math.random() < config.longMessageChance();
        }
        
        return false;
    }
    
    private GossipType classifyGossip(String message) {
        String lower = message.toLowerCase();
        
        if (containsAny(lower, "约会", "喜欢", "爱", "追求", "恋爱", "男女朋友")) {
            return GossipType.ROMANCE;
        }
        if (containsAny(lower, "战争", "阴谋", "政变", "领导", "国家", "背叛")) {
            return GossipType.POLITICS;
        }
        if (containsAny(lower, "有钱", "富有", "金币", "财富", "资产", "破产")) {
            return GossipType.WEALTH;
        }
        if (containsAny(lower, "偷", "抢", "骗", "作弊", "外挂", "违规")) {
            return GossipType.CRIME;
        }
        
        return GossipType.GENERAL;
    }
    
    private int calculateSensitivity(String message, GossipType type) {
        int baseSensitivity = switch (type) {
            case CRIME -> 80;
            case POLITICS -> 60;
            case ROMANCE -> 50;
            case WEALTH -> 40;
            case GENERAL -> 20;
        };
        
        // 检查敏感词
        for (String word : config.sensitiveWords()) {
            if (message.contains(word)) {
                baseSensitivity += 10;
            }
        }
        
        return Math.min(100, baseSensitivity);
    }
}
```

---

## 3. 命令设计

### /gossip 命令
```
/gossip list              - 查看你听到的八卦
/gossip about <玩家>      - 查看关于某人的八卦
/gossip share <八卦ID> <玩家> - 传播八卦给某人
/gossip verify <八卦ID> <真|假> [澄清] - 验证八卦
/gossip subscribe <玩家>  - 订阅某人八卦
/gossip unsubscribe <玩家> - 取消订阅
/gossip subscriptions     - 查看订阅列表
/gossip skill             - 查看社交技能

# 社交关系
/relation <玩家>          - 查看与某人的关系
/relation trust <玩家> <+/-值> - 调整信任度
```

### GossipCommand.java
```java
public class GossipCommand implements CommandExecutor {
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("仅玩家可用", NamedTextColor.RED));
            return true;
        }
        
        if (args.length == 0) {
            showHelp(player);
            return true;
        }
        
        String sub = args[0].toLowerCase();
        
        return switch (sub) {
            case "list" -> handleList(player);
            case "about" -> handleAbout(player, args);
            case "share" -> handleShare(player, args);
            case "verify" -> handleVerify(player, args);
            case "subscribe" -> handleSubscribe(player, args);
            case "subscriptions" -> handleSubscriptions(player);
            case "skill" -> handleSkill(player);
            default -> { showHelp(player); yield true; }
        };
    }
    
    private void handleShare(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("用法: /gossip share <八卦ID> <玩家>", NamedTextColor.YELLOW));
            return;
        }
        
        UUID gossipId = UUID.fromString(args[1]);
        Player target = Bukkit.getPlayer(args[2]);
        
        if (target == null) {
            player.sendMessage(Component.text("玩家不在线", NamedTextColor.RED));
            return;
        }
        
        if (target.equals(player)) {
            player.sendMessage(Component.text("不能把八卦传给自己", NamedTextColor.RED));
            return;
        }
        
        try {
            gossipService.spreadGossip(gossipId, player, target);
            
            // 消耗社交技能点
            gossipService.improveSocialSkill(player.getUniqueId(), 1);
            
            player.sendMessage(Component.text()
                .append(Component.text("八卦已传递给 ", NamedTextColor.GREEN))
                .append(Component.text(target.getName(), NamedTextColor.GOLD))
            );
        } catch (Exception e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }
        
        return true;
    }
    
    private void handleVerify(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("用法: /gossip verify <八卦ID> <真|假> [澄清]", NamedTextColor.YELLOW));
            return;
        }
        
        UUID gossipId = UUID.fromString(args[1]);
        boolean isTrue = args[2].equalsIgnoreCase("真");
        String clarification = args.length > 3 ? args[3] : null;
        
        VerificationResult result = gossipService.verifyGossip(
            gossipId, player, isTrue, clarification);
        
        player.sendMessage(Component.text("验证完成！", NamedTextColor.GREEN));
        player.sendMessage(Component.text("验证准确度: " + String.format("%.0f%%", result.accuracy() * 100), NamedTextColor.GRAY));
    }
}
```

---

## 4. 配置文件

### config/gossip.yml
```yaml
# 八卦系统配置

# 传播设置
spread:
  # 最大传播次数
  max-spread: 5
  # 初始传播概率
  initial-spread-chance: 0.1
  # 八卦有效期(小时)
  gossip-lifetime: 48
  # 每次传播间隔(秒)
  spread-cooldown: 60

# 内容变形
transformation:
  # 是否启用内容变形
  enabled: true
  # 准确度低于此值开始变形
  distortion-threshold: 0.8
  # 最大变形程度(%)
  max-distortion: 50

# 社交关系
relationship:
  # 初始信任度
  initial-trust: 50
  # 每次互动信任变化
  trust-per-interaction: 1
  # 背叛惩罚
  betrayal-penalty: 20
  # 亲密值上限
  max-intimacy: 100

# 社交技能
social-skill:
  # 升级所需点数
  points-per-level: 100
  # 每级准确度加成(%)
  accuracy-bonus-per-level: 5
  # 最大等级
  max-level: 100

# 八卦关键词
keywords:
  - "听说"
  - "好像"
  - "据说"
  - "悄悄"
  - "秘密"
  - "告诉"
  - "别告诉"

# 敏感词(增加敏感度)
sensitive-words:
  - "领导"
  - "国家机密"
  - "偷窃"
  - "作弊"
  - "外挂"

# 八卦分类权重
type-sensitivity:
  CRIME: 80
  POLITICS: 60
  ROMANCE: 50
  WEALTH: 40
  GENERAL: 20

# 订阅系统
subscription:
  # 最大订阅数
  max-subscriptions: 20
  # 是否启用通知
  notifications-enabled: true
  # 通知延迟(秒)
  notification-delay: 30

# 奖励惩罚
rewards:
  # 分享八卦奖励
  share-reward: 10
  # 正确验证奖励
  correct-verify-reward: 50
  # 错误验证惩罚
  wrong-verify-penalty: 20
```

---

## 5. 事件系统

```java
// GossipRecordedEvent - 八卦被记录时触发
public record GossipRecordedEvent(
    GossipRecord gossip,
    Player source,
    Player target
) {}

// GossipSpreadEvent - 八卦传播时触发
public record GossipSpreadEvent(
    GossipRecord gossip,
    Player spreader,
    Player recipient,
    double accuracy
) {}

// GossipVerifiedEvent - 八卦被验证时触发
public record GossipVerifiedEvent(
    GossipRecord gossip,
    Player verifier,
    boolean isTrue
) {}

// SocialSkillUpgradedEvent - 社交技能升级时触发
public record SocialSkillUpgradedEvent(
    UUID playerId,
    int oldLevel,
    int newLevel
) {}
```

---

## 6. 实施计划

### Phase 1: 基础八卦 (1天)
- 数据库表
- 八卦记录和传播
- 基本命令

### Phase 2: 内容变形 (1天)
- 变形算法
- 准确度计算
- 关系系统

### Phase 3: 订阅和通知 (1天)
- 订阅系统
- 通知推送
- GUI界面

### Phase 4: 社交技能 (1天)
- 技能系统
- 升级逻辑
- 奖励惩罚

### 总工时: 约 4-5 人天