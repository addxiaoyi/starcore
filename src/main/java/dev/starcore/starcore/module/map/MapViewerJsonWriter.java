package dev.starcore.starcore.module.map;

import java.math.BigDecimal;

final class MapViewerJsonWriter {
    String toJson(ViewerDetails viewer) {
        if (viewer == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder(512);
        builder.append('{');
        appendField(builder, "playerId", viewer.playerId());
        builder.append(',');
        appendField(builder, "playerName", viewer.playerName());
        builder.append(',');
        appendMoneyField(builder, "balance", viewer.balance());
        builder.append(',');
        appendField(builder, "nationId", viewer.nationId());
        builder.append(',');
        appendField(builder, "nationName", viewer.nationName());
        builder.append(',');
        appendField(builder, "nationKind", viewer.nationKind());
        builder.append(',');
        appendField(builder, "founderName", viewer.founderName());
        builder.append(',');
        appendField(builder, "government", viewer.government());
        builder.append(',');
        appendField(builder, "role", viewer.role());
        builder.append(',');
        appendNumberField(builder, "nationLevel", viewer.nationLevel());
        builder.append(',');
        appendNumberField(builder, "nationExperience", viewer.nationExperience());
        builder.append(',');
        appendNumberField(builder, "nationExperienceProgress", viewer.nationExperienceProgress());
        builder.append(',');
        appendNumberField(builder, "nationNextLevelExperience", viewer.nationNextLevelExperience());
        builder.append(',');
        appendNumberField(builder, "nationExperienceRemaining", viewer.nationExperienceRemaining());
        builder.append(',');
        appendBooleanField(builder, "nationMaxLevelReached", viewer.nationMaxLevelReached());
        builder.append(',');
        appendNumberField(builder, "claimCount", viewer.claimCount());
        builder.append(',');
        appendNumberField(builder, "claimLimit", viewer.claimLimit());
        builder.append(',');
        appendNumberField(builder, "cityStateCount", viewer.cityStateCount());
        builder.append(',');
        appendNumberField(builder, "cityStateLimit", viewer.cityStateLimit());
        builder.append(',');
        appendNumberField(builder, "resourceDistrictCount", viewer.resourceDistrictCount());
        builder.append(',');
        appendNumberField(builder, "resourceDistrictLimit", viewer.resourceDistrictLimit());
        builder.append(',');
        appendBooleanField(builder, "online", viewer.online());
        builder.append(',');
        appendField(builder, "world", viewer.world());
        builder.append(',');
        appendNumberField(builder, "x", viewer.x());
        builder.append(',');
        appendNumberField(builder, "y", viewer.y());
        builder.append(',');
        appendNumberField(builder, "z", viewer.z());
        builder.append(',');
        appendBooleanField(builder, "founder", viewer.founder());
        builder.append('}');
        return builder.toString();
    }

    record ViewerDetails(
        String playerId,
        String playerName,
        BigDecimal balance,
        String nationId,
        String nationName,
        String nationKind,
        String founderName,
        String government,
        String role,
        int nationLevel,
        long nationExperience,
        long nationExperienceProgress,
        long nationNextLevelExperience,
        long nationExperienceRemaining,
        boolean nationMaxLevelReached,
        int claimCount,
        int claimLimit,
        int cityStateCount,
        int cityStateLimit,
        int resourceDistrictCount,
        int resourceDistrictLimit,
        boolean online,
        String world,
        int x,
        int y,
        int z,
        boolean founder
    ) {
    }

    private void appendField(StringBuilder builder, String name, String value) {
        builder.append('"').append(escape(name)).append("\":\"").append(escape(value)).append('"');
    }

    private void appendStringValue(StringBuilder builder, String value) {
        builder.append('"').append(escape(value)).append('"');
    }

    private void appendNumberField(StringBuilder builder, String name, int value) {
        builder.append('"').append(escape(name)).append("\":").append(value);
    }

    private void appendNumberField(StringBuilder builder, String name, long value) {
        builder.append('"').append(escape(name)).append("\":").append(value);
    }

    private void appendBooleanField(StringBuilder builder, String name, boolean value) {
        builder.append('"').append(escape(name)).append("\":").append(value);
    }

    private void appendMoneyField(StringBuilder builder, String name, BigDecimal value) {
        builder.append('"').append(escape(name)).append("\":");
        if (value == null) {
            builder.append("\"0.00\"");
        } else {
            appendStringValue(builder, value.toPlainString());
        }
    }

    private String escape(String input) {
        return (input == null ? "" : input)
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t");
    }
}
