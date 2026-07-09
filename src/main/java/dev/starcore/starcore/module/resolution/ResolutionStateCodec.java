package dev.starcore.starcore.module.resolution;

import dev.starcore.starcore.module.diplomacy.DiplomacyRelation;
import dev.starcore.starcore.module.government.model.GovernmentType;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.resolution.model.ChangeDiplomacyRelationAction;
import dev.starcore.starcore.module.resolution.model.ChangeGovernmentAction;
import dev.starcore.starcore.module.resolution.model.JoinNationRequestAction;
import dev.starcore.starcore.module.resolution.model.RenameNationAction;
import dev.starcore.starcore.module.resolution.model.Resolution;
import dev.starcore.starcore.module.resolution.model.ResolutionAction;
import dev.starcore.starcore.module.resolution.model.ResolutionKind;
import dev.starcore.starcore.module.resolution.model.ResolutionState;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

final class ResolutionStateCodec {
    private ResolutionStateCodec() {
    }

    static Properties toProperties(java.util.Collection<Resolution> resolutions) {
        Properties properties = new Properties();
        List<Resolution> snapshot = resolutions.stream()
            .sorted((left, right) -> left.createdAt().compareTo(right.createdAt()))
            .toList();
        properties.setProperty("count", String.valueOf(snapshot.size()));
        for (int index = 0; index < snapshot.size(); index++) {
            Resolution resolution = snapshot.get(index);
            String prefix = "resolution." + index + '.';
            properties.setProperty(prefix + "id", resolution.id().toString());
            properties.setProperty(prefix + "nationId", resolution.nationId().toString());
            properties.setProperty(prefix + "proposerId", resolution.proposerId().toString());
            properties.setProperty(prefix + "proposerName", resolution.proposerName());
            properties.setProperty(prefix + "createdAt", resolution.createdAt().toString());
            properties.setProperty(prefix + "expiresAt", resolution.expiresAt().toString());
            properties.setProperty(prefix + "state", resolution.state().name());
            List<UUID> signatures = resolution.signatures().stream().sorted().toList();
            properties.setProperty(prefix + "signatureCount", String.valueOf(signatures.size()));
            for (int signatureIndex = 0; signatureIndex < signatures.size(); signatureIndex++) {
                properties.setProperty(prefix + "signature." + signatureIndex, signatures.get(signatureIndex).toString());
            }
            saveAction(properties, prefix + "action.", resolution.action());
        }
        return properties;
    }

    static List<Resolution> fromProperties(Properties properties) {
        int count = parseInt(properties.getProperty("count"), 0);
        java.util.ArrayList<Resolution> resolutions = new java.util.ArrayList<>();
        for (int index = 0; index < count; index++) {
            Resolution resolution = loadResolution(properties, "resolution." + index + '.');
            if (resolution != null) {
                resolutions.add(resolution);
            }
        }
        return List.copyOf(resolutions);
    }

    private static void saveAction(Properties properties, String prefix, ResolutionAction action) {
        properties.setProperty(prefix + "kind", action.kind().name());
        properties.setProperty(prefix + "nationId", action.nationId().toString());
        if (action instanceof JoinNationRequestAction joinAction) {
            properties.setProperty(prefix + "applicantId", joinAction.applicantId().toString());
            properties.setProperty(prefix + "applicantName", joinAction.applicantName());
            return;
        }
        if (action instanceof RenameNationAction renameAction) {
            properties.setProperty(prefix + "oldName", renameAction.oldName());
            properties.setProperty(prefix + "newName", renameAction.newName());
            return;
        }
        if (action instanceof ChangeGovernmentAction governmentAction) {
            properties.setProperty(prefix + "from", governmentAction.from().name());
            properties.setProperty(prefix + "to", governmentAction.to().name());
            return;
        }
        if (action instanceof ChangeDiplomacyRelationAction diplomacyAction) {
            properties.setProperty(prefix + "targetNationId", diplomacyAction.targetNationId().toString());
            properties.setProperty(prefix + "targetNationName", diplomacyAction.targetNationName());
            properties.setProperty(prefix + "relation", diplomacyAction.relation().name());
        }
    }

    private static Resolution loadResolution(Properties properties, String prefix) {
        try {
            UUID id = UUID.fromString(properties.getProperty(prefix + "id"));
            NationId nationId = parseNationId(properties.getProperty(prefix + "nationId"));
            UUID proposerId = UUID.fromString(properties.getProperty(prefix + "proposerId"));
            String proposerName = properties.getProperty(prefix + "proposerName");
            Instant createdAt = Instant.parse(properties.getProperty(prefix + "createdAt"));
            Instant expiresAt = Instant.parse(properties.getProperty(prefix + "expiresAt"));
            ResolutionState state = parseState(properties.getProperty(prefix + "state"));
            ResolutionAction action = loadAction(properties, prefix + "action.");
            Set<UUID> signatures = new LinkedHashSet<>();
            int signatureCount = parseInt(properties.getProperty(prefix + "signatureCount"), 0);
            for (int signatureIndex = 0; signatureIndex < signatureCount; signatureIndex++) {
                String value = properties.getProperty(prefix + "signature." + signatureIndex);
                if (value != null) {
                    signatures.add(UUID.fromString(value));
                }
            }
            if (nationId == null || proposerName == null || action == null) {
                logParseError("Missing required fields for resolution at " + prefix);
                return null;
            }
            return new Resolution(id, nationId, proposerId, proposerName, action, createdAt, expiresAt, state, signatures);
        } catch (RuntimeException exception) {
            logParseError("Failed to parse resolution at " + prefix + ": " + exception.getMessage());
            return null;
        }
    }

    private static void logParseError(String message) {
        System.getLogger(ResolutionStateCodec.class.getName()).log(System.Logger.Level.WARNING, message);
    }

    private static ResolutionAction loadAction(Properties properties, String prefix) {
        ResolutionKind kind = parseKind(properties.getProperty(prefix + "kind"));
        NationId nationId = parseNationId(properties.getProperty(prefix + "nationId"));
        if (kind == null || nationId == null) {
            logParseError("Missing kind or nationId for action at " + prefix);
            return null;
        }
        try {
            return switch (kind) {
                case JOIN_NATION -> new JoinNationRequestAction(
                    nationId,
                    UUID.fromString(properties.getProperty(prefix + "applicantId")),
                    properties.getProperty(prefix + "applicantName")
                );
                case RENAME_NATION -> new RenameNationAction(
                    nationId,
                    properties.getProperty(prefix + "oldName"),
                    properties.getProperty(prefix + "newName")
                );
                case CHANGE_GOVERNMENT -> new ChangeGovernmentAction(
                    nationId,
                    parseGovernment(properties.getProperty(prefix + "from")),
                    parseGovernment(properties.getProperty(prefix + "to"))
                );
                case CHANGE_DIPLOMACY_RELATION -> new ChangeDiplomacyRelationAction(
                    nationId,
                    parseNationId(properties.getProperty(prefix + "targetNationId")),
                    properties.getProperty(prefix + "targetNationName"),
                    parseRelation(properties.getProperty(prefix + "relation"))
                );
                default -> {
                    logParseError("Unknown resolution kind: " + kind.name() + " at " + prefix);
                    yield null;
                }
            };
        } catch (RuntimeException exception) {
            logParseError("Failed to parse action at " + prefix + ": " + exception.getMessage());
            return null;
        }
    }

    private static NationId parseNationId(String value) {
        if (value == null) {
            return null;
        }
        try {
            return new NationId(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static ResolutionKind parseKind(String value) {
        if (value == null) {
            return null;
        }
        try {
            return ResolutionKind.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static ResolutionState parseState(String value) {
        if (value == null) {
            return ResolutionState.OPEN;
        }
        try {
            return ResolutionState.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ResolutionState.OPEN;
        }
    }

    private static GovernmentType parseGovernment(String value) {
        if (value == null) {
            return GovernmentType.MONARCHY;
        }
        try {
            return GovernmentType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return GovernmentType.MONARCHY;
        }
    }

    private static DiplomacyRelation parseRelation(String value) {
        if (value == null) {
            return DiplomacyRelation.NEUTRAL;
        }
        try {
            return DiplomacyRelation.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return DiplomacyRelation.NEUTRAL;
        }
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
