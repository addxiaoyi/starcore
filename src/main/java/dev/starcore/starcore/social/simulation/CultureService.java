package dev.starcore.starcore.social.simulation;

import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;

import java.util.*;

/**
 * 文化服务接口
 *
 * 管理国家文化发展:
 * - 文化值积累
 * - 文化特质
 * - 文化传播
 * - 文化冲突
 */
public interface CultureService {

    /**
     * 获取国家的文化值
     */
    int getCulturePoints(NationId nationId);

    /**
     * 获取国家的文化特质
     */
    List<CultureTrait> getTraits(NationId nationId);

    /**
     * 增加文化值
     */
    void addCulturePoints(NationId nationId, int amount, CultureCategory category);

    /**
     * 获取文化等级
     */
    CultureLevel getLevel(NationId nationId);

    /**
     * 检查文化是否可以传播
     */
    boolean canSpreadCulture(NationId from, NationId to);

    /**
     * 传播文化到邻国
     */
    void spreadCulture(NationId from, NationId to);

    /**
     * 文化冲突检测
     */
    boolean hasCultureConflict(NationId nation1, NationId nation2);

    /**
     * 加载所有文化数据
     */
    void loadCultures();

    /**
     * 保存所有文化数据
     */
    void saveCultures();

    enum CultureCategory {
        ART("艺术"),
        SCIENCE("科学"),
        MILITARY("军事"),
        TRADE("商业"),
        RELIGION("宗教"),
        POLITICS("政治");

        private final String name;
        CultureCategory(String name) { this.name = name; }
        public String getName() { return name; }
    }

    enum CultureLevel {
        BARBARIC(0, "§4", "野蛮"),
        CIVILIZED(100, "§a", "文明"),
        DEVELOPED(500, "§e", "发达"),
        FLOURISHING(2000, "§b", "繁荣"),
        GOLDEN_AGE(10000, "§6", "黄金时代"),
        LEGENDARY(50000, "§d", "传奇");

        private final int threshold;
        private final String color;
        private final String name;

        CultureLevel(int threshold, String color, String name) {
            this.threshold = threshold;
            this.color = color;
            this.name = name;
        }

        public int getThreshold() { return threshold; }
        public String getColor() { return color; }
        public String getName() { return name; }

        public static CultureLevel fromPoints(int points) {
            CultureLevel[] values = values();
            for (int i = values.length - 1; i >= 0; i--) {
                if (points >= values[i].threshold) {
                    return values[i];
                }
            }
            return BARBARIC;
        }
    }

    record CultureTrait(
        String id,
        String name,
        String description,
        CultureCategory category,
        int level,
        Set<String> bonuses
    ) {
        public String getDisplayName() { return name; }
    }
}
