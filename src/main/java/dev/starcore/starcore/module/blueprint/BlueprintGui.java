package dev.starcore.starcore.module.blueprint;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.blueprint.listener.BlueprintListener;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 蓝图 GUI 管理器
 * 提供蓝图浏览、选择、操作的可视化界面
 */
public final class BlueprintGui implements InventoryHolder {

    /**
     * 元数据编辑字段枚举
     */
    public enum MetadataField {
        NAME("名称", Material.NAME_TAG),
        DESCRIPTION("描述", Material.PAPER),
        CATEGORY("分类", Material.CHEST),
        PUBLIC("公开蓝图", Material.LIME_CONCRETE),
        SHARED("允许共享", Material.BOOK);

        private final String displayName;
        private final Material icon;

        MetadataField(String displayName, Material icon) {
            this.displayName = displayName;
            this.icon = icon;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Material getIcon() {
            return icon;
        }
    }

    /**
     * GUI 页面类型
     */
    public enum MenuPage {
        MAIN,           // 主菜单 - 蓝图列表
        CATEGORIES,     // 分类浏览
        BLUEPRINT_INFO, // 蓝图详情
        CLIPBOARD,      // 剪贴板管理
        SETTINGS,       // 设置
        METADATA_EDIT   // 元数据编辑
    }

    private static final int PAGE_SIZE = 45; // 5行9列，保留最后一排导航
    private static final int ITEMS_PER_PAGE = 45;

    // 静态 listener 引用（用于搜索输入）
    private static BlueprintListener listener;

    /**
     * 设置静态 listener 引用
     */
    public static void setListener(BlueprintListener l) {
        listener = l;
    }

    private final Player player;
    private final MessageService messages;
    private final BlueprintService service;

    private Inventory inventory;
    private Component currentTitle;
    private MenuPage currentPage;
    private int currentPageIndex;
    private String currentCategory;
    private Blueprint currentBlueprint;
    private Consumer<Blueprint> onBlueprintSelected;

    private List<BlueprintTypes.BlueprintMetadata> displayedBlueprints;
    private boolean awaitingSearchInput;
    private boolean isFirstBuild = true;

    // 元数据编辑状态
    private boolean awaitingMetadataInput;
    private MetadataField editingField;

    /**
     * 创建蓝图 GUI
     */
    public BlueprintGui(Player player, MessageService messages, BlueprintService service) {
        this.player = player;
        this.messages = messages;
        this.service = service;
        this.currentPage = MenuPage.MAIN;
        this.currentPageIndex = 0;
        this.displayedBlueprints = new ArrayList<>();
        this.isFirstBuild = true;

        loadPlayerBlueprints();
        buildMenu();
    }

    /**
     * 创建蓝图 GUI（带回调）
     */
    public static BlueprintGui createForSelection(Player player, MessageService messages,
                                                   BlueprintService service,
                                                   Consumer<Blueprint> onSelect) {
        BlueprintGui gui = new BlueprintGui(player, messages, service);
        gui.onBlueprintSelected = onSelect;
        return gui;
    }

    /**
     * 创建蓝图 GUI（带分类过滤）
     */
    public static BlueprintGui createWithCategory(Player player, MessageService messages,
                                                  BlueprintService service, String category) {
        BlueprintGui gui = new BlueprintGui(player, messages, service);
        gui.currentCategory = category;
        gui.loadBlueprintsByCategory(category);
        return gui;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /**
     * 加载玩家蓝图
     */
    private void loadPlayerBlueprints() {
        // 使用 LinkedHashSet 基于蓝图 ID 去重，保持插入顺序
        Set<BlueprintTypes.BlueprintMetadata> uniqueBlueprints = new LinkedHashSet<>();

        // 添加玩家蓝图
        uniqueBlueprints.addAll(service.getPlayerBlueprints(player.getUniqueId()));

        // 添加公开蓝图（自动去重，相同 ID 的蓝图不会重复添加）
        uniqueBlueprints.addAll(service.getPublicBlueprints());

        this.displayedBlueprints = new ArrayList<>(uniqueBlueprints);
    }

    /**
     * 按分类加载蓝图
     */
    private void loadBlueprintsByCategory(String categoryId) {
        this.displayedBlueprints = service.getBlueprintsByCategory(categoryId);
    }

    /**
     * 搜索蓝图
     */
    private void searchBlueprints(String query) {
        this.displayedBlueprints = service.searchBlueprints(query);
        this.currentPageIndex = 0;
    }

    /**
     * 打开剪贴板页面
     */
    public void openClipboardPage() {
        this.currentPage = MenuPage.CLIPBOARD;
        buildMenu();
    }

    /**
     * 打开分类页面
     */
    public void openCategoriesPage() {
        this.currentPage = MenuPage.CATEGORIES;
        buildMenu();
    }

    /**
     * 打开搜索输入模式
     * 点击搜索按钮后，等待玩家在聊天框输入搜索关键词
     */
    public void openSearchInput() {
        this.awaitingSearchInput = true;
        player.closeInventory();

        // 注册到 listener 以处理聊天输入
        if (listener != null) {
            listener.registerSearchInput(player.getUniqueId(), this);
        }

        player.sendMessage(Component.text()
            .append(Component.text("[蓝图] ", NamedTextColor.GOLD))
            .append(Component.text("请在聊天框输入搜索关键词，或输入 ", NamedTextColor.GRAY))
            .append(Component.text("cancel ", NamedTextColor.RED))
            .append(Component.text("取消搜索", NamedTextColor.GRAY)));
    }

    /**
     * 处理聊天输入的搜索
     * 由 BlueprintListener 调用
     */
    public boolean handleSearchInput(String input) {
        if (!awaitingSearchInput) {
            return false;
        }

        this.awaitingSearchInput = false;

        if (input == null || input.trim().isEmpty()) {
            player.sendMessage(Component.text()
                .append(Component.text("[蓝图] ", NamedTextColor.YELLOW))
                .append(Component.text("搜索已取消", NamedTextColor.GRAY)));
            return true;
        }

        String query = input.trim();

        // 取消搜索
        if (query.equalsIgnoreCase("cancel")) {
            player.sendMessage(Component.text()
                .append(Component.text("[蓝图] ", NamedTextColor.YELLOW))
                .append(Component.text("搜索已取消", NamedTextColor.GRAY)));
            return true;
        }

        // 执行搜索
        searchBlueprints(query);

        player.sendMessage(Component.text()
            .append(Component.text("[蓝图] ", NamedTextColor.GOLD))
            .append(Component.text("搜索 \"", NamedTextColor.GRAY))
            .append(Component.text(query, NamedTextColor.AQUA))
            .append(Component.text("\" 结果: " + displayedBlueprints.size() + " 个蓝图", NamedTextColor.GRAY)));

        // 重新打开GUI
        player.openInventory(this.inventory);
        return true;
    }

    /**
     * 检查是否正在等待搜索输入
     */
    public boolean isAwaitingSearchInput() {
        return awaitingSearchInput;
    }

    /**
     * 打开蓝图详情
     */
    public void openBlueprintDetail(BlueprintTypes.BlueprintMetadata metadata) {
        service.loadBlueprint(metadata.id()).ifPresent(bp -> {
            this.currentBlueprint = bp;
            this.currentPage = MenuPage.BLUEPRINT_INFO;
            buildMenu();
        });
    }

    /**
     * 切换页面
     */
    public void nextPage() {
        int maxPage = (displayedBlueprints.size() - 1) / ITEMS_PER_PAGE;
        if (currentPageIndex < maxPage) {
            currentPageIndex++;
            buildMenu();
        }
    }

    public void previousPage() {
        if (currentPageIndex > 0) {
            currentPageIndex--;
            buildMenu();
        }
    }

    /**
     * 处理点击事件
     */
    public void handleClick(int slot) {
        switch (currentPage) {
            case MAIN -> handleMainClick(slot);
            case CATEGORIES -> handleCategoriesClick(slot);
            case BLUEPRINT_INFO -> handleBlueprintInfoClick(slot);
            case CLIPBOARD -> handleClipboardClick(slot);
            case SETTINGS -> handleSettingsClick(slot);
            case METADATA_EDIT -> handleMetadataEditClick(slot);
        }
    }

    /**
     * 构建菜单
     */
    private void buildMenu() {
        // 如果需要重新创建库存（例如首次创建或标题变化），则重新创建
        if (isFirstBuild || currentTitle == null) {
            Component title = determineTitle();
            this.inventory = Bukkit.createInventory(this, 54, title);
            this.currentTitle = title;
            this.isFirstBuild = false;
        } else {
            inventory.clear();
        }

        switch (currentPage) {
            case MAIN -> buildMainMenu();
            case CATEGORIES -> buildCategoriesMenu();
            case BLUEPRINT_INFO -> buildBlueprintInfoMenu();
            case CLIPBOARD -> buildClipboardMenu();
            case SETTINGS -> buildSettingsMenu();
            case METADATA_EDIT -> buildMetadataEditMenu();
        }

        // 添加导航栏
        addNavigationBar();
    }

    /**
     * 确定当前页面的标题
     */
    private Component determineTitle() {
        return switch (currentPage) {
            case MAIN -> Component.text("蓝图文库 - 我的蓝图", NamedTextColor.GOLD);
            case CATEGORIES -> Component.text("蓝图文库 - 分类", NamedTextColor.GOLD);
            case BLUEPRINT_INFO -> currentBlueprint != null
                ? Component.text("蓝图: " + currentBlueprint.getName(), NamedTextColor.GOLD)
                : Component.text("蓝图文库 - 详情", NamedTextColor.GOLD);
            case CLIPBOARD -> Component.text("蓝图文库 - 剪贴板", NamedTextColor.GOLD);
            case SETTINGS -> Component.text("蓝图文库 - 设置", NamedTextColor.GOLD);
            case METADATA_EDIT -> Component.text("编辑蓝图信息", NamedTextColor.GOLD);
        };
    }

    /**
     * 构建主菜单
     */
    private void buildMainMenu() {
        // 标题行
        updateTitle(Component.text("蓝图文库 - 我的蓝图", NamedTextColor.GOLD));

        // 顶部工具栏
        inventory.setItem(45, createCategoryButton());
        inventory.setItem(46, createSearchButton());
        inventory.setItem(47, createClipboardButton());
        inventory.setItem(48, createSettingsButton());

        // 蓝图列表
        int start = currentPageIndex * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, displayedBlueprints.size());

        for (int i = start; i < end; i++) {
            BlueprintTypes.BlueprintMetadata bp = displayedBlueprints.get(i);
            int slot = i - start;
            inventory.setItem(slot, createBlueprintItem(bp));
        }

        // 如果没有蓝图，显示提示
        if (displayedBlueprints.isEmpty()) {
            inventory.setItem(22, createEmptyPlaceholder());
        }
    }

    /**
     * 构建分类菜单
     */
    private void buildCategoriesMenu() {
        updateTitle(Component.text("蓝图文库 - 分类", NamedTextColor.GOLD));

        List<BlueprintCategory> categories = service.getAllCategories();

        // 添加返回按钮
        inventory.setItem(45, createBackButton());

        for (int i = 0; i < Math.min(categories.size(), ITEMS_PER_PAGE); i++) {
            BlueprintCategory cat = categories.get(i);
            inventory.setItem(i, createCategoryItem(cat));
        }
    }

    /**
     * 构建蓝图详情菜单
     */
    private void buildBlueprintInfoMenu() {
        if (currentBlueprint == null) {
            buildMainMenu();
            return;
        }

        updateTitle(Component.text("蓝图: " + currentBlueprint.getName(), NamedTextColor.GOLD));

        // 蓝图预览
        inventory.setItem(4, createBlueprintPreviewItem());

        // 信息区域
        inventory.setItem(19, createInfoItem("author", "作者", currentBlueprint.getAuthorName()));
        inventory.setItem(20, createInfoItem("size", "尺寸", getBlueprintSize()));
        inventory.setItem(21, createInfoItem("blocks", "方块数", String.valueOf(currentBlueprint.getBlockCount())));
        inventory.setItem(22, createInfoItem("materials", "材料种类", String.valueOf(currentBlueprint.getPalette().getUniqueMaterialCount())));

        // 操作按钮
        inventory.setItem(29, createPasteButton());
        inventory.setItem(30, createLoadToClipboardButton());
        inventory.setItem(31, createRotateButton());
        inventory.setItem(32, createMirrorButton());

        // 元数据按钮
        inventory.setItem(33, createEditButton());

        // 返回按钮
        inventory.setItem(45, createBackButton());
    }

    /**
     * 构建剪贴板菜单
     */
    private void buildClipboardMenu() {
        updateTitle(Component.text("蓝图文库 - 剪贴板", NamedTextColor.GOLD));

        // 获取剪贴板信息
        service.getClipboard(player.getUniqueId()).ifPresentOrElse(
            clipboard -> {
                // 剪贴板预览
                inventory.setItem(4, createClipboardPreviewItem(clipboard));

                // 剪贴板信息
                inventory.setItem(19, createInfoItem("blocks", "方块数", String.valueOf(clipboard.getBlockCount())));
                inventory.setItem(20, createInfoItem("undo", "撤销历史", String.valueOf(clipboard.getUndoStackSize())));
                inventory.setItem(21, createInfoItem("redo", "重做历史", String.valueOf(clipboard.getRedoStackSize())));

                // 操作按钮
                inventory.setItem(29, createPasteClipboardButton());
                inventory.setItem(30, createClearClipboardButton());
                inventory.setItem(33, createFlipButton());
            },
            () -> {
                // 剪贴板为空
                inventory.setItem(22, createEmptyClipboardPlaceholder());
            }
        );

        // 返回按钮
        inventory.setItem(45, createBackButton());
    }

    /**
     * 构建设置菜单
     */
    private void buildSettingsMenu() {
        updateTitle(Component.text("蓝图文库 - 设置", NamedTextColor.GOLD));

        // 快捷粘贴设置
        inventory.setItem(20, createToggleItem("includeAir", "包含空气方块", true));
        inventory.setItem(22, createToggleItem("includeEntities", "包含实体", false));
        inventory.setItem(24, createToggleItem("autoSave", "自动保存", true));

        // 返回按钮
        inventory.setItem(45, createBackButton());
    }

    /**
     * 构建元数据编辑菜单
     */
    private void buildMetadataEditMenu() {
        updateTitle(Component.text("编辑蓝图信息", NamedTextColor.GOLD));

        if (currentBlueprint == null) {
            inventory.setItem(22, createEmptyPlaceholder());
            inventory.setItem(45, createBackButton());
            return;
        }

        // 蓝图预览（只读）
        inventory.setItem(4, createBlueprintPreviewItem());

        // 元数据字段编辑按钮
        // 名称
        inventory.setItem(19, createMetadataFieldItem(MetadataField.NAME,
            currentBlueprint.getName(), Material.NAME_TAG));
        // 描述
        inventory.setItem(20, createMetadataFieldItem(MetadataField.DESCRIPTION,
            truncateString(currentBlueprint.getDescription(), 20), Material.PAPER));
        // 分类
        inventory.setItem(21, createMetadataFieldItem(MetadataField.CATEGORY,
            currentBlueprint.getCategory(), Material.CHEST));

        // 公开状态
        inventory.setItem(28, createMetadataToggleItem(MetadataField.PUBLIC,
            currentBlueprint instanceof BlueprintImpl impl && impl.isPublic(), Material.LIME_CONCRETE, Material.GRAY_CONCRETE));
        // 共享状态
        inventory.setItem(29, createMetadataToggleItem(MetadataField.SHARED,
            currentBlueprint instanceof BlueprintImpl impl && impl.isShared(), Material.BOOK, Material.BOOKSHELF));

        // 分类选择快捷按钮
        List<BlueprintCategory> categories = service.getAllCategories();
        int slot = 33;
        for (BlueprintCategory cat : categories) {
            if (slot < 45 && slot != 45) {
                inventory.setItem(slot, createCategoryQuickSelectItem(cat));
                slot++;
            }
        }

        // 保存按钮
        inventory.setItem(49, createSaveButton());

        // 返回按钮
        inventory.setItem(45, createBackButton());
    }

    /**
     * 添加导航栏
     */
    private void addNavigationBar() {
        // 关闭按钮
        inventory.setItem(53, createCloseButton());

        // 页面导航
        int totalPages = Math.max(1, (displayedBlueprints.size() - 1) / ITEMS_PER_PAGE + 1);

        if (totalPages > 1) {
            inventory.setItem(51, createPreviousPageButton());
            inventory.setItem(52, createPageIndicator());
            inventory.setItem(50, createNextPageButton());
        }
    }

    // ==================== 菜单点击处理 ====================

    private void handleMainClick(int slot) {
        // 工具栏按钮
        if (slot >= 45 && slot <= 48) {
            switch (slot) {
                case 45 -> openCategoriesPage();
                case 46 -> openSearchInput();
                case 47 -> openClipboardPage();
                case 48 -> {
                    currentPage = MenuPage.SETTINGS;
                    buildMenu();
                }
            }
            return;
        }

        // 蓝图列表
        int index = currentPageIndex * ITEMS_PER_PAGE + slot;
        if (index >= 0 && index < displayedBlueprints.size()) {
            BlueprintTypes.BlueprintMetadata bp = displayedBlueprints.get(index);

            if (onBlueprintSelected != null) {
                // 选择模式
                service.loadBlueprint(bp.id()).ifPresent(b -> {
                    onBlueprintSelected.accept(b);
                    player.closeInventory();
                });
            } else {
                // 浏览模式
                openBlueprintDetail(bp);
            }
        }
    }

    private void handleCategoriesClick(int slot) {
        if (slot == 45) {
            currentPage = MenuPage.MAIN;
            buildMenu();
            return;
        }

        List<BlueprintCategory> categories = service.getAllCategories();
        int index = slot;
        if (index >= 0 && index < categories.size()) {
            BlueprintCategory cat = categories.get(index);
            BlueprintGui newGui = createWithCategory(player, messages, service, cat.getId());
            player.openInventory(newGui.getInventory());
        }
    }

    private void handleBlueprintInfoClick(int slot) {
        if (slot == 45) {
            currentPage = MenuPage.MAIN;
            buildMenu();
            return;
        }

        switch (slot) {
            case 29 -> pasteBlueprint();
            case 30 -> loadToClipboard();
            case 31 -> rotateBlueprint();
            case 32 -> mirrorBlueprint();
            case 33 -> editBlueprint();
        }
    }

    private void handleClipboardClick(int slot) {
        if (slot == 45) {
            currentPage = MenuPage.MAIN;
            buildMenu();
            return;
        }

        switch (slot) {
            case 29 -> pasteClipboard();
            case 30 -> clearClipboard();
            case 33 -> flipClipboard();
        }
    }

    private void handleSettingsClick(int slot) {
        if (slot == 45) {
            currentPage = MenuPage.MAIN;
            buildMenu();
        }
    }

    private void handleMetadataEditClick(int slot) {
        if (slot == 45) {
            // 返回蓝图详情页
            currentPage = MenuPage.BLUEPRINT_INFO;
            buildMenu();
            return;
        }

        if (slot == 49) {
            // 保存并返回
            saveBlueprintMetadata();
            currentPage = MenuPage.BLUEPRINT_INFO;
            buildMenu();
            return;
        }

        if (currentBlueprint == null) return;

        switch (slot) {
            case 19 -> {
                // 编辑名称
                openTextInput(MetadataField.NAME, currentBlueprint.getName());
            }
            case 20 -> {
                // 编辑描述
                openTextInput(MetadataField.DESCRIPTION, currentBlueprint.getDescription());
            }
            case 21 -> {
                // 编辑分类
                openCategoryInput();
            }
            case 28 -> {
                // 切换公开状态
                togglePublicStatus();
            }
            case 29 -> {
                // 切换共享状态
                toggleSharedStatus();
            }
            default -> {
                // 检查是否是分类快捷选择
                if (slot >= 33 && slot < 45) {
                    handleCategoryQuickSelect(slot);
                }
            }
        }
    }

    // ==================== 元数据编辑方法 ====================

    private void openTextInput(MetadataField field, String currentValue) {
        awaitingMetadataInput = true;
        editingField = field;
        player.closeInventory();

        Component prompt = Component.text("[蓝图] ", NamedTextColor.GOLD)
            .append(Component.text("请输入新的 " + field.getDisplayName() + "，或输入 ", NamedTextColor.GRAY))
            .append(Component.text("cancel ", NamedTextColor.RED))
            .append(Component.text("取消", NamedTextColor.GRAY));

        Component current = Component.text("[蓝图] ", NamedTextColor.YELLOW)
            .append(Component.text("当前 " + field.getDisplayName() + ": ", NamedTextColor.GRAY))
            .append(Component.text(currentValue != null ? currentValue : "(无)", NamedTextColor.AQUA));

        player.sendMessage(prompt);
        player.sendMessage(current);
    }

    private void openCategoryInput() {
        awaitingMetadataInput = true;
        editingField = MetadataField.CATEGORY;
        player.closeInventory();

        List<BlueprintCategory> categories = service.getAllCategories();
        player.sendMessage(Component.text()
            .append(Component.text("[蓝图] ", NamedTextColor.GOLD))
            .append(Component.text("请输入分类 ID，或输入以下选项之一：", NamedTextColor.GRAY)));

        // 显示可选分类
        for (BlueprintCategory cat : categories) {
            player.sendMessage(Component.text()
                .append(Component.text("  - ", NamedTextColor.DARK_GRAY))
                .append(Component.text(cat.getId(), NamedTextColor.AQUA))
                .append(Component.text(": " + cat.getName(), NamedTextColor.GRAY)));
        }

        player.sendMessage(Component.text()
            .append(Component.text("当前分类: ", NamedTextColor.YELLOW))
            .append(Component.text(currentBlueprint.getCategory(), NamedTextColor.AQUA)));
        player.sendMessage(Component.text()
            .append(Component.text("输入 cancel 取消", NamedTextColor.RED)));
    }

    /**
     * 处理元数据文本输入
     */
    public boolean handleMetadataInput(String input) {
        if (!awaitingMetadataInput || editingField == null) {
            return false;
        }

        awaitingMetadataInput = false;

        if (input == null || input.trim().isEmpty() || input.equalsIgnoreCase("cancel")) {
            player.sendMessage(Component.text()
                .append(Component.text("[蓝图] ", NamedTextColor.YELLOW))
                .append(Component.text("编辑已取消", NamedTextColor.GRAY)));
            editingField = null;
            return true;
        }

        String newValue = input.trim();
        boolean success = false;

        switch (editingField) {
            case NAME -> {
                if (newValue.length() > 64) {
                    player.sendMessage(Component.text()
                        .append(Component.text("[蓝图] ", NamedTextColor.RED))
                        .append(Component.text("名称过长，最多64个字符", NamedTextColor.WHITE)));
                    editingField = null;
                    return true;
                }
                success = updateBlueprintName(newValue);
            }
            case DESCRIPTION -> {
                if (newValue.length() > 500) {
                    player.sendMessage(Component.text()
                        .append(Component.text("[蓝图] ", NamedTextColor.RED))
                        .append(Component.text("描述过长，最多500个字符", NamedTextColor.WHITE)));
                    editingField = null;
                    return true;
                }
                success = updateBlueprintDescription(newValue);
            }
            case CATEGORY -> {
                success = updateBlueprintCategory(newValue);
            }
            default -> {
                // 布尔值字段不需要文本输入
            }
        }

        if (success) {
            player.sendMessage(Component.text()
                .append(Component.text("[蓝图] ", NamedTextColor.GREEN))
                .append(Component.text(editingField.getDisplayName() + " 已更新", NamedTextColor.WHITE)));
        } else {
            player.sendMessage(Component.text()
                .append(Component.text("[蓝图] ", NamedTextColor.RED))
                .append(Component.text("更新失败，请重试", NamedTextColor.WHITE)));
        }

        editingField = null;

        // 重新打开元数据编辑菜单
        currentPage = MenuPage.METADATA_EDIT;
        buildMenu();
        player.openInventory(this.inventory);
        return true;
    }

    /**
     * 检查是否正在等待元数据输入
     */
    public boolean isAwaitingMetadataInput() {
        return awaitingMetadataInput;
    }

    private void togglePublicStatus() {
        if (currentBlueprint instanceof BlueprintImpl impl) {
            impl.setPublic(!impl.isPublic());
            player.sendMessage(Component.text()
                .append(Component.text("[蓝图] ", NamedTextColor.GREEN))
                .append(Component.text("蓝图" + (impl.isPublic() ? "已公开" : "已设为私有"), NamedTextColor.WHITE)));
            buildMetadataEditMenu();
        }
    }

    private void toggleSharedStatus() {
        if (currentBlueprint instanceof BlueprintImpl impl) {
            impl.setShared(!impl.isShared());
            player.sendMessage(Component.text()
                .append(Component.text("[蓝图] ", NamedTextColor.GREEN))
                .append(Component.text("共享" + (impl.isShared() ? "已启用" : "已禁用"), NamedTextColor.WHITE)));
            buildMetadataEditMenu();
        }
    }

    private void handleCategoryQuickSelect(int slot) {
        List<BlueprintCategory> categories = service.getAllCategories();
        int index = slot - 33;
        if (index >= 0 && index < categories.size()) {
            BlueprintCategory cat = categories.get(index);
            if (updateBlueprintCategory(cat.getId())) {
                player.sendMessage(Component.text()
                    .append(Component.text("[蓝图] ", NamedTextColor.GREEN))
                    .append(Component.text("分类已更新为: " + cat.getName(), NamedTextColor.WHITE)));
                buildMetadataEditMenu();
            }
        }
    }

    private boolean updateBlueprintName(String newName) {
        if (currentBlueprint == null) return false;
        currentBlueprint.setName(newName);
        return saveCurrentBlueprint();
    }

    private boolean updateBlueprintDescription(String newDescription) {
        if (currentBlueprint == null) return false;
        currentBlueprint.setDescription(newDescription);
        return saveCurrentBlueprint();
    }

    private boolean updateBlueprintCategory(String categoryId) {
        if (currentBlueprint == null) return false;
        // 验证分类是否存在
        List<BlueprintCategory> categories = service.getAllCategories();
        boolean validCategory = categories.stream()
            .anyMatch(c -> c.getId().equalsIgnoreCase(categoryId));
        if (!validCategory) {
            player.sendMessage(Component.text()
                .append(Component.text("[蓝图] ", NamedTextColor.RED))
                .append(Component.text("无效的分类 ID: " + categoryId, NamedTextColor.WHITE)));
            return false;
        }
        currentBlueprint.setCategory(categoryId);
        return saveCurrentBlueprint();
    }

    private boolean saveCurrentBlueprint() {
        if (currentBlueprint == null) return false;
        try {
            service.saveBlueprint(currentBlueprint);
            return true;
        } catch (Exception e) {
            player.sendMessage(Component.text()
                .append(Component.text("[蓝图] ", NamedTextColor.RED))
                .append(Component.text("保存失败: " + e.getMessage(), NamedTextColor.WHITE)));
            return false;
        }
    }

    private void saveBlueprintMetadata() {
        if (currentBlueprint == null) return;

        if (saveCurrentBlueprint()) {
            player.sendMessage(Component.text()
                .append(Component.text("[蓝图] ", NamedTextColor.GREEN))
                .append(Component.text("蓝图信息已保存", NamedTextColor.WHITE)));
        } else {
            player.sendMessage(Component.text()
                .append(Component.text("[蓝图] ", NamedTextColor.RED))
                .append(Component.text("保存失败", NamedTextColor.WHITE)));
        }
    }

    // ==================== 蓝图操作 ====================

    private void pasteBlueprint() {
        if (currentBlueprint == null) return;

        var result = service.pasteBlueprint(player, currentBlueprint.getId(),
            player.getLocation(), true, false);

        if (result.success()) {
            player.sendMessage(Component.text()
                .append(Component.text("[蓝图] ", NamedTextColor.GREEN))
                .append(Component.text("已粘贴 " + result.blocksPlaced() + " 个方块", NamedTextColor.WHITE)));
        } else {
            player.sendMessage(Component.text()
                .append(Component.text("[蓝图] ", NamedTextColor.RED))
                .append(Component.text("粘贴失败: " + result.errorMessage(), NamedTextColor.WHITE)));
        }

        player.closeInventory();
    }

    private void loadToClipboard() {
        if (currentBlueprint == null) return;

        service.copyToClipboard(player, currentBlueprint.getRegion());
        player.sendMessage(Component.text()
            .append(Component.text("[蓝图] ", NamedTextColor.GREEN))
            .append(Component.text("已加载到剪贴板", NamedTextColor.WHITE)));

        openClipboardPage();
    }

    private void rotateBlueprint() {
        service.getClipboard(player.getUniqueId()).ifPresent(clipboard -> {
            if (clipboard instanceof BlueprintClipboardImpl impl) {
                impl.rotate(1);
                player.sendMessage(Component.text()
                    .append(Component.text("[蓝图] ", NamedTextColor.GREEN))
                    .append(Component.text("已旋转 90 度", NamedTextColor.WHITE)));
                buildClipboardMenu();
            }
        });
    }

    private void mirrorBlueprint() {
        service.getClipboard(player.getUniqueId()).ifPresent(clipboard -> {
            if (clipboard instanceof BlueprintClipboardImpl impl) {
                impl.mirror("x");
                player.sendMessage(Component.text()
                    .append(Component.text("[蓝图] ", NamedTextColor.GREEN))
                    .append(Component.text("已镜像翻转", NamedTextColor.WHITE)));
                buildClipboardMenu();
            }
        });
    }

    private void editBlueprint() {
        if (currentBlueprint == null) {
            player.sendMessage(Component.text()
                .append(Component.text("[蓝图] ", NamedTextColor.RED))
                .append(Component.text("请先选择一个蓝图", NamedTextColor.GRAY)));
            return;
        }

        // 检查是否是蓝图作者
        if (!currentBlueprint.getAuthorId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text()
                .append(Component.text("[蓝图] ", NamedTextColor.RED))
                .append(Component.text("只有蓝图作者才能编辑", NamedTextColor.GRAY)));
            return;
        }

        // 打开元数据编辑页面
        this.currentPage = MenuPage.METADATA_EDIT;
        buildMenu();
    }

    private void pasteClipboard() {
        var result = service.pasteClipboard(player, player.getLocation(), true);

        if (result.success()) {
            player.sendMessage(Component.text()
                .append(Component.text("[蓝图] ", NamedTextColor.GREEN))
                .append(Component.text("已粘贴 " + result.blocksPlaced() + " 个方块", NamedTextColor.WHITE)));
        } else {
            player.sendMessage(Component.text()
                .append(Component.text("[蓝图] ", NamedTextColor.RED))
                .append(Component.text("粘贴失败: " + result.errorMessage(), NamedTextColor.WHITE)));
        }

        player.closeInventory();
    }

    private void clearClipboard() {
        service.clearClipboard(player.getUniqueId());
        player.sendMessage(Component.text()
            .append(Component.text("[蓝图] ", NamedTextColor.GREEN))
            .append(Component.text("剪贴板已清空", NamedTextColor.WHITE)));
        buildClipboardMenu();
    }

    private void flipClipboard() {
        service.getClipboard(player.getUniqueId()).ifPresent(clipboard -> {
            if (clipboard instanceof BlueprintClipboardImpl impl) {
                impl.mirror("xz");
                player.sendMessage(Component.text()
                    .append(Component.text("[蓝图] ", NamedTextColor.GREEN))
                    .append(Component.text("已对角翻转", NamedTextColor.WHITE)));
                buildClipboardMenu();
            }
        });
    }

    // ==================== UI 组件创建 ====================

    /**
     * 更新库存标题
     * 由于 Inventory 不支持动态更新标题，需要重新创建库存
     */
    private void updateTitle(Component newTitle) {
        if (newTitle.equals(currentTitle)) {
            return; // 标题未变化，无需更新
        }

        // 重新创建库存以更新标题
        this.inventory = Bukkit.createInventory(this, 54, newTitle);
        this.currentTitle = newTitle;

        // 重建菜单内容
        rebuildCurrentPage();
    }

    /**
     * 重建当前页面内容
     */
    private void rebuildCurrentPage() {
        switch (currentPage) {
            case MAIN -> {
                // 蓝图列表
                int start = currentPageIndex * ITEMS_PER_PAGE;
                int end = Math.min(start + ITEMS_PER_PAGE, displayedBlueprints.size());

                for (int i = start; i < end; i++) {
                    BlueprintTypes.BlueprintMetadata bp = displayedBlueprints.get(i);
                    int slot = i - start;
                    inventory.setItem(slot, createBlueprintItem(bp));
                }

                // 顶部工具栏
                inventory.setItem(45, createCategoryButton());
                inventory.setItem(46, createSearchButton());
                inventory.setItem(47, createClipboardButton());
                inventory.setItem(48, createSettingsButton());

                // 如果没有蓝图，显示提示
                if (displayedBlueprints.isEmpty()) {
                    inventory.setItem(22, createEmptyPlaceholder());
                }
            }
            case CATEGORIES -> {
                List<BlueprintCategory> categories = service.getAllCategories();
                for (int i = 0; i < Math.min(categories.size(), ITEMS_PER_PAGE); i++) {
                    BlueprintCategory cat = categories.get(i);
                    inventory.setItem(i, createCategoryItem(cat));
                }
                inventory.setItem(45, createBackButton());
            }
            case BLUEPRINT_INFO -> {
                if (currentBlueprint != null) {
                    inventory.setItem(4, createBlueprintPreviewItem());
                    inventory.setItem(19, createInfoItem("author", "作者", currentBlueprint.getAuthorName()));
                    inventory.setItem(20, createInfoItem("size", "尺寸", getBlueprintSize()));
                    inventory.setItem(21, createInfoItem("blocks", "方块数", String.valueOf(currentBlueprint.getBlockCount())));
                    inventory.setItem(22, createInfoItem("materials", "材料种类", String.valueOf(currentBlueprint.getPalette().getUniqueMaterialCount())));
                    inventory.setItem(29, createPasteButton());
                    inventory.setItem(30, createLoadToClipboardButton());
                    inventory.setItem(31, createRotateButton());
                    inventory.setItem(32, createMirrorButton());
                    inventory.setItem(33, createEditButton());
                }
                inventory.setItem(45, createBackButton());
            }
            case CLIPBOARD -> {
                service.getClipboard(player.getUniqueId()).ifPresentOrElse(
                    clipboard -> {
                        inventory.setItem(4, createClipboardPreviewItem(clipboard));
                        inventory.setItem(19, createInfoItem("blocks", "方块数", String.valueOf(clipboard.getBlockCount())));
                        inventory.setItem(20, createInfoItem("undo", "撤销历史", String.valueOf(clipboard.getUndoStackSize())));
                        inventory.setItem(21, createInfoItem("redo", "重做历史", String.valueOf(clipboard.getRedoStackSize())));
                        inventory.setItem(29, createPasteClipboardButton());
                        inventory.setItem(30, createClearClipboardButton());
                        inventory.setItem(33, createFlipButton());
                    },
                    () -> inventory.setItem(22, createEmptyClipboardPlaceholder())
                );
                inventory.setItem(45, createBackButton());
            }
            case SETTINGS -> {
                inventory.setItem(20, createToggleItem("includeAir", "包含空气方块", true));
                inventory.setItem(22, createToggleItem("includeEntities", "包含实体", false));
                inventory.setItem(24, createToggleItem("autoSave", "自动保存", true));
                inventory.setItem(45, createBackButton());
            }
        }
        addNavigationBar();
    }

    private ItemStack createBlueprintItem(BlueprintTypes.BlueprintMetadata bp) {
        Material material = getCategoryIconMaterial(bp.category());
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.displayName(Component.text(bp.name(), NamedTextColor.AQUA));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("作者: " + bp.authorName(), NamedTextColor.GRAY));
        lore.add(Component.text("方块数: " + bp.blockCount(), NamedTextColor.GRAY));

        if (bp.category() != null && !bp.category().isEmpty()) {
            lore.add(Component.text("分类: " + bp.category(), NamedTextColor.DARK_GRAY));
        }

        lore.add(Component.text(""));
        lore.add(Component.text("左键查看详情 | 右键快速粘贴", NamedTextColor.DARK_GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createCategoryItem(BlueprintCategory category) {
        Material material = getCategoryMaterial(category.getIcon());
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.displayName(Component.text(category.getName(), NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text(category.getDescription(), NamedTextColor.GRAY));
        lore.add(Component.text("蓝图数量: " + category.getBlueprintCount(), NamedTextColor.DARK_GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("点击浏览", NamedTextColor.GREEN));

        meta.lore(lore);
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createBlueprintPreviewItem() {
        ItemStack item = new ItemStack(Material.MAP);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        if (currentBlueprint != null) {
            meta.displayName(Component.text(currentBlueprint.getName(), NamedTextColor.GOLD));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("预览", NamedTextColor.DARK_GRAY));

            meta.lore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMetadataFieldItem(MetadataField field, String currentValue, Material icon) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.displayName(Component.text(field.getDisplayName(), NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("当前值: " + (currentValue != null ? truncateString(currentValue, 30) : "未设置"), NamedTextColor.AQUA));
        lore.add(Component.text(""));
        lore.add(Component.text("点击修改", NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMetadataToggleItem(MetadataField field, boolean currentValue, Material enabledIcon, Material disabledIcon) {
        ItemStack item = new ItemStack(currentValue ? enabledIcon : disabledIcon);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        String status = currentValue ? "已启用" : "已禁用";
        NamedTextColor statusColor = currentValue ? NamedTextColor.GREEN : NamedTextColor.RED;

        meta.displayName(Component.text(field.getDisplayName(), NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("状态: " + status, statusColor));
        lore.add(Component.text(""));
        lore.add(Component.text("点击切换", NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCategoryQuickSelectItem(BlueprintCategory category) {
        Material iconMat = Material.CHEST;
        try {
            iconMat = Material.valueOf(category.getIcon());
        } catch (Exception e) {
            // Use default
        }
        ItemStack item = new ItemStack(iconMat);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.displayName(Component.text(category.getName(), NamedTextColor.AQUA));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("蓝图数: " + category.getBlueprintCount(), NamedTextColor.GRAY));
        lore.add(Component.text("点击快速选择", NamedTextColor.DARK_GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSaveButton() {
        ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.displayName(Component.text("保存更改", NamedTextColor.GREEN));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("保存蓝图元数据更改", NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createClipboardPreviewItem(BlueprintClipboard clipboard) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.displayName(Component.text("剪贴板内容", NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("包含 " + clipboard.getBlockCount() + " 个方块", NamedTextColor.AQUA));
        lore.add(Component.text(""));
        lore.add(Component.text("使用 /bp paste 粘贴", NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createInfoItem(String key, String label, String value) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.displayName(Component.text(label, NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text(value, NamedTextColor.AQUA));

        meta.lore(lore);
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createCategoryButton() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.displayName(Component.text("分类浏览", NamedTextColor.GOLD));
        meta.lore(List.of(
            Component.text(""),
            Component.text("按分类查看蓝图", NamedTextColor.GRAY)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSearchButton() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.displayName(Component.text("搜索蓝图", NamedTextColor.GOLD));
        meta.lore(List.of(
            Component.text(""),
            Component.text("输入关键词搜索", NamedTextColor.GRAY)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createClipboardButton() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.displayName(Component.text("剪贴板", NamedTextColor.GOLD));
        meta.lore(List.of(
            Component.text(""),
            Component.text("管理剪贴板内容", NamedTextColor.GRAY)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSettingsButton() {
        ItemStack item = new ItemStack(Material.COMPARATOR);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.displayName(Component.text("设置", NamedTextColor.GOLD));
        meta.lore(List.of(
            Component.text(""),
            Component.text("配置粘贴选项", NamedTextColor.GRAY)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPasteButton() {
        ItemStack item = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.displayName(Component.text("粘贴", NamedTextColor.GREEN));
        meta.lore(List.of(
            Component.text(""),
            Component.text("在当前位置粘贴蓝图", NamedTextColor.GRAY)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createLoadToClipboardButton() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.displayName(Component.text("加载到剪贴板", NamedTextColor.AQUA));
        meta.lore(List.of(
            Component.text(""),
            Component.text("复制到剪贴板进行操作", NamedTextColor.GRAY)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRotateButton() {
        ItemStack item = new ItemStack(Material.REPEATER);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.displayName(Component.text("旋转 90°", NamedTextColor.YELLOW));
        meta.lore(List.of(
            Component.text(""),
            Component.text("顺时针旋转剪贴板", NamedTextColor.GRAY)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMirrorButton() {
        ItemStack item = new ItemStack(Material.END_PORTAL_FRAME);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.displayName(Component.text("镜像 X 轴", NamedTextColor.LIGHT_PURPLE));
        meta.lore(List.of(
            Component.text(""),
            Component.text("水平镜像剪贴板", NamedTextColor.GRAY)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createEditButton() {
        ItemStack item = new ItemStack(Material.ANVIL);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.displayName(Component.text("编辑", NamedTextColor.RED));
        meta.lore(List.of(
            Component.text(""),
            Component.text("修改蓝图元数据", NamedTextColor.GRAY)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPasteClipboardButton() {
        ItemStack item = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.displayName(Component.text("粘贴剪贴板", NamedTextColor.GREEN));
        meta.lore(List.of(
            Component.text(""),
            Component.text("在当前位置粘贴", NamedTextColor.GRAY)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createClearClipboardButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.displayName(Component.text("清空剪贴板", NamedTextColor.RED));
        meta.lore(List.of(
            Component.text(""),
            Component.text("清除所有剪贴板内容", NamedTextColor.GRAY)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createFlipButton() {
        ItemStack item = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.displayName(Component.text("对角翻转", NamedTextColor.DARK_PURPLE));
        meta.lore(List.of(
            Component.text(""),
            Component.text("沿对角线镜像", NamedTextColor.GRAY)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createToggleItem(String key, String label, boolean currentValue) {
        Material material = currentValue ? Material.LIME_DYE : Material.GRAY_DYE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.displayName(Component.text(label, NamedTextColor.WHITE));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("当前: " + (currentValue ? "启用" : "禁用"),
            currentValue ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("点击切换", NamedTextColor.DARK_GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.displayName(Component.text("返回", NamedTextColor.YELLOW));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPageIndicator() {
        int totalPages = Math.max(1, (displayedBlueprints.size() - 1) / ITEMS_PER_PAGE + 1);

        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.displayName(Component.text("第 " + (currentPageIndex + 1) + " / " + totalPages + " 页",
            NamedTextColor.WHITE));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPreviousPageButton() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.displayName(Component.text("上一页", NamedTextColor.YELLOW));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNextPageButton() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.displayName(Component.text("下一页", NamedTextColor.YELLOW));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.displayName(Component.text("关闭", NamedTextColor.RED));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createEmptyPlaceholder() {
        ItemStack item = new ItemStack(Material.STRUCTURE_VOID);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.displayName(Component.text("没有蓝图", NamedTextColor.GRAY));
        meta.lore(List.of(
            Component.text(""),
            Component.text("使用 /bp save <名称> 创建蓝图", NamedTextColor.DARK_GRAY)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createEmptyClipboardPlaceholder() {
        ItemStack item = new ItemStack(Material.STRUCTURE_VOID);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.displayName(Component.text("剪贴板为空", NamedTextColor.GRAY));
        meta.lore(List.of(
            Component.text(""),
            Component.text("使用 /bp load <蓝图> 加载", NamedTextColor.DARK_GRAY)
        ));

        item.setItemMeta(meta);
        return item;
    }

    // ==================== 辅助方法 ====================

    private String getBlueprintSize() {
        if (currentBlueprint == null) return "N/A";

        var region = currentBlueprint.getRegion();
        return region.getWidth() + "x" + region.getHeight() + "x" + region.getDepth();
    }

    private Material getCategoryIconMaterial(String category) {
        if (category == null) return Material.CHEST;

        return switch (category.toLowerCase()) {
            case "building" -> Material.BRICK;
            case "decoration" -> Material.FLOWER_POT;
            case "farm" -> Material.WHEAT;
            case "redstone" -> Material.REPEATER;
            case "storage" -> Material.CHEST;
            case "default" -> Material.PAPER;
            default -> Material.CHEST;
        };
    }

    private Material getCategoryMaterial(String icon) {
        if (icon == null) return Material.CHEST;

        try {
            return Material.valueOf(icon);
        } catch (IllegalArgumentException e) {
            return Material.CHEST;
        }
    }

    /**
     * 截断字符串
     */
    private String truncateString(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }

    /**
     * 获取玩家
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * 获取当前页面
     */
    public MenuPage getCurrentPage() {
        return currentPage;
    }
}
