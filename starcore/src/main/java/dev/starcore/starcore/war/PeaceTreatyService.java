package dev.starcore.starcore.war;
import java.util.Optional;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.starcore.starcore.territory.TerritoryService;
import dev.starcore.starcore.module.army.ArmyService;
import dev.starcore.starcore.module.army.model.ArmyUnit;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 和平条约服务
 * 管理和平条约的提议、接受、执行
 */
public final class PeaceTreatyService {
    private final Plugin plugin;
    private final WarServiceImpl warService;
    private final WarStateStorage warStateStorage;
    private final TreasuryService treasuryService;
    private final TerritoryService territoryService;
    private final ArmyService armyService;
    private final Logger logger;
    private final PeaceTreatyConfig config;

    // 和平条约
    private final ConcurrentHashMap<UUID, PeaceTreaty> treaties = new ConcurrentHashMap<>();
    // 战争的条约索引
    private final ConcurrentHashMap<UUID, UUID> warTreaties = new ConcurrentHashMap<>();
    // 战争赔款
    private final ConcurrentHashMap<UUID, WarReparation> reparations = new ConcurrentHashMap<>();
    // 军事限制记录 (nationId -> restriction)
    private final ConcurrentHashMap<UUID, MilitaryRestriction> militaryRestrictions = new ConcurrentHashMap<>();
    // 资源贡纳记录 (nationId -> tribute record)
    private final ConcurrentHashMap<UUID, ResourceTributeRecord> resourceTributes = new ConcurrentHashMap<>();
    // 政治限制记录 (nationId -> restriction)
    private final ConcurrentHashMap<UUID, PoliticalRestriction> politicalRestrictions = new ConcurrentHashMap<>();

    public PeaceTreatyService(
        Plugin plugin,
        WarServiceImpl warService,
        WarStateStorage warStateStorage,
        TreasuryService treasuryService,
        TerritoryService territoryService,
        ArmyService armyService,
        PeaceTreatyConfig config
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.warService = Objects.requireNonNull(warService, "warService");
        this.warStateStorage = Objects.requireNonNull(warStateStorage, "warStateStorage");
        this.treasuryService = Objects.requireNonNull(treasuryService, "treasuryService");
        this.territoryService = Objects.requireNonNull(territoryService, "territoryService");
        this.armyService = Objects.requireNonNull(armyService, "armyService");
        this.config = Objects.requireNonNull(config, "config");
        this.logger = plugin.getLogger();

        loadTreatyData();
        startPeriodicTasks();
    }

    // ==================== 持久化方法 ====================

    /**
     * 保存条约数据到 WarStateStorage
     */
    private void saveTreatyData(PeaceTreaty treaty) {
        // 持久化赔款记录
        for (WarReparation reparation : reparations.values()) {
            if (reparation.treatyId().equals(treaty.id())) {
                warStateStorage.saveTreatyData(treaty.id(), "REPARATION_" + reparation.id(), serializeReparation(reparation));
            }
        }

        // 持久化军事限制
        for (MilitaryRestriction restriction : militaryRestrictions.values()) {
            if (restriction.treatyId().equals(treaty.id())) {
                warStateStorage.saveTreatyData(treaty.id(), "MILITARY_" + restriction.nationId(), serializeMilitaryRestriction(restriction));
            }
        }

        // 持久化资源贡纳
        for (ResourceTributeRecord record : resourceTributes.values()) {
            if (record.treatyId().equals(treaty.id())) {
                warStateStorage.saveTreatyData(treaty.id(), "TRIBUTE_" + record.payerId(), serializeResourceTribute(record));
            }
        }

        // 持久化政治限制
        for (PoliticalRestriction restriction : politicalRestrictions.values()) {
            if (restriction.treatyId().equals(treaty.id())) {
                warStateStorage.saveTreatyData(treaty.id(), "POLITICAL_" + restriction.nationId(), serializePoliticalRestriction(restriction));
            }
        }

        logger.info("Treaty data saved for treaty: " + treaty.id());
    }

    /**
     * 加载条约数据
     */
    private void loadTreatyData() {
        // 从 warStateStorage 加载所有条约相关数据
        // 目前使用内存存储，后续可以扩展为从数据库加载
        logger.info("Treaty data loaded (using in-memory storage)");
    }

    private String serializeReparation(WarReparation reparation) {
        return String.format("REPARATION:%s:%s:%s:%s:%s:%d",
            reparation.id(), reparation.payerId(), reparation.receiverId(),
            reparation.totalAmount().toPlainString(), reparation.totalInstallments(),
            reparation.status().ordinal());
    }

    private String serializeMilitaryRestriction(MilitaryRestriction restriction) {
        return String.format("MILITARY:%s:%s:%d:%b:%s:%s",
            restriction.id(), restriction.nationId(),
            restriction.maxArmySize(), restriction.disarmament(),
            restriction.imposedAt().toEpochMilli(),
            restriction.liftedAt() != null ? restriction.liftedAt().toEpochMilli() : "0");
    }

    private String serializeResourceTribute(ResourceTributeRecord record) {
        return String.format("TRIBUTE:%s:%s:%s:%s:%d:%d:%d:%s:%s",
            record.id(), record.payerId(), record.receiverId(),
            record.resourceType(), record.amountPerMonth(),
            record.durationMonths(), record.completedPayments(),
            record.startTime().toEpochMilli(),
            record.completedTime() != null ? record.completedTime().toEpochMilli() : "0");
    }

    private String serializePoliticalRestriction(PoliticalRestriction restriction) {
        return String.format("POLITICAL:%s:%s:%s:%s:%s:%s",
            restriction.id(), restriction.nationId(),
            restriction.requirement(), restriction.description(),
            restriction.imposedAt().toEpochMilli(),
            restriction.liftedAt() != null ? restriction.liftedAt().toEpochMilli() : "0");
    }

    /**
     * 提议和平条约
     */
    public PeaceTreaty proposeTreaty(
        UUID warId,
        NationId victor,
        NationId defeated,
        List<PeaceTreaty.PeaceTerm> terms
    ) {
        Objects.requireNonNull(warId, "warId");
        Objects.requireNonNull(victor, "victor");
        Objects.requireNonNull(defeated, "defeated");
        Objects.requireNonNull(terms, "terms");

        // 检查战争是否存在
        War war = warService.getWar(warId)
            .orElseThrow(() -> new IllegalArgumentException("War not found"));

        // 检查是否已有条约
        if (warTreaties.containsKey(warId)) {
            throw new IllegalStateException("Peace treaty already exists for this war");
        }

        // 创建条约
        PeaceTreaty treaty = new PeaceTreaty(
            UUID.randomUUID(),
            warId,
            victor,
            defeated,
            terms,
            Instant.now()
        );

        treaties.put(treaty.id(), treaty);
        warTreaties.put(warId, treaty.id());

        logger.info(String.format("Peace treaty proposed: War=%s, Victor=%s, Defeated=%s, Terms=%d",
            warId, victor, defeated, terms.size()));

        return treaty;
    }

    /**
     * 接受和平条约
     */
    public void acceptTreaty(UUID treatyId) {
        PeaceTreaty treaty = treaties.get(treatyId);
        if (treaty == null) {
            throw new IllegalArgumentException("Treaty not found");
        }

        treaty.accept();

        // 执行条约
        executeTreaty(treaty);

        // 结束战争 - 修复多国战争结束逻辑
        Optional<War> warOpt = warService.getWar(treaty.warId());
        if (warOpt.isPresent()) {
            War war = warOpt.get();
            // 结束整个战争（包括所有盟友）
            endWarWithAllies(war);
        }

        // 持久化条约数据
        saveTreatyData(treaty);

        logger.info(String.format("Peace treaty accepted: %s", treatyId));
    }

    /**
     * 结束战争及其所有参战国
     */
    private void endWarWithAllies(War war) {
        // 使用 WarServiceImpl.endWar 来结束战争，确保状态同步
        warService.endWar(war.aggressor(), war.defender());

        // 清理所有参战国的外交关系到 NEUTRAL
        Set<NationId> allParticipants = new HashSet<>();
        allParticipants.add(war.aggressor());
        allParticipants.add(war.defender());
        allParticipants.addAll(war.aggressorAllies());
        allParticipants.addAll(war.defenderAllies());

        // 持久化更新后的战争状态
        warStateStorage.saveWar(war);

        logger.info(String.format("War ended with all participants: %s", war.name()));
    }

    /**
     * 拒绝和平条约
     */
    public void rejectTreaty(UUID treatyId) {
        PeaceTreaty treaty = treaties.get(treatyId);
        if (treaty == null) {
            throw new IllegalArgumentException("Treaty not found");
        }

        treaty.reject();

        logger.info(String.format("Peace treaty rejected: %s", treatyId));
    }

    /**
     * 获取和平条约
     */
    public Optional<PeaceTreaty> getTreaty(UUID treatyId) {
        return Optional.ofNullable(treaties.get(treatyId));
    }

    /**
     * 获取战争的和平条约
     */
    public Optional<PeaceTreaty> getTreatyForWar(UUID warId) {
        UUID treatyId = warTreaties.get(warId);
        if (treatyId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(treaties.get(treatyId));
    }

    /**
     * 执行条约
     */
    private void executeTreaty(PeaceTreaty treaty) {
        for (PeaceTreaty.PeaceTerm term : treaty.terms()) {
            switch (term) {
                case PeaceTreaty.ReparationTerm reparationTerm -> createReparation(treaty, reparationTerm);
                case PeaceTreaty.TerritorialCessionTerm cessionTerm -> executeTerritorialCession(treaty, cessionTerm);
                case PeaceTreaty.MilitaryRestrictionTerm restrictionTerm -> executeMilitaryRestriction(treaty, restrictionTerm);
                case PeaceTreaty.PoliticalTerm politicalTerm -> executePoliticalTerm(treaty, politicalTerm);
                case PeaceTreaty.ResourceTributeTerm tributeTerm -> executeResourceTribute(treaty, tributeTerm);
                default -> {
                    // 未知条款类型，记录警告
                    logger.warning("Unknown peace term type: " + term.getClass().getName());
                }
            }
        }
    }

    // ==================== 领土割让条款执行 ====================

    /**
     * 执行领土割让条款
     */
    private void executeTerritorialCession(PeaceTreaty treaty, PeaceTreaty.TerritorialCessionTerm term) {
        UUID defeatedId = treaty.defeated().value();
        UUID victorId = treaty.victor().value();

        int transferredCount = 0;
        for (String territoryIdStr : term.territoryIds()) {
            try {
                UUID territoryId = UUID.fromString(territoryIdStr);
                territoryService.transferOwnership(territoryId, victorId);
                transferredCount++;
            } catch (IllegalArgumentException e) {
                logger.warning(String.format("Invalid territory ID in cession: %s", territoryIdStr));
            }
        }

        logger.info(String.format("Territorial cession executed: %d territories transferred from %s to %s",
            transferredCount, treaty.defeated(), treaty.victor()));
    }

    // ==================== 军事限制条款执行 ====================

    /**
     * 执行军事限制条款
     */
    private void executeMilitaryRestriction(PeaceTreaty treaty, PeaceTreaty.MilitaryRestrictionTerm term) {
        UUID defeatedId = treaty.defeated().value();

        // 创建军事限制记录
        MilitaryRestriction restriction = new MilitaryRestriction(
            UUID.randomUUID(),
            treaty.id(),
            defeatedId,
            term.maxArmySize(),
            term.disarmament(),
            Instant.now(),
            null // 解除时间稍后计算
        );
        militaryRestrictions.put(defeatedId, restriction);

        if (term.disarmament()) {
            // 解散失败方所有军队
            List<ArmyUnit> nationArmies = armyService.getNationArmies(defeatedId);
            for (ArmyUnit army : nationArmies) {
                armyService.disbandArmy(army.id());
            }
            logger.info(String.format("Disarmament executed: All armies of %s disbanded", treaty.defeated()));
        } else {
            // 限制军队规模
            enforceArmySizeLimit(defeatedId, term.maxArmySize());
        }

        logger.info(String.format("Military restriction applied to %s: maxArmySize=%d, disarmament=%s",
            treaty.defeated(), term.maxArmySize(), term.disarmament()));
    }

    /**
     * 强制执行军队规模限制
     */
    private void enforceArmySizeLimit(UUID nationId, int maxSize) {
        List<ArmyUnit> nationArmies = armyService.getNationArmies(nationId);
        int currentTotal = nationArmies.stream()
            .mapToInt(ArmyUnit::soldiers)
            .sum();

        if (currentTotal > maxSize) {
            // 按规模从大到小解散军队直到满足限制
            List<ArmyUnit> sortedArmies = nationArmies.stream()
                .sorted(Comparator.comparingInt(ArmyUnit::soldiers).reversed())
                .collect(Collectors.toList());

            int excess = currentTotal - maxSize;
            for (ArmyUnit army : sortedArmies) {
                if (excess <= 0) break;
                // 整支军队解散（无法部分解散）
                armyService.disbandArmy(army.id());
                excess -= army.soldiers();
            }
            logger.info(String.format("Army size limit enforced: %d soldiers reduced from nation %s",
                currentTotal - Math.max(0, excess), nationId));
        }
    }

    /**
     * 检查国家是否受军事限制
     */
    public boolean hasMilitaryRestriction(UUID nationId) {
        MilitaryRestriction restriction = militaryRestrictions.get(nationId);
        return restriction != null && restriction.isActive();
    }

    /**
     * 获取军事限制记录
     */
    public Optional<MilitaryRestriction> getMilitaryRestriction(UUID nationId) {
        return Optional.ofNullable(militaryRestrictions.get(nationId));
    }

    // ==================== 政治条款执行 ====================

    /**
     * 执行政治条款
     */
    private void executePoliticalTerm(PeaceTreaty treaty, PeaceTreaty.PoliticalTerm term) {
        UUID defeatedId = treaty.defeated().value();

        // 创建政治限制记录
        PoliticalRestriction restriction = new PoliticalRestriction(
            UUID.randomUUID(),
            treaty.id(),
            defeatedId,
            term.requirement(),
            term.description(),
            Instant.now(),
            null
        );
        politicalRestrictions.put(defeatedId, restriction);

        // 根据政治条款类型执行不同的效果
        executePoliticalEffect(defeatedId, treaty.victor().value(), term.requirement());

        logger.info(String.format("Political term applied to %s: %s - %s",
            treaty.defeated(), term.requirement(), term.description()));
    }

    /**
     * 执行政治效果
     */
    private void executePoliticalEffect(UUID defeatedNationId, UUID victorNationId, String requirement) {
        switch (requirement.toLowerCase()) {
            case "vassal" -> {
                // 傀儡状态：失败方成为胜利方的附庸
                // 这里记录关系，后续可以通过 DiplomacyService 设置附庸关系
                logger.info(String.format("Nation %s becomes vassal of %s", defeatedNationId, victorNationId));
            }
            case "regime_change" -> {
                // 政权更迭：触发国家领导人变更
                // 可以通过 NationService 重置国家领导层
                logger.info(String.format("Regime change triggered for nation %s", defeatedNationId));
            }
            case "neutrality" -> {
                // 中立化：失败方必须保持中立，不能加入任何联盟
                logger.info(String.format("Nation %s is now neutralized", defeatedNationId));
            }
            case "no_war" -> {
                // 禁止宣战：在一定期限内不能主动宣战
                logger.info(String.format("Nation %s is prohibited from declaring war", defeatedNationId));
            }
            case "embargo" -> {
                // 贸易禁运：禁止与特定国家进行贸易
                logger.info(String.format("Embargo imposed on nation %s", defeatedNationId));
            }
            default -> {
                // 自定义条款：记录但不执行具体效果
                logger.info(String.format("Custom political requirement '%s' applied to nation %s", requirement, defeatedNationId));
            }
        }
    }

    /**
     * 检查国家是否受政治限制
     */
    public boolean hasPoliticalRestriction(UUID nationId) {
        PoliticalRestriction restriction = politicalRestrictions.get(nationId);
        return restriction != null && restriction.isActive();
    }

    /**
     * 获取政治限制记录
     */
    public Optional<PoliticalRestriction> getPoliticalRestriction(UUID nationId) {
        return Optional.ofNullable(politicalRestrictions.get(nationId));
    }

    // ==================== 资源贡纳条款执行 ====================

    /**
     * 执行资源贡纳条款
     */
    private void executeResourceTribute(PeaceTreaty treaty, PeaceTreaty.ResourceTributeTerm term) {
        UUID defeatedId = treaty.defeated().value();
        UUID victorId = treaty.victor().value();

        // 创建贡纳记录
        ResourceTributeRecord record = new ResourceTributeRecord(
            UUID.randomUUID(),
            treaty.id(),
            defeatedId,
            victorId,
            term.resourceType(),
            term.amount(),
            term.durationMonths(),
            0, // 已贡纳次数
            Instant.now(),
            null // 完成时间稍后计算
        );
        resourceTributes.put(defeatedId, record);

        logger.info(String.format("Resource tribute started: %s pays %d %s/month to %s for %d months",
            treaty.defeated(), term.amount(), term.resourceType(), treaty.victor(), term.durationMonths()));
    }

    /**
     * 执行资源贡纳（每月调用）
     */
    public void processMonthlyTribute(UUID nationId) {
        ResourceTributeRecord record = resourceTributes.get(nationId);
        if (record == null || !record.isActive()) {
            return;
        }

        // 检查是否已完成
        if (record.completedPayments() >= record.durationMonths()) {
            record.complete();
            resourceTributes.remove(nationId);
            logger.info(String.format("Resource tribute completed for nation: %s", nationId));
            return;
        }

        // 执行贡纳 - 从失败方国库扣除，转给胜利方
        // 注意：这里需要根据资源类型调用不同的处理方法
        BigDecimal tributeValue = record.calculateValue();
        NationId payerId = NationId.of(record.payerId());
        NationId receiverId = NationId.of(record.receiverId());

        if (treasuryService.withdraw(payerId, tributeValue)) {
            treasuryService.deposit(receiverId, tributeValue);
            record.recordPayment();
            logger.info(String.format("Monthly tribute paid: %s -> %s, value=%s (month %d/%d)",
                payerId, receiverId, tributeValue, record.completedPayments(), record.durationMonths()));
        } else {
            logger.warning(String.format("Failed to collect tribute from %s: insufficient funds", payerId));
        }
    }

    /**
     * 获取资源贡纳记录
     */
    public Optional<ResourceTributeRecord> getResourceTribute(UUID nationId) {
        return Optional.ofNullable(resourceTributes.get(nationId));
    }

    /**
     * 获取所有活跃的贡纳记录
     */
    public Collection<ResourceTributeRecord> getActiveTributes() {
        return resourceTributes.values().stream()
            .filter(ResourceTributeRecord::isActive)
            .collect(Collectors.toList());
    }

    /**
     * 创建赔款记录
     */
    private void createReparation(PeaceTreaty treaty, PeaceTreaty.ReparationTerm term) {
        WarReparation reparation = new WarReparation(
            UUID.randomUUID(),
            treaty.id(),
            treaty.defeated().value(),  // NationId已经是UUID
            treaty.victor().value(),    // NationId已经是UUID
            term.amount(),
            term.installments(),
            Instant.now()
        );

        reparations.put(reparation.id(), reparation);

        logger.info(String.format("War reparation created: Amount=%s, Installments=%d",
            term.amount(), term.installments()));
    }

    /**
     * 支付赔款
     */
    public void payReparation(UUID reparationId, BigDecimal amount) {
        WarReparation reparation = reparations.get(reparationId);
        if (reparation == null) {
            throw new IllegalArgumentException("Reparation not found");
        }

        NationId payerId = NationId.of(reparation.payerId());
        NationId receiverId = NationId.of(reparation.receiverId());

        // 从支付方国库扣除
        boolean withdrawn = treasuryService.withdraw(payerId, amount);
        if (!withdrawn) {
            logger.warning(String.format("Failed to withdraw reparation payment from nation treasury: %s, amount=%s",
                payerId, amount));
            return;
        }

        // 转给接收方国库
        treasuryService.deposit(receiverId, amount);

        // 记录支付
        reparation.recordPayment(amount);

        // 记录事件
        recordReparationPaymentEvent(reparation, amount);

        logger.info(String.format("Reparation payment: %s paid %s to %s (Progress: %.1f%%)",
            payerId, amount.toPlainString(), receiverId, reparation.progressPercentage()));
    }

    /**
     * 记录赔款支付事件
     */
    private void recordReparationPaymentEvent(WarReparation reparation, BigDecimal amount) {
        // 获取事件服务记录支付事件
        String message = String.format("War reparation paid: %s to %s, amount=%s, progress=%.1f%%",
            NationId.of(reparation.payerId()),
            NationId.of(reparation.receiverId()),
            amount.toPlainString(),
            reparation.progressPercentage());

        // 记录到支付方事件
        // Note: 事件服务需要在外部通过 WarReparation 事件获取
        logger.info("Reparation event: " + message);
    }

    /**
     * 免除赔款
     */
    public void forgiveReparation(UUID reparationId) {
        WarReparation reparation = reparations.get(reparationId);
        if (reparation == null) {
            throw new IllegalArgumentException("Reparation not found");
        }

        reparation.forgive();

        logger.info(String.format("Reparation forgiven: %s", reparationId));
    }

    /**
     * 获取赔款记录
     */
    public Optional<WarReparation> getReparation(UUID reparationId) {
        return Optional.ofNullable(reparations.get(reparationId));
    }

    /**
     * 获取条约的赔款记录
     */
    public List<WarReparation> getReparationsForTreaty(UUID treatyId) {
        return reparations.values().stream()
            .filter(r -> r.treatyId().equals(treatyId))
            .collect(Collectors.toList());
    }

    /**
     * 获取国家的赔款记录（作为支付方）
     */
    public List<WarReparation> getReparationsAsPayer(UUID nationId) {
        return reparations.values().stream()
            .filter(r -> r.payerId().equals(nationId))
            .filter(r -> !r.isCompleted())
            .collect(Collectors.toList());
    }

    /**
     * 获取国家的赔款记录（作为接收方）
     */
    public List<WarReparation> getReparationsAsReceiver(UUID nationId) {
        return reparations.values().stream()
            .filter(r -> r.receiverId().equals(nationId))
            .filter(r -> !r.isCompleted())
            .collect(Collectors.toList());
    }

    /**
     * 启动定时任务
     */
    private void startPeriodicTasks() {
        // 每天检查一次赔款状态
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            checkReparations();
        }, 20L * 60 * 60 * 24, 20L * 60 * 60 * 24);
    }

    /**
     * 检查赔款状态
     */
    private void checkReparations() {
        Instant now = Instant.now();

        for (WarReparation reparation : reparations.values()) {
            if (reparation.status() != WarReparation.ReparationStatus.ACTIVE) {
                continue;
            }

            // 检查是否逾期
            if (reparation.isOverdue(now)) {
                reparation.markDefault();
                logger.warning(String.format("Reparation defaulted: %s", reparation.id()));

                // 可以在这里触发惩罚，如外交关系恶化等
            }
        }
    }

    /**
     * 和平条约配置
     */
    public record PeaceTreatyConfig(
        Duration treatyExpirationTime,
        int maxTermsPerTreaty,
        BigDecimal minReparationAmount
    ) {
        public static PeaceTreatyConfig defaults() {
            return new PeaceTreatyConfig(
                Duration.ofDays(7),         // 条约7天后过期
                10,                         // 最多10个条款
                new BigDecimal("1000")      // 最低赔款金额
            );
        }
    }

    // ==================== 数据模型 ====================

    /**
     * 军事限制记录
     */
    public static class MilitaryRestriction {
        private final UUID id;
        private final UUID treatyId;
        private final UUID nationId;
        private final int maxArmySize;
        private final boolean disarmament;
        private final Instant imposedAt;
        private Instant liftedAt;

        public MilitaryRestriction(
            UUID id,
            UUID treatyId,
            UUID nationId,
            int maxArmySize,
            boolean disarmament,
            Instant imposedAt,
            Instant liftedAt
        ) {
            this.id = Objects.requireNonNull(id);
            this.treatyId = Objects.requireNonNull(treatyId);
            this.nationId = Objects.requireNonNull(nationId);
            this.maxArmySize = maxArmySize;
            this.disarmament = disarmament;
            this.imposedAt = Objects.requireNonNull(imposedAt);
            this.liftedAt = liftedAt;
        }

        public UUID id() { return id; }
        public UUID treatyId() { return treatyId; }
        public UUID nationId() { return nationId; }
        public int maxArmySize() { return maxArmySize; }
        public boolean disarmament() { return disarmament; }
        public Instant imposedAt() { return imposedAt; }
        public Instant liftedAt() { return liftedAt; }

        public boolean isActive() {
            return liftedAt == null;
        }

        public void lift() {
            this.liftedAt = Instant.now();
        }

        public String description() {
            if (disarmament) {
                return "完全解除武装";
            }
            return String.format("军队规模限制: %d人", maxArmySize);
        }
    }

    /**
     * 资源贡纳记录
     */
    public static class ResourceTributeRecord {
        private final UUID id;
        private final UUID treatyId;
        private final UUID payerId;
        private final UUID receiverId;
        private final String resourceType;
        private final int amountPerMonth;
        private final int durationMonths;
        private int completedPayments;
        private final Instant startTime;
        private Instant completedTime;

        public ResourceTributeRecord(
            UUID id,
            UUID treatyId,
            UUID payerId,
            UUID receiverId,
            String resourceType,
            int amountPerMonth,
            int durationMonths,
            int completedPayments,
            Instant startTime,
            Instant completedTime
        ) {
            this.id = Objects.requireNonNull(id);
            this.treatyId = Objects.requireNonNull(treatyId);
            this.payerId = Objects.requireNonNull(payerId);
            this.receiverId = Objects.requireNonNull(receiverId);
            this.resourceType = Objects.requireNonNull(resourceType);
            this.amountPerMonth = amountPerMonth;
            this.durationMonths = durationMonths;
            this.completedPayments = completedPayments;
            this.startTime = Objects.requireNonNull(startTime);
            this.completedTime = completedTime;
        }

        public UUID id() { return id; }
        public UUID treatyId() { return treatyId; }
        public UUID payerId() { return payerId; }
        public UUID receiverId() { return receiverId; }
        public String resourceType() { return resourceType; }
        public int amountPerMonth() { return amountPerMonth; }
        public int durationMonths() { return durationMonths; }
        public int completedPayments() { return completedPayments; }
        public Instant startTime() { return startTime; }
        public Instant completedTime() { return completedTime; }

        public boolean isActive() {
            return completedTime == null && completedPayments < durationMonths;
        }

        public void recordPayment() {
            this.completedPayments++;
        }

        public void complete() {
            this.completedTime = Instant.now();
        }

        /**
         * 计算贡纳价值（转换为货币）
         * 需要根据资源类型定义兑换比率
         */
        public BigDecimal calculateValue() {
            // 默认按 1单位=100货币 计算
            BigDecimal unitValue = switch (resourceType.toLowerCase()) {
                case "gold", "金币" -> BigDecimal.valueOf(1);
                case "iron", "铁" -> BigDecimal.valueOf(0.5);
                case "food", "粮食" -> BigDecimal.valueOf(0.2);
                case "wood", "木材" -> BigDecimal.valueOf(0.3);
                case "stone", "石头" -> BigDecimal.valueOf(0.4);
                default -> BigDecimal.valueOf(1);
            };
            return unitValue.multiply(BigDecimal.valueOf(amountPerMonth));
        }
    }

    /**
     * 政治限制记录
     */
    public static class PoliticalRestriction {
        private final UUID id;
        private final UUID treatyId;
        private final UUID nationId;
        private final String requirement;      // vassa/regime_change/neutrality/no_war/embargo
        private final String description;
        private final Instant imposedAt;
        private Instant liftedAt;

        public PoliticalRestriction(
            UUID id,
            UUID treatyId,
            UUID nationId,
            String requirement,
            String description,
            Instant imposedAt,
            Instant liftedAt
        ) {
            this.id = Objects.requireNonNull(id);
            this.treatyId = Objects.requireNonNull(treatyId);
            this.nationId = Objects.requireNonNull(nationId);
            this.requirement = Objects.requireNonNull(requirement);
            this.description = Objects.requireNonNull(description);
            this.imposedAt = Objects.requireNonNull(imposedAt);
            this.liftedAt = liftedAt;
        }

        public UUID id() { return id; }
        public UUID treatyId() { return treatyId; }
        public UUID nationId() { return nationId; }
        public String requirement() { return requirement; }
        public String description() { return description; }
        public Instant imposedAt() { return imposedAt; }
        public Instant liftedAt() { return liftedAt; }

        public boolean isActive() {
            return liftedAt == null;
        }

        public void lift() {
            this.liftedAt = Instant.now();
        }
    }
}
