package dev.starcore.starcore.nation.permission;

/**
 * Nation权限枚举系统
 * 基于SimpleClans的39种标准权限节点
 *
 * 三层权限模型：
 * 1. Bukkit权限层（插件权限管理）
 * 2. 职位层级（Leader/Trusted/Member）
 * 3. Rank自定义权限（灵活配置）
 */
public enum NationPermission {

    // ==================== 城市管理 (5) ====================
    CITY_CREATE(
        "starcore.nation.city.create",
        "创建城市",
        PermissionLevel.LEADER
    ),
    CITY_REMOVE(
        "starcore.nation.city.remove",
        "移除城市",
        PermissionLevel.LEADER
    ),
    CITY_RENAME(
        "starcore.nation.city.rename",
        "重命名城市",
        PermissionLevel.LEADER
    ),
    CITY_SET_CAPITAL(
        "starcore.nation.city.setcapital",
        "设置首都",
        PermissionLevel.LEADER
    ),
    CITY_TRANSFER(
        "starcore.nation.city.transfer",
        "转让城市",
        PermissionLevel.LEADER
    ),

    // ==================== 领地管理 (6) ====================
    TERRITORY_CLAIM(
        "starcore.nation.territory.claim",
        "领取领地",
        PermissionLevel.TRUSTED
    ),
    TERRITORY_UNCLAIM(
        "starcore.nation.territory.unclaim",
        "放弃领地",
        PermissionLevel.TRUSTED
    ),
    TERRITORY_SET_TYPE(
        "starcore.nation.territory.settype",
        "设置领地类型",
        PermissionLevel.TRUSTED
    ),
    TERRITORY_RENAME(
        "starcore.nation.territory.rename",
        "重命名领地",
        PermissionLevel.TRUSTED
    ),
    TERRITORY_SET_SPAWN(
        "starcore.nation.territory.setspawn",
        "设置领地出生点",
        PermissionLevel.TRUSTED
    ),
    TERRITORY_PERMISSIONS(
        "starcore.nation.territory.permissions",
        "修改领地权限",
        PermissionLevel.TRUSTED
    ),

    // ==================== 成员管理 (6) ====================
    MEMBER_INVITE(
        "starcore.nation.member.invite",
        "邀请成员",
        PermissionLevel.TRUSTED
    ),
    MEMBER_KICK(
        "starcore.nation.member.kick",
        "踢出成员",
        PermissionLevel.LEADER
    ),
    MEMBER_PROMOTE(
        "starcore.nation.member.promote",
        "提升职位",
        PermissionLevel.LEADER
    ),
    MEMBER_DEMOTE(
        "starcore.nation.member.demote",
        "降低职位",
        PermissionLevel.LEADER
    ),
    MEMBER_BAN(
        "starcore.nation.member.ban",
        "封禁玩家",
        PermissionLevel.LEADER
    ),
    MEMBER_UNBAN(
        "starcore.nation.member.unban",
        "解封玩家",
        PermissionLevel.LEADER
    ),

    // ==================== 外交权限 (4) ====================
    ALLY_ADD(
        "starcore.nation.ally.add",
        "添加盟友",
        PermissionLevel.LEADER
    ),
    ALLY_REMOVE(
        "starcore.nation.ally.remove",
        "移除盟友",
        PermissionLevel.LEADER
    ),
    ENEMY_ADD(
        "starcore.nation.enemy.add",
        "宣布敌对",
        PermissionLevel.LEADER
    ),
    ENEMY_REMOVE(
        "starcore.nation.enemy.remove",
        "解除敌对",
        PermissionLevel.LEADER
    ),

    // ==================== 战争权限 (5) ====================
    WAR_DECLARE(
        "starcore.nation.war.declare",
        "宣战",
        PermissionLevel.LEADER
    ),
    WAR_END(
        "starcore.nation.war.end",
        "停战",
        PermissionLevel.LEADER
    ),
    RAID_INITIATE(
        "starcore.nation.raid.initiate",
        "发动突袭",
        PermissionLevel.LEADER
    ),
    WAR_OCCUPY(
        "starcore.nation.war.occupy",
        "占领城市",
        PermissionLevel.TRUSTED
    ),
    WAR_SIEGE(
        "starcore.nation.war.siege",
        "攻城",
        PermissionLevel.TRUSTED
    ),
    WAR_SURRENDER(
        "starcore.nation.war.surrender",
        "投降",
        PermissionLevel.LEADER
    ),

    // ==================== 经济权限 (5) ====================
    // TODO audit A-031: BANK_DEPOSIT 默认 MEMBER 直接放行，无金额上限/日限配置。
    //   成员可恶意存入巨款再被创始人取走；后续应增加 BANK_DEPOSIT_THRESHOLD 与审核流程。
    BANK_DEPOSIT(
        "starcore.nation.bank.deposit",
        "存款",
        PermissionLevel.MEMBER
    ),
    BANK_WITHDRAW(
        "starcore.nation.bank.withdraw",
        "取款",
        PermissionLevel.LEADER
    ),
    BANK_VIEW(
        "starcore.nation.bank.view",
        "查看余额",
        PermissionLevel.MEMBER
    ),
    TAX_SET(
        "starcore.nation.tax.set",
        "设置税率",
        PermissionLevel.LEADER
    ),
    TAX_COLLECT(
        "starcore.nation.tax.collect",
        "收取税款",
        PermissionLevel.LEADER
    ),

    // ==================== 军事权限 (4) ====================
    ARMY_CREATE(
        "starcore.nation.army.create",
        "创建军队",
        PermissionLevel.LEADER
    ),
    ARMY_DISBAND(
        "starcore.nation.army.disband",
        "解散军队",
        PermissionLevel.LEADER
    ),
    ARMY_COMMAND(
        "starcore.nation.army.command",
        "指挥军队",
        PermissionLevel.TRUSTED
    ),
    ARMY_RECRUIT(
        "starcore.nation.army.recruit",
        "招募士兵",
        PermissionLevel.TRUSTED
    ),

    // ==================== 俘虏管理 (2) ====================
    PRISONER_RELEASE(
        "starcore.nation.prisoner.release",
        "释放俘虏",
        PermissionLevel.TRUSTED
    ),
    PRISONER_EXECUTE(
        "starcore.nation.prisoner.execute",
        "处决俘虏",
        PermissionLevel.LEADER
    ),

    // ==================== 特殊权限 (4) ====================
    SPAWN_SET(
        "starcore.nation.spawn.set",
        "设置出生点",
        PermissionLevel.LEADER
    ),
    SPAWN_TP(
        "starcore.nation.spawn.tp",
        "传送出生点",
        PermissionLevel.MEMBER
    ),
    ANNOUNCEMENT(
        "starcore.nation.announcement",
        "发布公告",
        PermissionLevel.TRUSTED
    ),
    CHAT_NATION(
        "starcore.nation.chat",
        "国家频道",
        PermissionLevel.MEMBER
    ),

    // ==================== 配置权限 (5) ====================
    SETTINGS_MODIFY(
        "starcore.nation.settings.modify",
        "修改设置",
        PermissionLevel.LEADER
    ),

    // ==================== 政策权限 (2) ====================
    POLICY_CREATE(
        "starcore.nation.policy.create",
        "创建政策",
        PermissionLevel.LEADER
    ),
    POLICY_ACTIVATE(
        "starcore.nation.policy.activate",
        "激活政策",
        PermissionLevel.LEADER
    ),
    RANK_CREATE(
        "starcore.nation.rank.create",
        "创建职位",
        PermissionLevel.LEADER
    ),
    RANK_DELETE(
        "starcore.nation.rank.delete",
        "删除职位",
        PermissionLevel.LEADER
    ),
    RANK_ASSIGN(
        "starcore.nation.rank.assign",
        "分配职位",
        PermissionLevel.LEADER
    ),
    RANK_MODIFY(
        "starcore.nation.rank.modify",
        "修改职位权限",
        PermissionLevel.LEADER
    ),

    // ==================== 合并/分裂权限 (4) ====================
    MERGE_PROPOSE(
        "starcore.nation.merge.propose",
        "发起合并公投",
        PermissionLevel.LEADER
    ),
    MERGE_VOTE(
        "starcore.nation.merge.vote",
        "投票合并公投",
        PermissionLevel.TRUSTED
    ),
    SPLIT_PROPOSE(
        "starcore.nation.split.propose",
        "发起分裂请求",
        PermissionLevel.LEADER
    ),
    SPLIT_APPROVE(
        "starcore.nation.split.approve",
        "批准分裂请求",
        PermissionLevel.LEADER
    ),

    // ==================== 高级权限 (3) ====================
    NATION_DISBAND(
        "starcore.nation.disband",
        "解散国家",
        PermissionLevel.FOUNDER
    ),
    NATION_RENAME(
        "starcore.nation.rename",
        "更名",
        PermissionLevel.LEADER
    ),
    NATION_TRANSFER(
        "starcore.nation.transfer",
        "转让国家",
        PermissionLevel.FOUNDER
    );

    // ==================== 字段 ====================

    private final String bukkitNode;
    private final String description;
    private final PermissionLevel defaultLevel;

    // ==================== 构造函数 ====================

    NationPermission(String bukkitNode, String description, PermissionLevel defaultLevel) {
        this.bukkitNode = bukkitNode;
        this.description = description;
        this.defaultLevel = defaultLevel;
    }

    // ==================== Getter ====================

    public String getBukkitNode() {
        return bukkitNode;
    }

    public String getDescription() {
        return description;
    }

    public PermissionLevel getDefaultLevel() {
        return defaultLevel;
    }

    /**
     * 获取配置文件键名
     * 例如：CITY_CREATE -> "city.create"
     */
    public String getConfigKey() {
        return name().toLowerCase().replace('_', '.');
    }

    /**
     * 从配置键获取权限
     */
    public static NationPermission fromConfigKey(String key) {
        String enumName = key.toUpperCase().replace('.', '_');
        try {
            return valueOf(enumName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ==================== 权限组 ====================

    /**
     * 获取所有城市管理权限
     */
    public static NationPermission[] getCityPermissions() {
        return new NationPermission[]{
            CITY_CREATE, CITY_REMOVE, CITY_RENAME,
            CITY_SET_CAPITAL, CITY_TRANSFER
        };
    }

    /**
     * 获取所有领地管理权限
     */
    public static NationPermission[] getTerritoryPermissions() {
        return new NationPermission[]{
            TERRITORY_CLAIM, TERRITORY_UNCLAIM, TERRITORY_SET_TYPE,
            TERRITORY_RENAME, TERRITORY_SET_SPAWN, TERRITORY_PERMISSIONS
        };
    }

    /**
     * 获取所有成员管理权限
     */
    public static NationPermission[] getMemberPermissions() {
        return new NationPermission[]{
            MEMBER_INVITE, MEMBER_KICK, MEMBER_PROMOTE,
            MEMBER_DEMOTE, MEMBER_BAN, MEMBER_UNBAN
        };
    }

    /**
     * 获取所有外交权限
     */
    public static NationPermission[] getDiplomacyPermissions() {
        return new NationPermission[]{
            ALLY_ADD, ALLY_REMOVE, ENEMY_ADD, ENEMY_REMOVE
        };
    }

    /**
     * 获取所有战争权限
     */
    public static NationPermission[] getWarPermissions() {
        return new NationPermission[]{
            WAR_DECLARE, WAR_END, WAR_OCCUPY, WAR_SIEGE, WAR_SURRENDER
        };
    }

    /**
     * 获取所有经济权限
     */
    public static NationPermission[] getEconomyPermissions() {
        return new NationPermission[]{
            BANK_DEPOSIT, BANK_WITHDRAW, BANK_VIEW, TAX_SET, TAX_COLLECT
        };
    }

    /**
     * 获取所有军事权限
     */
    public static NationPermission[] getMilitaryPermissions() {
        return new NationPermission[]{
            ARMY_CREATE, ARMY_DISBAND, ARMY_COMMAND, ARMY_RECRUIT
        };
    }

    @Override
    public String toString() {
        return getConfigKey();
    }
}
