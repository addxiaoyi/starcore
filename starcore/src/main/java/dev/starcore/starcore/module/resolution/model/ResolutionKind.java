package dev.starcore.starcore.module.resolution.model;

public enum ResolutionKind {
    JOIN_NATION,
    RENAME_NATION,
    CHANGE_GOVERNMENT,
    CHANGE_DIPLOMACY_RELATION,
    // Extended resolution types
    DISBAND_NATION,         // 国家解散
    TRANSFER_TERRITORY,     // 领土转让
    DECLARE_WAR,            // 宣战
    END_WAR,                // 停战
    ADJUST_TAX,             // 税收调整
    APPOINT_OFFICER,        // 官员任命
    REMOVE_OFFICER,         // 移除官员
    CHANGE_POLICY,          // 政策变更
    UNLOCK_TECHNOLOGY,     // 解锁科技
    CREATE_CITY,           // 创建城市
    BANKRUPTCY_EXIT       // 破产退出决议
}
