package dev.starcore.starcore.module.arbitration;

import dev.starcore.starcore.module.arbitration.model.ArbitrationCase;
import dev.starcore.starcore.module.arbitration.model.ArbitrationCaseType;
import dev.starcore.starcore.module.arbitration.model.ArbitrationResult;
import dev.starcore.starcore.module.arbitration.model.ArbitrationStatus;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.foundation.territory.TerritoryService;
import dev.starcore.starcore.core.persistence.PersistenceService;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.util.*;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 领土仲裁服务实现
 */
public final class ArbitrationServiceImpl implements ArbitrationService {
    private static final String PERSISTENCE_NAMESPACE = "arbitration";
    private static final String CASES_FILE = "cases.dat";

    private final Plugin plugin;
    private final NationService nationService;
    private final TreasuryService treasuryService;
    private final TerritoryService territoryService;
    private final PersistenceService persistenceService;
    private final ArbitrationConfig config;

    // 内存中的案例存储
    private final Map<UUID, ArbitrationCase> cases = new ConcurrentHashMap<>();
    // 国家到案例的索引
    private final Map<NationId, Set<UUID>> nationCases = new ConcurrentHashMap<>();
    // 仲裁员到案例的索引
    private final Map<UUID, Set<UUID>> arbitratorCases = new ConcurrentHashMap<>();

    public ArbitrationServiceImpl(
        Plugin plugin,
        NationService nationService,
        TreasuryService treasuryService,
        TerritoryService territoryService,
        PersistenceService persistenceService,
        ArbitrationConfig config
    ) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.treasuryService = treasuryService;
        this.territoryService = territoryService;
        this.persistenceService = persistenceService;
        this.config = config;

        loadState();
    }

    // ==================== Case Submission ====================

    @Override
    public ArbitrationCase submitCase(
        NationId claimant,
        NationId respondent,
        ArbitrationCaseType caseType,
        List<ChunkCoordinate> disputedChunks,
        String evidence,
        BigDecimal claimFee
    ) {
        // 验证双方国家存在
        if (nationService.nationById(claimant).isEmpty()) {
            throw new IllegalArgumentException("Claimant nation not found");
        }
        if (nationService.nationById(respondent).isEmpty()) {
            throw new IllegalArgumentException("Respondent nation not found");
        }

        // 不能申诉自己
        if (claimant.equals(respondent)) {
            throw new IllegalArgumentException("Cannot file case against yourself");
        }

        // 验证费用
        if (claimFee.compareTo(config.minimumClaimFee()) < 0) {
            throw new IllegalStateException("Claim fee below minimum: " + config.minimumClaimFee());
        }
        if (claimFee.compareTo(config.maximumClaimFee()) > 0) {
            throw new IllegalStateException("Claim fee above maximum: " + config.maximumClaimFee());
        }

        // 检查是否有重复案件
        if (hasPendingCaseBetween(claimant, respondent)) {
            throw new IllegalStateException("Pending case already exists between these nations");
        }

        // 从国库扣除费用
        if (config.chargeFilingFee() && claimFee.signum() > 0) {
            if (!treasuryService.withdraw(claimant, claimFee)) {
                throw new IllegalStateException("Insufficient treasury balance for filing fee");
            }
        }

        // 创建案例
        ArbitrationCase arbitrationCase = new ArbitrationCase(
            UUID.randomUUID(),
            claimant,
            respondent,
            caseType,
            disputedChunks != null ? disputedChunks : List.of(),
            evidence,
            claimFee
        );

        // 存储
        cases.put(arbitrationCase.id(), arbitrationCase);
        nationCases.computeIfAbsent(claimant, k -> ConcurrentHashMap.newKeySet()).add(arbitrationCase.id());
        nationCases.computeIfAbsent(respondent, k -> ConcurrentHashMap.newKeySet()).add(arbitrationCase.id());

        // 持久化
        persistCase(arbitrationCase);

        plugin.getLogger().info("Arbitration case submitted: " + arbitrationCase.id() + " by " + claimant + " against " + respondent);

        return arbitrationCase;
    }

    @Override
    public boolean acceptCase(UUID caseId, UUID arbitrator) {
        ArbitrationCase arbitrationCase = cases.get(caseId);
        if (arbitrationCase == null) {
            return false;
        }

        if (arbitrationCase.status() != ArbitrationStatus.PENDING) {
            return false;
        }

        if (!arbitrationCase.assignArbitrator(arbitrator)) {
            return false;
        }

        // 更新索引
        arbitratorCases.computeIfAbsent(arbitrator, k -> ConcurrentHashMap.newKeySet()).add(caseId);

        // 持久化
        persistCase(arbitrationCase);

        plugin.getLogger().info("Arbitration case accepted: " + caseId + " by arbitrator " + arbitrator);

        return true;
    }

    @Override
    public boolean submitDefense(UUID caseId, NationId respondent, String defense) {
        ArbitrationCase arbitrationCase = cases.get(caseId);
        if (arbitrationCase == null) {
            return false;
        }

        if (!arbitrationCase.isRespondent(respondent)) {
            return false;
        }

        if (!arbitrationCase.submitDefense(respondent, defense)) {
            return false;
        }

        // 状态转为等待证据收集
        arbitrationCase.setStatus(ArbitrationStatus.EVIDENCE_GATHERING);

        persistCase(arbitrationCase);
        return true;
    }

    @Override
    public boolean addEvidence(UUID caseId, NationId submitter, String evidence) {
        ArbitrationCase arbitrationCase = cases.get(caseId);
        if (arbitrationCase == null) {
            return false;
        }

        if (!arbitrationCase.involvesNation(submitter)) {
            return false;
        }

        if (!arbitrationCase.addEvidence(submitter, evidence)) {
            return false;
        }

        persistCase(arbitrationCase);
        return true;
    }

    @Override
    public boolean makeRuling(UUID caseId, UUID arbitrator, ArbitrationResult result, String ruling) {
        ArbitrationCase arbitrationCase = cases.get(caseId);
        if (arbitrationCase == null) {
            return false;
        }

        if (!arbitrator.equals(arbitrationCase.arbitrator())) {
            return false;
        }

        if (!arbitrationCase.makeRuling(result, ruling, null)) {
            return false;
        }

        // 如果裁决涉及领土转让，执行转让
        if (result.involvesTransfer()) {
            executeTransfer(arbitrationCase, result);
        }

        persistCase(arbitrationCase);

        plugin.getLogger().info("Arbitration case ruled: " + caseId + " with result " + result);

        return true;
    }

    @Override
    public boolean withdrawCase(UUID caseId) {
        ArbitrationCase arbitrationCase = cases.get(caseId);
        if (arbitrationCase == null) {
            return false;
        }

        if (!arbitrationCase.withdraw()) {
            return false;
        }

        // 退还费用（如果是未完成的案件）
        if (config.refundOnWithdrawal() && arbitrationCase.claimFee().signum() > 0) {
            treasuryService.deposit(arbitrationCase.claimant(), arbitrationCase.claimFee());
        }

        persistCase(arbitrationCase);

        plugin.getLogger().info("Arbitration case withdrawn: " + caseId);

        return true;
    }

    // ==================== Query Methods ====================

    @Override
    public Optional<ArbitrationCase> getCase(UUID caseId) {
        return Optional.ofNullable(cases.get(caseId));
    }

    @Override
    public Collection<ArbitrationCase> getCasesForNation(NationId nationId) {
        Set<UUID> caseIds = nationCases.get(nationId);
        if (caseIds == null) {
            return List.of();
        }
        return caseIds.stream()
            .map(cases::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    @Override
    public Collection<ArbitrationCase> getPendingCases() {
        return cases.values().stream()
            .filter(c -> c.status() == ArbitrationStatus.PENDING)
            .collect(Collectors.toList());
    }

    @Override
    public Collection<ArbitrationCase> getCasesForArbitrator(UUID arbitrator) {
        Set<UUID> caseIds = arbitratorCases.get(arbitrator);
        if (caseIds == null) {
            return List.of();
        }
        return caseIds.stream()
            .map(cases::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    @Override
    public boolean hasPendingCase(NationId nationId) {
        Set<UUID> caseIds = nationCases.get(nationId);
        if (caseIds == null) {
            return false;
        }
        return caseIds.stream()
            .map(cases::get)
            .anyMatch(c -> c != null && !c.status().isTerminal());
    }

    @Override
    public Collection<ArbitrationCase> getCasesByStatus(ArbitrationStatus status) {
        return cases.values().stream()
            .filter(c -> c.status() == status)
            .collect(Collectors.toList());
    }

    // ==================== Fee Configuration ====================

    @Override
    public BigDecimal getFilingFee() {
        return config.filingFee();
    }

    @Override
    public BigDecimal getMinimumClaimFee() {
        return config.minimumClaimFee();
    }

    @Override
    public BigDecimal getMaximumClaimFee() {
        return config.maximumClaimFee();
    }

    // ==================== Persistence ====================

    @Override
    public void saveState() {
        if (persistenceService == null) {
            return;
        }

        try {
            var props = new Properties();
            for (Map.Entry<UUID, ArbitrationCase> entry : cases.entrySet()) {
                String key = entry.getKey().toString();
                String json = serializeCase(entry.getValue());
                props.setProperty(key, json);
            }
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, CASES_FILE, props);
            plugin.getLogger().info("Saved " + cases.size() + " arbitration cases");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save arbitration state: " + e.getMessage());
        }
    }

    private void loadState() {
        if (persistenceService == null) {
            return;
        }

        try {
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, CASES_FILE);
            for (String key : props.stringPropertyNames()) {
                try {
                    UUID caseId = UUID.fromString(key);
                    String json = props.getProperty(key);
                    ArbitrationCase arbitrationCase = deserializeCase(json);
                    cases.put(caseId, arbitrationCase);

                    // 重建索引
                    nationCases.computeIfAbsent(arbitrationCase.claimant(), k -> ConcurrentHashMap.newKeySet()).add(caseId);
                    nationCases.computeIfAbsent(arbitrationCase.respondent(), k -> ConcurrentHashMap.newKeySet()).add(caseId);
                    if (arbitrationCase.arbitrator() != null) {
                        arbitratorCases.computeIfAbsent(arbitrationCase.arbitrator(), k -> ConcurrentHashMap.newKeySet()).add(caseId);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load case " + key + ": " + e.getMessage());
                }
            }
            plugin.getLogger().info("Loaded " + cases.size() + " arbitration cases");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load arbitration state: " + e.getMessage());
        }
    }

    private void persistCase(ArbitrationCase arbitrationCase) {
        if (persistenceService == null) {
            return;
        }

        try {
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, CASES_FILE);
            String key = arbitrationCase.id().toString();
            String json = serializeCase(arbitrationCase);
            props.setProperty(key, json);
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, CASES_FILE, props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to persist case " + arbitrationCase.id() + ": " + e.getMessage());
        }
    }

    private String serializeCase(ArbitrationCase arbitrationCase) {
        StringBuilder sb = new StringBuilder();
        sb.append("id=").append(arbitrationCase.id().toString()).append("|");
        sb.append("claimant=").append(arbitrationCase.claimant().toString()).append("|");
        sb.append("respondent=").append(arbitrationCase.respondent().toString()).append("|");
        sb.append("caseType=").append(arbitrationCase.caseType().key()).append("|");
        sb.append("status=").append(arbitrationCase.status().key()).append("|");
        sb.append("arbitrator=").append(arbitrationCase.arbitrator() != null ? arbitrationCase.arbitrator().toString() : "").append("|");
        sb.append("result=").append(arbitrationCase.result() != null ? arbitrationCase.result().key() : "").append("|");
        sb.append("ruling=").append(arbitrationCase.ruling() != null ? arbitrationCase.ruling().replace("|", "\\|").replace("\n", "\\n") : "").append("|");
        sb.append("claimFee=").append(arbitrationCase.claimFee().toPlainString()).append("|");
        sb.append("createdAt=").append(arbitrationCase.createdAt().toEpochMilli()).append("|");
        sb.append("updatedAt=").append(arbitrationCase.updatedAt().toEpochMilli());
        return sb.toString();
    }

    private ArbitrationCase deserializeCase(String data) {
        // 简化版反序列化 - 实际生产环境应使用 JSON
        String[] parts = data.split("\\|");
        Map<String, String> fields = new HashMap<>();
        for (String part : parts) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                String key = part.substring(0, eq);
                String value = part.substring(eq + 1);
                fields.put(key, value);
            }
        }

        return ArbitrationCase.builder()
            .id(UUID.fromString(fields.get("id")))
            .claimant(NationId.fromString(fields.get("claimant")))
            .respondent(NationId.fromString(fields.get("respondent")))
            .caseType(ArbitrationCaseType.fromKey(fields.get("caseType")))
            .claimFee(new BigDecimal(fields.getOrDefault("claimFee", "0")))
            .build();
    }

    // ==================== Utility ====================

    private boolean hasPendingCaseBetween(NationId nation1, NationId nation2) {
        return cases.values().stream()
            .filter(c -> !c.status().isTerminal())
            .anyMatch(c -> c.involvesNation(nation1) && c.involvesNation(nation2));
    }

    private void executeTransfer(ArbitrationCase arbitrationCase, ArbitrationResult result) {
        List<ChunkCoordinate> chunks = arbitrationCase.disputedChunks();
        if (chunks.isEmpty()) {
            return;
        }

        NationId fromNation = null;
        NationId toNation = null;

        switch (result) {
            case CLAIMANT_FAVOR -> {
                fromNation = arbitrationCase.respondent();
                toNation = arbitrationCase.claimant();
            }
            case RESPONDENT_FAVOR -> {
                fromNation = arbitrationCase.claimant();
                toNation = arbitrationCase.respondent();
            }
            case SPLIT_DECISION -> {
                // 简单处理：前半归申诉方，后半归被申诉方
                NationId splitFrom = arbitrationCase.respondent();
                NationId splitTo = arbitrationCase.claimant();
                int mid = chunks.size() / 2;
                for (int i = 0; i < mid; i++) {
                    ChunkCoordinate chunk = chunks.get(i);
                    territoryService.unclaim(splitFrom.toString(), chunk);
                    territoryService.claim(splitTo.toString(), chunk);
                }
                for (int i = mid; i < chunks.size(); i++) {
                    ChunkCoordinate chunk = chunks.get(i);
                    territoryService.unclaim(splitTo.toString(), chunk);
                    territoryService.claim(splitFrom.toString(), chunk);
                }
                return;
            }
            default -> {
                return;
            }
        }

        // 执行单方转让
        for (ChunkCoordinate chunk : chunks) {
            territoryService.unclaim(fromNation.toString(), chunk);
            territoryService.claim(toNation.toString(), chunk);
        }
    }

    @Override
    public String summary() {
        long pending = cases.values().stream().filter(c -> c.status() == ArbitrationStatus.PENDING).count();
        long inProgress = cases.values().stream().filter(c -> !c.status().isTerminal()).count();
        return String.format("Arbitration: %d total cases, %d pending, %d in progress", cases.size(), pending, inProgress);
    }

    /**
     * 仲裁配置
     */
    public record ArbitrationConfig(
        BigDecimal filingFee,
        BigDecimal minimumClaimFee,
        BigDecimal maximumClaimFee,
        boolean chargeFilingFee,
        boolean refundOnWithdrawal,
        int maxEvidencePerSide,
        int caseExpirationDays
    ) {
        public static ArbitrationConfig defaults() {
            return new ArbitrationConfig(
                new BigDecimal("100"),
                new BigDecimal("500"),
                new BigDecimal("10000"),
                true,
                true,
                10,
                30
            );
        }

        public static ArbitrationConfig fromConfig(org.bukkit.configuration.ConfigurationSection section) {
            if (section == null) {
                return defaults();
            }
            return new ArbitrationConfig(
                new BigDecimal(section.getString("filing-fee", "100")),
                new BigDecimal(section.getString("minimum-claim-fee", "500")),
                new BigDecimal(section.getString("maximum-claim-fee", "10000")),
                section.getBoolean("charge-filing-fee", true),
                section.getBoolean("refund-on-withdrawal", true),
                section.getInt("max-evidence-per-side", 10),
                section.getInt("case-expiration-days", 30)
            );
        }
    }
}
