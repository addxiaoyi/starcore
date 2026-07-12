package dev.starcore.starcore.module.war;

import dev.starcore.starcore.module.nation.model.NationId;

import java.util.Collection;
import java.util.Optional;

public interface WarService {
    boolean declareWar(NationId left, NationId right);

    boolean endWar(NationId left, NationId right);

    boolean atWar(NationId left, NationId right);

    Collection<WarSnapshot> activeWars();

    Collection<WarSnapshot> activeWarsOf(NationId nationId);

    /**
     * 获取战争历史记录
     */
    Collection<WarSnapshot> warHistory(NationId nationId);

    /**
     * 查找活跃战争
     */
    Optional<WarSnapshot> findActiveWar(NationId nation1, NationId nation2);

    String summary();
}
