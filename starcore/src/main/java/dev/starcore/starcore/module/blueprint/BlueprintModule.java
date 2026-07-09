package dev.starcore.starcore.module.blueprint;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.blueprint.command.BlueprintCommand;
import dev.starcore.starcore.module.blueprint.listener.BlueprintListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.logging.Logger;

/**
 * 建筑蓝图模块
 * 提供建筑蓝图创建、存储、复制、粘贴等功能
 */
public final class BlueprintModule implements StarCoreModule, BlueprintService {

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "blueprint",
        "建筑蓝图",
        ModuleLayer.MODULE,
        List.of(),
        List.of(BlueprintService.class),
        "Building blueprint system with save, copy, paste, rotate and mirror support."
    );

    private StarCoreContext context;
    private BlueprintServiceImpl service;
    private BlueprintListener listener;
    private Logger logger;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.context = context;
        this.logger = context.plugin().getLogger();

        // 初始化服务
        this.service = new BlueprintServiceImpl(context.plugin());
        context.serviceRegistry().register(BlueprintService.class, service);

        // 注册事件监听器
        this.listener = new BlueprintListener(context.plugin(), service);
        context.plugin().getServer().getPluginManager().registerEvents(listener, context.plugin());

        // 设置 GUI 的静态 listener 引用（用于搜索输入）
        BlueprintGui.setListener(listener);

        // 注册命令
        registerCommands(context.plugin());

        logger.info("STARCORE Blueprint module enabled.");
    }

    private void registerCommands(JavaPlugin plugin) {
        // 注册蓝图命令
        PluginCommand blueprintCmd = plugin.getCommand("blueprint");
        if (blueprintCmd != null) {
            MessageService messages = context.serviceRegistry().find(MessageService.class).orElse(null);
            BlueprintCommand executor = new BlueprintCommand(service, messages, plugin);
            blueprintCmd.setExecutor(executor);
            if (executor instanceof org.bukkit.command.TabCompleter tabCompleter) {
                blueprintCmd.setTabCompleter(tabCompleter);
            }
            logger.info("Blueprint command registered.");
        }
    }

    @Override
    public void disable(StarCoreContext context) {
        // 关闭服务
        if (service != null) {
            service.shutdown();
        }

        // 注销事件监听
        if (listener != null) {
            listener.unregister();
        }

        logger.info("STARCORE Blueprint module disabled.");
    }

    public BlueprintService service() {
        return service;
    }

    // ========== 委托 BlueprintService 方法 ==========

    @Override
    public Blueprint createBlueprint(org.bukkit.entity.Player player,
                                    RegionSelection region,
                                    String name,
                                    String description) {
        return service.createBlueprint(player, region, name, description);
    }

    @Override
    public void saveBlueprint(Blueprint blueprint) throws BlueprintException {
        service.saveBlueprint(blueprint);
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> saveBlueprintAsync(Blueprint blueprint) {
        return service.saveBlueprintAsync(blueprint);
    }

    @Override
    public java.util.Optional<Blueprint> loadBlueprint(String blueprintId) {
        return service.loadBlueprint(blueprintId);
    }

    @Override
    public java.util.concurrent.CompletableFuture<java.util.Optional<Blueprint>> loadBlueprintAsync(String blueprintId) {
        return service.loadBlueprintAsync(blueprintId);
    }

    @Override
    public boolean deleteBlueprint(String blueprintId) {
        return service.deleteBlueprint(blueprintId);
    }

    @Override
    public boolean renameBlueprint(String blueprintId, String newName) {
        return service.renameBlueprint(blueprintId, newName);
    }

    @Override
    public Blueprint copyBlueprint(String blueprintId, String newName) {
        return service.copyBlueprint(blueprintId, newName);
    }

    @Override
    public java.util.List<dev.starcore.starcore.module.blueprint.BlueprintTypes.BlueprintMetadata> getPlayerBlueprints(java.util.UUID playerId) {
        return service.getPlayerBlueprints(playerId);
    }

    @Override
    public java.util.List<dev.starcore.starcore.module.blueprint.BlueprintTypes.BlueprintMetadata> getNationBlueprints(String nationId) {
        return service.getNationBlueprints(nationId);
    }

    @Override
    public java.util.List<dev.starcore.starcore.module.blueprint.BlueprintTypes.BlueprintMetadata> getPublicBlueprints() {
        return service.getPublicBlueprints();
    }

    @Override
    public java.util.List<dev.starcore.starcore.module.blueprint.BlueprintTypes.BlueprintMetadata> getBlueprintsByCategory(String categoryId) {
        return service.getBlueprintsByCategory(categoryId);
    }

    @Override
    public java.util.List<dev.starcore.starcore.module.blueprint.BlueprintTypes.BlueprintMetadata> searchBlueprints(String query) {
        return service.searchBlueprints(query);
    }

    @Override
    public java.util.Optional<Blueprint> findBlueprintByName(String name) {
        return service.findBlueprintByName(name);
    }

    @Override
    public java.util.List<BlueprintCategory> getAllCategories() {
        return service.getAllCategories();
    }

    @Override
    public BlueprintCategory createCategory(String name, String description, String icon, String color) {
        return service.createCategory(name, description, icon, color);
    }

    @Override
    public boolean deleteCategory(String categoryId) {
        return service.deleteCategory(categoryId);
    }

    @Override
    public boolean updateCategory(String categoryId, String name, String description) {
        return service.updateCategory(categoryId, name, description);
    }

    @Override
    public java.util.Optional<BlueprintClipboard> getClipboard(java.util.UUID playerId) {
        return service.getClipboard(playerId);
    }

    @Override
    public BlueprintClipboard copyToClipboard(org.bukkit.entity.Player player, RegionSelection region) {
        return service.copyToClipboard(player, region);
    }

    @Override
    public dev.starcore.starcore.module.blueprint.BlueprintTypes.PasteResult pasteClipboard(org.bukkit.entity.Player player,
                                                org.bukkit.Location origin,
                                                boolean includeAir) {
        return service.pasteClipboard(player, origin, includeAir);
    }

    @Override
    public void clearClipboard(java.util.UUID playerId) {
        service.clearClipboard(playerId);
    }

    @Override
    public java.util.Optional<EditSession> getEditSession(java.util.UUID playerId) {
        return service.getEditSession(playerId);
    }

    @Override
    public EditSession createEditSession(org.bukkit.entity.Player player) {
        return service.createEditSession(player);
    }

    @Override
    public void closeEditSession(java.util.UUID playerId) {
        service.closeEditSession(playerId);
    }

    @Override
    public boolean undo(java.util.UUID playerId) {
        return service.undo(playerId);
    }

    @Override
    public boolean redo(java.util.UUID playerId) {
        return service.redo(playerId);
    }

    @Override
    public dev.starcore.starcore.module.blueprint.BlueprintTypes.PasteResult pasteBlueprint(org.bukkit.entity.Player player,
                                                String blueprintId,
                                                org.bukkit.Location origin,
                                                boolean includeAir,
                                                boolean entities) {
        return service.pasteBlueprint(player, blueprintId, origin, includeAir, entities);
    }

    @Override
    public java.util.concurrent.CompletableFuture<dev.starcore.starcore.module.blueprint.BlueprintTypes.PasteResult> pasteBlueprintAsync(
            org.bukkit.entity.Player player,
            String blueprintId,
            org.bukkit.Location origin,
            boolean includeAir,
            boolean entities) {
        return service.pasteBlueprintAsync(player, blueprintId, origin, includeAir, entities);
    }

    @Override
    public java.util.concurrent.CompletableFuture<java.util.List<dev.starcore.starcore.module.blueprint.BlueprintTypes.PasteResult>> batchPaste(
            java.util.UUID playerId,
            java.util.List<String> blueprintIds,
            org.bukkit.Location origin) {
        return service.batchPaste(playerId, blueprintIds, origin);
    }

    @Override
    public java.util.Optional<Blueprint> importBlueprint(String filePath, String format) {
        return service.importBlueprint(filePath, format);
    }

    @Override
    public boolean exportBlueprint(String blueprintId, String filePath, String format) {
        return service.exportBlueprint(blueprintId, filePath, format);
    }

    @Override
    public java.util.List<String> getSupportedImportFormats() {
        return service.getSupportedImportFormats();
    }

    @Override
    public java.util.List<String> getSupportedExportFormats() {
        return service.getSupportedExportFormats();
    }

    @Override
    public dev.starcore.starcore.module.blueprint.BlueprintTypes.ServiceStats getStats() {
        return service.getStats();
    }
}
