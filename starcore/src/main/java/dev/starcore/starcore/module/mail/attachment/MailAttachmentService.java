package dev.starcore.starcore.module.mail.attachment;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 邮件附件服务接口
 *
 * 提供邮件附件的完整管理功能：
 * 1. 发送带附件的邮件
 * 2. 获取邮件附件
 * 3. 领取附件
 * 4. 一键领取所有附件
 * 5. 删除邮件
 * 6. 未读邮件数量统计
 */
public interface MailAttachmentService {

    // ========== 核心方法 ==========

    /**
     * 发送带附件的邮件
     *
     * @param sender    发送者玩家
     * @param recipient 接收者名称
     * @param subject   邮件主题
     * @param message   邮件内容
     * @param items     附件物品列表
     * @return 发送是否成功
     */
    boolean sendMailWithAttachment(Player sender, String recipient, String subject, String message, List<ItemStack> items);

    /**
     * 异步发送带附件的邮件
     *
     * @param sender    发送者玩家
     * @param recipient 接收者名称
     * @param subject   邮件主题
     * @param message   邮件内容
     * @param items     附件物品列表
     * @return 异步结果
     */
    CompletableFuture<Boolean> sendMailWithAttachmentAsync(Player sender, String recipient, String subject, String message, List<ItemStack> items);

    /**
     * 获取邮件附件列表
     *
     * @param mailId 邮件ID
     * @return 附件物品列表
     */
    List<ItemStack> getMailAttachments(UUID mailId);

    /**
     * 领取附件
     *
     * @param player 玩家
     * @param mailId 邮件ID
     * @return 领取结果
     */
    AttachmentClaimResult claimAttachment(Player player, UUID mailId);

    /**
     * 异步领取附件
     *
     * @param player 玩家
     * @param mailId 邮件ID
     * @return 异步结果
     */
    CompletableFuture<AttachmentClaimResult> claimAttachmentAsync(Player player, UUID mailId);

    /**
     * 一键领取所有附件
     *
     * @param player 玩家
     * @return 领取结果列表
     */
    List<AttachmentClaimResult> claimAllAttachments(Player player);

    /**
     * 删除邮件
     *
     * @param player 玩家
     * @param mailId 邮件ID
     * @return 删除是否成功
     */
    boolean deleteMail(Player player, UUID mailId);

    /**
     * 获取玩家未读邮件数量
     *
     * @param playerId 玩家UUID
     * @return 未读邮件数量
     */
    int getUnreadCount(UUID playerId);

    /**
     * 获取玩家所有邮件
     *
     * @param playerId 玩家UUID
     * @return 邮件列表
     */
    List<MailAttachment> getPlayerMails(UUID playerId);

    /**
     * 获取玩家未领取附件的邮件数量
     *
     * @param playerId 玩家UUID
     * @return 未领取附件的邮件数量
     */
    int getUnclaimedAttachmentCount(UUID playerId);

    // ========== 数据模型 ==========

    /**
     * 邮件附件数据模型
     */
    record MailAttachment(
        UUID id,
        UUID senderId,
        String senderName,
        UUID recipientId,
        String recipientName,
        String subject,
        String message,
        List<AttachmentItem> items,
        long sentAt,
        boolean read,
        long expiresAt
    ) {
        /**
         * 检查是否有附件
         */
        public boolean hasAttachments() {
            return items != null && !items.isEmpty();
        }

        /**
         * 检查附件是否已领取
         */
        public boolean isClaimed() {
            if (items == null || items.isEmpty()) {
                return true;
            }
            return items.stream().allMatch(AttachmentItem::claimed);
        }

        /**
         * 检查邮件是否已过期
         */
        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }

        /**
         * 获取剩余过期天数
         */
        public long getRemainingDays() {
            long remaining = (expiresAt - System.currentTimeMillis()) / (24 * 60 * 60 * 1000);
            return Math.max(0, remaining);
        }

        /**
         * 从 ItemStack 列表创建附件
         */
        public static List<AttachmentItem> fromItemStacks(List<ItemStack> items) {
            if (items == null) {
                return List.of();
            }
            return items.stream()
                .filter(item -> item != null && !item.getType().isAir())
                .map(AttachmentItem::fromItemStack)
                .toList();
        }
    }

    /**
     * 附件物品数据模型
     */
    record AttachmentItem(
        String itemId,
        int amount,
        String serializedItem,
        boolean claimed
    ) {
        /**
         * 从 ItemStack 创建附件物品
         */
        public static AttachmentItem fromItemStack(ItemStack item) {
            if (item == null || item.getType().isAir()) {
                return null;
            }
            return new AttachmentItem(
                item.getType().name(),
                item.getAmount(),
                serializeItem(item),
                false
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
    }

    /**
     * 附件领取结果
     */
    record AttachmentClaimResult(
        boolean success,
        String message,
        UUID mailId,
        List<ItemStack> claimedItems,
        int claimedCount,
        List<ItemStack> overflowItems
    ) {
        public static AttachmentClaimResult success(UUID mailId, List<ItemStack> items, int count) {
            return new AttachmentClaimResult(true, "领取成功", mailId, items, count, List.of());
        }

        public static AttachmentClaimResult successWithOverflow(UUID mailId, List<ItemStack> items, int count, List<ItemStack> overflow) {
            return new AttachmentClaimResult(true, "部分领取成功，背包空间不足，部分物品被返还", mailId, items, count, overflow);
        }

        public static AttachmentClaimResult failure(UUID mailId, String message) {
            return new AttachmentClaimResult(false, message, mailId, List.of(), 0, List.of());
        }

        public static AttachmentClaimResult alreadyClaimed(UUID mailId) {
            return new AttachmentClaimResult(false, "附件已被领取", mailId, List.of(), 0, List.of());
        }

        public static AttachmentClaimResult noAttachments(UUID mailId) {
            return new AttachmentClaimResult(false, "此邮件没有附件", mailId, List.of(), 0, List.of());
        }

        public static AttachmentClaimResult noSpace(UUID mailId) {
            return new AttachmentClaimResult(false, "背包空间不足，请清理背包后重试", mailId, List.of(), 0, List.of());
        }
    }
}
