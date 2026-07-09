package dev.starcore.starcore.social.mail.attachment.gui;

import dev.starcore.starcore.social.mail.attachment.MailAttachmentService;
import dev.starcore.starcore.social.mail.attachment.MailAttachmentService.AttachmentValidationResult;
import dev.starcore.starcore.social.mail.attachment.MailAttachmentService.MailAttachment;
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
import java.util.function.Consumer;

/**
 * 邮件附件编辑 GUI
 *
 * 用于在发送邮件时添加/编辑附件
 */
public final class MailAttachmentEditGui implements InventoryHolder {

    public static final int SIZE = 54;

    // 附件槽位范围 (7个槽位)
    public static final int ATTACHMENT_SLOT_START = 28;
    public static final int ATTACHMENT_SLOT_END = 34;

    private final Player player;
    private final MailAttachmentService attachmentService;
    private final List<ItemStack> attachments;
    private final Consumer<List<ItemStack>> onConfirm;
    private final Consumer<Void> onCancel;

    private final Inventory inventory;

    public MailAttachmentEditGui(Player player, MailAttachmentService attachmentService,
                                  List<ItemStack> existingAttachments,
                                  Consumer<List<ItemStack>> onConfirm,
                                  Consumer<Void> onCancel) {
        this.player = player;
        this.attachmentService = attachmentService;
        this.attachments = existingAttachments != null ? new ArrayList<>(existingAttachments) : new ArrayList<>();
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        this.inventory = Bukkit.createInventory(this, SIZE,
            Component.text("添加附件", NamedTextColor.GOLD));

        buildMenu();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }

    public List<ItemStack> getAttachments() {
        return new ArrayList<>(attachments);
    }

    /**
     * 构建菜单
     */
    private void buildMenu() {
        inventory.clear();

        // 标题
        inventory.setItem(4, createTitleItem());

        // 提示信息
        inventory.setItem(19, createHintItem());

        // 附件数量统计
        inventory.setItem(20, createCountItem());

        // 附件区域
        buildAttachmentSlots();

        // 操作按钮
        buildActionButtons();

        // 导航按钮
        addNavigationButtons();
    }

    /**
     * 构建附件槽位
     */
    private void buildAttachmentSlots() {
        // 槽位说明
        inventory.setItem(25, createSlotInfoItem());

        // 附件槽位
        for (int i = 0; i < getMaxSlots(); i++) {
            int slot = ATTACHMENT_SLOT_START + i;
            if (i < attachments.size()) {
                inventory.setItem(slot, attachments.get(i));
            } else {
                inventory.setItem(slot, createEmptySlot(i));
            }
        }
    }

    /**
     * 构建操作按钮
     */
    private void buildActionButtons() {
        // 确认添加按钮
        inventory.setItem(37, createConfirmButton());

        // 清除所有按钮
        if (!attachments.isEmpty()) {
            inventory.setItem(43, createClearAllButton());
        }
    }

    /**
     * 添加导航按钮
     */
    private void addNavigationButtons() {
        inventory.setItem(45, createCancelButton());
        inventory.setItem(53, createCloseButton());
    }

    // ==================== 物品创建方法 ====================

    private ItemStack createTitleItem() {
        ItemStack item = new ItemStack(Material.ENDER_CHEST);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("添加邮件附件", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("=== 附件编辑 ===", NamedTextColor.GOLD));
        lore.add(Component.text("拖动物品到下方槽位添加附件", NamedTextColor.GRAY));
        lore.add(Component.text("最多添加 " + getMaxSlots() + " 个物品", NamedTextColor.YELLOW));
        lore.add(Component.text(""));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createHintItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("使用说明", NamedTextColor.AQUA));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("1. 将物品放入下方槽位", NamedTextColor.GRAY));
        lore.add(Component.text("2. 点击物品可移除", NamedTextColor.GRAY));
        lore.add(Component.text("3. 确认后附件将被发送", NamedTextColor.GRAY));
        lore.add(Component.text(""));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCountItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        int count = attachments.size();
        NamedTextColor color = count > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY;

        meta.displayName(Component.text("当前附件数量", color));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("数量: " + count + " / " + getMaxSlots(), color));
        lore.add(Component.text(""));

        if (count > 0) {
            long value = attachmentService.estimateTotalValue(
                attachments.stream()
                    .map(MailAttachment::fromItemStack)
                    .filter(a -> a != null)
                    .toList()
            );
            lore.add(Component.text("总估值: " + formatValue(value) + " 金币", NamedTextColor.YELLOW));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSlotInfoItem() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("附件槽位", NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("下方 " + getMaxSlots() + " 个槽位", NamedTextColor.GRAY));
        lore.add(Component.text("可放置附件物品", NamedTextColor.GRAY));
        lore.add(Component.text(""));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createEmptySlot(int index) {
        Material material = switch (index % 3) {
            case 0 -> Material.GRAY_STAINED_GLASS_PANE;
            case 1 -> Material.LIGHT_GRAY_STAINED_GLASS_PANE;
            default -> Material.WHITE_STAINED_GLASS_PANE;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("空槽位 " + (index + 1), NamedTextColor.DARK_GRAY));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("拖入物品添加附件", NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createConfirmButton() {
        boolean canConfirm = !attachments.isEmpty();

        Material material = canConfirm ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (canConfirm) {
            meta.displayName(Component.text("确认添加附件", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true));
        } else {
            meta.displayName(Component.text("未添加附件", NamedTextColor.RED));
        }

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        if (canConfirm) {
            lore.add(Component.text("点击确认添加 " + attachments.size() + " 个附件", NamedTextColor.GREEN));
            lore.add(Component.text("返回邮件编辑界面", NamedTextColor.GRAY));
        } else {
            lore.add(Component.text("请先添加至少一个物品", NamedTextColor.RED));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createClearAllButton() {
        ItemStack item = new ItemStack(Material.TNT);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("清除所有附件", NamedTextColor.RED));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("当前附件: " + attachments.size() + " 个", NamedTextColor.GRAY));
        lore.add(Component.text("点击清除所有附件", NamedTextColor.RED));
        lore.add(Component.text("此操作不可撤销！", NamedTextColor.DARK_RED));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCancelButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("取消", NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("取消添加附件", NamedTextColor.GRAY));
        lore.add(Component.text("不修改邮件附件", NamedTextColor.GRAY));

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
     * 获取最大槽位数
     */
    private int getMaxSlots() {
        return ATTACHMENT_SLOT_END - ATTACHMENT_SLOT_START + 1;
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

    // ==================== 动作处理 ====================

    /**
     * 动作枚举
     */
    public enum AttachmentEditAction {
        NONE,
        CONFIRM,
        CLEAR_ALL,
        CANCEL,
        CLOSE,
        REMOVE_ATTACHMENT
    }

    /**
     * 从槽位获取动作
     */
    public static AttachmentEditAction getActionFromSlot(int slot) {
        return switch (slot) {
            case 37 -> AttachmentEditAction.CONFIRM;
            case 43 -> AttachmentEditAction.CLEAR_ALL;
            case 45 -> AttachmentEditAction.CANCEL;
            case 53 -> AttachmentEditAction.CLOSE;
            default -> (slot >= ATTACHMENT_SLOT_START && slot <= ATTACHMENT_SLOT_END)
                ? AttachmentEditAction.REMOVE_ATTACHMENT
                : AttachmentEditAction.NONE;
        };
    }

    /**
     * 处理物品添加（从外部调用）
     */
    public boolean addItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }

        if (attachments.size() >= getMaxSlots()) {
            return false;
        }

        // 验证物品
        AttachmentValidationResult result = attachmentService.validateAttachments(List.of(item));
        if (!result.valid()) {
            return false;
        }

        attachments.add(item.clone());
        buildMenu();
        return true;
    }

    /**
     * 处理物品移除
     */
    public boolean removeItem(int slot) {
        int index = slot - ATTACHMENT_SLOT_START;
        if (index < 0 || index >= attachments.size()) {
            return false;
        }

        attachments.remove(index);
        buildMenu();
        return true;
    }

    /**
     * 清除所有附件
     */
    public void clearAll() {
        attachments.clear();
        buildMenu();
    }

    /**
     * 确认添加附件
     */
    public void confirm() {
        if (onConfirm != null && !attachments.isEmpty()) {
            onConfirm.accept(new ArrayList<>(attachments));
        }
    }

    /**
     * 取消
     */
    public void cancel() {
        if (onCancel != null) {
            onCancel.accept(null);
        }
    }
}
