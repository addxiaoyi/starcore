package dev.starcore.starcore.module.map;

import java.util.UUID;

record MapViewerAccess(UUID viewerId, boolean fullAccess, long expiresAtEpochSecond, String source) {
    static MapViewerAccess publicView() {
        return new MapViewerAccess(null, false, Long.MAX_VALUE, "public");
    }

    boolean isPublic() {
        return viewerId == null;
    }

    boolean isExpiredAt(long epochSecond) {
        return !isPublic() && epochSecond > expiresAtEpochSecond;
    }
}
