package dev.starcore.starcore.module.officer;

import java.util.UUID;

public record OfficerAppointment(
    String role,
    UUID playerId,
    String playerName
) {
}
