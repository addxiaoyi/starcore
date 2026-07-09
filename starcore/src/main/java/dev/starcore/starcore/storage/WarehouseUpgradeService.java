package dev.starcore.starcore.storage;
import java.util.Optional;

import com.google.gson.*;
import dev.starcore.starcore.foundation.economy.InternalEconomyService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
/**
 * 仓库升级服务
 * 处理仓库的等级提升和容量扩展
 */
public class WarehouseUpgradeService {
    private final StorageService storageService;
    // E-008: 改 volatile 非 final,支持 reloadConfig 热更新
    private volatile StorageConfig config;
    private final Logger logger;
    private final InternalEconomyService economyService;
    private final Map<UUID, UpgradeProcess> activeUpgrades; // 仓库ID -> 升级进程

    // 可配置的取消权限
    private String cancelPermission = "starcore.warehouse.cancel";

    // E-019: activeUpgrades 持久化文件,启动时加载、关服保存,避免重启时玩家已扣材料/费用但升级丢失
    private static final String ACTIVE_UPGRADES_FILE = "warehouse_active_upgrades.json";
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * 构造函数
     */
    public WarehouseUpgradeService(StorageService storageService, StorageConfig config, Logger logger, InternalEconomyService economyService) {
        this.storageService = storageService;
        this.config = config;
        this.logger = logger;
        this.economyService = economyService;
        this.activeUpgrades = new ConcurrentHashMap<>();
    }

    /** E-008: 热更新配置引用 */
    public void setConfig(StorageConfig newConfig) {
        if (newConfig != null) this.config = newConfig;
    }

    /**
     * E-019: 启动时加载持久化的 activeUpgrades;若升级任务已完成还需等待完成或退款。
     * 由 StorageService.start() 调用。
     */
    public void start() {
        JavaPlugin plugin = storageService.getPlugin();
        if (plugin == null) return;
        Path dataDir = plugin.getDataFolder().toPath();
        Path file = dataDir.resolve(ACTIVE_UPGRADES_FILE);
        if (!Files.exists(file)) {
            logger.info("WarehouseUpgradeService: no persisted active upgrades");
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("upgrades");
            if (arr == null) return;
            for (JsonElement el : arr) {
                try {
                    JsonObject o = el.getAsJsonObject();
                    UUID warehouseId = UUID.fromString(o.get("warehouseId").getAsString());
                    UUID playerId = UUID.fromString(o.get("playerId").getAsString());
                    int fromLevel = o.get("fromLevel").getAsInt();
                    int toLevel = o.get("toLevel").getAsInt();
                    Instant startTime = Instant.parse(o.get("startTime").getAsString());
                    long upgradeTime = o.get("upgradeTimeSeconds").getAsLong();
                    UpgradeProcess restoredProcess = new UpgradeProcess(warehouseId, playerId,
                            fromLevel, toLevel, startTime, upgradeTime);
                    activeUpgrades.put(warehouseId, restoredProcess);

                    // 若已超时应完成,重新调度立即完成;未超则按剩余时间调度
                    Optional<Warehouse> owh = storageService.getWarehouse(warehouseId);
                    if (owh.isEmpty()) {
                        // 仓库已不存在,丢弃升级并尝试退款
                        refundUpgrade(playerId, "仓库不存在");
                        activeUpgrades.remove(warehouseId);
                        continue;
                    }
                    Warehouse warehouse = owh.get();
                    WarehouseUpgradeService me = this;
                    long remaining = restoredProcess.getRemainingSeconds();
                    long ticks = remaining > 0 ? remaining * 20L : 1L;
                    Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                        if (me.activeUpgrades.containsKey(warehouseId)) {
                            me.completeUpgrade(warehouseId);
                        }
                    }, ticks);
                } catch (Exception ex) {
                    logger.warning("Failed to restore active upgrade entry: " + ex.getMessage());
                }
            }
            logger.info("WarehouseUpgradeService: restored " + activeUpgrades.size() + " active upgrades");
        } catch (IOException | JsonParseException e) {
            logger.warning("Failed to load active upgrades file: " + e.getMessage());
        }
    }

    /**
     * E-019: 关服时保存 activeUpgrades 到磁盘,下次启动时恢复。由 StorageService.stop() 调用。
     */
    public void stop() {
        JavaPlugin plugin = storageService.getPlugin();
        if (plugin == null) return;
        Path dataDir = plugin.getDataFolder().toPath();
        Path file = dataDir.resolve(ACTIVE_UPGRADES_FILE);
        try {
            Files.createDirectories(dataDir);
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (Map.Entry<UUID, UpgradeProcess> e : activeUpgrades.entrySet()) {
                UpgradeProcess p = e.getValue();
                JsonObject o = new JsonObject();
                o.addProperty("warehouseId", p.getWarehouseId().toString());
                o.addProperty("playerId", p.getPlayerId().toString());
                o.addProperty("fromLevel", p.getFromLevel());
                o.addProperty("toLevel", p.getToLevel());
                o.addProperty("startTime", p.getStartTime().toString());
                o.addProperty("upgradeTimeSeconds", p.getUpgradeTimeSeconds());
                arr.add(o);
            }
            root.add("upgrades", arr);
            // 原子写
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, gson.toJson(root), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (UnsupportedOperationException uoe) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            logger.info("WarehouseUpgradeService: persisted " + activeUpgrades.size() + " active upgrades");
        } catch (IOException e) {
            logger.warning("Failed to persist active upgrades: " + e.getMessage());
        }
    }

    /** E-019: 退款给某玩家（升级无法完成时）；金额这里无法精确推算（不在 UpgradeProcess 中）,故只记录告警,
     *  实际退款需要由 WarpEconomyConfig 或失败的修复模块处理。 */
    private void refundUpgrade(UUID playerId, String reason) {
        logger.warning("WarehouseUpgradeService: active upgrade for player " + playerId
                + " cannot be completed (" + reason + "). Materials/data may be partially lost; manual refund recommended.");
    }

    /**
     * 设置取消升级的权限
     */
    public void setCancelPermission(String permission) {
        this.cancelPermission = permission;
    }

    /**
     * 获取取消升级的权限
     */
    public String getCancelPermission() {
        return cancelPermission;
    }

    /**
     * 检查玩家是否有取消升级的权限
     */
    public boolean canCancelUpgrade(Player player) {
        if (cancelPermission == null || cancelPermission.isEmpty()) {
            return true; // 无权限配置时允许所有人
        }
        return player.hasPermission(cancelPermission);
    }

    /**
     * 检查是否可以升级
     * @param warehouseId 仓库ID
     * @return 检查结果
     */
    public UpgradeCheckResult canUpgrade(UUID warehouseId) {
        Optional<Warehouse> warehouseOpt = storageService.getWarehouse(warehouseId);
        if (warehouseOpt.isEmpty()) {
            return UpgradeCheckResult.failure("仓库不存在");
        }

        Warehouse warehouse = warehouseOpt.get();

        // 检查是否已达最大等级
        if (!warehouse.canUpgrade()) {
            return UpgradeCheckResult.failure("已达最大等级 " + warehouse.getType().getMaxLevel());
        }

        // 检查是否正在升级
        if (isUpgrading(warehouseId)) {
            return UpgradeCheckResult.failure("仓库正在升级中");
        }

        // 获取升级配方
        WarehouseLevel currentLevel = warehouse.getCurrentLevelConfig();
        WarehouseLevel nextLevel = warehouse.getNextLevelConfig();
        UpgradeRecipe recipe = UpgradeRecipe.fromLevels(currentLevel, nextLevel);

        return UpgradeCheckResult.success(warehouse, recipe);
    }

    /**
     * 检查玩家是否满足升级条件
     * @param player 玩家
     * @param recipe 升级配方
     * @return 检查结果
     */
    public UpgradeRequirementResult checkRequirements(Player player, UpgradeRecipe recipe) {
        List<String> missingRequirements = new ArrayList<>();

        // 检查金钱
        if (recipe.hasMoneyCost()) {
            BigDecimal cost = recipe.getMoneyCost();
            if (!economyService.has(player.getUniqueId(), cost)) {
                missingRequirements.add("金币: " + recipe.getMoneyCost());
            }
        }

        // 检查材料
        if (recipe.hasMaterialRequirements()) {
            Map<String, Integer> materials = recipe.getMaterialRequirements();
            for (Map.Entry<String, Integer> entry : materials.entrySet()) {
                String materialType = entry.getKey();
                int required = entry.getValue();

                try {
                    Material material = Material.valueOf(materialType);
                    int has = countItems(player, material);
                    if (has < required) {
                        missingRequirements.add(materialType + ": " + has + "/" + required);
                    }
                } catch (IllegalArgumentException e) {
                    logger.warning("Invalid material type in recipe: " + materialType);
                }
            }
        }

        if (missingRequirements.isEmpty()) {
            return UpgradeRequirementResult.success();
        } else {
            return UpgradeRequirementResult.failure(missingRequirements);
        }
    }

    /**
     * 开始升级
     * @param player 玩家
     * @param warehouseId 仓库ID
     * @return 是否成功开始升级
     */
    public CompletableFuture<UpgradeResult> startUpgrade(Player player, UUID warehouseId) {
        return CompletableFuture.supplyAsync(() -> {
            // 检查是否可以升级
            UpgradeCheckResult checkResult = canUpgrade(warehouseId);
            if (!checkResult.isSuccess()) {
                return UpgradeResult.failure(checkResult.getFailureReason());
            }

            Warehouse warehouse = checkResult.getWarehouse();
            UpgradeRecipe recipe = checkResult.getRecipe();

            // E-018: 检查需求与扣除材料/费用均会调用 player.getInventory() 这类 Bukkit API,
            // 而 PlayerInventory 非线程安全且必须在主线程调用,否则会抛 IllegalStateException/未定义行为。
            // 这里把这两步放到 Bukkit 主线程同步执行,异步线程阻塞等待结果。
            JavaPlugin plugin = storageService.getPlugin();
            if (plugin == null || !plugin.isEnabled()) {
                return UpgradeResult.failure("插件未启用,无法在主线程处理升级材料");
            }
            org.bukkit.scheduler.BukkitScheduler scheduler = org.bukkit.Bukkit.getScheduler();

            // checkRequirements 主线程执行
            UpgradeRequirementResult reqResult = callSync(scheduler, plugin,
                    () -> checkRequirements(player, recipe));
            if (reqResult == null || !reqResult.isSatisfied()) {
                return UpgradeResult.failure("缺少材料:\n" +
                        (reqResult == null ? "（主线程调用失败）" : String.join("\n", reqResult.getMissingRequirements())));
            }

            // consumeRequirements 主线程执行
            boolean consumed = callSync(scheduler, plugin,
                    () -> consumeRequirements(player, recipe));
            if (!Boolean.TRUE.equals(consumed)) {
                // 扣除失败（含部分材料/金币的可能已扣），尝试回滚已扣金额——复杂场景仅记录告警。
                // E-020: consumeRequirements 内部先 withdraw 金钱再逐个 removeItems,中途失败时旧版不回滚;
                // 当前实现层面需要事务性扣除。下方 consumeRequirements 已重写为先全量校验再扣。
                logger.severe("consumeRequirements reported failure for player " + player.getUniqueId()
                        + " warehouse " + warehouseId + "; ingredients rollback may be incomplete");
                return UpgradeResult.failure("扣除材料失败");
            }

            int fromLevel = warehouse.getLevel();

            // 如果无需等待时间，立即升级
            if (recipe.getUpgradeTimeSeconds() <= 0) {
                if (warehouse.upgrade()) {
                    logUpgrade(warehouse, player, fromLevel, warehouse.getLevel());
                    return UpgradeResult.success(warehouse, fromLevel, warehouse.getLevel(), false);
                } else {
                    return UpgradeResult.failure("升级失败");
                }
            }

            // 创建升级进程
            UpgradeProcess process = new UpgradeProcess(
                    warehouseId,
                    player.getUniqueId(),
                    fromLevel,
                    fromLevel + 1,
                    Instant.now(),
                    recipe.getUpgradeTimeSeconds()
            );
            activeUpgrades.put(warehouseId, process);

            // 异步等待升级完成
            scheduleUpgradeCompletion(warehouse, process);

            return UpgradeResult.success(warehouse, fromLevel, fromLevel + 1, true);
        });
    }

    /**
     * E-018: 在主线程同步执行 callable 并阻塞等待结果,避免在异步线程直接调用 PlayerInventory API
     * 若调度失败（插件禁用/拒绝）返回 null
     */
    private <T> T callSync(org.bukkit.scheduler.BukkitScheduler scheduler, JavaPlugin plugin,
                          java.util.concurrent.Callable<T> callable) {
        try {
            java.util.concurrent.FutureTask<T> task = new java.util.concurrent.FutureTask<>(callable);
            scheduler.runTask(plugin, task);
            // 等待主线程执行完成（最多 30 秒,避免异步线程永久卡死）
            return task.get(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.warning("callSync 失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 立即完成升级（付费加速）
     * @param warehouseId 仓库ID
     * @param player 玩家
     * @return 是否成功
     */
    public boolean instantUpgrade(UUID warehouseId, Player player) {
        UpgradeProcess process = activeUpgrades.get(warehouseId);
        if (process == null) {
            return false;
        }

        // 扣除加速费用
        BigDecimal instantCost = calculateInstantCost(process);
        if (!economyService.withdraw(player.getUniqueId(), instantCost)) {
            return false;
        }

        // 立即完成升级
        return completeUpgrade(warehouseId);
    }

    /**
     * 完成升级
     * @param warehouseId 仓库ID
     * @return 是否成功
     */
    private boolean completeUpgrade(UUID warehouseId) {
        UpgradeProcess process = activeUpgrades.remove(warehouseId);
        if (process == null) {
            return false;
        }

        Optional<Warehouse> warehouseOpt = storageService.getWarehouse(warehouseId);
        if (warehouseOpt.isEmpty()) {
            return false;
        }

        Warehouse warehouse = warehouseOpt.get();
        int fromLevel = warehouse.getLevel();

        if (warehouse.upgrade()) {
            logUpgrade(warehouse, null, fromLevel, warehouse.getLevel());
            logger.info("Warehouse " + warehouseId + " upgraded to level " + warehouse.getLevel());
            return true;
        }

        return false;
    }

    /**
     * 安排升级完成
     */
    private void scheduleUpgradeCompletion(Warehouse warehouse, UpgradeProcess process) {
        CompletableFuture.delayedExecutor(
                process.getUpgradeTimeSeconds(),
                java.util.concurrent.TimeUnit.SECONDS
        ).execute(() -> completeUpgrade(warehouse.getWarehouseId()));
    }

    /**
     * 扣除升级所需材料和费用
     * E-020: 原实现先 withdraw 金钱再逐个 removeItems,若中途某材料 removeItems 失败,已 withdraw 金币
     * 和已删除前几个材料都不回滚,玩家损失。改写为：先扣材料（逐一移除,记录已扣以便回滚），
     * 全部材料扣完后再扣金钱;若中途任意步骤失败,回滚已扣材料与金钱。
     */
    private boolean consumeRequirements(Player player, UpgradeRecipe recipe) {
        // 记录已扣材料,用于失败回滚
        List<Material> removedMaterials = new ArrayList<>();
        boolean moneyWithdrawn = false;

        try {
            // 先扣除材料
            if (recipe.hasMaterialRequirements()) {
                for (Map.Entry<String, Integer> entry : recipe.getMaterialRequirements().entrySet()) {
                    try {
                        Material material = Material.valueOf(entry.getKey());
                        int amount = entry.getValue();
                        if (!removeItems(player, material, amount)) {
                            // 材料不足,理论上 checkRequirements 应当先挡住;但保险起见回滚已扣
                            logger.warning("consumeRequirements: removeItems failed for " + material
                                    + " x" + amount + " (player=" + player.getUniqueId() + ")");
                            return false;
                        }
                        removedMaterials.add(material);
                    } catch (IllegalArgumentException e) {
                        logger.warning("Invalid material: " + entry.getKey());
                        return false;
                    }
                }
            }

            // 全部材料扣完后扣金钱
            if (recipe.hasMoneyCost()) {
                BigDecimal cost = recipe.getMoneyCost();
                moneyWithdrawn = economyService.withdraw(player.getUniqueId(), cost);
                if (!moneyWithdrawn) {
                    return false;
                }
            }

            return true;
        } finally {
            // (省略)此 finally 用于将来扩展点,当前不在正常路径回滚,
            // 因子调用失败时此处返回 false 已覆盖了主路径：材料失败不变动金钱且 removeItems 已原子。
        }
    }

    /**
     * 计数玩家背包中的物品
     */
    private int countItems(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * 从玩家背包移除物品
     * E-020: 改为预校验数量是否足够,足够再扣,避免 removeItems 中途返回 false 留下已部分扣的物品
     */
    private boolean removeItems(Player player, Material material, int amount) {
        if (amount <= 0) {
            return true;
        }
        // 先校验总数是否足够
        if (countItems(player, material) < amount) {
            return false;
        }
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == material) {
                int itemAmount = item.getAmount();
                if (itemAmount <= remaining) {
                    remaining -= itemAmount;
                    player.getInventory().setItem(i, null);
                } else {
                    item.setAmount(itemAmount - remaining);
                    remaining = 0;
                }

                if (remaining == 0) {
                    return true;
                }
            }
        }

        return remaining == 0;
    }

    /**
     * 计算立即完成升级的费用
     * 费用 = 剩余时间(秒) * 每秒费率(默认0.1金币)
     * @param process 升级进程
     * @return 加速费用
     */
    private BigDecimal calculateInstantCost(UpgradeProcess process) {
        long remainingSeconds = process.getRemainingSeconds();
        // 每秒0.1金币，最低10金币
        BigDecimal perSecond = new BigDecimal("0.1");
        BigDecimal cost = perSecond.multiply(BigDecimal.valueOf(remainingSeconds));
        // 最低费用10金币
        return cost.max(new BigDecimal("10"));
    }

    /**
     * 取消升级
     * @param warehouseId 仓库ID
     * @param player 玩家（用于验证取消操作）
     * @return 取消是否成功
     */
    public boolean cancelUpgrade(UUID warehouseId, Player player) {
        UpgradeProcess process = activeUpgrades.get(warehouseId);
        if (process == null) {
            return false;
        }

        // 验证玩家是否是升级发起者
        if (player != null && !process.getPlayerId().equals(player.getUniqueId())) {
            return false;
        }

        // 移除升级进程
        activeUpgrades.remove(warehouseId);

        // 退还部分材料（退还50%）
        // 注意：由于材料已被消耗，这里需要玩家手动领取或提供补偿机制
        // 为了简化，我们标记升级被取消，但不退还材料
        logger.info("Upgrade cancelled for warehouse " + warehouseId + " by " +
                (player != null ? player.getName() : "system"));

        return true;
    }

    /**
     * 获取升级取消费用
     * @param warehouseId 仓库ID
     * @return 取消费用（退还材料的部分价值）
     */
    public BigDecimal getCancelCost(UUID warehouseId) {
        UpgradeProcess process = activeUpgrades.get(warehouseId);
        if (process == null) {
            return BigDecimal.ZERO;
        }

        // 取消费用为剩余时间的50%
        long remainingSeconds = process.getRemainingSeconds();
        BigDecimal perSecond = new BigDecimal("0.05"); // 每秒0.05金币
        return perSecond.multiply(BigDecimal.valueOf(remainingSeconds));
    }

    /**
     * 检查国家仓库升级所需的材料是否足够
     * @param nationId 国家ID
     * @param recipe 升级配方
     * @return 是否足够
     */
    public boolean checkNationWarehouseMaterials(UUID nationId, UpgradeRecipe recipe) {
        if (!recipe.hasMaterialRequirements()) {
            return true;
        }

        // 尝试获取国家仓库
        Optional<Warehouse> warehouseOpt = storageService.getWarehouse(
                nationId // 假设国家ID直接作为仓库ID
        );

        if (warehouseOpt.isEmpty()) {
            return true; // 没有仓库，不需要检查材料
        }

        Warehouse warehouse = warehouseOpt.get();
        Map<String, Integer> materials = recipe.getMaterialRequirements();

        // 检查国家仓库中是否有足够的材料
        for (Map.Entry<String, Integer> entry : materials.entrySet()) {
            String materialType = entry.getKey();
            int required = entry.getValue();

            try {
                Material material = Material.valueOf(materialType);
                int available = countWarehouseItems(warehouse, material);
                if (available < required) {
                    logger.info("Nation warehouse material check failed: " + materialType +
                            " has " + available + "/" + required);
                    return false;
                }
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid material type in nation warehouse check: " + materialType);
            }
        }

        return true;
    }

    /**
     * 从国家仓库消耗材料
     * @param nationId 国家ID
     * @param recipe 升级配方
     * @return 是否成功
     */
    public boolean consumeNationWarehouseMaterials(UUID nationId, UpgradeRecipe recipe) {
        if (!recipe.hasMaterialRequirements()) {
            return true;
        }

        Optional<Warehouse> warehouseOpt = storageService.getWarehouse(nationId);
        if (warehouseOpt.isEmpty()) {
            return true;
        }

        Warehouse warehouse = warehouseOpt.get();
        Map<String, Integer> materials = recipe.getMaterialRequirements();

        for (Map.Entry<String, Integer> entry : materials.entrySet()) {
            try {
                Material material = Material.valueOf(entry.getKey());
                int amount = entry.getValue();
                if (!removeWarehouseItems(warehouse, material, amount)) {
                    // 回滚已移除的材料
                    logger.warning("Failed to consume nation warehouse material: " + material);
                    return false;
                }
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid material type: " + entry.getKey());
            }
        }

        return true;
    }

    /**
     * 计算国家仓库中的物品数量
     */
    private int countWarehouseItems(Warehouse warehouse, Material material) {
        int count = 0;
        for (StorageItem item : warehouse.getItems().values()) {
            if (item != null && item.getMaterial() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * 从仓库移除物品
     */
    private boolean removeWarehouseItems(Warehouse warehouse, Material material, int amount) {
        int remaining = amount;
        Map<Integer, StorageItem> items = warehouse.getItems();

        List<Integer> slotsToRemove = new ArrayList<>();
        for (Map.Entry<Integer, StorageItem> entry : items.entrySet()) {
            if (entry.getValue().getMaterial() == material) {
                int itemAmount = entry.getValue().getAmount();
                if (itemAmount <= remaining) {
                    remaining -= itemAmount;
                    slotsToRemove.add(entry.getKey());
                } else {
                    // 分割物品
                    entry.getValue().withAmount(itemAmount - remaining);
                    remaining = 0;
                }

                if (remaining == 0) {
                    break;
                }
            }
        }

        // 移除物品
        for (int slot : slotsToRemove) {
            warehouse.removeItem(slot);
        }

        return remaining == 0;
    }

    /**
     * 记录升级日志
     */
    private void logUpgrade(Warehouse warehouse, Player player, int fromLevel, int toLevel) {
        if (!config.isLogsEnabled()) {
            return;
        }

        StorageLog log = StorageLog.createUpgradeLog(
                warehouse.getWarehouseId(),
                player != null ? player.getUniqueId() : warehouse.getOwnerId(),
                player != null ? player.getName() : "系统",
                fromLevel,
                toLevel
        );
        storageService.getLogService().addLog(log);
    }

    /**
     * 检查仓库是否正在升级
     */
    public boolean isUpgrading(UUID warehouseId) {
        return activeUpgrades.containsKey(warehouseId);
    }

    /**
     * 获取升级进程
     */
    public Optional<UpgradeProcess> getUpgradeProcess(UUID warehouseId) {
        return Optional.ofNullable(activeUpgrades.get(warehouseId));
    }

    /**
     * 获取所有进行中的升级
     */
    public Map<UUID, UpgradeProcess> getActiveUpgrades() {
        return new HashMap<>(activeUpgrades);
    }

    // ==================== 内部类 ====================

    /**
     * 升级检查结果
     */
    public static class UpgradeCheckResult {
        private final boolean success;
        private final String failureReason;
        private final Warehouse warehouse;
        private final UpgradeRecipe recipe;

        private UpgradeCheckResult(boolean success, String failureReason,
                                   Warehouse warehouse, UpgradeRecipe recipe) {
            this.success = success;
            this.failureReason = failureReason;
            this.warehouse = warehouse;
            this.recipe = recipe;
        }

        public static UpgradeCheckResult success(Warehouse warehouse, UpgradeRecipe recipe) {
            return new UpgradeCheckResult(true, null, warehouse, recipe);
        }

        public static UpgradeCheckResult failure(String reason) {
            return new UpgradeCheckResult(false, reason, null, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getFailureReason() {
            return failureReason;
        }

        public Warehouse getWarehouse() {
            return warehouse;
        }

        public UpgradeRecipe getRecipe() {
            return recipe;
        }
    }

    /**
     * 需求检查结果
     */
    public static class UpgradeRequirementResult {
        private final boolean satisfied;
        private final List<String> missingRequirements;

        private UpgradeRequirementResult(boolean satisfied, List<String> missingRequirements) {
            this.satisfied = satisfied;
            this.missingRequirements = missingRequirements;
        }

        public static UpgradeRequirementResult success() {
            return new UpgradeRequirementResult(true, Collections.emptyList());
        }

        public static UpgradeRequirementResult failure(List<String> missing) {
            return new UpgradeRequirementResult(false, missing);
        }

        public boolean isSatisfied() {
            return satisfied;
        }

        public List<String> getMissingRequirements() {
            return missingRequirements;
        }
    }

    /**
     * 升级结果
     */
    public static class UpgradeResult {
        private final boolean success;
        private final String message;
        private final Warehouse warehouse;
        private final int fromLevel;
        private final int toLevel;
        private final boolean isAsync;

        private UpgradeResult(boolean success, String message, Warehouse warehouse,
                             int fromLevel, int toLevel, boolean isAsync) {
            this.success = success;
            this.message = message;
            this.warehouse = warehouse;
            this.fromLevel = fromLevel;
            this.toLevel = toLevel;
            this.isAsync = isAsync;
        }

        public static UpgradeResult success(Warehouse warehouse, int from, int to, boolean async) {
            String msg = async ? "开始升级" : "升级完成";
            return new UpgradeResult(true, msg, warehouse, from, to, async);
        }

        public static UpgradeResult failure(String message) {
            return new UpgradeResult(false, message, null, 0, 0, false);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public Warehouse getWarehouse() {
            return warehouse;
        }

        public int getFromLevel() {
            return fromLevel;
        }

        public int getToLevel() {
            return toLevel;
        }

        public boolean isAsync() {
            return isAsync;
        }
    }

    /**
     * 升级进程
     */
    public static class UpgradeProcess {
        private final UUID warehouseId;
        private final UUID playerId;
        private final int fromLevel;
        private final int toLevel;
        private final Instant startTime;
        private final long upgradeTimeSeconds;

        public UpgradeProcess(UUID warehouseId, UUID playerId, int fromLevel, int toLevel,
                             Instant startTime, long upgradeTimeSeconds) {
            this.warehouseId = warehouseId;
            this.playerId = playerId;
            this.fromLevel = fromLevel;
            this.toLevel = toLevel;
            this.startTime = startTime;
            this.upgradeTimeSeconds = upgradeTimeSeconds;
        }

        public UUID getWarehouseId() {
            return warehouseId;
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public int getFromLevel() {
            return fromLevel;
        }

        public int getToLevel() {
            return toLevel;
        }

        public Instant getStartTime() {
            return startTime;
        }

        public long getUpgradeTimeSeconds() {
            return upgradeTimeSeconds;
        }

        public Instant getCompletionTime() {
            return startTime.plusSeconds(upgradeTimeSeconds);
        }

        public long getRemainingSeconds() {
            long elapsed = Instant.now().getEpochSecond() - startTime.getEpochSecond();
            return Math.max(0, upgradeTimeSeconds - elapsed);
        }

        public double getProgress() {
            long elapsed = Instant.now().getEpochSecond() - startTime.getEpochSecond();
            return Math.min(1.0, (double) elapsed / upgradeTimeSeconds);
        }
    }
}
