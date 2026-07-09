package dev.starcore.starcore.event.random;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import dev.starcore.starcore.event.random.RandomEvent.EventResponse;

/**
 * 随机事件响应菜单
 * 显示事件响应选项供玩家选择
 */
public class RandomEventResponseMenu implements InventoryHolder {

    private static final String MENU_TITLE = "§6§l[事件响应]§r ";
    private static final int RESPONSES_PER_PAGE = 7;
    private static final int[] RESPONSE_SLOTS = {10, 12, 14, 16, 28, 30, 32};

    private final RandomEvent event;
    private final Player player;
    private final RandomEventService eventService;
    private final Map<String, EventResponse> responses;
    private final Inventory inventory;
    private final int page;
    private final List<String> responseKeys;
    private final UUID eventChainId;

    // 待选择的需求信息
    private final Map<String, Map<String, Object>> pendingSelections;

    private RandomEventResponseMenu(RandomEvent event, Player player,
                                    RandomEventService eventService,
                                    int page, UUID eventChainId) {
        this.event = event;
        this.player = player;
        this.eventService = eventService;
        this.responses = new HashMap<>(event.getResponses());
        this.responseKeys = new ArrayList<>(responses.keySet());
        this.page = page;
        this.eventChainId = eventChainId;
        this.pendingSelections = new HashMap<>();

        String title = MENU_TITLE + event.getName();
        if (page > 0) {
            title += " (§e第 " + (page + 1) + " 页§r)";
        }
        this.inventory = Bukkit.createInventory(this, 54, Component.text(title));
    }

    /**
     * 创建响应菜单
     */
    public static RandomEventResponseMenu create(RandomEvent event, Player player,
                                                   RandomEventService eventService,
                                                   UUID eventChainId) {
        return new RandomEventResponseMenu(event, player, eventService, 0, eventChainId);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public RandomEvent getEvent() {
        return event;
    }

    public Player getPlayer() {
        return player;
    }

    public Map<String, Map<String, Object>> getPendingSelections() {
        return pendingSelections;
    }

    public void buildMenu() {
        // 清空菜单
        inventory.clear();

        // 标题物品
        inventory.setItem(4, createTitleItem());

        // 显示响应选项
        int startIndex = page * RESPONSES_PER_PAGE;
        int endIndex = Math.min(startIndex + RESPONSES_PER_PAGE, responseKeys.size());

        for (int i = startIndex; i < endIndex; i++) {
            String responseId = responseKeys.get(i);
            RandomEvent.EventResponse response = responses.get(responseId);
            int slot = RESPONSE_SLOTS[i - startIndex];
            inventory.setItem(slot, createResponseItem(responseId, response));
        }

        // 导航按钮
        addNavigationButtons();

        // 如果没有响应选项，显示提示
        if (responses.isEmpty()) {
            inventory.setItem(22, createNoResponseItem());
        }
    }

    /**
     * 创建标题物品
     */
    private ItemStack createTitleItem() {
        ItemStack item = new ItemStack(Material.BELL);
        ItemMeta meta = item.getItemMeta();

        Component displayName = Component.text(event.getName())
            .color(NamedTextColor.GOLD);
        meta.displayName(displayName);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("§7" + event.getDescription()));
        lore.add(Component.text(""));
        lore.add(Component.text("§e选择你的应对方式：", NamedTextColor.YELLOW));
        lore.add(Component.text(""));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 创建响应选项物品
     */
    private ItemStack createResponseItem(String responseId, RandomEvent.EventResponse response) {
        Material material = getResponseMaterial(response);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // 检查是否满足需求
        boolean meetsRequirements = response.meetsRequirements(player);
        TextColor nameColor = meetsRequirements ? NamedTextColor.GREEN : NamedTextColor.RED;

        // 名称
        Component displayName = Component.text(response.getName())
            .color(nameColor);
        meta.displayName(displayName);

        // 描述
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("§7" + response.getDescription()));
        lore.add(Component.text(""));

        // 显示需求
        addRequirementsLore(lore, response.getRequirements(), meetsRequirements);

        // 显示效果预览
        addEffectPreviewLore(lore, response.getEffects());

        lore.add(Component.text(""));
        if (meetsRequirements) {
            lore.add(Component.text("§a[点击选择]", NamedTextColor.GREEN));
        } else {
            lore.add(Component.text("§c[需求未满足]", NamedTextColor.RED));
        }

        meta.lore(lore);
        item.setItemMeta(meta);

        // 设置自定义模型数据标识可点击
        return item;
    }

    /**
     * 根据响应类型获取材质
     */
    private Material getResponseMaterial(RandomEvent.EventResponse response) {
        Map<String, Object> requirements = response.getRequirements();

        if (requirements.containsKey("money")) {
            double amount = ((Number) requirements.get("money")).doubleValue();
            if (amount > 1000) {
                return Material.GOLD_BLOCK;
            } else if (amount > 100) {
                return Material.GOLD_INGOT;
            } else {
                return Material.GOLD_NUGGET;
            }
        }

        if (requirements.containsKey("permission")) {
            return Material.COMMAND_BLOCK;
        }

        if (requirements.containsKey("items")) {
            return Material.CHEST;
        }

        // 默认材质根据效果类型
        for (EventEffect effect : response.getEffects()) {
            switch (effect.getType()) {
                case "PLAYER":
                    return Material.BREAD;
                case "ECONOMY":
                    return Material.GOLD_INGOT;
                case "CROP":
                    return Material.WHEAT;
                case "BUILDING":
                    return Material.BRICKS;
                case "SPAWN":
                    return Material.SPAWNER;
                default:
                    break;
            }
        }

        return Material.PAPER;
    }

    /**
     * 添加需求提示
     */
    private void addRequirementsLore(List<Component> lore, Map<String, Object> requirements, boolean meetsAll) {
        if (requirements == null || requirements.isEmpty()) {
            lore.add(Component.text("§7需求：无", NamedTextColor.GRAY));
            return;
        }

        lore.add(Component.text("§e需求：", NamedTextColor.YELLOW));

        if (requirements.containsKey("money")) {
            double money = ((Number) requirements.get("money")).doubleValue();
            boolean hasMoney = eventService.hasPlayerMoney(player, money);
            String color = hasMoney ? "§a" : "§c";
            lore.add(Component.text(color + "  金币: " + String.format("%.2f", money)));
        }

        if (requirements.containsKey("permission")) {
            String perm = (String) requirements.get("permission");
            boolean hasPerm = player.hasPermission(perm);
            String color = hasPerm ? "§a" : "§c";
            lore.add(Component.text(color + "  权限: " + perm));
        }

        if (requirements.containsKey("items")) {
            lore.add(Component.text("  §6物品: "));
            Object itemsObj = requirements.get("items");
            if (itemsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) itemsObj;
                for (Map<String, Object> item : items) {
                    String itemName = (String) item.getOrDefault("name", "未知物品");
                    int amount = ((Number) item.getOrDefault("amount", 1)).intValue();
                    boolean hasItem = eventService.hasPlayerItems(player, item);
                    String color = hasItem ? "§a" : "§c";
                    lore.add(Component.text(color + "    " + itemName + " x" + amount));
                }
            }
        }
    }

    /**
     * 添加效果预览
     */
    private void addEffectPreviewLore(List<Component> lore, List<EventEffect> effects) {
        if (effects == null || effects.isEmpty()) {
            return;
        }

        lore.add(Component.text("§b效果预览：", NamedTextColor.AQUA));
        for (EventEffect effect : effects) {
            lore.add(Component.text("§7  • " + effect.getDescription()));
        }
    }

    /**
     * 创建无响应选项提示
     */
    private ItemStack createNoResponseItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("无响应选项", NamedTextColor.GRAY));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("§7此事件没有可选择的响应", NamedTextColor.GRAY));
        lore.add(Component.text("§7事件将自动执行", NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 添加导航按钮
     */
    private void addNavigationButtons() {
        // 上一页
        if (page > 0) {
            inventory.setItem(45, createPrevPageButton());
        }

        // 关闭按钮
        inventory.setItem(49, createCloseButton());

        // 下一页
        int totalPages = (responseKeys.size() + RESPONSES_PER_PAGE - 1) / RESPONSES_PER_PAGE;
        if (page < totalPages - 1) {
            inventory.setItem(53, createNextPageButton());
        }
    }

    private ItemStack createPrevPageButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("上一页", NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("§7第 " + (page + 1) + " / " +
            ((responseKeys.size() + RESPONSES_PER_PAGE - 1) / RESPONSES_PER_PAGE) + " 页"));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNextPageButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("下一页", NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("§7第 " + (page + 1) + " / " +
            ((responseKeys.size() + RESPONSES_PER_PAGE - 1) / RESPONSES_PER_PAGE) + " 页"));

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
        lore.add(Component.text("§7关闭此菜单", NamedTextColor.GRAY));
        lore.add(Component.text("§7事件将自动执行", NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 获取指定槽位的响应ID
     */
    public String getResponseIdAtSlot(int slot) {
        int index = -1;
        for (int i = 0; i < RESPONSE_SLOTS.length; i++) {
            if (RESPONSE_SLOTS[i] == slot) {
                index = page * RESPONSES_PER_PAGE + i;
                break;
            }
        }

        if (index >= 0 && index < responseKeys.size()) {
            return responseKeys.get(index);
        }
        return null;
    }

    /**
     * 创建下一页菜单
     */
    public RandomEventResponseMenu createNextPage() {
        if (page < (responseKeys.size() + RESPONSES_PER_PAGE - 1) / RESPONSES_PER_PAGE - 1) {
            return new RandomEventResponseMenu(event, player, eventService, page + 1, eventChainId);
        }
        return null;
    }

    /**
     * 创建上一页菜单
     */
    public RandomEventResponseMenu createPrevPage() {
        if (page > 0) {
            return new RandomEventResponseMenu(event, player, eventService, page - 1, eventChainId);
        }
        return null;
    }

    /**
     * 获取事件链ID
     */
    public UUID getEventChainId() {
        return eventChainId;
    }
}
