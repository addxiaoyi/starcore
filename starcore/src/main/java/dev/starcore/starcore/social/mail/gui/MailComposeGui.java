package dev.starcore.starcore.social.mail.gui;

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

/**
 * 发送邮件 GUI（第二步：填写主题和内容）
 */
public final class MailComposeGui implements InventoryHolder {

    public static final int SIZE = 54;

    private final Player player;
    private final MailService mailService;
    private final Inventory inventory;

    private final String recipientName;
    private String subject;
    private String content;
    private final List<ItemStack> attachments;

    public MailComposeGui(Player player, MailService mailService, String recipientName,
                         List<ItemStack> attachments) {
        this.player = player;
        this.mailService = mailService;
        this.recipientName = recipientName;
        this.subject = "";
        this.content = "";
        this.attachments = attachments != null ? new ArrayList<>(attachments) : new ArrayList<>();

        this.inventory = Bukkit.createInventory(this, SIZE,
            Component.text("发送邮件 - 主题和内容", NamedTextColor.GOLD));

        buildMenu();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<ItemStack> getAttachments() {
        return attachments;
    }

    /**
     * 构建菜单
     */
    private void buildMenu() {
        inventory.clear();

        // 标题区域
        inventory.setItem(4, createTitleItem());

        // 收件人信息
        inventory.setItem(19, createRecipientItem());

        // 主题输入区域
        inventory.setItem(21, createSubjectItem());

        // 内容输入区域（使用大书本展示）
        inventory.setItem(23, createContentItem());

        // 附件信息
        inventory.setItem(25, createAttachmentInfoItem());

        // 操作按钮
        addActionButtons();

        // 导航按钮
        addNavigationButtons();
    }

    /**
     * 添加操作按钮
     */
    private void addActionButtons() {
        // 预览按钮
        inventory.setItem(37, createPreviewButton());

        // 发送按钮
        boolean canSend = subject != null && !subject.isEmpty() && content != null && !content.isEmpty();
        inventory.setItem(40, createSendButton(canSend));

        // 返回上一步
        inventory.setItem(43, createBackButton());
    }

    /**
     * 添加导航按钮
     */
    private void addNavigationButtons() {
        // 返回按钮
        inventory.setItem(45, createReturnButton());

        // 关闭按钮
        inventory.setItem(53, createCloseButton());
    }

    // ==================== 物品创建方法 ====================

    private ItemStack createTitleItem() {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("发送邮件", NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("=== 发送邮件 ===", NamedTextColor.GOLD));
        lore.add(Component.text("第2步: 填写主题和内容", NamedTextColor.GRAY));
        lore.add(Component.text(""));

        if (subject.isEmpty()) {
            lore.add(Component.text("主题: 未填写", NamedTextColor.RED));
        } else {
            lore.add(Component.text("主题: " + subject, NamedTextColor.GREEN));
        }

        if (content.isEmpty()) {
            lore.add(Component.text("内容: 未填写", NamedTextColor.RED));
        } else {
            lore.add(Component.text("内容: 已填写", NamedTextColor.GREEN));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRecipientItem() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("收件人: " + recipientName, NamedTextColor.AQUA));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("发送给: " + recipientName, NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSubjectItem() {
        Material material = subject != null && !subject.isEmpty()
            ? Material.PAPER : Material.BOOK;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("主题", NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("点击填写邮件主题", NamedTextColor.YELLOW));
        lore.add(Component.text(""));

        if (subject != null && !subject.isEmpty()) {
            lore.add(Component.text("当前: " + subject, NamedTextColor.GREEN));
        } else {
            lore.add(Component.text("未填写", NamedTextColor.RED));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createContentItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("邮件内容", NamedTextColor.WHITE));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("点击填写邮件内容", NamedTextColor.YELLOW));
        lore.add(Component.text(""));

        if (content != null && !content.isEmpty()) {
            lore.add(Component.text("已填写 " + content.length() + " 个字符", NamedTextColor.GREEN));
        } else {
            lore.add(Component.text("未填写", NamedTextColor.RED));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createAttachmentInfoItem() {
        Material material = attachments.isEmpty() ? Material.CHEST : Material.ENDER_CHEST;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("附件", attachments.isEmpty() ? NamedTextColor.GRAY : NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("附件数量: " + attachments.size(), NamedTextColor.GRAY));

        if (!attachments.isEmpty()) {
            lore.add(Component.text(""));
            lore.add(Component.text("点击查看附件", NamedTextColor.YELLOW));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPreviewButton() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("预览邮件", NamedTextColor.DARK_PURPLE));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("点击预览邮件效果", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSendButton(boolean canSend) {
        Material material = canSend ? Material.LIME_STAINED_GLASS : Material.RED_STAINED_GLASS;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (canSend) {
            meta.displayName(Component.text("发送邮件", NamedTextColor.GREEN));
        } else {
            meta.displayName(Component.text("请填写完整", NamedTextColor.RED));
        }

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));

        if (canSend) {
            lore.add(Component.text("收件人: " + recipientName, NamedTextColor.GRAY));
            lore.add(Component.text("附件: " + attachments.size() + " 个", NamedTextColor.GRAY));
            lore.add(Component.text(""));
            lore.add(Component.text("点击发送邮件", NamedTextColor.GREEN));
        } else {
            lore.add(Component.text("请填写主题和内容", NamedTextColor.RED));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("上一步", NamedTextColor.AQUA));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("返回修改收件人/附件", NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createReturnButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("返回", NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("返回收件人填写", NamedTextColor.GRAY));

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
        lore.add(Component.text("点击关闭（草稿不保存）", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 发送邮件动作
     */
    public enum MailComposeAction {
        NONE,
        SUBJECT,
        CONTENT,
        PREVIEW,
        SEND,
        BACK,
        RETURN,
        CLOSE
    }

    /**
     * 从槽位获取动作
     */
    public static MailComposeAction getActionFromSlot(int slot) {
        return switch (slot) {
            case 21 -> MailComposeAction.SUBJECT;
            case 23 -> MailComposeAction.CONTENT;
            case 37 -> MailComposeAction.PREVIEW;
            case 40 -> MailComposeAction.SEND;
            case 43 -> MailComposeAction.BACK;
            case 45 -> MailComposeAction.RETURN;
            case 53 -> MailComposeAction.CLOSE;
            default -> MailComposeAction.NONE;
        };
    }
}
