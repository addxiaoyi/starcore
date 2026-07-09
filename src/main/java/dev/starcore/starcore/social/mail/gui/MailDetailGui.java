package dev.starcore.starcore.social.mail.gui;

import dev.starcore.starcore.social.mail.Mail;
import dev.starcore.starcore.social.mail.MailService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 邮件详情 GUI
 */
public final class MailDetailGui implements InventoryHolder {

    public static final int SIZE = 54;

    private final Player player;
    private final MailService mailService;
    private final Mail mail;
    private final Inventory inventory;

    public MailDetailGui(Player player, MailService mailService, Mail mail) {
        this.player = player;
        this.mailService = mailService;
        this.mail = mail;

        // 先读取邮件（标记为已读）
        mailService.readMail(player.getUniqueId(), mail.getId());

        this.inventory = Bukkit.createInventory(this, SIZE,
            Component.text("邮件详情", NamedTextColor.GOLD));

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

        // 邮件头部信息
        inventory.setItem(4, createHeaderItem());
        inventory.setItem(19, createSenderItem());
        inventory.setItem(20, createSubjectItem());
        inventory.setItem(21, createTimeItem());

        // 邮件内容区域
        inventory.setItem(22, createContentItem());

        // 附件区域（如果邮件有附件）
        if (mail.hasAttachments()) {
            buildAttachmentSection();
        }

        // 操作按钮
        buildActionButtons();

        // 导航按钮
        addNavigationButtons();
    }

    /**
     * 构建附件区域
     */
    private void buildAttachmentSection() {
        // 附件标题
        ItemStack titleItem = new ItemStack(
            mail.isClaimed() ? Material.CHEST : Material.ENDER_CHEST
        );
        ItemMeta meta = titleItem.getItemMeta();
        meta.displayName(Component.text(
            mail.isClaimed() ? "附件（已领取）" : "附件（点击领取）",
            mail.isClaimed() ? NamedTextColor.GREEN : NamedTextColor.GOLD
        ));
        titleItem.setItemMeta(meta);
        inventory.setItem(25, titleItem);

        // 附件物品展示
        List<ItemStack> attachments = new ArrayList<>();
        for (var attach : mail.getAttachments()) {
            ItemStack item = attach.deserializeItem();
            if (item != null) {
                attachments.add(item);
            }
        }

        // 在下方展示附件
        int slot = 28;
        for (ItemStack item : attachments) {
            if (slot <= 34 && attachments.indexOf(item) < 7) {
                inventory.setItem(slot++, item);
            }
        }

        // 领取按钮（如果没有领取）
        if (!mail.isClaimed()) {
            inventory.setItem(49, createClaimButton());
        } else {
            inventory.setItem(49, createClaimedButton());
        }
    }

    /**
     * 构建操作按钮
     */
    private void buildActionButtons() {
        // 回复按钮
        inventory.setItem(37, createReplyButton());

        // 删除按钮
        inventory.setItem(43, createDeleteButton());
    }

    /**
     * 添加导航按钮
     */
    private void addNavigationButtons() {
        // 返回按钮
        inventory.setItem(45, createBackButton());

        // 关闭按钮
        inventory.setItem(53, createCloseButton());
    }

    // ==================== 物品创建方法 ====================

    private ItemStack createHeaderItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        Component displayName = Component.text()
            .append(Component.text("[")
                .color(NamedTextColor.DARK_GRAY))
            .append(Component.text(mail.isRead() ? "已读" : "未读")
                .color(mail.isRead() ? NamedTextColor.GRAY : NamedTextColor.RED))
            .append(Component.text("] ")
                .color(NamedTextColor.DARK_GRAY))
            .append(Component.text("邮件详情")
                .color(NamedTextColor.GOLD))
            .build();

        meta.displayName(displayName);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("=== 邮件信息 ===", NamedTextColor.GOLD));
        lore.add(Component.text("过期时间: " + mail.getRemainingDays() + " 天后", NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSenderItem() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("发件人: " + mail.getSenderName(), NamedTextColor.AQUA));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("发送者: " + mail.getSenderName(), NamedTextColor.GRAY));
        lore.add(Component.text("UUID: " + mail.getSenderId(), NamedTextColor.DARK_GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSubjectItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("主题: " + mail.getSubject(), NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("邮件主题", NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTimeItem() {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("发送时间: " + mail.getFormattedTime(), NamedTextColor.GREEN));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("发送时间: " + mail.getFormattedTime(), NamedTextColor.GRAY));
        lore.add(Component.text("剩余: " + mail.getRemainingDays() + " 天后过期", NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createContentItem() {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("邮件内容", NamedTextColor.WHITE));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));

        // 分割内容为多行
        String content = mail.getContent();
        if (content != null && !content.isEmpty()) {
            String[] lines = content.split("\n");
            for (int i = 0; i < Math.min(lines.length, 6); i++) {
                if (lines[i].length() > 30) {
                    lore.add(Component.text(lines[i].substring(0, 30), NamedTextColor.GRAY));
                    if (lines[i].length() > 60) {
                        lore.add(Component.text(lines[i].substring(30, Math.min(60, lines[i].length())), NamedTextColor.GRAY));
                    } else {
                        lore.add(Component.text(lines[i].substring(30), NamedTextColor.GRAY));
                    }
                } else {
                    lore.add(Component.text(lines[i], NamedTextColor.GRAY));
                }
            }
            if (lines.length > 6) {
                lore.add(Component.text("...", NamedTextColor.DARK_GRAY));
            }
        } else {
            lore.add(Component.text("(无内容)", NamedTextColor.GRAY));
        }

        lore.add(Component.text(""));
        lore.add(Component.text("点击查看完整内容", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createReplyButton() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("回复邮件", NamedTextColor.GREEN));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("给 " + mail.getSenderName() + " 回复邮件", NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("点击回复", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createClaimButton() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("领取附件", NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("附件数量: " + mail.getAttachments().size(), NamedTextColor.GOLD));
        lore.add(Component.text(""));
        lore.add(Component.text("点击领取所有附件", NamedTextColor.YELLOW));
        lore.add(Component.text("(物品将放入你的背包)", NamedTextColor.GRAY));

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

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createDeleteButton() {
        ItemStack item = new ItemStack(Material.TNT);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("删除邮件", NamedTextColor.RED));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("删除此邮件", NamedTextColor.GRAY));
        lore.add(Component.text("此操作不可撤销！", NamedTextColor.DARK_RED));
        lore.add(Component.text(""));
        lore.add(Component.text("点击删除", NamedTextColor.RED));

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

    /**
     * 邮件详情动作枚举
     */
    public enum MailDetailAction {
        NONE,
        REPLY,
        CLAIM,
        DELETE,
        BACK,
        CLOSE
    }

    /**
     * 从槽位获取动作
     */
    public static MailDetailAction getActionFromSlot(int slot) {
        return switch (slot) {
            case 37 -> MailDetailAction.REPLY;
            case 43 -> MailDetailAction.DELETE;
            case 45 -> MailDetailAction.BACK;
            case 49 -> MailDetailAction.CLAIM;
            case 53 -> MailDetailAction.CLOSE;
            default -> MailDetailAction.NONE;
        };
    }
}
