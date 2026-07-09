package dev.starcore.starcore.module.merge;

import dev.starcore.starcore.module.merge.model.MergeReferendum;
import dev.starcore.starcore.module.merge.model.MergeReferendumState;
import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.*;

/**
 * 合并公投状态编解码器
 * 将 MergeReferendum 对象与 Properties 互相转换
 */
public final class MergeStateCodec {

    private static final String PREFIX = "referendum.";
    private static final String PREFIX_IDS = "referendum.ids";
    private static final String PREFIX_MEMBERS = ".member.";
    private static final String PREFIX_VOTES = ".votes.";
    private static final String PREFIX_AGAINST = ".against.";
    private static final String PREFIX_STATE = ".state";
    private static final String PREFIX_CREATED = ".created";
    private static final String PREFIX_EXPIRES = ".expires";
    private static final String PREFIX_PROPOSER = ".proposer";
    private static final String PREFIX_PROPOSER_NAME = ".proposerName";
    private static final String PREFIX_NATION1 = ".nation1";
    private static final String PREFIX_NATION2 = ".nation2";
    private static final String PREFIX_TARGET_NAME = ".targetName";
    private static final String PREFIX_RESULT_NATION = ".resultNation";
    private static final String PREFIX_RESULT_MSG = ".resultMessage";

    private MergeStateCodec() {}

    /**
     * 将公投列表编码为 Properties
     */
    public static Properties toProperties(Collection<MergeReferendum> referendums) {
        Properties props = new Properties();

        List<String> ids = new ArrayList<>();
        for (MergeReferendum referendum : referendums) {
            String id = referendum.id().toString();
            ids.add(id);

            props.setProperty(PREFIX + id + PREFIX_STATE, referendum.state().name());
            props.setProperty(PREFIX + id + PREFIX_CREATED, String.valueOf(referendum.createdAt().toEpochMilli()));
            props.setProperty(PREFIX + id + PREFIX_EXPIRES, String.valueOf(referendum.expiresAt().toEpochMilli()));
            props.setProperty(PREFIX + id + PREFIX_PROPOSER, referendum.proposerId().toString());
            props.setProperty(PREFIX + id + PREFIX_PROPOSER_NAME, referendum.proposerName());
            props.setProperty(PREFIX + id + PREFIX_NATION1, referendum.proposerNationId().value().toString());
            props.setProperty(PREFIX + id + PREFIX_NATION2, referendum.targetNationId().value().toString());
            props.setProperty(PREFIX + id + PREFIX_TARGET_NAME, referendum.newNationName());

            if (referendum.resultNationId() != null) {
                props.setProperty(PREFIX + id + PREFIX_RESULT_NATION, referendum.resultNationId().value().toString());
            }
            if (referendum.resultMessage() != null) {
                props.setProperty(PREFIX + id + PREFIX_RESULT_MSG, referendum.resultMessage());
            }

            // 编码投票
            Set<UUID> votes = referendum.votes();
            if (!votes.isEmpty()) {
                props.setProperty(PREFIX + id + PREFIX_VOTES, encodeUUIDSet(votes));
            }

            Set<UUID> againstVotes = referendum.againstVotes();
            if (!againstVotes.isEmpty()) {
                props.setProperty(PREFIX + id + PREFIX_AGAINST, encodeUUIDSet(againstVotes));
            }
        }

        props.setProperty(PREFIX_IDS, String.join(",", ids));
        return props;
    }

    /**
     * 从 Properties 解码为公投列表
     */
    public static List<MergeReferendum> fromProperties(Properties props) {
        List<MergeReferendum> referendums = new ArrayList<>();

        if (props == null || props.isEmpty()) {
            return referendums;
        }

        String idsStr = props.getProperty(PREFIX_IDS, "");
        if (idsStr.isEmpty()) {
            return referendums;
        }

        String[] ids = idsStr.split(",");
        for (String idStr : ids) {
            try {
                MergeReferendum referendum = decodeReferendum(props, idStr.trim());
                if (referendum != null) {
                    referendums.add(referendum);
                }
            } catch (Exception e) {
                // 跳过无效的公投
            }
        }

        return referendums;
    }

    private static MergeReferendum decodeReferendum(Properties props, String idStr) {
        UUID id = UUID.fromString(idStr);

        String stateStr = props.getProperty(PREFIX + idStr + PREFIX_STATE, "PENDING");
        MergeReferendumState state;
        try {
            state = MergeReferendumState.valueOf(stateStr);
        } catch (IllegalArgumentException e) {
            state = MergeReferendumState.PENDING;
        }

        String createdStr = props.getProperty(PREFIX + idStr + PREFIX_CREATED, "0");
        String expiresStr = props.getProperty(PREFIX + idStr + PREFIX_EXPIRES, "0");
        Instant createdAt = Instant.ofEpochMilli(Long.parseLong(createdStr));
        Instant expiresAt = Instant.ofEpochMilli(Long.parseLong(expiresStr));

        UUID proposerId = UUID.fromString(props.getProperty(PREFIX + idStr + PREFIX_PROPOSER, ""));
        String proposerName = props.getProperty(PREFIX + idStr + PREFIX_PROPOSER_NAME, "");

        UUID nation1Uuid = UUID.fromString(props.getProperty(PREFIX + idStr + PREFIX_NATION1, ""));
        UUID nation2Uuid = UUID.fromString(props.getProperty(PREFIX + idStr + PREFIX_NATION2, ""));
        NationId proposerNationId = NationId.of(nation1Uuid);
        NationId targetNationId = NationId.of(nation2Uuid);

        String targetName = props.getProperty(PREFIX + idStr + PREFIX_TARGET_NAME, "");

        String resultNationStr = props.getProperty(PREFIX + idStr + PREFIX_RESULT_NATION, null);
        NationId resultNationId = resultNationStr != null ? NationId.of(UUID.fromString(resultNationStr)) : null;

        String resultMessage = props.getProperty(PREFIX + idStr + PREFIX_RESULT_MSG, null);

        Set<UUID> votes = decodeUUIDSet(props.getProperty(PREFIX + idStr + PREFIX_VOTES, ""));
        Set<UUID> againstVotes = decodeUUIDSet(props.getProperty(PREFIX + idStr + PREFIX_AGAINST, ""));

        return new MergeReferendum(
            id,
            proposerNationId,
            proposerId,
            proposerName,
            targetNationId,
            targetName,
            targetName,
            createdAt,
            expiresAt,
            state,
            votes,
            againstVotes,
            resultNationId,
            resultMessage
        );
    }

    private static String encodeUUIDSet(Set<UUID> uuids) {
        StringBuilder sb = new StringBuilder();
        for (UUID uuid : uuids) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(uuid.toString());
        }
        return sb.toString();
    }

    private static Set<UUID> decodeUUIDSet(String str) {
        Set<UUID> result = new LinkedHashSet<>();
        if (str == null || str.isEmpty()) {
            return result;
        }
        String[] parts = str.split(",");
        for (String part : parts) {
            try {
                result.add(UUID.fromString(part.trim()));
            } catch (Exception e) {
                // 跳过无效的 UUID
            }
        }
        return result;
    }
}