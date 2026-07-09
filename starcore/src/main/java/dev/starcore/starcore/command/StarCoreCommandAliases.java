package dev.starcore.starcore.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class StarCoreCommandAliases {
    private static final List<String> ROOT_ARGUMENTS = List.of("h", "?", "s", "status", "mods", "mod", "rl", "n", "res", "gov", "m", "dip", "tr", "po", "rsc", "tech", "w", "off", "ev", "era", "eco",
        "money", "tm", "rt", "time", "dbg", "help", "modules", "reload", "nation", "resolution", "government", "map", "diplomacy", "treasury", "policy", "resource", "technology", "war", "officer", "event", "epoch", "economy", "timesync", "debug");
    private static final List<String> ROOT_ARGUMENTS_ZH = List.of("帮", "帮助", "状", "状态", "模", "模块", "重", "重载", "国", "国家", "议", "决议", "政", "政体", "图", "地图", "交", "外交",
        "库", "金库", "策", "国策", "资", "矿", "资源", "技", "科技", "战", "战争", "官", "官员", "事", "事件", "纪", "纪元", "钱", "经济", "时", "时间", "现实时间", "调", "调试");
    private static final List<String> ROOT_SUGGESTIONS = merged(ROOT_ARGUMENTS, ROOT_ARGUMENTS_ZH);

    private StarCoreCommandAliases() {
    }

    static List<String> rootSuggestions() {
        return ROOT_SUGGESTIONS;
    }

    static String normalizeRoot(String value) {
        return switch (normalizeToken(value)) {
            case "帮", "帮助", "help", "h", "?" -> "help";
            case "状", "状态", "status", "info", "s" -> "status";
            case "模", "模块", "modules", "module", "mods", "mod" -> "modules";
            case "重", "重载", "reload", "rl" -> "reload";
            case "国", "国家", "nation", "n" -> "nation";
            case "议", "决议", "resolution", "resolutions", "res" -> "resolution";
            case "政", "政体", "government", "gov" -> "government";
            case "图", "地图", "map", "m" -> "map";
            case "交", "外交", "diplomacy", "dip" -> "diplomacy";
            case "库", "金库", "treasury", "tr" -> "treasury";
            case "策", "国策", "policy", "po" -> "policy";
            case "资", "矿", "资源", "resource", "rsc" -> "resource";
            case "技", "科技", "technology", "tech" -> "technology";
            case "战", "战争", "war", "w" -> "war";
            case "官", "官员", "officer", "off" -> "officer";
            case "事", "事件", "event", "ev" -> "event";
            case "纪", "纪元", "epoch", "era" -> "epoch";
            case "时", "时间", "现实时间", "同步时间", "time", "timesync", "tm", "rt" -> "time";
            case "钱", "经济", "economy", "money", "eco" -> "economy";
            case "调", "调试", "debug", "dbg" -> "debug";
            default -> normalizeToken(value);
        };
    }

    static String normalizeNationSubcommand(String value) {
        return switch (normalizeToken(value)) {
            case "建", "创建", "create", "new", "c" -> "create";
            case "城", "城邦", "city", "citystate", "city-state", "ct" -> "city";
            case "信", "信息", "info", "i" -> "info";
            case "圈", "圈地", "claim", "cl" -> "claim";
            case "工", "工具", "tool", "t" -> "tool";
            case "确", "确认", "confirm", "ok" -> "confirm";
            case "取", "取消", "cancel", "x" -> "cancel";
            case "放", "放弃", "unclaim", "un" -> "unclaim";
            case "领", "领地", "领地列表", "claims", "claimlist" -> "claims";
            case "此", "此处", "脚下", "here", "h" -> "here";
            case "列", "列表", "list", "ls" -> "list";
            case "加", "加入", "join" -> "join";
            case "改", "改名", "rename", "rn" -> "rename";
            default -> normalizeToken(value);
        };
    }

    static String normalizeEconomySubcommand(String value) {
        return switch (normalizeToken(value)) {
            case "余", "余额", "balance", "bal", "b" -> "balance";
            case "给予", "给", "give", "g" -> "give";
            case "扣除", "扣", "take", "t" -> "take";
            case "设", "设置", "set", "s" -> "set";
            default -> normalizeToken(value);
        };
    }

    static String normalizeResolutionSubcommand(String value) {
        return switch (normalizeToken(value)) {
            case "列", "列表", "list", "ls" -> "list";
            case "签", "签署", "签名", "sign" -> "sign";
            case "取", "取消", "撤回", "cancel", "x" -> "cancel";
            case "信", "信息", "详情", "info", "i", "details" -> "info";
            case "历", "历史记录", "history", "his" -> "history";
            case "菜", "菜单", "gui", "menu" -> "gui";
            default -> normalizeToken(value);
        };
    }

    static String normalizeGovernmentSubcommand(String value) {
        return switch (normalizeToken(value)) {
            case "信", "信息", "info", "i" -> "info";
            case "提", "提案", "提议", "propose", "p" -> "propose";
            default -> normalizeToken(value);
        };
    }

    static String normalizeMapSubcommand(String value) {
        return switch (normalizeToken(value)) {
            case "状", "状态", "status", "s" -> "status";
            case "导", "导出", "export", "ex" -> "export";
            case "网", "网页", "网站", "web", "w" -> "web";
            case "确", "确认", "confirm", "ok" -> "confirm";
            case "取", "取消", "cancel", "x" -> "cancel";
            default -> normalizeToken(value);
        };
    }

    static String normalizeDiplomacySubcommand(String value) {
        return switch (normalizeToken(value)) {
            case "状", "状态", "status", "s" -> "status";
            case "设", "设置", "set" -> "set";
            case "列", "列表", "list", "ls" -> "list";
            case "提", "提案", "提议", "propose", "p" -> "propose";
            default -> normalizeToken(value);
        };
    }

    static String normalizeTreasurySubcommand(String value) {
        return switch (normalizeToken(value)) {
            case "状", "状态", "status", "s" -> "status";
            case "存", "存入", "deposit", "d" -> "deposit";
            case "支", "支出", "取出", "withdraw", "w" -> "withdraw";
            case "收", "收入", "结算", "日收", "income", "inc" -> "income";
            case "奖", "奖励", "任务奖励", "reward", "rw" -> "reward";
            case "税", "税收", "收税", "tax", "tx" -> "tax";
            default -> normalizeToken(value);
        };
    }

    static String normalizePolicySubcommand(String value) {
        return switch (normalizeToken(value)) {
            case "状", "状态", "status", "s" -> "status";
            case "设", "设置", "set" -> "set";
            case "清", "清除", "clear", "x" -> "clear";
            case "树", "菜", "菜单", "国策树", "tree", "gui", "menu", "t" -> "tree";
            default -> normalizeToken(value);
        };
    }

    static String normalizeResourceSubcommand(String value) {
        return switch (normalizeToken(value)) {
            case "状", "状态", "status", "s" -> "status";
            case "区", "区块", "资", "资源区块", "district", "districts", "d" -> "districts";
            case "查", "查看", "检查", "详情", "inspect", "info", "i" -> "inspect";
            case "迁", "迁移", "migrate", "move", "m" -> "migrate";
            case "增", "增加", "发放", "grant", "g" -> "grant";
            case "消", "消耗", "consume", "c" -> "consume";
            default -> normalizeToken(value);
        };
    }

    static String normalizeTechnologySubcommand(String value) {
        return switch (normalizeToken(value)) {
            case "状", "状态", "status", "s" -> "status";
            case "解", "解锁", "unlock", "u" -> "unlock";
            case "撤", "撤销", "移除", "revoke", "x" -> "revoke";
            case "树", "菜", "菜单", "科技树", "tree", "gui", "menu", "t" -> "tree";
            default -> normalizeToken(value);
        };
    }

    static String normalizeWarSubcommand(String value) {
        return switch (normalizeToken(value)) {
            case "状", "状态", "status", "s" -> "status";
            case "宣", "宣战", "declare", "d" -> "declare";
            case "停", "停战", "结束", "end", "e" -> "end";
            default -> normalizeToken(value);
        };
    }

    static String normalizeOfficerSubcommand(String value) {
        return switch (normalizeToken(value)) {
            case "状", "状态", "status", "s" -> "status";
            case "任", "任命", "appoint", "a" -> "appoint";
            case "移", "移除", "remove", "rm" -> "remove";
            default -> normalizeToken(value);
        };
    }

    static String normalizeSimple(String value) {
        return switch (normalizeToken(value)) {
            case "状", "状态", "status", "s" -> "status";
            case "建", "创建", "create", "c" -> "create";
            case "列", "列表", "list", "ls" -> "list";
            default -> normalizeToken(value);
        };
    }

    static String normalizeToken(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static List<String> merged(List<String> first, List<String> second) {
        List<String> merged = new ArrayList<>(first.size() + second.size());
        merged.addAll(first);
        merged.addAll(second);
        return List.copyOf(merged);
    }
}
