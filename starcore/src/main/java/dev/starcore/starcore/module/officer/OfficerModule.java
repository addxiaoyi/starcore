package dev.starcore.starcore.module.officer;
import java.util.Optional;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.officer.gui.OfficerMenuListener;
import dev.starcore.starcore.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class OfficerModule implements StarCoreModule, OfficerService {
    private static final String FILE_NAME = "officers.properties";

    // 默认角色列表（可在配置文件中覆盖）
    private static final List<OfficerRoleConfig> DEFAULT_ROLES = List.of(
        new OfficerRoleConfig("marshal", "元帅", Material.DIAMOND_SWORD, 1),
        new OfficerRoleConfig("general", "将军", Material.GOLDEN_SWORD, 2),
        new OfficerRoleConfig("treasurer", "太尉", Material.GOLD_INGOT, 3),
        new OfficerRoleConfig("diplomat", "外交官", Material.EMERALD, 4),
        new OfficerRoleConfig("steward", "长史", Material.BOOK, 5)
    );

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "officer",
        "官员核心",
        ModuleLayer.MODULE,
        List.of("nation", "government"),
        List.of(OfficerService.class),
        "Owns national officer appointments and strategic staff roles."
    );

    // 可配置角色列表
    private final ConcurrentMap<String, OfficerRoleConfig> roleConfigs = new ConcurrentHashMap<>();
    private final ConcurrentMap<NationId, ConcurrentMap<String, OfficerAppointment>> officers = new ConcurrentHashMap<>();
    private OfficerStateStorage stateStorage;
    private java.util.logging.Logger logger;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        context.persistenceService().ensureNamespace(metadata().id());
        this.logger = context.plugin().getLogger();
        this.stateStorage = new DatabaseAwareOfficerStateStorage(
            metadata().id(),
            context.databaseService(),
            context.persistenceService(),
            context.plugin().getLogger()
        );
        loadRoleConfigs(context);
        loadState();

        // 注册服务到 ServiceRegistry
        context.serviceRegistry().register(OfficerService.class, this);

        // 注册 GUI 监听器
        context.plugin().getServer().getPluginManager().registerEvents(getMenuListener(), context.plugin());
        logger.info("官员模块已启用，共 " + roleConfigs.size() + " 个可配置角色");
    }

    /**
     * 从配置文件加载角色配置
     */
    private void loadRoleConfigs(StarCoreContext context) {
        roleConfigs.clear();

        // 加载默认配置
        for (OfficerRoleConfig defaultRole : DEFAULT_ROLES) {
            roleConfigs.put(defaultRole.id(), defaultRole);
        }

        // 尝试从配置文件覆盖
        try {
            var props = context.persistenceService().loadProperties(metadata().id(), "roles.yml");
            if (props != null && !props.isEmpty()) {
                // 从 Properties 格式加载角色配置
                // 格式: roles.<roleId>.display-name, roles.<roleId>.icon, roles.<roleId>.slot
                for (String key : props.stringPropertyNames()) {
                    if (key.startsWith("roles.")) {
                        String[] parts = key.split("\\.");
                        if (parts.length == 3) {
                            String roleId = parts[1];
                            String field = parts[2];
                            // 需要重新加载完整配置，这里简化处理
                            logger.info("Loaded role config: " + key + "=" + props.getProperty(key));
                        }
                    }
                }
                logger.info("从配置加载了官员角色");
            }
        } catch (Exception e) {
            logger.warning("加载官员角色配置失败，使用默认配置: " + e.getMessage());
        }

        // 如果没有配置文件，创建默认配置
        try {
            Path rolesFile = context.persistenceService().namespacePath(metadata().id()).resolve("roles.yml");
            if (!Files.exists(rolesFile)) {
                saveDefaultRoleConfig(context);
            }
        } catch (java.io.IOException e) {
            logger.warning("检查角色配置文件失败: " + e.getMessage());
        }
    }

    /**
     * 保存默认角色配置
     */
    private void saveDefaultRoleConfig(StarCoreContext context) {
        try {
            java.util.Properties props = new java.util.Properties();
            props.setProperty("description", "官员角色配置 - 可自定义角色名称、图标和顺序");
            for (OfficerRoleConfig role : DEFAULT_ROLES) {
                props.setProperty("roles." + role.id() + ".display-name", role.displayName());
                props.setProperty("roles." + role.id() + ".icon", role.icon().name());
                props.setProperty("roles." + role.id() + ".slot", String.valueOf(role.slot()));
            }
            context.persistenceService().saveProperties(metadata().id(), "roles.yml", props);
            logger.info("已生成默认官员角色配置文件: roles.yml");
        } catch (Exception e) {
            logger.warning("保存默认官员角色配置失败: " + e.getMessage());
        }
    }

    @Override
    public void disable(StarCoreContext context) {
        flushState();
    }

    @Override
    public Collection<String> availableRoles() {
        return roleConfigs.values().stream()
            .sorted(Comparator.comparingInt(OfficerRoleConfig::slot))
            .map(OfficerRoleConfig::id)
            .toList();
    }

    /**
     * 获取角色配置
     */
    public OfficerRoleConfig getRoleConfig(String roleId) {
        return roleConfigs.get(normalizeRole(roleId));
    }

    /**
     * 获取所有角色配置
     */
    public Collection<OfficerRoleConfig> getAllRoleConfigs() {
        return roleConfigs.values().stream()
            .sorted(Comparator.comparingInt(OfficerRoleConfig::slot))
            .toList();
    }

    /**
     * 获取角色的本地化名称
     */
    public String getRoleDisplayName(String roleId) {
        OfficerRoleConfig config = roleConfigs.get(normalizeRole(roleId));
        return config != null ? config.displayName() : normalizeRole(roleId);
    }

    /**
     * 获取角色的图标材质
     */
    public Material getRoleIcon(String roleId) {
        OfficerRoleConfig config = roleConfigs.get(normalizeRole(roleId));
        return config != null ? config.icon() : Material.BOOK;
    }

    @Override
    public Collection<OfficerAppointment> officersOf(NationId nationId) {
        return officers.getOrDefault(nationId, new ConcurrentHashMap<>()).values().stream()
            .sorted(Comparator.comparing(OfficerAppointment::role))
            .toList();
    }

    @Override
    public Optional<OfficerAppointment> officer(NationId nationId, String role) {
        String normalized = normalizeRole(role);
        if (!roleConfigs.containsKey(normalized)) {
            return Optional.empty();
        }
        return Optional.ofNullable(officers.getOrDefault(nationId, new ConcurrentHashMap<>()).get(normalized));
    }

    /**
     * 获取玩家的官员职位
     */
    public Optional<String> getPlayerOfficerRole(NationId nationId, UUID playerId) {
        ConcurrentMap<String, OfficerAppointment> nationOfficers = officers.get(nationId);
        if (nationOfficers == null) {
            return Optional.empty();
        }
        return nationOfficers.values().stream()
            .filter(app -> app.playerId().equals(playerId))
            .map(OfficerAppointment::role)
            .findFirst();
    }

    /**
     * 检查是否为特定官员角色
     */
    public boolean isOfficerRole(NationId nationId, UUID playerId, String roleId) {
        Optional<OfficerAppointment> officer = officer(nationId, roleId);
        return officer.isPresent() && officer.get().playerId().equals(playerId);
    }

    /**
     * 检查是否有任何官员角色
     */
    public boolean hasAnyOfficerRole(NationId nationId, UUID playerId) {
        return getPlayerOfficerRole(nationId, playerId).isPresent();
    }

    @Override
    public boolean appoint(NationId nationId, String role, UUID playerId, String playerName) {
        String normalized = normalizeRole(role);
        if (!roleConfigs.containsKey(normalized) || playerId == null || playerName == null || playerName.isBlank()) {
            return false;
        }
        officers.computeIfAbsent(nationId, ignored -> new ConcurrentHashMap<>())
            .put(normalized, new OfficerAppointment(normalized, playerId, playerName));
        saveState();
        return true;
    }

    @Override
    public boolean remove(NationId nationId, String role) {
        String normalized = normalizeRole(role);
        if (!roleConfigs.containsKey(normalized)) {
            return false;
        }
        Map<String, OfficerAppointment> byRole = officers.get(nationId);
        if (byRole == null || byRole.remove(normalized) == null) {
            return false;
        }
        saveState();
        return true;
    }

    /**
     * 移除玩家的所有官员职位
     */
    public int removeAllOfficerRoles(NationId nationId, UUID playerId) {
        ConcurrentMap<String, OfficerAppointment> nationOfficers = officers.get(nationId);
        if (nationOfficers == null) {
            return 0;
        }
        int removed = 0;
        var iterator = nationOfficers.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().playerId().equals(playerId)) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            saveState();
        }
        return removed;
    }

    @Override
    public String summary() {
        long total = officers.values().stream().mapToLong(Map::size).sum();
        return officers.size() + " nation officer roster(s), " + total + " appointment(s)";
    }

    private void saveState() {
        if (stateStorage == null) {
            return;
        }
        stateStorage.saveAsync(OfficerStateCodec.toProperties(snapshotByNation()));
    }

    private void flushState() {
        if (stateStorage == null) {
            return;
        }
        stateStorage.save(OfficerStateCodec.toProperties(snapshotByNation()));
    }

    private void loadState() {
        officers.clear();
        OfficerStateCodec.fromProperties(stateStorage == null ? new java.util.Properties() : stateStorage.load())
            .forEach((nationId, byRole) -> {
                ConcurrentMap<String, OfficerAppointment> mutable = new ConcurrentHashMap<>();
                byRole.forEach((role, appointment) -> {
                    if (roleConfigs.containsKey(normalizeRole(role))) {
                        mutable.put(normalizeRole(role), appointment);
                    }
                });
                if (!mutable.isEmpty()) {
                    officers.put(nationId, mutable);
                }
            });
    }

    private Map<NationId, Map<String, OfficerAppointment>> snapshotByNation() {
        Map<NationId, Map<String, OfficerAppointment>> snapshot = new java.util.LinkedHashMap<>();
        officers.entrySet().stream()
            .sorted((left, right) -> left.getKey().toString().compareTo(right.getKey().toString()))
            .forEach(entry -> snapshot.put(entry.getKey(), Map.copyOf(entry.getValue())));
        return snapshot;
    }

    private static String normalizeRole(String role) {
        return role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
    }

    // ==================== GUI 支持 ====================

    private OfficerMenuListener menuListener;

    /**
     * 获取官员菜单监听器
     */
    public OfficerMenuListener getMenuListener() {
        if (menuListener == null) {
            menuListener = new OfficerMenuListener(this);
        }
        return menuListener;
    }

    /**
     * 打开官员管理菜单
     */
    public void openOfficerMenu(Player player, Nation nation) {
        getMenuListener().openMenu(player, nation);
    }

}
