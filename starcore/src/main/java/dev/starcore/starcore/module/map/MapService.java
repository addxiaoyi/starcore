package dev.starcore.starcore.module.map;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import dev.starcore.starcore.module.map.model.WebClaimConfirmationResult;
import dev.starcore.starcore.module.map.model.MapSnapshot;

public interface MapService {
    MapSnapshot snapshot();

    String summary();

    Path exportStaticSite() throws IOException;

    Optional<String> webAddress();

    Optional<String> viewerWebAddress(UUID viewerId, boolean fullAccess);

    Optional<String> bindViewerWebAddress(UUID viewerId, boolean fullAccess, String remoteAddress);

    WebClaimConfirmationResult confirmWebClaim(UUID playerId, String pendingId);

    WebClaimConfirmationResult cancelWebClaim(UUID playerId, String pendingId);
}
