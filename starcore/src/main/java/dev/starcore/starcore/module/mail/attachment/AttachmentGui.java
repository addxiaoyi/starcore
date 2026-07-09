package dev.starcore.starcore.module.mail.attachment;

import dev.starcore.starcore.module.mail.attachment.MailAttachmentService.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邮件附件 GUI
 *
 * 提供邮件系统的图形界面：
 * 1. 邮件列表界面
 * 2. 发送邮件界面
 * 3. 邮件详情界面
 * 4. 附件编辑界面
 */
public final class AttachmentGui implements InventoryHolder, Listener {

    public static final int MAIN_SIZE = 54;
    public static final int MAIL_SLOTS_PER_PAGE = 36;

    // 邮件列表槽位 (6x6 = 36)
    private static final int MAIL_LIST_START = 0;
    private static final int MAIL_LIST_END = 35;

    // 按钮槽位
    private static final int PREV_PAGE = 45;
    private static final int NEXT_PAGE = 53;
    private static final int STATS_SLOT = 50;
    private static final int CLOSE_SLOT = 53;

    // 发送GUI槽位
    private static final int RECIPIENT_SLOT = 4;
    private static final int SUBJECT_SLOT = 19;
    private static final int ATTACHMENT_START = 27;
    private static final int ATTACHMENT_END = 44;
    private static final int SEND_CONFIRM_SLOT = 49;
    private static final int CANCEL_SLOT = 53;

    // 邮件详情GUI槽位
    private static final int MAIL_INFO_SLOT = 4;
    private static final int SENDER_SLOT = 19;
    private static final int ATTACHMENT_DISPLAY_START = 27;
    private static final int CLAIM_BUTTON = 37;
    private static final int DELETE_BUTTON = 43;
    private static final int BACK_BUTTON = 45;
    private static final int CLOSE_DETAIL_SLOT = 53;

    private final Plugin plugin;
    private final MailAttachmentService service;
    private final Map<UUID, Integer> playerPages = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> viewingMail = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingRecipient = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingSubject = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingMessage = new ConcurrentHashMap<>();
    private final Map<UUID, List<ItemStack>> pendingAttachments = new ConcurrentHashMap<>();

    public AttachmentGui(Plugin plugin, MailAttachmentService service) {
        this.plugin = plugin;
        this.service = service;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public Inventory getInventory() {
        return null; // 由具体方法返回
    }

    // ==================== 邮件列表 GUI ====================

    /**
     * 打开邮件列表 GUI
     */
    public static void openMailListGui(Player player, MailAttachmentService service, int page) {
        new AttachmentGuiBuilder(player, service).openMailList(page);
    }

    /**
     * 打开发送邮件 GUI
     */
    public static void openSendGui(Player player, MailAttachmentService service, String recipient, String subject) {
        new AttachmentGuiBuilder(player, service).openSendGui(recipient, subject);
    }

    /**
     * 打开邮件详情 GUI
     */
    public static void openMailDetailGui(Player player, MailAttachmentService service, UUID mailId) {
        new AttachmentGuiBuilder(player, service).openMailDetail(mailId);
    }

    // ==================== GUI 构建器 ====================

    private static class AttachmentGuiBuilder implements InventoryHolder {
        private final Player player;
        private final MailAttachmentService service;
        private Inventory inventory;

        AttachmentGuiBuilder(Player player, MailAttachmentService service) {
            this.player = player;
            this.service = service;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        void openMailList(int page) {
            List<MailAttachment> mails = service.getPlayerMails(player.getUniqueId());
            int totalPages = Math.max(1, (int) Math.ceil((double) mails.size() / MAIL_SLOTS_PER_PAGE));
            page = Math.max(1, Math.min(page, totalPages));

            String title = "邮件列表 (第 " + page + "/" + totalPages + " 页)";
            inventory = Bukkit.createInventory(this, MAIN_SIZE, net.kyori.adventure.text.Component.text(title, NamedTextColor.GOLD));

            buildMailListContent(mails, page, totalPages);

            player.openInventory(inventory);
        }

        void openSendGui(String recipient, String subject) {
            String title = "发送邮件";
            inventory = Bukkit.createInventory(this, MAIN_SIZE, net.kyori.adventure.text.Component.text(title, NamedTextColor.GOLD));

            buildSendGuiContent(recipient, subject);

            player.openInventory(inventory);
        }

        void openMailDetail(UUID mailId) {
            MailAttachment mail = findMail(mailId);
            if (mail == null) {
                player.sendMessage(net.kyori.adventure.text.Component.text("[邮件] ").color(NamedTextColor.RED)
                    .append(net.kyori.adventure.text.Component.text("邮件不存在", NamedTextColor.WHITE)));
                return;
            }

            // 标记为已读
            if (!mail.read() && service instanceof MailAttachmentServiceImpl impl) {
                impl.markAsRead(player.getUniqueId(), mailId);
            }

            String detailTitle = "邮件详情 - " + mail.subject();
            inventory = Bukkit.createInventory(this, MAIN_SIZE, net.kyori.adventure.text.Component.text(detailTitle, NamedTextColor.GOLD));

            buildMailDetailContent(mail);

            player.openInventory(inventory);
        }

        private MailAttachment findMail(UUID mailId) {
            return service.getPlayerMails(player.getUniqueId()).stream()
                .filter(m -> m.id().equals(mailId))
                .findFirst()
                .orElse(null);
        }

        private void buildMailListContent(List<MailAttachment> mails, int page, int totalPages) {
            for (int i = 0; i < MAIN_SIZE; i++) {
                inventory.setItem(i, createEmptyPane());
            }

            inventory.setItem(4, createTitleItem("邮件列表", mails.size()));

            int unread = (int) mails.stream().filter(m -> !m.read() && !m.isExpired()).count();
            int unclaimed = (int) mails.stream().filter(m -> m.hasAttachments() && !m.isClaimed() && !m.isExpired()).count();
            inventory.setItem(49, createStatsItem(unread, unclaimed));

            int start = (page - 1) * MAIL_SLOTS_PER_PAGE;
            int end = Math.min(start + MAIL_SLOTS_PER_PAGE, mails.size());

            for (int i = start; i < end; i++) {
                int slot = i - start;
                inventory.setItem(slot, createMailItem(mails.get(i)));
            }

            if (page > 1) {
                inventory.setItem(PREV_PAGE, createPrevPageItem());
            }
            if (page < totalPages) {
                inventory.setItem(NEXT_PAGE, createNextPageItem());
            }

            inventory.setItem(CLOSE_SLOT, createCloseItem());
        }

        private void buildSendGuiContent(String recipient, String subject) {
            for (int i = 0; i < MAIN_SIZE; i++) {
                inventory.setItem(i, createEmptyPane());
            }

            inventory.setItem(RECIPIENT_SLOT, createRecipientItem(recipient != null ? recipient : ""));
            inventory.setItem(SUBJECT_SLOT, createSubjectItem(subject != null ? subject : "无主题"));
            inventory.setItem(ATTACHMENT_START - 1, createAttachmentHintItem());
            inventory.setItem(SEND_CONFIRM_SLOT, createSendConfirmItem());
            inventory.setItem(CANCEL_SLOT, createCancelItem());
        }

        private void buildMailDetailContent(MailAttachment mail) {
            for (int i = 0; i < MAIN_SIZE; i++) {
                inventory.setItem(i, createEmptyPane());
            }

            inventory.setItem(MAIL_INFO_SLOT, createMailTitleItem(mail));
            inventory.setItem(SENDER_SLOT, createSenderItem(mail.senderName()));
            inventory.setItem(20, createTimeItem(mail.sentAt(), mail.getRemainingDays()));
            inventory.setItem(21, createContentItem(mail.message()));

            if (mail.hasAttachments()) {
                inventory.setItem(22, createAttachmentInfoItem(mail));

                int slot = ATTACHMENT_DISPLAY_START;
                for (AttachmentItem item : mail.items()) {
                    if (slot > ATTACHMENT_END) break;
                    ItemStack displayItem = item.toItemStack();
                    if (displayItem != null) {
                        ItemMeta meta = displayItem.getItemMeta();
                        List<Component> lore = new ArrayList<>();
                        lore.add(Component.text(""));
                        lore.add(Component.text("数量: " + item.amount(), NamedTextColor.GRAY));
                        if (item.claimed()) {
                            lore.add(Component.text("已领取", NamedTextColor.GREEN));
                        }
                        meta.lore(lore);
                        displayItem.setItemMeta(meta);
                        inventory.setItem(slot++, displayItem);
                    }
                }
            } else {
                inventory.setItem(22, createNoAttachmentItem());
            }

            if (!mail.isClaimed() && mail.hasAttachments()) {
                inventory.setItem(CLAIM_BUTTON, createClaimButton());
            } else if (mail.hasAttachments()) {
                inventory.setItem(CLAIM_BUTTON, createClaimedButton());
            }

            inventory.setItem(DELETE_BUTTON, createDeleteButton());
            inventory.setItem(BACK_BUTTON, createBackButton());
            inventory.setItem(CLOSE_DETAIL_SLOT, createCloseItem());
        }

        // ==================== 物品创建方法 ====================

        private ItemStack createEmptyPane() {
            ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(" ", NamedTextColor.BLACK));
            item.setItemMeta(meta);
            return item;
        }

        private ItemStack createTitleItem(String title, int mailCount) {
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(title, NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("总邮件数: " + mailCount, NamedTextColor.GRAY));

            meta.lore(lore);
            item.setItemMeta(meta);
            return item;
        }

        private ItemStack createStatsItem(int unread, int unclaimed) {
            Material material = (unread > 0 || unclaimed > 0) ? Material.NETHER_STAR : Material.CHEST;
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();

            Component displayName;
            if (unread > 0 || unclaimed > 0) {
                displayName = Component.text("有新邮件!", NamedTextColor.RED).decoration(TextDecoration.BOLD, true);
            } else {
                displayName = Component.text("邮件统计", NamedTextColor.GREEN);
            }
            meta.displayName(displayName);

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("未读: " + unread, unread > 0 ? NamedTextColor.RED : NamedTextColor.GRAY));
            lore.add(Component.text("待领取附件: " + unclaimed, unclaimed > 0 ? NamedTextColor.GOLD : NamedTextColor.GRAY));

            meta.lore(lore);
            item.setItemMeta(meta);
            return item;
        }

        private ItemStack createMailItem(MailAttachment mail) {
            Material material = mail.read()
                ? (mail.hasAttachments() && !mail.isClaimed() ? Material.GOLD_INGOT : Material.PAPER)
                : (mail.hasAttachments() && !mail.isClaimed() ? Material.NETHER_STAR : Material.BOOK);

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();

            Component title = Component.text()
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text(mail.read() ? "已读" : "未读",
                    mail.read() ? NamedTextColor.GRAY : NamedTextColor.RED))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text(mail.subject(),
                    mail.read() ? NamedTextColor.GRAY : NamedTextColor.WHITE))
                .build();
            meta.displayName(title);

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("发件人: " + mail.senderName(), NamedTextColor.GRAY));
            lore.add(Component.text("时间: " + formatTimeAgo(mail.sentAt()), NamedTextColor.GRAY));
            lore.add(Component.text("剩余: " + mail.getRemainingDays() + " 天", NamedTextColor.DARK_GRAY));

            if (mail.hasAttachments()) {
                lore.add(Component.text(""));
                if (mail.isClaimed()) {
                    lore.add(Component.text("[附件] 已领取", NamedTextColor.GREEN));
                } else {
                    lore.add(Component.text("[附件] " + mail.items().size() + " 个物品", NamedTextColor.GOLD));
                }
            }

            lore.add(Component.text(""));
            lore.add(Component.text("点击查看详情", NamedTextColor.YELLOW));

            meta.lore(lore);
            item.setItemMeta(meta);
            return item;
        }

        private ItemStack createRecipientItem(String recipient) {
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("收件人", NamedTextColor.AQUA));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text(recipient.isEmpty() ? "未指定" : recipient, NamedTextColor.WHITE));
            lore.add(Component.text(""));

            meta.lore(lore);
            item.setItemMeta(meta);
            return item;
        }

        private ItemStack createSubjectItem(String subject) {
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("主题: " + subject, NamedTextColor.YELLOW));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("点击编辑主题", NamedTextColor.GRAY));

            meta.lore(lore);
            item.setItemMeta(meta);
            return item;
        }

        private ItemStack createAttachmentHintItem() {
            ItemStack item = new ItemStack(Material.CHEST);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("附件区域", NamedTextColor.GOLD));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("拖入物品添加附件", NamedTextColor.GRAY));
            lore.add(Component.text("最多 18 个物品", NamedTextColor.DARK_GRAY));

            meta.lore(lore);
            item.setItemMeta(meta);
            return item;
        }

        private ItemStack createSendConfirmItem() {
            ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("发送邮件", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("点击发送邮件", NamedTextColor.GREEN));

            meta.lore(lore);
            item.setItemMeta(meta);
            return item;
        }

        private ItemStack createCancelItem() {
            ItemStack item = new ItemStack(Material.BARRIER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("取消", NamedTextColor.RED));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("点击取消", NamedTextColor.GRAY));

            meta.lore(lore);
            item.setItemMeta(meta);
            return item;
        }

        private ItemStack createMailTitleItem(MailAttachment mail) {
            ItemStack item = new ItemStack(Material.BOOK);
            ItemMeta meta = item.getItemMeta();

            Component title = Component.text()
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text(mail.read() ? "已读" : "未读",
                    mail.read() ? NamedTextColor.GRAY : NamedTextColor.RED))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text(mail.subject(), NamedTextColor.GOLD))
                .build();
            meta.displayName(title);

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("过期时间: " + mail.getRemainingDays() + " 天后", NamedTextColor.GRAY));

            meta.lore(lore);
            item.setItemMeta(meta);
            return item;
        }

        private ItemStack createSenderItem(String senderName) {
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("发件人", NamedTextColor.AQUA));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text(senderName, NamedTextColor.WHITE));

            meta.lore(lore);
            item.setItemMeta(meta);
            return item;
        }

        private ItemStack createTimeItem(long sentAt, long remainingDays) {
            ItemStack item = new ItemStack(Material.CLOCK);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("发送时间", NamedTextColor.AQUA));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text(formatTimeAgo(sentAt), NamedTextColor.WHITE));
            lore.add(Component.text("剩余: " + remainingDays + " 天", NamedTextColor.GRAY));

            meta.lore(lore);
            item.setItemMeta(meta);
            return item;
        }

        private ItemStack createContentItem(String content) {
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("邮件内容", NamedTextColor.WHITE));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            String displayContent = content.isEmpty() ? "(无内容)" : (content.length() > 50 ? content.substring(0, 50) + "..." : content);
            lore.add(Component.text(displayContent, NamedTextColor.GRAY));

            meta.lore(lore);
            item.setItemMeta(meta);
            return item;
        }

        private ItemStack createAttachmentInfoItem(MailAttachment mail) {
            ItemStack item = new ItemStack(Material.CHEST);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("附件 (" + mail.items().size() + ")", NamedTextColor.GOLD));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("共 " + mail.items().size() + " 个物品", NamedTextColor.GRAY));
            if (mail.isClaimed()) {
                lore.add(Component.text("状态: 已领取", NamedTextColor.GREEN));
            } else {
                lore.add(Component.text("状态: 待领取", NamedTextColor.YELLOW));
            }

            meta.lore(lore);
            item.setItemMeta(meta);
            return item;
        }

        private ItemStack createNoAttachmentItem() {
            ItemStack item = new ItemStack(Material.BARRIER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("无附件", NamedTextColor.GRAY));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("此邮件没有附件", NamedTextColor.DARK_GRAY));

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
            lore.add(Component.text("[注意] 确保背包有足够空间", NamedTextColor.RED));

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
            lore.add(Component.text("点击删除此邮件", NamedTextColor.YELLOW));
            lore.add(Component.text("此操作不可撤销", NamedTextColor.RED));

            meta.lore(lore);
            item.setItemMeta(meta);
            return item;
        }

        private ItemStack createBackButton() {
            ItemStack item = new ItemStack(Material.ARROW);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("返回列表", NamedTextColor.YELLOW));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("返回邮件列表", NamedTextColor.GRAY));

            meta.lore(lore);
            item.setItemMeta(meta);
            return item;
        }

        private ItemStack createCloseItem() {
            ItemStack item = new ItemStack(Material.BARRIER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("关闭", NamedTextColor.RED));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("点击关闭", NamedTextColor.GRAY));

            meta.lore(lore);
            item.setItemMeta(meta);
            return item;
        }

        private ItemStack createPrevPageItem() {
            ItemStack item = new ItemStack(Material.ARROW);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("上一页", NamedTextColor.YELLOW));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("点击查看上一页", NamedTextColor.GRAY));

            meta.lore(lore);
            item.setItemMeta(meta);
            return item;
        }

        private ItemStack createNextPageItem() {
            ItemStack item = new ItemStack(Material.ARROW);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("下一页", NamedTextColor.YELLOW));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("点击查看下一页", NamedTextColor.GRAY));

            meta.lore(lore);
            item.setItemMeta(meta);
            return item;
        }

        private String formatTimeAgo(long timestamp) {
            long diff = System.currentTimeMillis() - timestamp;
            long seconds = diff / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;

            if (days > 0) {
                return days + "天前";
            } else if (hours > 0) {
                return hours + "小时前";
            } else if (minutes > 0) {
                return minutes + "分钟前";
            } else {
                return "刚刚";
            }
        }
    }

    // ==================== 事件处理 ====================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AttachmentGuiBuilder builder)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot >= 54) {
            return;
        }

        event.setCancelled(true);

        // 获取 GUI 标题
        String title = event.getView().getTitle();

        if (title.contains("邮件列表")) {
            handleMailListClick(player, builder, slot, title);
        } else if (title.contains("发送邮件")) {
            handleSendGuiClick(player, builder, slot);
        } else if (title.contains("邮件详情")) {
            handleMailDetailClick(player, builder, slot, title);
        }
    }

    private void handleMailListClick(Player player, AttachmentGuiBuilder builder, int slot, String title) {
        List<MailAttachment> mails = service.getPlayerMails(player.getUniqueId());

        int currentPage = 1;
        try {
            String pageStr = title.replaceAll(".*\\(", "").replaceAll("/.*", "");
            currentPage = Integer.parseInt(pageStr.trim());
        } catch (NumberFormatException e) {
            // 使用默认值 1
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) mails.size() / MAIL_SLOTS_PER_PAGE));

        if (slot >= MAIL_LIST_START && slot <= MAIL_LIST_END) {
            int mailIndex = (currentPage - 1) * MAIL_SLOTS_PER_PAGE + slot;
            if (mailIndex < mails.size()) {
                MailAttachment mail = mails.get(mailIndex);
                if (!mail.isExpired()) {
                    openMailDetailGui(player, service, mail.id());
                }
            }
        } else if (slot == PREV_PAGE && currentPage > 1) {
            openMailListGui(player, service, currentPage - 1);
        } else if (slot == NEXT_PAGE && currentPage < totalPages) {
            openMailListGui(player, service, currentPage + 1);
        } else if (slot == CLOSE_SLOT) {
            player.closeInventory();
        }
    }

    private void handleSendGuiClick(Player player, AttachmentGuiBuilder builder, int slot) {
        if (slot == CANCEL_SLOT) {
            player.closeInventory();
            player.sendMessage(Component.text("[邮件] ").color(NamedTextColor.YELLOW)
                .append(Component.text("邮件发送已取消", NamedTextColor.WHITE)));
        } else if (slot == SEND_CONFIRM_SLOT) {
            String recipient = pendingRecipient.getOrDefault(player.getUniqueId(), "");
            String subject = pendingSubject.getOrDefault(player.getUniqueId(), "无主题");
            String message = pendingMessage.getOrDefault(player.getUniqueId(), "");
            List<ItemStack> attachments = pendingAttachments.getOrDefault(player.getUniqueId(), new ArrayList<>());

            if (recipient.isEmpty()) {
                player.sendMessage(Component.text("[邮件] ").color(NamedTextColor.RED)
                    .append(Component.text("请指定收件人", NamedTextColor.WHITE)));
                return;
            }

            boolean success = service.sendMailWithAttachment(player, recipient, subject, message, attachments);

            if (success) {
                player.sendMessage(Component.text("[邮件] ").color(NamedTextColor.GREEN)
                    .append(Component.text("邮件已发送给 " + recipient, NamedTextColor.WHITE)));
            } else {
                player.sendMessage(Component.text("[邮件] ").color(NamedTextColor.RED)
                    .append(Component.text("发送失败，收件人不存在", NamedTextColor.WHITE)));
            }

            pendingRecipient.remove(player.getUniqueId());
            pendingSubject.remove(player.getUniqueId());
            pendingMessage.remove(player.getUniqueId());
            pendingAttachments.remove(player.getUniqueId());

            player.closeInventory();
        }
    }

    private void handleMailDetailClick(Player player, AttachmentGuiBuilder builder, int slot, String title) {
        String mailIdStr = title.replaceAll(".*邮件详情 - ", "").replaceAll("[^a-zA-Z0-9-]", "");
        UUID mailId = null;
        try {
            mailId = UUID.fromString(mailIdStr);
        } catch (IllegalArgumentException e) {
            // 无效的UUID，忽略
        }

        if (mailId == null) {
            return;
        }

        if (slot == CLAIM_BUTTON) {
            AttachmentClaimResult result = service.claimAttachment(player, mailId);

            Component prefix = Component.text("[附件] ").color(
                result.success() ? NamedTextColor.GREEN : NamedTextColor.RED);
            player.sendMessage(prefix.append(Component.text(result.message(), NamedTextColor.WHITE)));

            if (result.success()) {
                player.sendMessage(Component.text("已领取 " + result.claimedCount() + " 个物品", NamedTextColor.GREEN));

                if (!result.overflowItems().isEmpty()) {
                    player.sendMessage(Component.text("背包空间不足，" + result.overflowItems().size() + " 个物品被返还", NamedTextColor.YELLOW));
                }

                openMailDetailGui(player, service, mailId);
            }
        } else if (slot == DELETE_BUTTON) {
            boolean deleted = service.deleteMail(player, mailId);

            if (deleted) {
                player.sendMessage(Component.text("[邮件] ").color(NamedTextColor.GREEN)
                    .append(Component.text("邮件已删除", NamedTextColor.WHITE)));
                player.closeInventory();
            }
        } else if (slot == BACK_BUTTON) {
            int currentPage = playerPages.getOrDefault(player.getUniqueId(), 1);
            openMailListGui(player, service, currentPage);
        } else if (slot == CLOSE_DETAIL_SLOT) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        playerPages.remove(playerId);
        viewingMail.remove(playerId);
        pendingRecipient.remove(playerId);
        pendingSubject.remove(playerId);
        pendingMessage.remove(playerId);
        pendingAttachments.remove(playerId);
    }
}
