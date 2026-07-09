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
 * 发送邮件 GUI（第一步：输入收件人）
 */
public final class MailSendGui implements InventoryHolder {

    public static final int SIZE = 54;

    private final Player player;
    private final MailService mailService;
    private final Inventory inventory;

    private String recipientName;
    private String subject;
    private String content;
    private final List<ItemStack> attachments;

    public MailSendGui(Player player, MailService mailService) {
        this.player = player;
        this.mailService = mailService;
        this.recipientName = "";
        this.subject = "";
        this.content = "";
        this.attachments = new ArrayList<>();

        this.inventory = Bukkit.createInventory(this, SIZE,
            Component.text("发送邮件 - 收件人", NamedTextColor.GOLD));

        buildMenu();
    }

    /**
     * 带回复信息的构造函数
     */
    public MailSendGui(Player player, MailService mailService, String replyToName) {
        this.player = player;
        this.mailService = mailService;
        this.recipientName = replyToName;
        this.subject = "";
        this.content = "";
        this.attachments = new ArrayList<>();

        this.inventory = Bukkit.createInventory(this, SIZE,
            Component.text("发送邮件 - 收件人", NamedTextColor.GOLD));

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

    public void setRecipientName(String name) {
        this.recipientName = name;
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

    public void addAttachment(ItemStack item) {
        if (item != null && !item.getType().isAir()) {
            attachments.add(item.clone());
        }
    }

    public void removeAttachment(int index) {
        if (index >= 0 && index < attachments.size()) {
            attachments.remove(index);
        }
    }

    /**
     * 构建菜单
     */
    private void buildMenu() {
        inventory.clear();

        // 标题区域
        inventory.setItem(4, createTitleItem());

        // 收件人输入区域
        inventory.setItem(19, createRecipientItem());

        // 附件区域
        buildAttachmentSection();

        // 预览区域
        inventory.setItem(40, createPreviewItem());

        // 操作按钮
        addActionButtons();

        // 导航按钮
        addNavigationButtons();
    }

    /**
     * 构建附件区域
     */
    private void buildAttachmentSection() {
        // 附件标题
        ItemStack titleItem = new ItemStack(Material.ENDER_CHEST);
        ItemMeta meta = titleItem.getItemMeta();
        meta.displayName(Component.text("附件（放入要发送的物品）", NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("将物品放入上方格子", NamedTextColor.GRAY));
        lore.add(Component.text("最多可添加 " + attachments.size() + " 个物品", NamedTextColor.GOLD));

        meta.lore(lore);
        titleItem.setItemMeta(meta);
        inventory.setItem(25, titleItem);

        // 附件展示区域（18-26 槽位，用于放置附件）
        for (int i = 0; i < 7; i++) {
            inventory.setItem(28 + i, createAttachmentSlot(i));
        }
    }

    private ItemStack createAttachmentSlot(int index) {
        if (index < attachments.size()) {
            return attachments.get(index);
        }
        return createEmptySlot();
    }

    private ItemStack createEmptySlot() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" ", NamedTextColor.BLACK));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 添加操作按钮
     */
    private void addActionButtons() {
        // 下一步按钮（填写主题）
        inventory.setItem(37, createNextButton());

        // 清除附件按钮
        if (!attachments.isEmpty()) {
            inventory.setItem(43, createClearAttachmentsButton());
        }
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

    private ItemStack createTitleItem() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("发送邮件", NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("=== 发送邮件 ===", NamedTextColor.GOLD));
        lore.add(Component.text("第1步: 填写收件人", NamedTextColor.GRAY));
        lore.add(Component.text(""));

        if (recipientName != null && !recipientName.isEmpty()) {
            lore.add(Component.text("收件人: " + recipientName, NamedTextColor.GREEN));
        } else {
            lore.add(Component.text("收件人: 未填写", NamedTextColor.RED));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRecipientItem() {
        Material material = recipientName != null && !recipientName.isEmpty()
            ? Material.PAPER : Material.BOOK;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("收件人", NamedTextColor.AQUA));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("点击填写收件人名称", NamedTextColor.YELLOW));
        lore.add(Component.text(""));

        if (recipientName != null && !recipientName.isEmpty()) {
            lore.add(Component.text("当前: " + recipientName, NamedTextColor.GREEN));
        } else {
            lore.add(Component.text("未填写", NamedTextColor.RED));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPreviewItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("预览", NamedTextColor.DARK_PURPLE));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("=== 邮件预览 ===", NamedTextColor.GOLD));
        lore.add(Component.text("收件人: " + (recipientName.isEmpty() ? "未填写" : recipientName), NamedTextColor.GRAY));
        lore.add(Component.text("主题: " + (subject.isEmpty() ? "未填写" : subject), NamedTextColor.GRAY));
        lore.add(Component.text("附件: " + attachments.size() + " 个", NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNextButton() {
        boolean canProceed = recipientName != null && !recipientName.isEmpty();

        Material material = canProceed ? Material.ARROW : Material.BARRIER;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (canProceed) {
            meta.displayName(Component.text("下一步 - 填写主题", NamedTextColor.GREEN));
        } else {
            meta.displayName(Component.text("请先填写收件人", NamedTextColor.RED));
        }

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        if (canProceed) {
            lore.add(Component.text("点击进入下一步", NamedTextColor.YELLOW));
        } else {
            lore.add(Component.text("请先填写收件人名称", NamedTextColor.RED));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createClearAttachmentsButton() {
        ItemStack item = new ItemStack(Material.TNT);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("清除附件", NamedTextColor.RED));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("当前附件: " + attachments.size() + " 个", NamedTextColor.GRAY));
        lore.add(Component.text("点击清除所有附件", NamedTextColor.RED));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("返回邮件列表", NamedTextColor.YELLOW));

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
     * 发送邮件动作
     */
    public enum MailSendAction {
        NONE,
        RECIPIENT,
        NEXT,
        CLEAR_ATTACHMENTS,
        BACK,
        CLOSE
    }

    /**
     * 从槽位获取动作
     */
    public static MailSendAction getActionFromSlot(int slot) {
        return switch (slot) {
            case 19 -> MailSendAction.RECIPIENT;
            case 37 -> MailSendAction.NEXT;
            case 43 -> MailSendAction.CLEAR_ATTACHMENTS;
            case 45 -> MailSendAction.BACK;
            case 53 -> MailSendAction.CLOSE;
            default -> (slot >= 28 && slot <= 34) ? MailSendAction.CLEAR_ATTACHMENTS : MailSendAction.NONE;
        };
    }
}
