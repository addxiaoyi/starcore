package dev.starcore.starcore.module.officer;

import dev.starcore.starcore.module.nation.model.NationId;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface OfficerService {
    Collection<String> availableRoles();

    Collection<OfficerAppointment> officersOf(NationId nationId);

    Optional<OfficerAppointment> officer(NationId nationId, String role);

    boolean appoint(NationId nationId, String role, UUID playerId, String playerName);

    boolean remove(NationId nationId, String role);

    String summary();
}
