package dev.starcore.starcore.social.mail.attachment;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 邮件附件服务接口
 *
 * 提供邮件附件的完整管理功能：
 * 1. 附件序列化与反序列化
 * 2. 附件验证与限制
 * 3. 附件领取逻辑
 * 4. 附件过期管理
 */
public interface MailAttachmentService {

    // ========== 核心方法 ==========

    /**
     * 验证附件是否有效
     * @param items 物品列表
     * @return 验证结果
     */
    AttachmentValidationResult validateAttachments(List<ItemStack> items);

    /**
     * 将物品序列化为附件数据
     * @param items 物品列表
     * @return 附件列表
     */
    List<MailAttachment> serializeAttachments(List<ItemStack> items);

    /**
     * 将附件反序列化为物品
     * @param attachments 附件列表
     * @return 物品列表
     */
    List<ItemStack> deserializeAttachments(List<MailAttachment> attachments);

    /**
     * 领取附件
     * @param player 玩家
     * @param mailId 邮件ID
     * @return 领取结果
     */
    AttachmentClaimResult claimAttachments(Player player, UUID mailId);

    /**
     * 异步领取附件
     * @param player 玩家
     * @param mailId 邮件ID
     * @return 异步结果
     */
    CompletableFuture<AttachmentClaimResult> claimAttachmentsAsync(Player player, UUID mailId);

    /**
     * 检查玩家背包是否有足够空间
     * @param player 玩家
     * @param items 物品列表
     * @return 是否有足够空间
     */
    boolean hasInventorySpace(Player player, List<ItemStack> items);

    /**
     * 计算物品所需背包槽位
     * @param player 玩家
     * @param items 物品列表
     * @return 所需槽位数
     */
    int calculateRequiredSlots(Player player, List<ItemStack> items);

    /**
     * 获取附件总价值估算
     * @param attachments 附件列表
     * @return 总价值（基于市场平均价）
     */
    long estimateTotalValue(List<MailAttachment> attachments);

    // ========== 数据记录 ==========

    /**
     * 记录附件领取日志
     * @param playerId 玩家ID
     * @param mailId 邮件ID
     * @param attachments 附件列表
     */
    void logAttachmentClaim(UUID playerId, UUID mailId, List<MailAttachment> attachments);

    /**
     * 获取玩家领取附件历史
     * @param playerId 玩家ID
     * @param limit 返回数量限制
     * @return 历史记录
     */
    List<AttachmentClaimRecord> getClaimHistory(UUID playerId, int limit);

    // ========== 数据模型 ==========

    /**
     * 附件验证结果
     */
    record AttachmentValidationResult(
        boolean valid,
        String errorMessage,
        int validCount,
        int invalidCount,
        List<String> warnings
    ) {
        public static AttachmentValidationResult success(int count) {
            return new AttachmentValidationResult(true, null, count, 0, List.of());
        }

        public static AttachmentValidationResult failure(String message) {
            return new AttachmentValidationResult(false, message, 0, 0, List.of());
        }

        public static AttachmentValidationResult partialSuccess(int valid, int invalid, List<String> warnings) {
            return new AttachmentValidationResult(true, null, valid, invalid, warnings);
        }
    }

    /**
     * 附件领取结果
     */
    record AttachmentClaimResult(
        boolean success,
        String message,
        List<ItemStack> claimedItems,
        int claimedCount,
        List<ItemStack> overflowItems
    ) {
        public static AttachmentClaimResult success(List<ItemStack> items, int count) {
            return new AttachmentClaimResult(true, "领取成功", items, count, List.of());
        }

        public static AttachmentClaimResult successWithOverflow(List<ItemStack> items, int count, List<ItemStack> overflow) {
            return new AttachmentClaimResult(true, "部分领取成功，背包空间不足，部分物品被返还", items, count, overflow);
        }

        public static AttachmentClaimResult failure(String message) {
            return new AttachmentClaimResult(false, message, List.of(), 0, List.of());
        }

        public static AttachmentClaimResult alreadyClaimed() {
            return new AttachmentClaimResult(false, "附件已被领取", List.of(), 0, List.of());
        }

        public static AttachmentClaimResult noAttachments() {
            return new AttachmentClaimResult(false, "此邮件没有附件", List.of(), 0, List.of());
        }
    }

    /**
     * 附件领取记录
     */
    record AttachmentClaimRecord(
        UUID recordId,
        UUID playerId,
        UUID mailId,
        UUID senderId,
        String senderName,
        List<MailAttachment> attachments,
        long claimedAt,
        long totalValue
    ) {
    }

    /**
     * 邮件附件数据模型
     */
    record MailAttachment(
        String itemId,
        int amount,
        String serializedItem,
        long attachedAt
    ) {
        /**
         * 从 ItemStack 创建附件
         */
        public static MailAttachment fromItemStack(ItemStack item) {
            if (item == null || item.getType().isAir()) {
                return null;
            }
            return new MailAttachment(
                item.getType().name(),
                item.getAmount(),
                serializeItem(item),
                System.currentTimeMillis()
            );
        }

        /**
         * 序列化物为字符串
         */
        private static String serializeItem(ItemStack item) {
            if (item == null || item.getType().isAir()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("material:").append(item.getType().name());
            if (item.getAmount() > 1) {
                sb.append(",amount:").append(item.getAmount());
            }
            if (item.hasItemMeta()) {
                if (item.getItemMeta().hasDisplayName()) {
                    sb.append(",display:").append(item.getItemMeta().getDisplayName().replace("§", "&"));
                }
                if (item.getItemMeta().hasEnchants()) {
                    sb.append(",enchants:");
                    item.getItemMeta().getEnchants().forEach((e, l) ->
                        sb.append(e.getKey().getKey()).append(":").append(l).append(";"));
                }
            }
            if (item.getDurability() > 0) {
                sb.append(",durability:").append(item.getDurability());
            }
            return sb.toString();
        }

        /**
         * 反序列化为物品
         */
        public ItemStack toItemStack() {
            if (serializedItem == null || serializedItem.isEmpty()) {
                return null;
            }
            try {
                String[] parts = serializedItem.split(",");
                String materialName = parts[0].replace("material:", "");
                int amount = 1;
                short durability = 0;

                for (String part : parts) {
                    if (part.startsWith("amount:")) {
                        amount = Integer.parseInt(part.replace("amount:", ""));
                    } else if (part.startsWith("durability:")) {
                        durability = (short) Integer.parseInt(part.replace("durability:", ""));
                    }
                }

                org.bukkit.Material material = org.bukkit.Material.valueOf(materialName);
                ItemStack item = new ItemStack(material, amount);
                if (durability > 0) {
                    item.setDurability(durability);
                }
                return item;
            } catch (Exception e) {
                return new ItemStack(org.bukkit.Material.STONE);
            }
        }

        /**
         * 获取物品名称
         */
        public String getDisplayName() {
            try {
                org.bukkit.Material material = org.bukkit.Material.valueOf(itemId);
                return material.name().toLowerCase().replace("_", " ");
            } catch (Exception e) {
                return itemId;
            }
        }

        /**
         * 转为旧版 MailAttachment 兼容格式
         */
        public dev.starcore.starcore.social.mail.MailAttachment toLegacyAttachment() {
            return new dev.starcore.starcore.social.mail.MailAttachment(itemId, amount, serializedItem);
        }

        /**
         * 从旧版 MailAttachment 创建
         */
        public static MailAttachment fromLegacyAttachment(dev.starcore.starcore.social.mail.MailAttachment legacy) {
            return new MailAttachment(
                legacy.getItemId(),
                legacy.getAmount(),
                legacy.getSerializedItem(),
                System.currentTimeMillis()
            );
        }
    }
}
