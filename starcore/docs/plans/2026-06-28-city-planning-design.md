# 城市规划许可证系统 - 详细设计方案

## 概述
建造大型建筑需申请城市规划许可。不同区域有不同规划要求：商业区、工业区、居住区，违规建设会被强制拆除或处罚。

---

## 1. 数据库设计

### 规划区表: planning_zones
```sql
CREATE TABLE planning_zones (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    zone_id CHAR(36) NOT NULL UNIQUE,
    nation_id CHAR(36) NOT NULL,
    
    -- 区域信息
    zone_name VARCHAR(64) NOT NULL,
    zone_type ENUM('RESIDENTIAL', 'COMMERCIAL', 'INDUSTRIAL', 'AGRICULTURAL', 
                   'CULTURAL', 'MILITARY', 'MIXED') NOT NULL,
    
    -- 范围
    center_x INT NOT NULL,
    center_z INT NOT NULL,
    radius INT DEFAULT 100,                       -- 区域半径
    points JSON,                                 -- 自定义多边形坐标
    
    -- 规划要求
    building_rules JSON,                         -- 建筑规则配置
    height_limit INT DEFAULT 20,                 -- 高度限制
    density_limit INT DEFAULT 50,                -- 密度限制
    style_requirement VARCHAR(64),               -- 风格要求
    
    -- 状态
    status ENUM('PLANNING', 'APPROVED', 'UNDER_REVIEW', 'REJECTED', 'ARCHIVED') DEFAULT 'PLANNING',
    approval_time TIMESTAMP,
    expiration_time TIMESTAMP,                   -- 规划到期时间
    
    -- 统计
    building_count INT DEFAULT 0,
    population_capacity INT DEFAULT 0,
    
    -- 时间戳
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified TIMESTAMP,
    
    INDEX idx_nation (nation_id),
    INDEX idx_type (zone_type),
    INDEX idx_status (status)
);
```

### 建筑许可证表: building_permits
```sql
CREATE TABLE building_permits (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    permit_id CHAR(36) NOT NULL UNIQUE,
    zone_id CHAR(36) NOT NULL,
    
    -- 申请者
    applicant_uuid CHAR(36) NOT NULL,
    nation_id CHAR(36) NOT NULL,
    
    -- 建筑信息
    building_name VARCHAR(64),
    building_type ENUM('HOUSE', 'SHOP', 'FACTORY', 'MONUMENT', 'INFRASTRUCTURE', 'OTHER') NOT NULL,
    location_x INT NOT NULL,
    location_y INT NOT NULL,
    location_z INT NOT NULL,
    world VARCHAR(64) NOT NULL,
    
    -- 设计规格
    blueprint_id CHAR(36),                       -- 蓝图ID
    design_style VARCHAR(64),                    -- 设计风格
    height INT NOT NULL,
    footprint INT NOT NULL,                      -- 占地面积
    floors INT DEFAULT 1,
    
    -- 申请状态
    status ENUM('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED', 'REVOKED') DEFAULT 'PENDING',
    rejection_reason TEXT,
    
    -- 审批信息
    reviewer_uuid CHAR(36),
    review_time TIMESTAMP,
    review_notes TEXT,
    
    -- 费用
    application_fee BIGINT DEFAULT 0,
    construction_fee BIGINT DEFAULT 0,
    paid BOOLEAN DEFAULT FALSE,
    
    -- 时间
    applied_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    approval_time TIMESTAMP,
    expiration_time TIMESTAMP,
    completion_deadline TIMESTAMP,
    
    -- 完成情况
    progress INT DEFAULT 0,                      -- 完成进度 0-100
    completion_time TIMESTAMP,
    
    INDEX idx_zone (zone_id),
    INDEX idx_applicant (applicant_uuid),
    INDEX idx_status (status),
    INDEX idx_location (world, location_x, location_z)
);
```

### 违规记录表: violations
```sql
CREATE TABLE violations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    violation_id CHAR(36) NOT NULL UNIQUE,
    zone_id CHAR(36) NOT NULL,
    
    -- 违规者
    violator_uuid CHAR(36) NOT NULL,
    nation_id CHAR(36) NOT NULL,
    
    -- 违规信息
    violation_type ENUM('HEIGHT_EXCEEDED', 'WRONG_ZONE', 'STYLE_VIOLATION', 
                       'NO_PERMIT', 'EXPIRED_PERMIT', 'DENSITY_EXCEEDED') NOT NULL,
    severity ENUM('MINOR', 'MODERATE', 'SEVERE', 'CRITICAL') DEFAULT 'MINOR',
    
    -- 位置
    location_x INT NOT NULL,
    location_y INT NOT NULL,
    location_z INT NOT NULL,
    world VARCHAR(64) NOT NULL,
    
    -- 处理状态
    status ENUM('REPORTED', 'WARNING', 'FINED', 'FORCED_REMOVAL', 'RESOLVED') DEFAULT 'REPORTED',
    
    -- 处罚
    fine_amount BIGINT DEFAULT 0,
    grace_period_hours INT DEFAULT 24,           -- 宽限期
    required_action TEXT,                         -- 必须执行的操作
    
    -- 时间
    reported_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_time TIMESTAMP,
    
    INDEX idx_violator (violator_uuid),
    INDEX idx_zone (zone_id),
    INDEX idx_status (status)
);
```

### 建筑风格表: building_styles
```sql
CREATE TABLE building_styles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    style_id CHAR(36) NOT NULL UNIQUE,
    
    -- 风格信息
    style_name VARCHAR(64) NOT NULL,             -- 如: "中式古典", "欧式哥特"
    culture VARCHAR(32),                         -- 文化背景
    era VARCHAR(32),                             -- 时代背景
    
    -- 规则配置
    allowed_materials JSON,                      -- 允许的材料
    banned_materials JSON,                       -- 禁止的材料
    required_features JSON,                      -- 必须的特征
    aesthetic_rules JSON,                        -- 美学规则
    
    -- 区域限制
    allowed_zone_types JSON,                     -- 允许的区域类型
    exclusive_zones JSON,                        -- 专属区域
    
    -- 使用统计
    usage_count INT DEFAULT 0,
    popularity_score DECIMAL(5,2) DEFAULT 0.50,
    
    UNIQUE KEY uk_name (style_name)
);
```

---

## 2. 核心类设计

### 接口: PlanningService
```java
package dev.starcore.starcore.module.city.planning;

import java.util.List;
import java.util.UUID;

public interface PlanningService {
    
    // ===== 规划区管理 =====
    /**
     * 创建规划区
     */
    PlanningZone createZone(UUID nationId, String name, ZoneType type, 
                           int centerX, int centerZ, int radius);
    
    /**
     * 获取规划区
     */
    Optional<PlanningZone> getZone(UUID zoneId);
    
    /**
     * 获取国家所有规划区
     */
    List<PlanningZone> getNationZones(UUID nationId);
    
    /**
     * 查询位置所属规划区
     */
    Optional<PlanningZone> getZoneAt(Location location);
    
    /**
     * 更新规划区规则
     */
    void updateZoneRules(UUID zoneId, ZoneRules rules);
    
    // ===== 许可证管理 =====
    /**
     * 申请建筑许可证
     */
    BuildingPermit applyForPermit(Player applicant, Location location, 
                                 BuildingType type, BuildingDesign design);
    
    /**
     * 审批许可证
     */
    PermitReviewResult reviewPermit(UUID permitId, UUID reviewer, 
                                   boolean approved, String notes);
    
    /**
     * 获取许可证
     */
    Optional<BuildingPermit> getPermit(UUID permitId);
    
    /**
     * 获取玩家待审批的许可证
     */
    List<BuildingPermit> getPendingPermits(UUID nationId);
    
    /**
     * 更新建筑进度
     */
    void updateProgress(UUID permitId, int progress);
    
    /**
     * 撤销许可证
     */
    void revokePermit(UUID permitId, String reason);
    
    // ===== 违规检测 =====
    /**
     * 检查违规
     */
    List<Violation> checkViolations(Location location);
    
    /**
     * 举报违规
     */
    void reportViolation(Player reporter, Location location, ViolationType type);
    
    /**
     * 处理违规
     */
    void resolveViolation(UUID violationId, ViolationResolution resolution);
    
    /**
     * 强制拆除违规建筑
     */
    void forceRemoval(UUID violationId);
    
    // ===== 建筑风格 =====
    /**
     * 获取可用建筑风格
     */
    List<BuildingStyle> getAvailableStyles();
    
    /**
     * 检查建筑是否符合风格
     */
    StyleCheckResult checkStyleCompliance(Location location, String styleId);
    
    // ===== 费用计算 =====
    /**
     * 计算申请费用
     */
    long calculateApplicationFee(BuildingType type, int size, ZoneType zoneType);
    
    /**
     * 计算罚款
     */
    long calculateFine(ViolationType type, ViolationSeverity severity);
}
```

### 模型类
```java
// PlanningZone.java
public record PlanningZone(
    UUID zoneId,
    UUID nationId,
    String name,
    ZoneType type,
    int centerX,
    int centerZ,
    int radius,
    List<Point> boundaryPoints,
    ZoneRules rules,
    int heightLimit,
    int densityLimit,
    String styleRequirement,
    ZoneStatus status,
    long approvalTime,
    long expirationTime,
    int buildingCount,
    int populationCapacity,
    long createdTime,
    long lastModified
) {
    public boolean contains(Location loc) {
        if (boundaryPoints != null && !boundaryPoints.isEmpty()) {
            return isPointInPolygon(loc.getX(), loc.getZ(), boundaryPoints);
        }
        // 圆形区域检测
        double dist = Math.sqrt(Math.pow(loc.getX() - centerX, 2) + Math.pow(loc.getZ() - centerZ, 2));
        return dist <= radius;
    }
}

// BuildingPermit.java
public record BuildingPermit(
    UUID permitId,
    UUID zoneId,
    UUID applicantId,
    UUID nationId,
    String buildingName,
    BuildingType type,
    Location location,
    BuildingDesign design,
    PermitStatus status,
    String rejectionReason,
    UUID reviewerId,
    long reviewTime,
    String reviewNotes,
    long applicationFee,
    long constructionFee,
    boolean paid,
    long appliedTime,
    long approvalTime,
    long expirationTime,
    long completionDeadline,
    int progress,
    long completionTime
) {}

// ZoneRules.java
public record ZoneRules(
    List<BuildingType> allowedBuildingTypes,
    List<BuildingType> prohibitedBuildingTypes,
    List<String> allowedMaterials,
    List<String> bannedMaterials,
    int minBuildingSpacing,
    int maxBuildingPerBlock,
    boolean requireAestheticReview,
    List<String> requiredFeatures,
    int setbackDistance
) {}

// Violation.java
public record Violation(
    UUID violationId,
    UUID zoneId,
    UUID violatorId,
    UUID nationId,
    ViolationType type,
    ViolationSeverity severity,
    Location location,
    ViolationStatus status,
    long fineAmount,
    int gracePeriodHours,
    String requiredAction,
    long reportedTime,
    long resolvedTime
) {}
```

### 核心实现: PlanningServiceImpl
```java
public class PlanningServiceImpl implements PlanningService, Listener {
    
    private final JavaPlugin plugin;
    private final DatabaseService databaseService;
    private final TreasuryService treasuryService;
    private final NationService nationService;
    private final StarCoreEventBus eventBus;
    private final BlueprintService blueprintService;
    
    private PlanningConfig config;
    
    // 区域缓存
    private final Map<World, RTree<PlanningZone>> zoneIndex = new ConcurrentHashMap<>();
    
    @Override
    public BuildingPermit applyForPermit(Player applicant, Location location,
                                        BuildingType type, BuildingDesign design) {
        // 检查国家成员
        Optional<Nation> nationOpt = nationService.nationOf(applicant.getUniqueId());
        if (nationOpt.isEmpty()) {
            throw new IllegalStateException("必须加入国家才能申请建筑许可");
        }
        Nation nation = nationOpt.get();
        
        // 检查位置是否在规划区内
        Optional<PlanningZone> zoneOpt = getZoneAt(location);
        if (zoneOpt.isEmpty()) {
            throw new IllegalStateException("该位置不在任何规划区内");
        }
        PlanningZone zone = zoneOpt.get();
        
        // 验证建筑类型是否允许
        if (!zone.rules().allowedBuildingTypes().contains(type)) {
            throw new IllegalArgumentException(
                "该规划区不允许建造 " + type.getDisplayName()
            );
        }
        
        // 检查高度限制
        if (design.height() > zone.heightLimit()) {
            throw new IllegalArgumentException(
                "建筑高度超过限制: 最大 " + zone.heightLimit() + " 格"
            );
        }
        
        // 检查风格要求
        if (zone.styleRequirement() != null && 
            !zone.styleRequirement().equals(design.style())) {
            throw new IllegalArgumentException(
                "该区域要求 " + zone.styleRequirement() + " 风格"
            );
        }
        
        // 计算费用
        long fee = calculateApplicationFee(type, design.footprint(), zone.type());
        
        // 冻结费用(预扣)
        if (!treasuryService.freeze(applicant.getUniqueId(), fee)) {
            throw new IllegalArgumentException("资金不足，需要 " + fee + " 金币");
        }
        
        // 创建许可证申请
        BuildingPermit permit = new BuildingPermit(
            UUID.randomUUID(),
            zone.zoneId(),
            applicant.getUniqueId(),
            nation.id().value(),
            design.name(),
            type,
            location,
            design,
            PermitStatus.PENDING,
            null,
            null,
            0,
            null,
            fee,
            calculateConstructionFee(design),
            false,
            System.currentTimeMillis(),
            0,
            0,
            System.currentTimeMillis() + config.defaultReviewPeriod() * 3600000L,
            0,
            0,
            0
        );
        
        savePermit(permit);
        
        // 通知审批者
        notifyReviewers(zone, permit);
        
        // 触发事件
        eventBus.publish(new PermitApplicationEvent(permit, applicant));
        
        applicant.sendMessage(Component.text()
            .append(Component.text("许可证申请已提交！", NamedTextColor.GREEN))
            .append(Component.text(" 申请费 " + fee + " 已冻结", NamedTextColor.GRAY))
        );
        
        return permit;
    }
    
    @Override
    public PermitReviewResult reviewPermit(UUID permitId, UUID reviewer,
                                          boolean approved, String notes) {
        BuildingPermit permit = getPermit(permitId)
            .orElseThrow(() -> new IllegalArgumentException("许可证不存在"));
        
        if (permit.status() != PermitStatus.PENDING) {
            throw new IllegalStateException("许可证状态不可审批");
        }
        
        // 检查审批者权限
        if (!nationService.isLeader(reviewer) && !nationService.hasPermission(reviewer, "planning.review")) {
            throw new IllegalStateException("你没有审批权限");
        }
        
        PermitStatus newStatus = approved ? PermitStatus.APPROVED : PermitStatus.REJECTED;
        
        BuildingPermit updated;
        if (approved) {
            updated = new BuildingPermit(
                permit.permitId(),
                permit.zoneId(),
                permit.applicantId(),
                permit.nationId(),
                permit.buildingName(),
                permit.type(),
                permit.location(),
                permit.design(),
                newStatus,
                null,
                reviewer,
                System.currentTimeMillis(),
                notes,
                permit.applicationFee(),
                permit.constructionFee(),
                true,
                permit.appliedTime(),
                System.currentTimeMillis(),
                System.currentTimeMillis() + config.defaultConstructionPeriod() * 3600000L,
                permit.expirationTime(),
                permit.progress(),
                permit.completionTime()
            );
            
            // 正式扣款
            treasuryService.unfreeze(permit.applicantId(), permit.applicationFee());
            treasuryService.withdraw(permit.nationId(), permit.applicationFee());
            
        } else {
            updated = new BuildingPermit(
                permit.permitId(),
                permit.zoneId(),
                permit.applicantId(),
                permit.nationId(),
                permit.buildingName(),
                permit.type(),
                permit.location(),
                permit.design(),
                newStatus,
                notes, // 拒绝原因
                reviewer,
                System.currentTimeMillis(),
                notes,
                permit.applicationFee(),
                permit.constructionFee(),
                false,
                permit.appliedTime(),
                0,
                0,
                0,
                permit.progress(),
                permit.completionTime()
            );
            
            // 退还费用
            treasuryService.unfreeze(permit.applicantId(), permit.applicationFee());
        }
        
        savePermit(updated);
        
        // 通知申请者
        Player applicant = Bukkit.getPlayer(permit.applicantId());
        if (applicant != null) {
            if (approved) {
                applicant.sendTitle("§a§l许可证已批准！", "§7可以开始建造了", 10, 60, 10);
            } else {
                applicant.sendMessage(Component.text()
                    .append(Component.text("[规划] 许可证被拒绝: ", NamedTextColor.RED))
                    .append(Component.text(notes, NamedTextColor.GRAY))
                );
            }
        }
        
        eventBus.publish(new PermitReviewedEvent(updated, reviewer, approved));
        
        return new PermitReviewResult(updated, approved);
    }
    
    @Override
    public List<Violation> checkViolations(Location location) {
        List<Violation> violations = new ArrayList<>();
        
        Optional<PlanningZone> zoneOpt = getZoneAt(location);
        if (zoneOpt.isEmpty()) {
            return violations; // 不在规划区内，无违规
        }
        
        PlanningZone zone = zoneOpt.get();
        
        // 检查是否有有效许可证
        Optional<BuildingPermit> permitOpt = getPermitAt(location);
        if (permitOpt.isEmpty()) {
            violations.add(createViolation(
                zone,
                location,
                ViolationType.NO_PERMIT,
                ViolationSeverity.CRITICAL
            ));
            return violations;
        }
        
        BuildingPermit permit = permitOpt.get();
        
        // 检查许可证是否过期
        if (permit.status() == PermitStatus.EXPIRED || 
            (permit.expirationTime() > 0 && System.currentTimeMillis() > permit.expirationTime())) {
            violations.add(createViolation(
                zone,
                location,
                ViolationType.EXPIRED_PERMIT,
                ViolationSeverity.SEVERE
            ));
        }
        
        // 检查建筑高度
        int currentHeight = getBuildingHeight(location);
        if (currentHeight > zone.heightLimit()) {
            violations.add(createViolation(
                zone,
                location,
                ViolationType.HEIGHT_EXCEEDED,
                ViolationSeverity.MODERATE
            ));
        }
        
        // 检查风格合规
        if (zone.styleRequirement() != null) {
            StyleCheckResult styleCheck = checkStyleCompliance(location, zone.styleRequirement());
            if (!styleCheck.compliant()) {
                violations.add(createViolation(
                    zone,
                    location,
                    ViolationType.STYLE_VIOLATION,
                    ViolationSeverity.MINOR
                ));
            }
        }
        
        return violations;
    }
    
    @Override
    public void resolveViolation(UUID violationId, ViolationResolution resolution) {
        Violation violation = getViolation(violationId)
            .orElseThrow(() -> new IllegalArgumentException("违规记录不存在"));
        
        Violation updated;
        
        switch (resolution) {
            case PAY_FINE -> {
                // 支付罚款
                if (!treasuryService.withdraw(violation.nationId(), violation.fineAmount())) {
                    throw new IllegalStateException("资金不足");
                }
                updated = new Violation(
                    violation.violationId(),
                    violation.zoneId(),
                    violation.violatorId(),
                    violation.nationId(),
                    violation.type(),
                    violation.severity(),
                    violation.location(),
                    ViolationStatus.RESOLVED,
                    violation.fineAmount(),
                    violation.gracePeriodHours(),
                    violation.requiredAction(),
                    violation.reportedTime(),
                    System.currentTimeMillis()
                );
            }
            case FIX -> {
                // 修复违规
                if (!checkViolations(violation.location()).isEmpty()) {
                    throw new IllegalStateException("违规尚未修复");
                }
                updated = new Violation(
                    violation.violationId(),
                    violation.zoneId(),
                    violation.violatorId(),
                    violation.nationId(),
                    violation.type(),
                    violation.severity(),
                    violation.location(),
                    ViolationStatus.RESOLVED,
                    violation.fineAmount(),
                    violation.gracePeriodHours(),
                    violation.requiredAction(),
                    violation.reportedTime(),
                    System.currentTimeMillis()
                );
            }
            case APPEAL -> {
                // 上诉
                updated = new Violation(
                    violation.violationId(),
                    violation.zoneId(),
                    violation.violatorId(),
                    violation.nationId(),
                    violation.type(),
                    violation.severity(),
                    violation.location(),
                    ViolationStatus.REPORTED, // 重新审核
                    violation.fineAmount(),
                    violation.gracePeriodHours() + 24, // 增加宽限期
                    violation.requiredAction(),
                    violation.reportedTime(),
                    violation.resolvedTime()
                );
            }
        }
        
        saveViolation(updated);
        eventBus.publish(new ViolationResolvedEvent(updated, resolution));
    }
    
    @Override
    public long calculateFine(ViolationType type, ViolationSeverity severity) {
        long baseFine = config.baseFine();
        
        long typeMultiplier = switch (type) {
            case NO_PERMIT -> 3.0;
            case HEIGHT_EXCEEDED -> 1.5;
            case WRONG_ZONE -> 2.0;
            case STYLE_VIOLATION -> 1.0;
            case EXPIRED_PERMIT -> 2.0;
            case DENSITY_EXCEEDED -> 1.5;
        };
        
        long severityMultiplier = switch (severity) {
            case MINOR -> 1.0;
            case MODERATE -> 2.0;
            case SEVERE -> 5.0;
            case CRITICAL -> 10.0;
        };
        
        return (long)(baseFine * typeMultiplier * severityMultiplier);
    }
    
    // ===== 事件监听: 建筑完成检测 =====
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        // 检查是否需要许可证
        Optional<PlanningZone> zoneOpt = getZoneAt(event.getBlock().getLocation());
        if (zoneOpt.isEmpty()) return;
        
        // 检查是否有有效许可证
        Optional<BuildingPermit> permitOpt = getPermitAt(event.getBlock().getLocation());
        
        if (permitOpt.isEmpty()) {
            // 无许可证，检查是否豁免
            if (!isExemptFromPermit(event.getPlayer(), event.getBlock())) {
                // 发送警告
                event.getPlayer().sendMessage(Component.text()
                    .append(Component.text("[规划警告] ", NamedTextColor.YELLOW))
                    .append(Component.text("该位置需要建筑许可证", NamedTextColor.GRAY))
                );
            }
        } else {
            // 更新进度
            BuildingPermit permit = permitOpt.get();
            if (permit.status() == PermitStatus.APPROVED) {
                int newProgress = calculateProgress(event.getBlock().getLocation(), permit);
                if (newProgress > permit.progress()) {
                    updateProgress(permit.permitId(), newProgress);
                }
            }
        }
    }
    
    @EventHandler
    public void onViolationDetected(ViolationDetectedEvent event) {
        Violation violation = event.getViolation();
        
        // 通知违规者
        Player violator = Bukkit.getPlayer(violation.violatorId());
        if (violator != null) {
            violator.sendMessage(Component.text()
                .append(Component.text("[规划违规] ", NamedTextColor.RED))
                .append(Component.text(violation.type().getDescription(), NamedTextColor.WHITE))
            );
            violator.sendMessage(Component.text()
                .append(Component.text("罚款: ", NamedTextColor.YELLOW))
                .append(Component.text(violation.fineAmount() + " 金币", NamedTextColor.GOLD))
            );
            violator.sendMessage(Component.text()
                .append(Component.text("宽限期: " + violation.gracePeriodHours() + " 小时", NamedTextColor.GRAY))
            );
        }
    }
}
```

---

## 3. 命令设计

### /planning 命令
```
/planning zone create <名称> <类型> [半径]  - 创建规划区
/planning zone list                            - 列出规划区
/planning zone rules <区域ID>                  - 查看/修改区域规则
/planning zone style <区域ID> <风格>           - 设置区域风格要求

/permit apply <类型> <高度> <面积>            - 申请建筑许可
/permit list                                   - 查看我的许可证
/permit status <许可证ID>                     - 查看许可证状态
/permit cancel <许可证ID>                     - 取消许可证
/permit progress <许可证ID> <进度>            - 更新建造进度

# 审批命令(官员)
/permit review <许可证ID> <通过|拒绝> [备注]  - 审批许可证
/permit pending                                - 查看待审批列表
/permit revoke <许可证ID> [原因]              - 撤销许可证

# 违规命令
/violation check <x> <z>                       - 检查违规
/violation report <x> <y> <z> <类型>          - 举报违规
/violation list                                - 查看违规列表
/violation resolve <违规ID> <缴纳罚款|修复|上诉> - 处理违规

# 风格命令
/style list                                    - 列出可用建筑风格
/style preview <风格ID>                        - 预览风格
/style check <x> <z>                           - 检查风格合规
```

---

## 4. 配置文件

### config/planning.yml
```yaml
# 城市规划系统配置

# 许可证设置
permit:
  # 默认审批期(小时)
  default-review-period: 24
  # 默认建造期(天)
  default-construction-period: 7
  # 许可证有效期(天)
  expiration-days: 30
  # 每次延期费用
  extension-fee: 10000

# 费用设置
fees:
  # 基础申请费
  base-application-fee: 5000
  # 按建筑类型费率
  type-rates:
    HOUSE: 1.0
    SHOP: 1.5
    FACTORY: 3.0
    MONUMENT: 5.0
    INFRASTRUCTURE: 2.0
  # 按区域类型费率
  zone-rates:
    RESIDENTIAL: 1.0
    COMMERCIAL: 1.5
    INDUSTRIAL: 2.0
    AGRICULTURAL: 0.5
    CULTURAL: 1.0
    MILITARY: 0.8
    MIXED: 1.2

# 违规罚款
violations:
  base-fine: 1000
  grace-period-hours: 24
  # 自动强制拆除阈值(天)
  auto-demolition-threshold: 168  # 7天

# 区域设置
zone:
  # 最小半径
  min-radius: 50
  # 最大半径
  max-radius: 500
  # 高度限制基础值
  base-height-limit: 20
  # 密度限制基础值
  base-density-limit: 50

# 建筑风格
styles:
  - id: chinese-classic
    name: 中式古典
    culture: CHINESE
    era: ANCIENT
    allowed-materials:
      - OAK_PLANKS
      - DARK_OAK_PLANKS
      - COBBLESTONE
      - STONE_BRICKS
    banned-materials:
      - IRON_BLOCK
      - GOLD_BLOCK
    required-features:
      - "中式屋顶"
      - "红色主调"
    allowed-zones:
      - CULTURAL
      - RESIDENTIAL
  
  - id: european-gothic
    name: 欧式哥特
    culture: EUROPEAN
    era: MEDIEVAL
    allowed-materials:
      - STONE_BRICKS
      - COBBLESTONE
      - DARK_PRISMARINE
    banned-materials:
      - OAK_PLANKS
      - SPRUCE_PLANKS
    required-features:
      - "尖拱"
      - "高塔"
    allowed-zones:
      - CULTURAL
      - RELIGIOUS

# 自动检测
auto-check:
  enabled: true
  interval-minutes: 15
  check-on-build: true
  notify-on-violation: true
```

---

## 5. 实施计划

### Phase 1: 基础规划 (2天)
- 数据库表
- 规划区管理
- 许可证申请/审批

### Phase 2: 违规检测 (1-2天)
- 违规检测逻辑
- 罚款计算
- 强制拆除

### Phase 3: 建筑风格 (1天)
- 风格系统
- 合规检查
- 美学规则

### 总工时: 约 5-6 人天