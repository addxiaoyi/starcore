package dev.starcore.starcore.social.mail.attachment.gui;

import dev.starcore.starcore.social.mail.Mail;
import dev.starcore.starcore.social.mail.attachment.MailAttachmentService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 邮件附件预览 GUI
 *
 * 用于显示邮件附件的详细信息并提供领取功能
 */
public final class MailAttachmentPreviewGui implements InventoryHolder {

    public static final int SIZE = 54;

    private final Player player;
    private final Mail mail;
    private final MailAttachmentService attachmentService;
    private final Inventory inventory;

    public MailAttachmentPreviewGui(Player player, Mail mail, MailAttachmentService attachmentService) {
        this.player = player;
        this.mail = mail;
        this.attachmentService = attachmentService;
        this.inventory = Bukkit.createInventory(this, SIZE,
            Component.text("附件预览 - " + mail.getSubject(), NamedTextColor.GOLD));

        buildMenu();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }

    public Mail getMail() {
        return mail;
    }

    /**
     * 构建菜单
     */
    private void buildMenu() {
        inventory.clear();

        // 标题
        inventory.setItem(4, createTitleItem());

        // 发件人信息
        inventory.setItem(19, createSenderInfoItem());

        // 附件信息
        inventory.setItem(21, createAttachmentInfoItem());

        // 附件展示区域
        buildAttachmentDisplay();

        // 价值估算
        inventory.setItem(25, createValueEstimateItem());

        // 操作按钮
        buildActionButtons();

        // 导航按钮
        addNavigationButtons();
    }

    /**
     * 构建附件展示区域
     */
    private void buildAttachmentDisplay() {
        if (!mail.hasAttachments()) {
            inventory.setItem(22, createNoAttachmentItem());
            return;
        }

        List<MailAttachmentService.MailAttachment> newAttachments = new ArrayList<>();
        for (dev.starcore.starcore.social.mail.MailAttachment legacyAttach : mail.getAttachments()) {
            newAttachments.add(MailAttachmentService.MailAttachment.fromLegacyAttachment(legacyAttach));
        }

        int slot = 28;

        for (MailAttachmentService.MailAttachment attach : newAttachments) {
            if (slot > 34) break;

            ItemStack item = attach.toItemStack();
            if (item != null) {
                // 添加 Lore 显示更多信息
                ItemMeta meta = item.getItemMeta();
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text(""));
                lore.add(Component.text("数量: " + attach.amount(), NamedTextColor.GRAY));

                // 显示价值估算
                long value = attachmentService.estimateTotalValue(List.of(attach));
                if (value > 0) {
                    lore.add(Component.text("估值: " + formatValue(value) + " 金币", NamedTextColor.YELLOW));
                }

                meta.lore(lore);
                item.setItemMeta(meta);
            }

            inventory.setItem(slot++, item);
        }

        // 填充空槽位
        for (int i = slot; i <= 34; i++) {
            inventory.setItem(i, createEmptySlot());
        }
    }

    /**
     * 构建操作按钮
     */
    private void buildActionButtons() {
        if (mail.isClaimed()) {
            inventory.setItem(37, createClaimedButton());
        } else if (mail.hasAttachments()) {
            inventory.setItem(37, createClaimButton());
        }

        inventory.setItem(43, createDetailsButton());
    }

    /**
     * 添加导航按钮
     */
    private void addNavigationButtons() {
        inventory.setItem(45, createBackButton());
        inventory.setItem(53, createCloseButton());
    }

    // ==================== 物品创建方法 ====================

    private ItemStack createTitleItem() {
        ItemStack item = new ItemStack(Material.ENDER_CHEST);
        ItemMeta meta = item.getItemMeta();

        Component displayName = Component.text()
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text(mail.isClaimed() ? "已领取" : "未领取", NamedTextColor.GREEN))
            .append(Component.text("] ", NamedTextColor.DARK_GRAY))
            .append(Component.text("邮件附件", NamedTextColor.GOLD))
            .build();

        meta.displayName(displayName);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("=== 附件预览 ===", NamedTextColor.GOLD));
        lore.add(Component.text("过期时间: " + mail.getRemainingDays() + " 天后", NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("点击返回邮件详情", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSenderInfoItem() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("发件人", NamedTextColor.AQUA));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text(mail.getSenderName(), NamedTextColor.WHITE));
        lore.add(Component.text(""));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createAttachmentInfoItem() {
        int count = mail.hasAttachments() ? mail.getAttachments().size() : 0;
        Material material = count > 0 ? Material.CHEST : Material.BARRIER;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("附件数量: " + count, NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("共 " + count + " 个物品", NamedTextColor.GRAY));
        lore.add(Component.text(""));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNoAttachmentItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("此邮件没有附件", NamedTextColor.RED));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("该邮件不包含任何物品附件", NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createValueEstimateItem() {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();

        long totalValue = 0;
        if (mail.hasAttachments()) {
            // 转换附件列表
            List<MailAttachmentService.MailAttachment> newAttachments = new ArrayList<>();
            for (dev.starcore.starcore.social.mail.MailAttachment legacy : mail.getAttachments()) {
                newAttachments.add(MailAttachmentService.MailAttachment.fromLegacyAttachment(legacy));
            }
            totalValue = attachmentService.estimateTotalValue(newAttachments);
        }

        meta.displayName(Component.text("总估值", NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("附件总价值", NamedTextColor.GOLD));
        lore.add(Component.text(formatValue(totalValue) + " 金币", NamedTextColor.YELLOW));
        lore.add(Component.text(""));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createClaimButton() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("领取附件", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("点击领取所有附件", NamedTextColor.YELLOW));
        lore.add(Component.text("物品将放入你的背包", NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("[警告] 确保背包有足够空间", NamedTextColor.RED));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createClaimedButton() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("附件已领取", NamedTextColor.GREEN));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("你已领取此邮件的附件", NamedTextColor.GRAY));
        lore.add(Component.text(""));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createDetailsButton() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("查看详情", NamedTextColor.AQUA));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("点击查看邮件完整内容", NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("返回", NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("返回邮件列表", NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("关闭", NamedTextColor.RED));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("点击关闭", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createEmptySlot() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" ", NamedTextColor.BLACK));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 格式化价值显示
     */
    private String formatValue(long value) {
        if (value >= 1_000_000_000) {
            return String.format("%.2fB", value / 1_000_000_000.0);
        } else if (value >= 1_000_000) {
            return String.format("%.2fM", value / 1_000_000.0);
        } else if (value >= 1_000) {
            return String.format("%.2fK", value / 1_000.0);
        }
        return String.valueOf(value);
    }

    /**
     * 执行领取附件
     */
    public void executeClaim() {
        MailAttachmentService.AttachmentClaimResult result = attachmentService.claimAttachments(player, mail.getId());

        if (result.success()) {
            player.sendMessage(Component.text("[附件] ").color(NamedTextColor.GREEN)
                .append(Component.text("成功领取 " + result.claimedCount() + " 个物品！", NamedTextColor.WHITE)));

            if (!result.overflowItems().isEmpty()) {
                player.sendMessage(Component.text("[附件] ").color(NamedTextColor.YELLOW)
                    .append(Component.text("背包空间不足，" + result.overflowItems().size() + " 个物品被返还", NamedTextColor.RED)));
            }

            // 更新 GUI
            buildMenu();
            player.openInventory(inventory);
        } else {
            player.sendMessage(Component.text("[附件] ").color(NamedTextColor.RED)
                .append(Component.text(result.message(), NamedTextColor.WHITE)));
        }
    }

    /**
     * 动作枚举
     */
    public enum AttachmentAction {
        NONE,
        CLAIM,
        DETAILS,
        BACK,
        CLOSE
    }

    /**
     * 从槽位获取动作
     */
    public static AttachmentAction getActionFromSlot(int slot) {
        return switch (slot) {
            case 37 -> AttachmentAction.CLAIM;
            case 43 -> AttachmentAction.DETAILS;
            case 45 -> AttachmentAction.BACK;
            case 53 -> AttachmentAction.CLOSE;
            default -> AttachmentAction.NONE;
        };
    }
}
