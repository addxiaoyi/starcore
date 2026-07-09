package dev.starcore.starcore.event.random;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 预设事件注册中心
 * 定义所有游戏中的高级事件
 */
public class AdvancedEventRegistry {

    // 事件注册表 - 使用ConcurrentHashMap保证线程安全
    private final Map<String, AdvancedEvent> events = new ConcurrentHashMap<>();

    public AdvancedEventRegistry() {
        registerCommonEvents();
        registerUncommonEvents();
        registerRareEvents();
        registerEpicEvents();
        registerLegendaryEvents();
    }

    // ==================== 普通事件 ====================
    private void registerCommonEvents() {
        // 阶段1-3可触发
        events.put("common_windfall", AdvancedEvent.builder()
            .id("common_windfall")
            .name("意外之财")
            .description("在野外发现了一袋金币！")
            .rarity(EventRarity.COMMON)
            .minStage(1)
            .condition(EventCondition.chance(0.3))
            .effect(EventEffect.message("§a你发现了一袋意外之财！"))
            .build());

        events.put("common_weather_clear", AdvancedEvent.builder()
            .id("common_weather_clear")
            .name("天气转晴")
            .description("持续的阴天终于过去，阳光再次照耀大地")
            .rarity(EventRarity.COMMON)
            .minStage(1)
            .condition(EventCondition.chance(0.4))
            .effect(EventEffect.message("§e天空放晴了！"))
            .build());

        events.put("common_wild_animal", AdvancedEvent.builder()
            .id("common_wild_animal")
            .name("野生动物出没")
            .description("一只野生动物出现在附近")
            .rarity(EventRarity.COMMON)
            .minStage(1)
            .condition(EventCondition.chance(0.2))
            .effect(EventEffect.message("§7一只鹿跑过你的面前"))
            .build());
    }

    // ==================== 稀有事件 ====================
    private void registerUncommonEvents() {
        // 阶段2+可触发
        events.put("uncommon_happy_worker", AdvancedEvent.builder()
            .id("uncommon_happy_worker")
            .name("工人效率提升")
            .description("工人们今天心情不错，工作效率大幅提升")
            .rarity(EventRarity.UNCOMMON)
            .minStage(2)
            .condition(EventCondition.and(
                EventCondition.stageGreaterThan(2),
                EventCondition.peaceful()
            ))
            .effect(EventEffect.message("§a工人们干劲十足！"))
            .effect(EventEffect.moraleChange(5))
            .build());

        events.put("uncommon_trader_arrival", AdvancedEvent.builder()
            .id("uncommon_trader_arrival")
            .name("商人到来")
            .description("一位商人带着珍稀货物到达")
            .rarity(EventRarity.UNCOMMON)
            .minStage(2)
            .condition(EventCondition.stageGreaterThan(1))
            .effect(EventEffect.message("§6一位商人带来了稀有商品！"))
            .build());

        events.put("uncommon_rain", AdvancedEvent.builder()
            .id("uncommon_rain")
            .name("及时雨")
            .description("庄稼得到了滋润的雨水")
            .rarity(EventRarity.UNCOMMON)
            .minStage(2)
            .condition(EventCondition.territory(5))
            .effect(EventEffect.message("§b庄稼获得了雨水的滋润！"))
            .effect(EventEffect.buff("农业丰收", 3600, 1.2))
            .build());
    }

    // ==================== 罕见事件 ====================
    private void registerRareEvents() {
        // 阶段4+可触发
        events.put("rare_prosperity_boom", AdvancedEvent.builder()
            .id("rare_prosperity_boom")
            .name("繁荣期到来")
            .description("国家进入了一段繁荣发展期")
            .rarity(EventRarity.RARE)
            .minStage(4)
            .condition(EventCondition.and(
                EventCondition.stageGreaterThan(3),
                EventCondition.peaceful(),
                EventCondition.treasuryGreaterThan(10000)
            ))
            .effect(EventEffect.playerMessage("§6§l【繁荣期】你的国家进入了繁荣发展期！", "全服进入繁荣期"))
            .effect(EventEffect.buff("繁荣期", 7200, 1.5))
            .sound("ui.toast.challenge_complete")
            .build());

        events.put("rare_spies_detected", AdvancedEvent.builder()
            .id("rare_spies_detected")
            .name("发现间谍")
            .description("情报机构发现了敌对国家的间谍活动")
            .rarity(EventRarity.RARE)
            .minStage(4)
            .condition(EventCondition.and(
                EventCondition.stageGreaterThan(3),
                EventCondition.atWar()
            ))
            .effect(EventEffect.playerMessage("§c【情报】发现了敌对势力的间谍！", null))
            .effect(EventEffect.moraleChange(-10))
            .build());

        events.put("rare_natural_disaster_warning", AdvancedEvent.builder()
            .id("rare_natural_disaster_warning")
            .name("自然灾害预警")
            .description("气象部门发出自然灾害预警")
            .rarity(EventRarity.RARE)
            .minStage(4)
            .condition(EventCondition.stageGreaterThan(3))
            .effect(EventEffect.message("§e⚠️ 自然灾害预警！"))
            .effect(EventEffect.announce(EventRarity.RARE, "自然灾害即将来临，请做好准备！"))
            .build());

        events.put("rare_technology_breakthrough", AdvancedEvent.builder()
            .id("rare_technology_breakthrough")
            .name("科技突破")
            .description("研究人员取得了重大突破")
            .rarity(EventRarity.RARE)
            .minStage(4)
            .condition(EventCondition.stageGreaterThan(3))
            .effect(EventEffect.playerMessage("§b§l【科技突破】", "全服科技突破"))
            .build());
    }

    // ==================== 史诗事件 ====================
    private void registerEpicEvents() {
        // 阶段6+触发
        events.put("epic_mass_uprising", AdvancedEvent.builder()
            .id("epic_mass_uprising")
            .name("农民起义")
            .description("税收过重引发了农民起义！")
            .rarity(EventRarity.EPIC)
            .minStage(6)
            .condition(EventCondition.and(
                EventCondition.stageGreaterThan(5),
                EventCondition.treasuryGreaterThan(50000)
            ))
            .effect(EventEffect.playerMessage("§4§l【危机】农民起义爆发！", "全服爆发农民起义"))
            .effect(EventEffect.debuff("起义镇压", 3600, -0.3))
            .sound("entity.wither.break")
            .build());

        events.put("epic_plague", AdvancedEvent.builder()
            .id("epic_plague")
            .name("瘟疫蔓延")
            .description("一场可怕的瘟疫正在全国蔓延")
            .rarity(EventRarity.EPIC)
            .minStage(6)
            .condition(EventCondition.and(
                EventCondition.stageGreaterThan(5),
                EventCondition.onlinePlayers(5)
            ))
            .effect(EventEffect.announce(EventRarity.EPIC, "瘟疫正在蔓延！"))
            .effect(EventEffect.debuff("瘟疫", 7200, -0.5))
            .build());

        events.put("epic_foreign_diplomacy", AdvancedEvent.builder()
            .id("epic_foreign_diplomacy")
            .name("外交突破")
            .description("与远方国家建立了外交关系")
            .rarity(EventRarity.EPIC)
            .minStage(6)
            .condition(EventCondition.stageGreaterThan(5))
            .effect(EventEffect.playerMessage("§9§l【外交】远方国家派来了使者！", null))
            .effect(EventEffect.buff("外交突破", 3600, 1.3))
            .build());

        events.put("epic_army_reform", AdvancedEvent.builder()
            .id("epic_army_reform")
            .name("军事改革")
            .description("军事改革提升了军队战斗力")
            .rarity(EventRarity.EPIC)
            .minStage(6)
            .condition(EventCondition.and(
                EventCondition.stageGreaterThan(5),
                EventCondition.atWar()
            ))
            .effect(EventEffect.playerMessage("§c§l【军事改革】", "全服军事改革"))
            .effect(EventEffect.buff("军事改革", 5400, 1.5))
            .build());
    }

    // ==================== 传说事件 ====================
    private void registerLegendaryEvents() {
        // 阶段8+触发
        events.put("legendary_national_hero", AdvancedEvent.builder()
            .id("legendary_national_hero")
            .name("民族英雄诞生")
            .description("一位英雄横空出世，带领国家走向辉煌")
            .rarity(EventRarity.LEGENDARY)
            .minStage(8)
            .condition(EventCondition.stageGreaterThan(7))
            .effect(EventEffect.announce(EventRarity.LEGENDARY, "一位民族英雄诞生了！"))
            .effect(EventEffect.buff("民族英雄祝福", 10800, 2.0))
            .effect(EventEffect.moraleChange(50))
            .sound("entity.ender_dragon.flap")
            .build());

        events.put("legendary_golden_age", AdvancedEvent.builder()
            .id("legendary_golden_age")
            .name("黄金时代")
            .description("国家进入了前所未有的黄金时代")
            .rarity(EventRarity.LEGENDARY)
            .minStage(8)
            .condition(EventCondition.and(
                EventCondition.stageGreaterThan(7),
                EventCondition.prosperous(),
                EventCondition.peaceful()
            ))
            .effect(EventEffect.announce(EventRarity.LEGENDARY, "全服进入了黄金时代！"))
            .effect(EventEffect.buff("黄金时代", 14400, 3.0))
            .effect(EventEffect.treasuryChange(100000))
            .sound("entity.ender_dragon.growl")
            .build());

        events.put("legendary_cataclysm", AdvancedEvent.builder()
            .id("legendary_cataclysm")
            .name("天灾降临")
            .description("一场毁灭性的天灾席卷全国")
            .rarity(EventRarity.LEGENDARY)
            .minStage(8)
            .condition(EventCondition.stageGreaterThan(7))
            .effect(EventEffect.announce(EventRarity.LEGENDARY, "天灾降临！请立即采取行动！"))
            .effect(EventEffect.debuff("天灾", 10800, -0.8))
            .effect(EventEffect.moraleChange(-30))
            .sound("entity.wither.spawn")
            .persistent()
            .build());

        events.put("legendary_founding", AdvancedEvent.builder()
            .id("legendary_founding")
            .name("开国大典")
            .description("纪念国家建立的盛大庆典")
            .rarity(EventRarity.LEGENDARY)
            .minStage(8)
            .condition(EventCondition.stageGreaterThan(7))
            .effect(EventEffect.announce(EventRarity.LEGENDARY, "举国欢庆开国大典！"))
            .effect(EventEffect.buff("开国荣光", 7200, 2.5))
            .effect(EventEffect.moraleChange(100))
            .build());
    }

    public Collection<AdvancedEvent> getAll() {
        return events.values();
    }

    public AdvancedEvent get(String id) {
        return events.get(id);
    }

    public List<AdvancedEvent> getByRarity(EventRarity rarity) {
        return events.values().stream()
            .filter(e -> e.getRarity() == rarity)
            .toList();
    }

    public List<AdvancedEvent> getByMinStage(int stage) {
        return events.values().stream()
            .filter(e -> e.getMinStageLevel() <= stage)
            .toList();
    }
}
