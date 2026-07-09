package dev.starcore.starcore.nation.rank;

import dev.starcore.starcore.nation.permission.NationPermission;
import dev.starcore.starcore.nation.permission.PermissionLevel;
import dev.starcore.starcore.util.ColorCodes;

import java.util.*;

/**
 * Nation职位系统
 * 基于SimpleClans的Rank设计
 *
 * 特性：
 * - 完全自定义权限
 * - 优先级排序
 * - 配置文件驱动
 * - 灵活分配
 */
public class NationRank implements Comparable<NationRank> {

    private final String name;                          // 唯一标识
    private String displayName;                         // 显示名称（支持颜色）
    private final Set<NationPermission> permissions;    // 权限集合
    private int priority;                               // 优先级（数字越大越高）

    // ==================== 构造函数 ====================

    public NationRank(String name, String displayName) {
        this(name, displayName, new HashSet<>(), 0);
    }

    public NationRank(String name, String displayName, Set<NationPermission> permissions) {
        this(name, displayName, permissions, 0);
    }

    public NationRank(String name, String displayName, Set<NationPermission> permissions, int priority) {
        this.name = name;
        this.displayName = displayName;
        this.permissions = new HashSet<>(permissions);
        this.priority = priority;
    }

    // ==================== Getter/Setter ====================

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Set<NationPermission> getPermissions() {
        return Collections.unmodifiableSet(permissions);
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    // ==================== 权限管理 ====================

    /**
     * 检查是否拥有权限
     */
    public boolean hasPermission(NationPermission permission) {
        return permissions.contains(permission);
    }

    /**
     * 添加权限
     */
    public void addPermission(NationPermission permission) {
        permissions.add(permission);
    }

    /**
     * 移除权限
     */
    public void removePermission(NationPermission permission) {
        permissions.remove(permission);
    }

    /**
     * 批量添加权限
     */
    public void addPermissions(Collection<NationPermission> perms) {
        permissions.addAll(perms);
    }

    /**
     * 批量移除权限
     */
    public void removePermissions(Collection<NationPermission> perms) {
        permissions.removeAll(perms);
    }

    /**
     * 清空所有权限
     */
    public void clearPermissions() {
        permissions.clear();
    }

    /**
     * 设置权限（替换）
     */
    public void setPermissions(Set<NationPermission> newPermissions) {
        permissions.clear();
        permissions.addAll(newPermissions);
    }

    // ==================== 权限统计 ====================

    /**
     * 获取权限数量
     */
    public int getPermissionCount() {
        return permissions.size();
    }

    /**
     * 获取按类别分组的权限
     */
    public Map<String, List<NationPermission>> getPermissionsByCategory() {
        Map<String, List<NationPermission>> result = new HashMap<>();

        result.put("城市管理", filterPermissions(NationPermission.getCityPermissions()));
        result.put("领地管理", filterPermissions(NationPermission.getTerritoryPermissions()));
        result.put("成员管理", filterPermissions(NationPermission.getMemberPermissions()));
        result.put("外交权限", filterPermissions(NationPermission.getDiplomacyPermissions()));
        result.put("战争权限", filterPermissions(NationPermission.getWarPermissions()));
        result.put("经济权限", filterPermissions(NationPermission.getEconomyPermissions()));
        result.put("军事权限", filterPermissions(NationPermission.getMilitaryPermissions()));

        return result;
    }

    private List<NationPermission> filterPermissions(NationPermission[] category) {
        List<NationPermission> result = new ArrayList<>();
        for (NationPermission perm : category) {
            if (permissions.contains(perm)) {
                result.add(perm);
            }
        }
        return result;
    }

    // ==================== 排序 ====================

    /**
     * 按优先级排序（高优先级在前）
     * 优先级相同时按权限数量排序
     */
    @Override
    public int compareTo(NationRank other) {
        // 优先级高的排前面
        int priorityCompare = Integer.compare(other.priority, this.priority);
        if (priorityCompare != 0) {
            return priorityCompare;
        }

        // 权限多的排前面
        return Integer.compare(other.permissions.size(), this.permissions.size());
    }

    // ==================== 工具方法 ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NationRank)) return false;
        NationRank that = (NationRank) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return displayName + " (" + permissions.size() + " 权限)";
    }

    /**
     * 克隆Rank
     */
    public NationRank clone() {
        return new NationRank(
            this.name,
            this.displayName,
            new HashSet<>(this.permissions),
            this.priority
        );
    }

    // ==================== 预设Rank ====================

    /**
     * 新兵Rank
     */
    public static NationRank createRecruit() {
        return new NationRank(
            "recruit",
            ColorCodes.GRAY + "新兵",
            Set.of(
                NationPermission.SPAWN_TP,
                NationPermission.CHAT_NATION,
                NationPermission.BANK_VIEW
            ),
            1
        );
    }

    /**
     * 外交官Rank
     */
    public static NationRank createDiplomat() {
        return new NationRank(
            "diplomat",
            ColorCodes.AQUA + "外交官",
            Set.of(
                NationPermission.ALLY_ADD,
                NationPermission.ALLY_REMOVE,
                NationPermission.ENEMY_ADD,
                NationPermission.ENEMY_REMOVE,
                NationPermission.ANNOUNCEMENT,
                NationPermission.SPAWN_TP,
                NationPermission.CHAT_NATION,
                NationPermission.BANK_VIEW
            ),
            3
        );
    }

    /**
     * 财政官Rank
     */
    public static NationRank createTreasurer() {
        return new NationRank(
            "treasurer",
            ColorCodes.GOLD + "财政官",
            Set.of(
                NationPermission.BANK_DEPOSIT,
                NationPermission.BANK_WITHDRAW,
                NationPermission.BANK_VIEW,
                NationPermission.TAX_SET,
                NationPermission.TAX_COLLECT,
                NationPermission.SPAWN_TP,
                NationPermission.CHAT_NATION
            ),
            4
        );
    }

    /**
     * 将军Rank
     */
    public static NationRank createGeneral() {
        return new NationRank(
            "general",
            ColorCodes.RED + "将军",
            Set.of(
                NationPermission.WAR_DECLARE,
                NationPermission.WAR_END,
                NationPermission.WAR_OCCUPY,
                NationPermission.WAR_SIEGE,
                NationPermission.ARMY_CREATE,
                NationPermission.ARMY_DISBAND,
                NationPermission.ARMY_COMMAND,
                NationPermission.ARMY_RECRUIT,
                NationPermission.SPAWN_TP,
                NationPermission.CHAT_NATION,
                NationPermission.BANK_VIEW
            ),
            5
        );
    }

    /**
     * 建筑师Rank
     */
    public static NationRank createArchitect() {
        return new NationRank(
            "architect",
            ColorCodes.YELLOW + "建筑师",
            Set.of(
                NationPermission.TERRITORY_CLAIM,
                NationPermission.TERRITORY_UNCLAIM,
                NationPermission.TERRITORY_SET_TYPE,
                NationPermission.TERRITORY_RENAME,
                NationPermission.TERRITORY_SET_SPAWN,
                NationPermission.TERRITORY_PERMISSIONS,
                NationPermission.CITY_CREATE,
                NationPermission.CITY_RENAME,
                NationPermission.SPAWN_TP,
                NationPermission.CHAT_NATION,
                NationPermission.BANK_VIEW
            ),
            4
        );
    }

    /**
     * 获取所有预设Rank
     */
    public static List<NationRank> getStarterRanks() {
        return List.of(
            createRecruit(),
            createDiplomat(),
            createTreasurer(),
            createGeneral(),
            createArchitect()
        );
    }

    /**
     * 从配置创建Rank
     */
    public static NationRank fromConfig(String name, String displayName, List<String> permissionKeys) {
        Set<NationPermission> permissions = new HashSet<>();

        for (String key : permissionKeys) {
            NationPermission perm = NationPermission.fromConfigKey(key);
            if (perm != null) {
                permissions.add(perm);
            }
        }

        return new NationRank(name, displayName, permissions);
    }
}
