# STARCORE Claim Economy And Resource District Plan

Date: 2026-06-04

## User Requirements Captured

- In-game claim tool and web-map claiming must use one shared backend logic.
- In-game claiming uses left click for the first chunk and right click for the second chunk.
- Claim height is unlimited; claims are chunk-based from bedrock to build height.
- Claim price is calculated per chunk.
- Per-chunk price increases by distance from world center coordinate `0,0` or configured spawn/world center.
- Per-chunk price is also affected by biome theoretical resource richness.
- Low-resource biomes such as ice plains should be cheaper.
- Resource richness should consider minerals, trees, farmable land, water, climate, and terrain usefulness.
- The map website must show the visiting player's balance and detailed claim price information.
- Creating a nation should create random resource districts inside that nation's territory once territory exists.
- Resource districts refresh mineral resources and nation experience according to biome richness, cooldown, and resource yield.
- Each resource district should have an unbreakable beacon on the surface with floating status text.
- Right-clicking the beacon opens a GUI for the nation owner.
- The nation owner can spend a default `100000` coins to migrate a resource district.
- Migration asks for a second confirmation.
- After confirmation, the owner receives a nether star.
- Right-clicking a target chunk with that nether star places the pending resource district target.
- Migration is delayed until all resources in the old resource district are depleted.
- If old resources are not depleted for a long time, force migration after 4 hours and clear remaining old resources.
- Every 10 nation levels grants 1 additional resource district limit.
- Nation max level is 100.
- Nation leveling increases nation claim chunk limit.
- Example: level 1 nation can cover 20 chunks.

## Implementation Order

1. Shared claim quote engine:
   - Build one `ClaimEconomyService` used by both `NationService` and `MapModule`.
   - Return chunk count, total price, player balance, affordability, and per-chunk details.
   - Per-chunk detail includes distance multiplier, biome key, biome richness, and final price.

2. Unified claim commit path:
   - In-game claim tool and web map both call `NationService.previewClaimSelection(...)` and `NationService.claimSelection(...)`.
   - Remove duplicated price math from UI paths.

3. Web-map claim detail:
   - Preview/request responses include visiting player balance and price breakdown.
   - Frontend renders balance, total cost, chunk count, and top expensive chunks.

4. Resource district foundation:
   - Add persistent resource district records keyed by nation and chunk.
   - Spawn beacon and floating display.
   - Add config for spawn chance, migration cost, force migration hours, level intervals, max nation level, and level claim limits.

5. Resource refresh and nation XP:
   - Tick on configurable cooldown.
   - Rich biomes refresh more resources and award more nation XP.
   - Depleted district can migrate when a pending migration target exists.

## Current Turn Slice

Completed in the current continuation:

- Added nation experience storage and configurable level progression.
- Level-based claim cap is now configurable, with level 1 defaulting to 20 chunks.
- Added persistent nation resource districts under `nation/resource`.
- Claim success now calls the resource district service to fill unlocked district slots.
- Resource district chunk selection is weighted by the same biome theoretical richness used by claim pricing.
- Resource districts place an unbreakable surface beacon and floating status text.
- Resource districts refresh tracked underground ore blocks only into natural stone-like blocks, avoiding player builds as much as possible.
- Resource refresh grants nation experience and district total experience.
- Right-clicking a resource beacon opens a status/migration GUI.
- Migration confirmation charges the configurable default `100000.00` coins and gives a marked nether star.
- Right-clicking a claimed target chunk with the marked nether star sets the pending migration target.
- Migration waits for tracked old resource blocks to be mined; after the configured force timeout, remaining tracked resource blocks are cleared and the district moves.
- Resource district beacons are protected from break, explosion, and piston movement.
- `/sc rsc d` / `/starcore resource districts` lists resource districts.
- `/sc rsc i <world:x:z>` / `/starcore resource inspect <world:x:z>` shows one district's detailed state, and players can omit the coordinate to inspect their current chunk.
- `/sc rsc m <world:x:z>` / `/starcore resource migrate <world:x:z>` starts migration from the command layer, reusing the same leader/balance/state checks as the beacon and web-map flows.
- Added `RESOURCE_DISTRICTS` to the web map snapshot, health/summary counts, access filtering, frontend layer toggle, legend, tooltip, sidebar list, and Chinese empty state.
- Externalized resource district beacon, migration, display, and GUI text into `messages_zh_cn.yml`, with bundled defaults still used as fallback on upgraded servers.
- Extracted resource district pure rules into `NationResourceDistrictRules` and added JUnit coverage for weighted candidate selection, richness-based refresh amount/experience, cooldown bounds, depletion-gated migration, and force-migration timeout eligibility.
- Externalized `/sc rsc` / `/starcore resource` command feedback into `messages_zh_cn.yml` under `command.resource.*`.
- Externalized `/sc tech`, `/sc w`, `/sc off`, and `/sc ev` command feedback into `messages_zh_cn.yml` under `command.technology.*`, `command.war.*`, `command.officer.*`, and `command.event.*`.
- Externalized high-frequency nation/map/policy/treasury/diplomacy/government/resolution command feedback, core nation claim/creation errors, web-claim API messages, epoch summaries, and real-time sync summaries into `messages_zh_cn.yml`.
- Added `scripts/smoke-starcore-paper-integration.ps1` for a repeatable Paper 1.21.11 live integration smoke.
- Fixed `ClaimToolService` public API registration so `api.claimToolService()` returns the concrete native claim tool service instead of the nation module.
- Added a migration target state gate: a marked migration core can set a target only while its resource district is in `AWAITING_TARGET`, preventing stale or duplicated cores from bypassing the paid confirmation step.
- Added fixed-slot resource district menu action rules so migration confirmation, begin migration, and cancel/return actions require both the expected GUI slot and expected material.
- Migrated resource district status and migration confirmation menus to Paper `MenuType.GENERIC_9X3` as the primary native menu path, while keeping a narrow legacy inventory fallback for proxy tests and exceptional environments.
- Added Paper MenuType policy tree GUI on `/sc po t` / `/starcore policy tree` / `/starcore 国策 菜单`, showing policy category, cost, prerequisites, state, effects, and activation command hints.
- Added Paper MenuType technology tree GUI on `/sc tech t` / `/starcore technology tree` / `/starcore 科技 菜单`, showing unlock state, treasury cost, resource cost, and admin unlock command hints.
- Added private authenticated map `viewer` snapshot data: visiting player balance, nation, level, claim capacity, and resource district capacity. The frontend renders a visitor intel card and shows current balance in the claim panel before any preview request.
- Added authenticated browser smoke with headless Edge CDP: `scripts/smoke-starcore-paper-integration.ps1 -BrowserSmoke` opens the signed personal map link, verifies the visitor card and pre-selection claim balance in DOM, and writes DOM/screenshot artifacts. The web map supports `terrain=off` for this smoke path so territory/resource UI verification does not request terrain tiles.
- Upgraded authenticated browser smoke to verify richer visitor intel rendering in the DOM, including government, role, online state, nation experience, claim capacity, and resource district capacity.
- Verified the live STARCORE API path on Paper: economy funding, nation creation, claim tool item creation/marker recognition, shared 2x2 claim preview/pricing, claim commit, resource district generation, protected beacon placement, right-click beacon GUI opening, migration menu click, confirmation menu click, migration core delivery, migration target selection, mined depletion migration completion, map resource marker count, web health endpoint, authenticated browser map rendering, and clean process/port teardown.
- Latest deep smoke marker: `STARCORE_SMOKE_PASS nation=Smokempzkcvak claims=4 price=299.63 districts=1 claimTool=GOLDEN_SHOVEL migration=gui+mined:world:61:61 viewer=ok beacon=984,63,1000:BEACON mapSummary=68 territory polygon(s), 0 player marker(s), 17 resource district marker(s)` with `ErrorMatches=none`.
- Latest browser smoke marker: `STARCORE_BROWSER_SMOKE_PASS viewer=SmokePlayer nation=Smokempzkcvak balance=9899200.37 browser=Edg/148.0.3967.96`.
- Added ordered async persistence for hot gameplay state: territory claim/unclaim, nation creation/member/name/experience updates, and resource district refresh/mining/migration state now queue asynchronous saves per state file; stop/disable paths drain or synchronously flush the latest snapshot.
- Verified the async persistence slice with `mvn -q test` and `mvn -q package`; latest jar size is `524363` bytes.
- Added the database foundation slice: HikariCP, SQLite JDBC, and MySQL Connector/J are bundled into the plugin jar; default SQLite starts under `plugins/STARCORE/starcore.db`; MySQL can be configured through `database.mysql.*`; the pool creates `starcore_metadata`; `/starcore status` shows the active database summary; `/starcore reload` restarts the pool.
- Verified the database foundation slice with `mvn -q test`, `mvn -q package`, and jar inspection for HikariCP, SQLite JDBC, MySQL Connector/J, and `META-INF/services/java.sql.Driver`; latest jar size is `17263944` bytes.
- Verified default SQLite on a real Paper startup: `scripts/smoke-starcore-paper-integration.ps1 -TimeoutSeconds 180` passed, Hikari logged `STARCORE-SQLite - Start completed`, `plugins/STARCORE/starcore.db` was created, and `starcore_metadata` contains `schema_version=1` plus `database_type=sqlite`.
- Migrated nation state to SQL-backed storage with automatic legacy `nations.properties` import when `starcore_nation_state` starts empty.
- Migrated nation resource district state to SQL-backed storage with automatic legacy `resource-districts.properties` import when `starcore_nation_resource_district_state` starts empty.
- Migrated treasury state to SQL-backed storage with automatic legacy `treasury.properties` import when `starcore_treasury_state` starts empty.
- Added focused diplomacy SQL persistence tests and extended Paper smoke to produce real diplomacy SQL rows.
- Added `SqlNationStateStorageTest` and `SqlNationResourceDistrictStateStorageTest` to cover legacy import and round-trip persistence for core nation gameplay state.
- Added `SqlTreasuryStateStorageTest` and `SqlDiplomacyStateStorageTest` to cover legacy import and overwrite-style SQL round-trip persistence.
- Re-verified the Paper smoke path after the nation/resource SQL migrations. Latest isolated artifact SQLite counts are `starcore_metadata=3`, `starcore_territory_claims=4`, `starcore_player_balances=1`, `starcore_nation_state=10`, and `starcore_nation_resource_district_state=40`.
- Re-verified the Paper smoke path after the treasury/diplomacy SQL slice. Latest isolated artifact SQLite counts are `starcore_metadata=3`, `starcore_territory_claims=4`, `starcore_player_balances=2`, `starcore_nation_state=19`, `starcore_nation_resource_district_state=40`, `starcore_diplomacy_state=4`, and `starcore_treasury_state=3`.
- Added `NativeClaimToolServiceTest` for first/second point selection, cross-world selection reset, preview generation, confirm result, and selection cleanup.
- Added `ClaimToolListenerTest` for left/right click mapping, event cancellation, block-required warning, preview text, and confirm hint.
- Added `StarCoreCommandTabCompletionTest` for structured short-alias and Chinese Tab completion coverage across `/sc n`, `/sc m`, `/sc rsc`, `/sc tech`, `/sc w`, and nested nation city-state menus.
- Added `StarCoreCommandShortAliasDispatchTest` for lightweight command dispatch coverage of `/sc n t`, `/sc n ok`, `/sc m w`, `/sc rsc d`, `/sc rsc i`, `/sc rsc m`, `/sc po t`, `/sc tech s`, `/sc tech t`, and `/sc w s` with mocked senders/services.

Next useful slices:

1. Continue SQL migration for the remaining shared module states such as policy runtime, technology, war, officer, event, and map-side transient audit data so backup/restore paths converge on one storage model.
2. Enrich authenticated map viewer intel even further with real online-player position smoke if a non-proxy player automation path becomes available.
3. Add real online-client smoke for inventory delivery and visual in-client interaction if a Minecraft client automation path becomes available; current Paper harness covers command/API paths, beacon GUI open/click/confirm, migration state, mined depletion migration, treasury/diplomacy state writes, and authenticated browser map rendering without requiring a client account.
