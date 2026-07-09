# STARCORE Reference Mapping

This document records how STARCORE studies upstream projects without inheriting their runtime dependencies or architectural baggage.

## Studied References

- `references/Towny`
- `references/BetterNations`
- `references/WorldDynamics-Engine`
- `references/ProtectorAPI`

For the 2026-06-03 verified external matrix, download links, license notes, and STARCORE roadmap implications, see `docs/OPEN_SOURCE_REFERENCE_ROADMAP_2026-06-03.md`.

## Borrow / Avoid / Rebuild

### Towny
Borrow:
- provider-based platform separation
- persistence abstraction and migration boundaries
- rich event surface for town/nation lifecycle operations
- clear resident/town/nation/territory object decomposition

Avoid:
- heavyweight global singleton state (`TownyUniverse` style)
- runtime coupling to Vault, PlaceholderAPI, LuckPerms, and broad add-on ecosystems
- business logic centered around legacy town-protection assumptions

Rebuild in STARCORE:
- territory foundation module with independent chunk/cuboid/polygon support
- dedicated repositories and services instead of a monolithic in-memory universe
- native STARCORE economy, permission, and event layers

### BetterNations
Borrow:
- government type modeled as ranks + permissions + decision policy
- resolution / proposal workflow with deadlines and signers
- treaty model with terms, signatures, veto, and activation
- war module concepts such as army stack states, supply, siege, invasion
- command grouping by nation/town/treaty/resolution/admin domains

Avoid:
- static global registries as primary storage
- fat domain entities that claim land, serialize themselves, broadcast messages, and mutate maps directly
- tight coupling between gameplay state, map display, GUI, and persistence
- embedding an ad-hoc general-purpose utility library inside the business plugin core

Rebuild in STARCORE:
- separate nation, government, resolution, diplomacy, and war modules
- repositories for persistence, services for orchestration, events for cross-module reactions
- effect/modifier engine shared by policy, government, diplomacy, economy, and war

### WorldDynamics-Engine
Borrow:
- product direction: legislature, diplomacy, economy, strategic national gameplay
- addon/API mindset and modular feature roadmap

Avoid:
- temporary dependence on Towny for land and Vault for economy
- treating README concepts as finished implementation

Rebuild in STARCORE:
- independent national strategy engine where policy, diplomacy, and governance are first-class native systems

### ProtectorAPI
Borrow:
- a narrow public API for querying whether a location is already covered by another protection provider
- optional multi-provider bridge vocabulary instead of hard-wiring one external plugin into STARCORE's claim flow
- provider naming and range display/id surfaces useful for user-facing conflict messages

Avoid:
- turning ProtectorAPI into a compile-time dependency of STARCORE core
- importing ProtectorAPI's many downstream integrations into STARCORE runtime assumptions
- letting external protection availability reshape STARCORE's own native claim/protection source of truth

Rebuild in STARCORE:
- keep STARCORE native claims, map, and protection logic authoritative
- isolate external conflict checks behind `ExternalProtectionService`
- centralize future bridge registration in `ExternalProtectionServices`
- centralize the exact reflection surface in `ProtectorApiBridgeContract`, so upstream API drift is corrected in one place
- keep `scripts/check-protectorapi-reference.ps1` in the maintenance path, so the local `references/ProtectorAPI` checkout and STARCORE bridge contract can be verified before release smoke
- treat `references/ProtectorAPI` as an API drift/reference checkout, not a code donor
