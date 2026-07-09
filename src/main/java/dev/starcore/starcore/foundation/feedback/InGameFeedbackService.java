package dev.starcore.starcore.foundation.feedback;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface InGameFeedbackService {
    void emit(String eventKey, Player player, Location location);
}
