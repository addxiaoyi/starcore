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
 * 邮件列表 GUI
 */
public final class MailListGui implements InventoryHolder {

    public static final int PAGE_SIZE = 36; // 6行，每行6个邮件
    public static final int TOTAL_SIZE = 54;

    private final Player player;
    private final MailService mailService;
    private final Inventory inventory;

    private int currentPage;
    private final List<Mail> allMails;

    public MailListGui(Player player, MailService mailService) {
        this.player = player;
        this.mailService = mailService;
        this.currentPage = 0;
        this.allMails = new ArrayList<>(mailService.getPlayerMails(player.getUniqueId()));

        // 过滤未过期的邮件
        allMails.removeIf(Mail::isExpired);

        this.inventory = Bukkit.createInventory(this, TOTAL_SIZE,
            Component.text("邮箱 - 第 " + (currentPage + 1) + " 页", NamedTextColor.GOLD));

        buildMenu();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }

    public List<Mail> getAllMails() {
        return allMails;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    /**
     * 构建菜单
     */
    private void buildMenu() {
        inventory.clear();

        // 标题区域
        inventory.setItem(4, createTitleItem());

        // 邮件列表
        int startIndex = currentPage * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, allMails.size());

        for (int i = startIndex; i < endIndex; i++) {
            int slot = (i - startIndex) + 9; // 从第二行开始
            if (slot < 45) {
                inventory.setItem(slot, allMails.get(i).toPreviewItem());
            }
        }

        // 导航按钮
        addNavigationButtons();
    }

    /**
     * 添加导航按钮
     */
    private void addNavigationButtons() {
        // 刷新按钮
        inventory.setItem(45, createRefreshButton());

        // 发送新邮件按钮
        inventory.setItem(48, createNewMailButton());

        // 上一页按钮
        if (currentPage > 0) {
            inventory.setItem(51, createPreviousPageButton());
        } else {
            inventory.setItem(51, createEmptyButton());
        }

        // 下一页按钮
        int totalPages = (int) Math.ceil((double) allMails.size() / PAGE_SIZE);
        if (currentPage < totalPages - 1) {
            inventory.setItem(52, createNextPageButton());
        } else {
            inventory.setItem(52, createEmptyButton());
        }

        // 关闭按钮
        inventory.setItem(53, createCloseButton());
    }

    // ==================== 物品创建方法 ====================

    private ItemStack createTitleItem() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();

        int unreadCount = mailService.getUnreadCount(player.getUniqueId());
        String title = unreadCount > 0
            ? "邮箱 (" + unreadCount + " 封未读)"
            : "邮箱";

        meta.displayName(Component.text(title, NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("=== 邮件统计 ===", NamedTextColor.GOLD));
        lore.add(Component.text("总邮件: " + allMails.size(), NamedTextColor.GRAY));
        lore.add(Component.text("未读: " + unreadCount, unreadCount > 0 ? NamedTextColor.RED : NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("点击邮件查看详情", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRefreshButton() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("刷新", NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("点击刷新邮件列表", NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNewMailButton() {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("发送邮件", NamedTextColor.GREEN));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("给其他玩家发送邮件", NamedTextColor.GRAY));
        lore.add(Component.text("支持附件", NamedTextColor.GOLD));
        lore.add(Component.text(""));
        lore.add(Component.text("点击打开发送界面", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPreviousPageButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("上一页", NamedTextColor.AQUA));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("当前第 " + (currentPage + 1) + " 页", NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("点击查看上一页", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNextPageButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("下一页", NamedTextColor.AQUA));

        int totalPages = (int) Math.ceil((double) allMails.size() / PAGE_SIZE);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("当前第 " + (currentPage + 1) + "/" + totalPages + " 页", NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("点击查看下一页", NamedTextColor.YELLOW));

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
        lore.add(Component.text("点击关闭邮箱", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createEmptyButton() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" ", NamedTextColor.BLACK));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 上一页
     */
    public void previousPage() {
        if (currentPage > 0) {
            currentPage--;
            rebuild();
        }
    }

    /**
     * 下一页
     */
    public void nextPage() {
        int totalPages = (int) Math.ceil((double) allMails.size() / PAGE_SIZE);
        if (currentPage < totalPages - 1) {
            currentPage++;
            rebuild();
        }
    }

    /**
     * 刷新
     */
    public void refresh() {
        allMails.clear();
        allMails.addAll(mailService.getPlayerMails(player.getUniqueId()));
        allMails.removeIf(Mail::isExpired);
        currentPage = 0;
        rebuild();
    }

    /**
     * 重建界面
     */
    private void rebuild() {
        // 更新标题
        int unreadCount = mailService.getUnreadCount(player.getUniqueId());
        String title = unreadCount > 0
            ? "邮箱 (" + unreadCount + " 封未读)"
            : "邮箱";

        // 创建新库存
        inventory.setContents(Bukkit.createInventory(this, TOTAL_SIZE,
            Component.text(title + " - 第 " + (currentPage + 1) + " 页", NamedTextColor.GOLD)).getContents());
        buildMenu();
    }

    /**
     * 从槽位获取邮件索引
     */
    public int getMailIndexFromSlot(int slot) {
        if (slot >= 9 && slot < 45) {
            int index = slot - 9 + (currentPage * PAGE_SIZE);
            if (index >= 0 && index < allMails.size()) {
                return index;
            }
        }
        return -1;
    }

    /**
     * 从槽位获取邮件
     */
    public Mail getMailFromSlot(int slot) {
        int index = getMailIndexFromSlot(slot);
        if (index >= 0 && index < allMails.size()) {
            return allMails.get(index);
        }
        return null;
    }

    /**
     * 获取槽位对应的动作
     */
    public static MailListAction getActionFromSlot(int slot) {
        return switch (slot) {
            case 45 -> MailListAction.REFRESH;
            case 48 -> MailListAction.NEW_MAIL;
            case 51 -> MailListAction.PREV_PAGE;
            case 52 -> MailListAction.NEXT_PAGE;
            case 53 -> MailListAction.CLOSE;
            default -> (slot >= 9 && slot < 45) ? MailListAction.VIEW_MAIL : MailListAction.NONE;
        };
    }

    /**
     * 邮件列表动作枚举
     */
    public enum MailListAction {
        NONE,
        REFRESH,
        NEW_MAIL,
        PREV_PAGE,
        NEXT_PAGE,
        VIEW_MAIL,
        CLOSE
    }
}
