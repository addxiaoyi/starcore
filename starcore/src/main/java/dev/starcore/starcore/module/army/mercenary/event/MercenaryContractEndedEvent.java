package dev.starcore.starcore.module.army.mercenary.event;

import dev.starcore.starcore.module.army.mercenary.MercenaryRank;
import dev.starcore.starcore.module.army.mercenary.MercenaryType;
import org.bukkit.event.HandlerList;
import java.util.UUID;

/**
 * 雇佣兵合同结束事件
 */
public final class MercenaryContractEndedEvent extends MercenaryContractEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ContractEndReason reason;
    private final int totalSalaryPaid;
    private final int missionsCompleted;

    public MercenaryContractEndedEvent(
        UUID mercenaryId,
        UUID employerId,
        UUID nationId,
        String mercenaryName,
        String employerName,
        String nationName,
        MercenaryType type,
        MercenaryRank rank,
        ContractEndReason reason,
        int totalSalaryPaid,
        int missionsCompleted
    ) {
        super(mercenaryId, employerId, nationId, mercenaryName, employerName, nationName, type, rank);
        this.reason = reason;
        this.totalSalaryPaid = totalSalaryPaid;
        this.missionsCompleted = missionsCompleted;
    }

    public ContractEndReason getReason() {
        return reason;
    }

    public int getTotalSalaryPaid() {
        return totalSalaryPaid;
    }

    public int getMissionsCompleted() {
        return missionsCompleted;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public enum ContractEndReason {
        COMPLETED("completed", "合同到期完成"),
        TERMINATED_BY_EMPLOYER("terminated_employer", "雇主终止"),
        TERMINATED_BY_MERCENARY("terminated_mercenary", "雇佣兵辞职"),
        DISMISSED("dismissed", "被解雇"),
        DEATH("death", "雇佣兵死亡"),
        NATION_DISBANDED("nation_disbanded", "国家解散");

        private final String key;
        private final String displayName;

        ContractEndReason(String key, String displayName) {
            this.key = key;
            this.displayName = displayName;
        }

        public String key() {
            return key;
        }

        public String displayName() {
            return displayName;
        }
    }
}