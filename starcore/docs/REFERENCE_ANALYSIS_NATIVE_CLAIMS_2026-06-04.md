# STARCORE Native Claims Reference Analysis

Date: 2026-06-04

Requirement: STARCORE must not depend on WorldEdit, WorldGuard, or Lands at runtime. These projects are reference material only.

## Cloned References

- `D:\qwq\项目\mapadd\references\WorldGuard`
- `D:\qwq\项目\mapadd\references\WorldEdit`
- `D:\qwq\项目\mapadd\references\LandsAPI`

The public Lands plugin repository was not available, so only the public Lands API repository was cloned.

## Useful Ideas Adopted

- WorldGuard's Bukkit layer is event-driven. Its listener surface covers block break/place, interaction, explosions, pistons, fire/liquid spread, and entity-driven block changes. STARCORE now follows the same broad event categories, but queries `TerritoryService` instead of any external region container.
- WorldEdit's relevant value here is selection modeling. STARCORE already has `ChunkClaimSelection.fromBlockBounds(...)`, so web drag selection remains internal and chunk-based.
- Lands API models permissions as flags tied to land/member roles. STARCORE V0.1 keeps this lighter: `allow-nation-members` controls whether ordinary nation members can operate in national claims, while founders and `starcore.admin` always bypass.

## Current STARCORE Design

- Claims are chunk records in `TerritoryService`.
- Claim ownership is stored as the owning `NationId` string.
- `NativeTerritoryProtectionListener` maps event blocks/entities to `ChunkCoordinate`, resolves the owner nation, and cancels unauthorized actions.
- Explosions remove claimed blocks from the affected block list.
- Pistons are cancelled only when moved blocks cross a claim ownership boundary.
- Liquid flow is cancelled when source and target chunks have different claim owners.

## Future Expansion Notes

- Add role-level permissions inside a nation if STARCORE later introduces officer/role claim rights.
- Add sub-areas or polygon claims only after the chunk model is stable and storage is migrated beyond `.properties`.
- Keep any future integrations optional and outside the core protection path.
