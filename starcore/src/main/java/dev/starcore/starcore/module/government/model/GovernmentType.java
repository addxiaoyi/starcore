package dev.starcore.starcore.module.government.model;

import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.resolution.model.Resolution;
import dev.starcore.starcore.module.resolution.model.ResolutionAction;
import dev.starcore.starcore.module.resolution.model.ResolutionKind;

import java.util.UUID;

public enum GovernmentType {
    MONARCHY("君主制") {
        @Override
        public boolean mayPropose(Nation nation, UUID proposerId, ResolutionAction action) {
            return action.kind() == ResolutionKind.JOIN_NATION || nation.founderId().equals(proposerId);
        }

        @Override
        public boolean maySign(Nation nation, UUID signerId, Resolution resolution) {
            return nation.founderId().equals(signerId);
        }

        @Override
        public boolean passes(Nation nation, Resolution resolution) {
            return resolution.signatures().contains(nation.founderId());
        }

        @Override
        public int requiredSignatures(Nation nation, Resolution resolution) {
            return 1;
        }
    },
    DICTATORSHIP("独裁制") {
        @Override
        public boolean mayPropose(Nation nation, UUID proposerId, ResolutionAction action) {
            return action.kind() == ResolutionKind.JOIN_NATION || nation.founderId().equals(proposerId);
        }

        @Override
        public boolean maySign(Nation nation, UUID signerId, Resolution resolution) {
            return nation.founderId().equals(signerId);
        }

        @Override
        public boolean passes(Nation nation, Resolution resolution) {
            return resolution.signatures().contains(nation.founderId());
        }

        @Override
        public int requiredSignatures(Nation nation, Resolution resolution) {
            return 1;
        }
    },
    REPUBLIC("共和国") {
        @Override
        public boolean mayPropose(Nation nation, UUID proposerId, ResolutionAction action) {
            return action.kind() == ResolutionKind.JOIN_NATION || nation.hasMember(proposerId);
        }

        @Override
        public boolean maySign(Nation nation, UUID signerId, Resolution resolution) {
            return nation.hasMember(signerId);
        }

        @Override
        public boolean passes(Nation nation, Resolution resolution) {
            int eligible = Math.max(1, nation.members().size());
            return resolution.signatures().size() >= Math.max(1, eligible / 2);
        }

        @Override
        public int requiredSignatures(Nation nation, Resolution resolution) {
            return Math.max(1, nation.members().size() / 2);
        }
    },
    DEMOCRACY("民主制") {
        @Override
        public boolean mayPropose(Nation nation, UUID proposerId, ResolutionAction action) {
            return action.kind() == ResolutionKind.JOIN_NATION || nation.hasMember(proposerId);
        }

        @Override
        public boolean maySign(Nation nation, UUID signerId, Resolution resolution) {
            return nation.hasMember(signerId);
        }

        @Override
        public boolean passes(Nation nation, Resolution resolution) {
            int eligible = Math.max(1, nation.members().size());
            return resolution.signatures().size() >= (eligible / 2) + 1;
        }

        @Override
        public int requiredSignatures(Nation nation, Resolution resolution) {
            return (nation.members().size() / 2) + 1;
        }
    },
    THEOCRACY("神权制") {
        @Override
        public boolean mayPropose(Nation nation, UUID proposerId, ResolutionAction action) {
            // 只有神职人员或君主可以提案
            return action.kind() == ResolutionKind.JOIN_NATION || nation.founderId().equals(proposerId);
        }

        @Override
        public boolean maySign(Nation nation, UUID signerId, Resolution resolution) {
            // 神权制只需要创始人签署即可通过
            return nation.founderId().equals(signerId);
        }

        @Override
        public boolean passes(Nation nation, Resolution resolution) {
            return resolution.signatures().contains(nation.founderId());
        }

        @Override
        public int requiredSignatures(Nation nation, Resolution resolution) {
            return 1;
        }
    },
    OLIGARCHY("寡头制") {
        @Override
        public boolean mayPropose(Nation nation, UUID proposerId, ResolutionAction action) {
            // 寡头制：任何成员都可以提案
            return action.kind() == ResolutionKind.JOIN_NATION || nation.hasMember(proposerId);
        }

        @Override
        public boolean maySign(Nation nation, UUID signerId, Resolution resolution) {
            // 寡头制：任何成员都可以签署
            return nation.hasMember(signerId);
        }

        @Override
        public boolean passes(Nation nation, Resolution resolution) {
            // 寡头制需要多数签名通过（类似共和制但更严格）
            int eligible = Math.max(1, nation.members().size());
            int required = (int) Math.ceil(eligible * 0.6); // 60%通过率
            return resolution.signatures().size() >= required;
        }

        @Override
        public int requiredSignatures(Nation nation, Resolution resolution) {
            return (int) Math.ceil(Math.max(1, nation.members().size()) * 0.6);
        }
    };

    private final String displayName;

    GovernmentType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public abstract boolean mayPropose(Nation nation, UUID proposerId, ResolutionAction action);

    public abstract boolean maySign(Nation nation, UUID signerId, Resolution resolution);

    public abstract boolean passes(Nation nation, Resolution resolution);

    public abstract int requiredSignatures(Nation nation, Resolution resolution);
}
