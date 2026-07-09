package dev.starcore.starcore.social.mail.gui;

import dev.starcore.starcore.social.mail.Mail;
import dev.starcore.starcore.social.mail.MailService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邮件 GUI 事件监听器
 */
public final class MailGuiListener implements Listener {

    private final MailService mailService;

    // 玩家当前打开的 GUI 类型
    private final Map<UUID, GuiType> playerGuiType = new ConcurrentHashMap<>();

    // 玩家正在发送的邮件草稿
    private final Map<UUID, MailDraft> playerDrafts = new ConcurrentHashMap<>();

    public MailGuiListener(MailService mailService) {
        this.mailService = mailService;
    }

    /**
     * 邮件草稿数据
     */
    public static class MailDraft {
        public String recipientName;
        public String subject;
        public String content;
        public ItemStack[] attachments;

        public MailDraft() {
            this.attachments = new ItemStack[7]; // 最多7个附件
        }
    }

    /**
     * GUI 类型枚举
     */
    public enum GuiType {
        MAIL_LIST,
        MAIL_DETAIL,
        MAIL_SEND,
        MAIL_COMPOSE,
        MAIL_INPUT
    }

    /**
     * 打开邮件列表 GUI
     */
    public void openMailList(Player player) {
        MailListGui gui = new MailListGui(player, mailService);
        player.openInventory(gui.getInventory());
        playerGuiType.put(player.getUniqueId(), GuiType.MAIL_LIST);
    }

    /**
     * 打开邮件详情 GUI
     */
    public void openMailDetail(Player player, Mail mail) {
        MailDetailGui gui = new MailDetailGui(player, mailService, mail);
        player.openInventory(gui.getInventory());
        playerGuiType.put(player.getUniqueId(), GuiType.MAIL_DETAIL);
    }

    /**
     * 打开发送邮件 GUI
     */
    public void openMailSend(Player player) {
        MailSendGui gui = new MailSendGui(player, mailService);
        player.openInventory(gui.getInventory());
        playerGuiType.put(player.getUniqueId(), GuiType.MAIL_SEND);
    }

    /**
     * 打开发送邮件 GUI（带回复对象）
     */
    public void openMailSend(Player player, String replyToName) {
        MailSendGui gui = new MailSendGui(player, mailService, replyToName);
        player.openInventory(gui.getInventory());
        playerGuiType.put(player.getUniqueId(), GuiType.MAIL_SEND);
    }

    /**
     * 打开邮件输入 GUI
     */
    public void openMailInput(Player player, String inputType, String currentValue) {
        // 使用铁砧界面进行文本输入
        // 这里我们使用一个简单的对话框方式
        player.closeInventory();

        if ("recipient".equals(inputType)) {
            player.sendMessage("§e[邮件] 请在聊天框输入收件人名称（输入 'cancel' 取消）：");
        } else if ("subject".equals(inputType)) {
            player.sendMessage("§e[邮件] 请在聊天框输入邮件主题（输入 'cancel' 取消）：");
        } else if ("content".equals(inputType)) {
            player.sendMessage("§e[邮件] 请在聊天框输入邮件内容（输入 'cancel' 取消）：");
        }

        playerGuiType.put(player.getUniqueId(), GuiType.MAIL_INPUT);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        GuiType guiType = playerGuiType.get(player.getUniqueId());
        if (guiType == null) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();

        switch (guiType) {
            case MAIL_LIST -> handleMailListClick(player, slot);
            case MAIL_DETAIL -> handleMailDetailClick(player, slot);
            case MAIL_SEND -> handleMailSendClick(player, slot, event);
            case MAIL_COMPOSE -> handleMailComposeClick(player, slot);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        UUID playerId = player.getUniqueId();
        GuiType guiType = playerGuiType.get(playerId);

        if (guiType == null) return;

        // 保存附件到草稿（如果在发送界面关闭）
        if (guiType == GuiType.MAIL_SEND) {
            saveAttachmentsToDraft(player);
        }

        // 延迟清理 GUI 状态
        Bukkit.getScheduler().runTaskLater(
            Bukkit.getPluginManager().getPlugin("StarCore"),
            () -> playerGuiType.remove(playerId),
            1L
        );
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (playerGuiType.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * 处理邮件列表点击
     */
    private void handleMailListClick(Player player, int slot) {
        MailListGui.MailListAction action = MailListGui.getActionFromSlot(slot);

        switch (action) {
            case REFRESH -> {
                openMailList(player);
            }
            case NEW_MAIL -> {
                openMailSend(player);
            }
            case PREV_PAGE -> {
                // 获取当前 GUI 并翻页
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof MailListGui gui) {
                    gui.previousPage();
                    player.openInventory(gui.getInventory());
                }
            }
            case NEXT_PAGE -> {
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof MailListGui gui) {
                    gui.nextPage();
                    player.openInventory(gui.getInventory());
                }
            }
            case VIEW_MAIL -> {
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof MailListGui gui) {
                    Mail mail = gui.getMailFromSlot(slot);
                    if (mail != null) {
                        openMailDetail(player, mail);
                    }
                }
            }
            case CLOSE -> {
                player.closeInventory();
            }
            default -> {}
        }
    }

    /**
     * 处理邮件详情点击
     */
    private void handleMailDetailClick(Player player, int slot) {
        MailDetailGui.MailDetailAction action = MailDetailGui.getActionFromSlot(slot);
        MailDetailGui gui = (MailDetailGui) player.getOpenInventory().getTopInventory().getHolder();
        Mail mail = gui.getMail();

        switch (action) {
            case REPLY -> {
                // 回复邮件
                openMailSend(player, mail.getSenderName());
            }
            case CLAIM -> {
                // 领取附件
                claimAttachments(player, mail);
            }
            case DELETE -> {
                // 删除邮件
                if (mailService.deleteMail(player.getUniqueId(), mail.getId())) {
                    player.sendMessage("§a[邮件] 邮件已删除");
                    playerGuiType.remove(player.getUniqueId());
                    openMailList(player);
                } else {
                    player.sendMessage("§c[邮件] 删除邮件失败");
                }
            }
            case BACK -> {
                playerGuiType.remove(player.getUniqueId());
                openMailList(player);
            }
            case CLOSE -> {
                player.closeInventory();
            }
            default -> {}
        }
    }

    /**
     * 处理发送邮件点击
     */
    private void handleMailSendClick(Player player, int slot, InventoryClickEvent event) {
        MailSendGui.MailSendAction action = MailSendGui.getActionFromSlot(slot);
        MailSendGui gui = (MailSendGui) player.getOpenInventory().getTopInventory().getHolder();

        switch (action) {
            case RECIPIENT -> {
                // 输入收件人
                openMailInput(player, "recipient", gui.getRecipientName());
            }
            case NEXT -> {
                // 下一步
                if (gui.getRecipientName() != null && !gui.getRecipientName().isEmpty()) {
                    // 收集附件
                    saveAttachmentsToDraft(player);
                    openMailCompose(player, gui);
                } else {
                    player.sendMessage("§c[邮件] 请先填写收件人");
                }
            }
            case CLEAR_ATTACHMENTS -> {
                // 清除附件
                gui.getAttachments().clear();
                player.openInventory(gui.getInventory());
            }
            case BACK -> {
                playerGuiType.remove(player.getUniqueId());
                openMailList(player);
            }
            case CLOSE -> {
                player.closeInventory();
            }
            default -> {}
        }
    }

    /**
     * 处理邮件编辑点击
     */
    private void handleMailComposeClick(Player player, int slot) {
        MailComposeGui.MailComposeAction action = MailComposeGui.getActionFromSlot(slot);
        MailComposeGui gui = (MailComposeGui) player.getOpenInventory().getTopInventory().getHolder();

        switch (action) {
            case SUBJECT -> {
                openMailInput(player, "subject", gui.getSubject());
            }
            case CONTENT -> {
                openMailInput(player, "content", gui.getContent());
            }
            case PREVIEW -> {
                // 显示邮件预览
                player.sendMessage("§6=== 邮件预览 ===");
                player.sendMessage("§e收件人: §f" + gui.getRecipientName());
                player.sendMessage("§e主题: §f" + gui.getSubject());
                player.sendMessage("§e内容: §f" + gui.getContent());
                player.sendMessage("§e附件: §f" + gui.getAttachments().size() + " 个物品");
                player.sendMessage("§7确认无误后点击 §a发送 §7按钮");
            }
            case SEND -> {
                // 发送邮件
                sendMail(player, gui);
            }
            case BACK -> {
                // 返回上一步
                openMailSend(player);
            }
            case RETURN -> {
                openMailSend(player);
            }
            case CLOSE -> {
                player.closeInventory();
            }
            default -> {}
        }
    }

    /**
     * 收集附件到草稿
     */
    private void saveAttachmentsToDraft(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof MailSendGui gui) {
            MailDraft draft = playerDrafts.computeIfAbsent(player.getUniqueId(), k -> new MailDraft());
            draft.recipientName = gui.getRecipientName();
            draft.attachments = new ItemStack[7];

            // 从 GUI 中收集附件
            for (int i = 0; i < 7; i++) {
                ItemStack item = player.getOpenInventory().getTopInventory().getItem(28 + i);
                if (item != null && !item.getType().isAir()) {
                    draft.attachments[i] = item;
                }
            }
        }
    }

    /**
     * 打开邮件编辑界面
     */
    private void openMailCompose(Player player, MailSendGui gui) {
        MailComposeGui composeGui = new MailComposeGui(
            player,
            mailService,
            gui.getRecipientName(),
            gui.getAttachments()
        );
        player.openInventory(composeGui.getInventory());
        playerGuiType.put(player.getUniqueId(), GuiType.MAIL_COMPOSE);
    }

    /**
     * 领取附件
     */
    private void claimAttachments(Player player, Mail mail) {
        if (mail.isClaimed()) {
            player.sendMessage("§c[邮件] 附件已被领取");
            return;
        }

        if (!mail.hasAttachments()) {
            player.sendMessage("§c[邮件] 这封邮件没有附件");
            return;
        }

        java.util.List<ItemStack> items = mailService.claimAttachments(player.getUniqueId(), mail.getId());

        // 将物品放入玩家背包
        for (ItemStack item : items) {
            java.util.HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            // 如果背包满了，将溢出的物品丢在地上
            for (ItemStack overflowItem : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflowItem);
            }
        }

        // D-027: 物品入背包后再确认 claimed，避免重复领取
        mailService.confirmClaimed(player.getUniqueId(), mail.getId());

        player.sendMessage("§a[邮件] 附件已领取！");
        player.openInventory(new MailDetailGui(player, mailService, mail).getInventory());
    }

    /**
     * 发送邮件
     */
    private void sendMail(Player player, MailComposeGui gui) {
        if (gui.getSubject() == null || gui.getSubject().isEmpty()) {
            player.sendMessage("§c[邮件] 请填写邮件主题");
            return;
        }

        if (gui.getContent() == null || gui.getContent().isEmpty()) {
            player.sendMessage("§c[邮件] 请填写邮件内容");
            return;
        }

        // 收集附件
        java.util.List<ItemStack> attachments = gui.getAttachments();

        // 发送邮件
        boolean success = mailService.sendMail(
            player,
            gui.getRecipientName(),
            gui.getSubject(),
            gui.getContent(),
            attachments
        );

        if (success) {
            player.sendMessage("§a[邮件] 邮件已发送！");

            // 清除附件
            for (ItemStack item : attachments) {
                player.getInventory().removeItem(item);
            }

            playerGuiType.remove(player.getUniqueId());
            openMailList(player);
        } else {
            player.sendMessage("§c[邮件] 发送失败，请检查收件人是否正确");
        }
    }

    /**
     * 处理玩家输入
     */
    public void handlePlayerInput(Player player, String input) {
        UUID playerId = player.getUniqueId();
        GuiType guiType = playerGuiType.get(playerId);

        if (guiType != GuiType.MAIL_INPUT) return;

        if ("cancel".equalsIgnoreCase(input)) {
            player.sendMessage("§e[邮件] 输入已取消");
            playerGuiType.remove(playerId);
            openMailList(player);
            return;
        }

        // 根据之前的操作类型处理输入
        MailDraft draft = playerDrafts.get(playerId);
        if (draft != null) {
            if (draft.recipientName == null) {
                // 正在输入收件人
                draft.recipientName = input;
                openMailSend(player);
            } else if (draft.subject == null) {
                // 正在输入主题
                draft.subject = input;
                openMailComposeWithDraft(player);
            } else if (draft.content == null) {
                // 正在输入内容
                draft.content = input;
                openMailComposeWithDraft(player);
            }
        }
    }

    /**
     * 使用草稿打开编辑界面
     */
    private void openMailComposeWithDraft(Player player) {
        MailDraft draft = playerDrafts.get(player.getUniqueId());
        if (draft != null) {
            java.util.List<ItemStack> attachments = new java.util.ArrayList<>();
            for (ItemStack item : draft.attachments) {
                if (item != null) {
                    attachments.add(item);
                }
            }

            MailComposeGui gui = new MailComposeGui(
                player,
                mailService,
                draft.recipientName,
                attachments
            );
            gui.setSubject(draft.subject != null ? draft.subject : "");
            gui.setContent(draft.content != null ? draft.content : "");

            player.openInventory(gui.getInventory());
            playerGuiType.put(player.getUniqueId(), GuiType.MAIL_COMPOSE);
        }
    }

    // E-035 修复: 玩家退出时清理 Map 状态
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        playerGuiType.remove(playerId);
        playerDrafts.remove(playerId);
    }
}
