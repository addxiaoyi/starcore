package dev.starcore.starcore.social.mail.attachment.gui;

import dev.starcore.starcore.social.mail.Mail;
import dev.starcore.starcore.social.mail.MailService;
import dev.starcore.starcore.social.mail.attachment.MailAttachmentService;
import dev.starcore.starcore.social.mail.attachment.gui.MailAttachmentPreviewGui.AttachmentAction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邮件附件 GUI 监听器
 *
 * 处理附件预览 GUI 的交互事件
 */
public final class MailAttachmentGuiListener implements Listener {

    private final MailService mailService;
    private final MailAttachmentService attachmentService;

    // 打开的 GUI 映射: playerId -> MailAttachmentPreviewGui
    private final Map<UUID, MailAttachmentPreviewGui> openGuis = new ConcurrentHashMap<>();

    public MailAttachmentGuiListener(MailService mailService, MailAttachmentService attachmentService) {
        this.mailService = mailService;
        this.attachmentService = attachmentService;
    }

    /**
     * 打开附件预览 GUI
     */
    public void openAttachmentPreview(Player player, Mail mail) {
        if (player == null || mail == null) {
            return;
        }

        MailAttachmentPreviewGui gui = new MailAttachmentPreviewGui(player, mail, attachmentService);
        openGuis.put(player.getUniqueId(), gui);
        player.openInventory(gui.getInventory());
    }

    /**
     * 关闭玩家打开的 GUI
     */
    public void closeAttachmentPreview(Player player) {
        if (player == null) {
            return;
        }
        openGuis.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof MailAttachmentPreviewGui gui)) {
            return;
        }

        // 检查是否是当前玩家打开的 GUI
        if (!openGuis.containsKey(player.getUniqueId())) {
            return;
        }

        // 取消事件
        event.setCancelled(true);

        // 检查点击位置
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }

        // 处理动作
        MailAttachmentPreviewGui.AttachmentAction action = MailAttachmentPreviewGui.getActionFromSlot(slot);

        switch (action) {
            case CLAIM -> handleClaim(player, gui);
            case DETAILS -> handleDetails(player, gui);
            case BACK -> handleBack(player, gui);
            case CLOSE -> player.closeInventory();
            case NONE -> {} // 忽略其他点击
        }
    }

    /**
     * 处理领取按钮
     */
    private void handleClaim(Player player, MailAttachmentPreviewGui gui) {
        Mail mail = gui.getMail();

        // 检查是否已领取
        if (mail.isClaimed()) {
            player.sendMessage(Component.text("[附件] 此邮件的附件已被领取", net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        // 检查背包空间
        var items = new java.util.ArrayList<org.bukkit.inventory.ItemStack>();
        for (var attach : mail.getAttachments()) {
            org.bukkit.inventory.ItemStack item = attach.deserializeItem();
            if (item != null) {
                items.add(item);
            }
        }

        if (!attachmentService.hasInventorySpace(player, items)) {
            // 提示玩家清理背包
            player.sendMessage(Component.text("[附件] 背包空间不足！", net.kyori.adventure.text.format.NamedTextColor.RED));
            player.sendMessage(Component.text("[附件] 请清理背包后重新打开此界面领取", net.kyori.adventure.text.format.NamedTextColor.YELLOW));

            // 显示所需槽位
            int requiredSlots = attachmentService.calculateRequiredSlots(player, items);
            player.sendMessage(Component.text("[附件] 需要 " + requiredSlots + " 个空槽位", net.kyori.adventure.text.format.NamedTextColor.GRAY));
            return;
        }

        // 执行领取
        gui.executeClaim();
    }

    /**
     * 处理查看详情按钮
     */
    private void handleDetails(Player player, MailAttachmentPreviewGui gui) {
        // 关闭当前 GUI，打开邮件详情 GUI
        player.closeInventory();

        // 使用 MailService 的 GUI
        try {
            var mailDetailGui = new dev.starcore.starcore.social.mail.gui.MailDetailGui(
                player, mailService, gui.getMail());
            player.openInventory(mailDetailGui.getInventory());
        } catch (Exception e) {
            player.sendMessage(Component.text("[邮件] 无法打开邮件详情", net.kyori.adventure.text.format.NamedTextColor.RED));
        }
    }

    /**
     * 处理返回按钮
     */
    private void handleBack(Player player, MailAttachmentPreviewGui gui) {
        // 关闭当前 GUI，打开邮件列表 GUI
        player.closeInventory();

        try {
            var mailListGui = new dev.starcore.starcore.social.mail.gui.MailListGui(
                player, mailService);
            player.openInventory(mailListGui.getInventory());
        } catch (Exception e) {
            player.sendMessage(Component.text("[邮件] 无法打开邮件列表", net.kyori.adventure.text.format.NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        Inventory inventory = event.getInventory();
        if (inventory.getHolder() instanceof MailAttachmentPreviewGui) {
            openGuis.remove(player.getUniqueId());
        }
    }

    /**
     * 获取当前打开 GUI 的玩家数量
     */
    public int getOpenGuiCount() {
        return openGuis.size();
    }

    /**
     * 清理所有打开的 GUI
     */
    public void clearAll() {
        openGuis.clear();
    }

    // E-041 修复: 玩家退出时清理 openGuis Map 状态
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        openGuis.remove(event.getPlayer().getUniqueId());
    }
}
