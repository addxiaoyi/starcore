package dev.starcore.starcore.quest;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * 委托完成事件监听器
 * 监听游戏事件自动触发委托进度/完成
 *
 * @author StarCore Team
 * @since 1.0.0
 */
public class CommissionCompleteListener implements Listener {

    private static final Logger logger = Logger.getLogger(CommissionCompleteListener.class.getName());
    private final CommissionService commissionService;

    public CommissionCompleteListener(CommissionService commissionService) {
        this.commissionService = commissionService;
    }

    /**
     * 方块破坏事件 - 处理收集类委托
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        // 使用 Material 直接创建 ItemStack (Paper 1.21 API 兼容)
        ItemStack blockItem = new ItemStack(event.getBlock().getBlockData().getMaterial(), 1);

        // 检查玩家是否有相关的收集委托
        checkCollectionProgress(player, "block:" + event.getBlock().getType().name(), 1, blockItem);
    }

    /**
     * 物品合成事件 - 处理收集类委托
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack result = event.getInventory().getResult();
        if (result != null) {
            checkCollectionProgress(player, "craft:" + result.getType().name(), 1, result);
        }
    }

    /**
     * 生物死亡事件 - 处理击杀类委托
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }

        String entityType = event.getEntityType().name();
        checkKillProgress(killer, entityType, 1);

        // 掉落物处理
        for (ItemStack drop : event.getDrops()) {
            if (drop != null && drop.getType() != org.bukkit.Material.AIR) {
                checkCollectionProgress(killer, "collect:" + drop.getType().name(), drop.getAmount(), drop);
            }
        }
    }

    /**
     * 物品放置事件 - 处理建造类委托
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        String material = event.getBlock().getType().name();
        checkBuildProgress(player, material, 1);
    }

    /**
     * 玩家经验变化 - 处理经验收集类委托
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onExpChange(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        int expGained = event.getAmount();

        if (expGained > 0) {
            checkExpProgress(player, expGained);
        }
    }

    /**
     * 右键交互事件 - 处理护送/探索类委托
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // 检查探索类委托
        if (event.getClickedBlock() != null) {
            String blockType = event.getClickedBlock().getType().name();
            checkExploreProgress(player, blockType);
        }
    }

    /**
     * 检查收集进度
     */
    private void checkCollectionProgress(Player player, String itemKey, int amount, ItemStack displayItem) {
        UUID playerId = player.getUniqueId();

        // 获取玩家接取的所有委托
        for (Commission commission : commissionService.getPlayerAcceptedCommissions(playerId)) {
            if (commission.isCompleted() || commission.isExpired()) {
                continue;
            }

            // 检查是否是收集类委托
            if (commission.getType() == Commission.CommissionType.COLLECT) {
                for (String requirement : commission.getRequirements()) {
                    if (matchesRequirement(itemKey, requirement) || matchesRequirement(displayItem.getType().name(), requirement)) {
                        updateProgressAndNotify(player, commission, amount);
                    }
                }
            }

            // 检查自定义要求（直接匹配物品类型）
            for (String requirement : commission.getRequirements()) {
                if (requirement.toUpperCase().contains(itemKey.toUpperCase()) ||
                    requirement.toUpperCase().contains(displayItem.getType().name().toUpperCase())) {
                    updateProgressAndNotify(player, commission, amount);
                }
            }
        }
    }

    /**
     * 检查击杀进度
     */
    private void checkKillProgress(Player player, String entityType, int amount) {
        UUID playerId = player.getUniqueId();

        for (Commission commission : commissionService.getPlayerAcceptedCommissions(playerId)) {
            if (commission.isCompleted() || commission.isExpired()) {
                continue;
            }

            // 检查是否是击杀类委托
            if (commission.getType() == Commission.CommissionType.KILL) {
                // 检查 targetEntity 字段
                if (commission.getTargetEntity() != null &&
                    commission.getTargetEntity().equalsIgnoreCase(entityType)) {
                    updateProgressAndNotify(player, commission, amount);
                    continue;
                }

                for (String requirement : commission.getRequirements()) {
                    if (requirement.toUpperCase().contains("KILL:") &&
                        requirement.toUpperCase().contains(entityType.toUpperCase())) {
                        updateProgressAndNotify(player, commission, amount);
                        break;
                    }
                    if (requirement.toUpperCase().startsWith("KILL:") &&
                        requirement.toUpperCase().contains(entityType.toUpperCase())) {
                        updateProgressAndNotify(player, commission, amount);
                        break;
                    }
                }
            }
        }
    }

    /**
     * 检查建造进度
     */
    private void checkBuildProgress(Player player, String material, int amount) {
        UUID playerId = player.getUniqueId();

        for (Commission commission : commissionService.getPlayerAcceptedCommissions(playerId)) {
            if (commission.isCompleted() || commission.isExpired()) {
                continue;
            }

            if (commission.getType() == Commission.CommissionType.BUILD) {
                for (String requirement : commission.getRequirements()) {
                    if (requirement.toUpperCase().contains(material.toUpperCase())) {
                        updateProgressAndNotify(player, commission, amount);
                    }
                }
            }
        }
    }

    /**
     * 检查探索进度
     */
    private void checkExploreProgress(Player player, String blockType) {
        UUID playerId = player.getUniqueId();

        for (Commission commission : commissionService.getPlayerAcceptedCommissions(playerId)) {
            if (commission.isCompleted() || commission.isExpired()) {
                continue;
            }

            if (commission.getType() == Commission.CommissionType.EXPLORE) {
                for (String requirement : commission.getRequirements()) {
                    if (requirement.toUpperCase().contains(blockType.toUpperCase()) ||
                        requirement.toUpperCase().contains("EXPLORE:")) {
                        updateProgressAndNotify(player, commission, 1);
                    }
                }
            }
        }
    }

    /**
     * 检查经验进度
     */
    private void checkExpProgress(Player player, int expAmount) {
        UUID playerId = player.getUniqueId();

        for (Commission commission : commissionService.getPlayerAcceptedCommissions(playerId)) {
            if (commission.isCompleted() || commission.isExpired()) {
                continue;
            }

            for (String requirement : commission.getRequirements()) {
                if (requirement.toUpperCase().contains("EXP:") ||
                    requirement.toUpperCase().contains("EXPERIENCE:")) {
                    updateProgressAndNotify(player, commission, expAmount);
                }
            }
        }
    }

    /**
     * 匹配需求
     */
    private boolean matchesRequirement(String itemKey, String requirement) {
        String req = requirement.toUpperCase().replace(" ", "").replace(":", ":");
        String key = itemKey.toUpperCase().replace(" ", "").replace(":", ":");

        // 处理格式: "collect:DIAMOND:10" 或 "收集:钻石:10"
        if (req.contains("COLLECT:") || req.contains("收集")) {
            String[] parts = req.split(":");
            if (parts.length >= 2) {
                String itemType = parts[1];
                return key.contains(itemType) || key.equals(itemType);
            }
        }

        return key.contains(req) || req.contains(key);
    }

    /**
     * 更新进度并通知
     * 通过 CommissionService.updateProgress() 同步到持久化层
     */
    private void updateProgressAndNotify(Player player, Commission commission, int amount) {
        UUID playerId = player.getUniqueId();
        int targetAmount = commission.getTargetAmount() > 0 ? commission.getTargetAmount() : extractTargetAmount(commission);
        int previousProgress = commission.getCurrentProgress();

        // 通过服务层更新进度（会持久化）
        boolean updated = commissionService.updateProgress(playerId, commission.getId(), amount);

        if (!updated) {
            return; // 更新失败（如委托不存在或不是该玩家接取）
        }

        // 计算新进度
        int newProgress = Math.min(previousProgress + amount, targetAmount);

        // 发送进度提示
        if (newProgress < targetAmount) {
            player.sendMessage("§6[委托] §e进度: " + newProgress + "/" + targetAmount + " (" + commission.getTitle() + ")");
        } else if (newProgress >= targetAmount) {
            // 通知玩家目标已完成
            player.sendMessage("§6[委托] §a委托目标已完成! 使用 /commission complete 领取奖励");

            // BUILD类型通知发布者确认
            if (commission.getType() == Commission.CommissionType.BUILD) {
                Player publisher = org.bukkit.Bukkit.getPlayer(commission.getPublisherId());
                if (publisher != null && publisher.isOnline()) {
                    publisher.sendMessage("§6[委托] §e建造委托 \"" + commission.getTitle() + "\" 已完成！请使用 /commission confirm 确认。");
                }
            }
        }
    }

    /**
     * 从委托要求中提取目标数量
     */
    private int extractTargetAmount(Commission commission) {
        // 优先使用 Commission 对象中的 targetAmount
        if (commission.getTargetAmount() > 0) {
            return commission.getTargetAmount();
        }

        for (String req : commission.getRequirements()) {
            String[] parts = req.split(":");
            if (parts.length >= 3) {
                try {
                    return Integer.parseInt(parts[parts.length - 1]);
                } catch (NumberFormatException e) {
                    logger.warning("[Commission] 无法解析目标数量: " + req + " - " + e.getMessage());
                }
                        // 静默跳过，保持数据兼容
            }
        }
        // 默认返回奖励金额 / 10 作为目标
        return Math.max(1, (int) (commission.getReward() / 10));
    }

    /**
     * 清除玩家进度缓存（当委托完成/放弃时调用）
     * @deprecated 由 CommissionService.abandonCommission() 内部处理
     */
    @Deprecated
    public void clearProgress(UUID playerId, String commissionId) {
        // 进度已存储在 Commission.currentProgress 中，不需要额外清理
    }

    /**
     * 清除玩家所有进度
     * @deprecated 由 CommissionService 处理
     */
    @Deprecated
    public void clearAllProgress(UUID playerId) {
        // 进度已存储在 Commission.currentProgress 中，不需要额外清理
    }
}
