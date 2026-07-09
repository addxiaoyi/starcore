# STARCORE Open Source Reference Roadmap

Verified on 2026-06-03. This note turns the external nation, policy, menu, and performance references into concrete STARCORE implementation guidance. It is intentionally evidence-backed because several older planning notes have already drifted.

## Current STARCORE Baseline

- Local target: `starcore` is a Java 21 Maven Paper plugin at `0.1.0-SNAPSHOT`, using `paper-api` `1.21.11-R0.1-SNAPSHOT`.
- Current core shape: `StarCorePlugin` wires `ConfigurationService`, `StarCoreScheduler`, `StarCoreEventBus`, `PersistenceService`, internal permission/economy services, `ModuleManager`, and module registration.
- Current modules: nation, government, resolution, treasury, diplomacy, policy, map, war, technology, resource, officer, and event are all enabled by default in `config.yml`.
- Current persistence: module state is persisted as namespaced `.properties` files. This is useful for V0.1 smoke tests, but it is not the final SQLite/MySQL engine described in the long-range goal.
- Current analysis tooling: CodeGraph is initialized and current for `1,248` files, `29,234` nodes, and `81,929` edges. Graphify CLI is not on PATH, but existing `.graphify_detect_map.json` / `.graphify_ast_map.json` capture the map module code slice.

## Reference Matrix

| Reference | Current status | Download / docs | License risk | STARCORE use |
|---|---:|---|---|---|
| [TownyAdvanced/Towny](https://github.com/TownyAdvanced/Towny) | Latest release `0.103.0.0`, published 2026-05-08; supports MC `1.19.*`, `1.20.*`, `1.21.*`, and `26.1.*`. | [Towny Advanced 0.103.0.0 zip](https://github.com/TownyAdvanced/Towny/releases/download/0.103.0.0/Towny.Advanced.0.103.0.0.zip) | CC BY-NC-ND 3.0. Do not copy code into MIT STARCORE. | Study resident/town/nation decomposition, rich event surface, database/migration boundaries, permission model vocabulary. |
| [Leralix/Towns-and-Nations](https://github.com/Leralix/Towns-and-Nations) | Latest release `v1.0.0`, published 2026-05-10; active modern RP town/nation plugin. | [TownsAndNations-1.0.0.jar](https://github.com/Leralix/Towns-and-Nations/releases/download/v1.0.0/TownsAndNations-1.0.0.jar) | GPL-3.0. Concept-only reference for MIT STARCORE. | Study lighter alliance/war UX, API boundary, map-plugin integration expectations. |
| [Santiago22022/NationTech-parent](https://github.com/Santiago22022/NationTech-parent) | No releases; last pushed 2025-11-28; very small/unfinished reference. | Clone/source only. | No declared license. Treat as read-only inspiration. | Keep as idea reference for national politics plus tech-tree coupling, not as implementation base. |
| [stoleyourharvs/WorldDynamics-Engine](https://github.com/stoleyourharvs/WorldDynamics-Engine) | Latest release `v0.2.1`, published 2024-02-11; `v1.x` README says Argon rewrite is unreleased. | [WorldDynamicsEngine-0.2.1.jar](https://github.com/stoleyourharvs/WorldDynamics-Engine/releases/download/v0.2.1/WorldDynamicsEngine-0.2.1.jar) | GPL-3.0. Concept-only reference for MIT STARCORE. | Best conceptual match for legislature, economy, diplomacy, eco-simulation, and addon/API direction. Avoid Towny/Vault coupling. |
| [ThizThizzyDizzy/skill-trees](https://github.com/ThizThizzyDizzy/skill-trees) | Latest release `v1.2.5`, published 2020-05-03; Minecraft 1.14+ era. | [SkillTree-1.2.5.jar](https://github.com/ThizThizzyDizzy/skill-trees/releases/download/v1.2.5/SkillTree-1.2.5.jar) | No declared license. | Study tree visualization and prerequisite/effect layout only. Too old for direct dependency. |
| [ASangarin/TechTree](https://github.com/ASangarin/TechTree) / [Spigot TechTree](https://www.spigotmc.org/resources/techtree.98962/) | Spiget reports `1.5.1`, tested through MC `1.20`; GitHub is issue/wiki oriented and old. | [Spigot download endpoint](https://api.spiget.org/v2/resources/98962/download) | No declared GitHub license. | Study configurable category/progression vocabulary. Do not base STARCORE tech module on it. |
| [Nations-Legacy on Spigot](https://www.spigotmc.org/resources/nations-legacy.110074/) | Spiget reports `v1.0.4`, tested through MC `1.21`. The older `ItsLucky/Nations-Legacy` GitHub link does not resolve. | [Spigot download endpoint](https://api.spiget.org/v2/resources/110074/download) | Source/license not verified from GitHub. | Lightweight diplomacy/flag/war UX reference only. |
| [Paper MenuType API](https://docs.papermc.io/paper/dev/menu-type-api/) | Official Paper docs page is live. | Docs page. | N/A | Preferred native menu direction for in-game policy tree UI; verify exact API against `paper-api` before coding. |
| [OkaeriPoland/okaeri-menu](https://github.com/OkaeriPoland/okaeri-menu) | No GitHub release; README dependency `eu.okaeri:okaeri-menu-bukkit:2.0.1-beta.4`; Paper 1.21+ and Java 21. | Maven repo `https://repo.okaeri.cloud/releases` | MIT. | Strong UX reference for pane-based, async, reactive menus. Optional implementation dependency if STARCORE accepts a GUI library. |
| [DevNatan/inventory-framework](https://github.com/DevNatan/inventory-framework) | Latest release `v3.7.1`, published 2025-11-30. | [inventory-framework-bukkit-3.7.1.jar](https://github.com/devnatan/inventory-framework/releases/download/v3.7.1/inventory-framework-bukkit-3.7.1.jar) | MIT. | Mature fallback GUI framework. Use only if Paper MenuType coverage is insufficient. |
| [shateq/papermc-example](https://github.com/shateq/papermc-example) | Small example repo; no releases. | Clone/source only. | No declared license. | Low priority; current STARCORE scaffold is already more complete. |
| [lucko/spark](https://github.com/lucko/spark) | Active profiler; no latest GitHub release through API, but repo is current. | [spark repo](https://github.com/lucko/spark) | GPL-3.0. Runtime tool only. | Use for performance profiling, not as linked code. |
| [flags.sh](https://flags.sh/) and [Paper Aikar flags docs](https://docs.papermc.io/paper/aikars-flags/) | Live server tuning references. | Docs pages. | N/A | Server baseline tuning; separate from plugin-internal async/cache work. |
| [Spigot open-source plugin list](https://www.spigotmc.org/wiki/list-of-open-source-plugins/) and [awesome-minecraft](https://github.com/LiteDevelopers/awesome-minecraft) | Both live; `awesome-minecraft` is MIT and still recently updated. | Directory/reference lists. | Mixed per linked project. | Use only for discovery and competitive scanning, not as architecture authority. |

## Supplemental Technical References

| Area | Reference | Current verified signal | STARCORE decision |
|---|---|---|---|
| Module system | [JPMS `java.lang.module`](https://docs.oracle.com/javase/9/docs/api/java/lang/module/package-summary.html), [PF4J](https://github.com/pf4j/pf4j) | JPMS docs live; PF4J latest release `release-3.15.0`, Apache-2.0. | Start with Maven package boundaries plus `ServiceLoader`/annotation metadata. Defer JPMS because Bukkit/Paper plugin classloading can make strict module layers awkward. Keep PF4J as an optional future extension mechanism, not V0.1 core. |
| Config and text | [Configurate](https://github.com/SpongePowered/Configurate), [Kyori Adventure](https://github.com/PaperMC/adventure) | Configurate latest `4.2.0`, Apache-2.0; Adventure latest `v5.1.1`, MIT. | Move complex configs and migrations to Configurate-style typed loaders. Use Adventure/MiniMessage for all player-facing text and future i18n. |
| Caching | [Caffeine](https://github.com/ben-manes/caffeine) | Latest `v3.2.4`, Apache-2.0; repo describes it as a high performance Java cache. | Prefer Caffeine for bounded computed caches: policy graph lookups, relation snapshots, territory spatial indexes, and map-derived view models. |
| Geometry | [JTS Topology Suite](https://github.com/locationtech/jts), [poly2tri.java forks](https://github.com/orbisgis/poly2tri.java) | JTS latest `1.20.0`; poly2tri Java forks are smaller and fragmented. | Prefer JTS for polygon containment/intersection and spatial indexing. Keep Poly2Tri only for visualization/triangulation use cases if needed. |
| Runtime CI | [FN-FAL113/minecraft-plugin-runtime-test](https://github.com/FN-FAL113/minecraft-plugin-runtime-test) | Third-party GitHub Action for testing plugin initialization on Paper versions; not official Paper docs. | Useful as optional CI inspiration, but STARCORE should still have its own unit/integration tests and local Paper smoke test scripts. |
| Paid profiling | [YourKit Java Profiler](https://www.yourkit.com/java/profiler/), [JProfiler](https://www.ej-technologies.com/products/jprofiler/overview.html) | Official product pages live. | Optional paid profiling for hard memory/CPU cases. Keep free spark profiling as the default community reproducibility path. |

## Borrow / Avoid / Rebuild

### Nation and Territory

Borrow:
- Towny's stable object vocabulary: player/resident, town/nation, plot/claim, rank, permission, event.
- Towns-and-Nations' lighter public UX around alliances, warfare, and map integration.

Avoid:
- Towny's global universe style and license-incompatible code reuse.
- Forcing STARCORE into a town-first model when the product goal is national strategy.

Rebuild:
- Move current chunk-only `InMemoryTerritoryService` toward a pluggable territory model: chunk, cuboid, polygon, and future province regions.
- Keep `Nation` persistence-agnostic, but split repository responsibilities out of `NationModule` before SQLite/MySQL migration.

### Policy, Government, and Technology Trees

Borrow:
- WorldDynamics' idea stack: legislative workflow, diplomacy/economy interplay, eco-strategic events, addon API.
- SkillTree/TechTree layout concepts: nodes, prerequisites, categories, visible locked/unlocked state, effect display.

Avoid:
- Hard-coding every policy definition forever inside `PolicyModule`.
- Treating one active policy as the whole national strategy state. The current `PolicyRuntimeState` is a good V0.1 simplification, not V1 architecture.

Rebuild:
- Define a shared `ModifierEngine` for policy, government, diplomacy, treasury, war, technology, and resource effects.
- Store policy and technology definitions in config/data packs, then compile them into immutable runtime graphs.
- Represent prerequisites as typed rules rather than only `Set<String>` keys: policy unlocked, tech unlocked, relation state, treasury minimum, claim count, government type, resource stock.

### GUI and Menu

Borrow:
- Paper MenuType as the preferred native API surface.
- Okaeri Menu's pane, async loading, loading/error/empty state, and pagination patterns.

Avoid:
- Old Inventory hack assumptions unless Paper MenuType cannot represent the required screen.
- Deep GUI coupling to business modules.

Rebuild:
- Add a `foundation.menu` package that exposes view models and rendering primitives.
- Let policy/tree modules provide pure view state. The menu renderer should ask services for view models and never mutate domain entities directly.
- Preserve the existing web map path as a separate `module.map` surface; do not merge in-game GUI and web map concerns.

### Persistence and Performance

Borrow:
- Towny's persistence/migration boundary thinking, not its exact implementation.
- Paper's database guidance and HikariCP direction for production database access.
- spark profiling for measurable hotspot work.
- Caffeine's bounded cache model and JTS's geometry primitives where dependency policy allows.

Avoid:
- Current synchronous `saveState()` calls on every small mutation becoming permanent architecture.
- Async-by-name only. `CompletableFuture` wrappers are not enough if the mutation path immediately performs blocking file IO.
- Treating Poly2Tri as a general claim-checking solution. Triangulation is not the same as robust territory containment/intersection.

Rebuild:
- Introduce repository interfaces per module and a shared data engine:
  - `starcore-data-api`: repository contracts, migrations, transaction boundary.
  - `starcore-data-sqlite`: default local store.
  - `starcore-data-mysql`: large-server store.
- Add write-behind batching for high-frequency state: claims, map terrain dirties, treasury transactions, relation changes.
- Add cache policy explicitly: Caffeine for local computed state, bounded maps for map tiles, metrics counters around every flush/render path.

## Recommended V0.1 to V1 Roadmap

### V0.1 Hardening

1. Keep the current monolithic Maven module for now, but formalize packages as if they were future modules.
2. Add repository interfaces beside each service and adapt current `.properties` persistence behind those interfaces.
3. Add event classes for nation create/member join/claim, relation changed, policy activated/expired, treasury transaction, and resolution enacted.
4. Make policy definitions configurable while keeping the five current hard-coded policies as bundled defaults.
5. Add tests for module dependency ordering, cyclic dependency failure, policy conflict/prerequisite/cooldown behavior, and persistence reload.
6. Start using Adventure/MiniMessage for command output and player-facing text keys.

### V0.5 Core Strategy

1. Add `ModifierEngine` and make policy effects queryable by key, scope, nation, duration, and source module.
2. Add multi-policy strategy state: permanent unlocks, active focuses, timed decrees, and mutually exclusive branches.
3. Move treasury from balances-only to transaction ledger plus accounts.
4. Move diplomacy from relation pairs only to treaty/resolution-backed relation changes.
5. Add Paper MenuType prototype for policy tree browsing and activation.
6. Add Caffeine caches and JTS-backed spatial services behind interfaces, with clear cache invalidation events.

### V1.0 Launch Cut

1. Switch production persistence to SQLite by default with migration metadata.
2. Use HikariCP-backed SQL repositories for SQLite/MySQL so the default store and large-server store share the same connection discipline.
3. Publish a minimal Java API around nation, policy, diplomacy, treasury, and map services.
4. Add CI matrix for supported Paper versions and unit/integration tests.
5. Add spark profiling scripts and acceptance budgets for map tile render, claim lookup, policy activation, and persistence flush; keep Aikar flags/server tuning as a deployment baseline rather than a plugin-internal substitute.
6. Keep external integrations optional: PlaceholderAPI-compatible placeholders can exist, but no hard runtime dependency on Vault, LuckPerms, Towny, or WorldDynamics.
7. Publish releases through Modrinth, Hangar, and SpigotMC with the same MIT artifact, license notes, and optional-adapter compatibility matrix.
8. Document optional paid profiling escalation with YourKit/JProfiler only for hard-to-reproduce CPU or memory cases.

## Immediate Technical Decisions

- Do not copy code from Towny, Towns-and-Nations, WorldDynamics, or TechTree into STARCORE. Licenses and age make them unsafe for direct reuse.
- Prefer MIT-compatible implementation dependencies if a dependency is unavoidable. Okaeri Menu and InventoryFramework are acceptable candidates; GPL references stay conceptual.
- Keep STARCORE independent of Towny/Vault/LuckPerms at runtime. If compatibility hooks are later added, make them optional adapters.
- Treat Paper 1.21.11 as the compile target, but verify every proposed MenuType/API call against the actual `paper-api` dependency before implementation.
- Replace "300% performance improvement" language with measurable budgets: max claim lookup latency, max policy activation latency, max map render concurrency, max flush time, and server TPS impact under spark profiling.
- Use Maven multi-module boundaries before JPMS. Adopt JPMS only if it does not fight Paper plugin classloading; treat PF4J as a future extension/plugin marketplace option.

## Source Links

- Towny repo and release: https://github.com/TownyAdvanced/Towny, https://github.com/TownyAdvanced/Towny/releases/tag/0.103.0.0
- Towns-and-Nations repo and release: https://github.com/Leralix/Towns-and-Nations, https://github.com/Leralix/Towns-and-Nations/releases/tag/v1.0.0
- NationTech parent: https://github.com/Santiago22022/NationTech-parent
- WorldDynamics Engine: https://github.com/stoleyourharvs/WorldDynamics-Engine
- SkillTree: https://github.com/ThizThizzyDizzy/skill-trees
- TechTree: https://github.com/ASangarin/TechTree, https://www.spigotmc.org/resources/techtree.98962/
- Nations-Legacy: https://www.spigotmc.org/resources/nations-legacy.110074/
- Paper MenuType API: https://docs.papermc.io/paper/dev/menu-type-api/
- Paper database guidance: https://docs.papermc.io/paper/dev/using-databases/
- HikariCP: https://github.com/brettwooldridge/HikariCP
- Okaeri Menu: https://github.com/OkaeriPoland/okaeri-menu
- Inventory Framework: https://github.com/devnatan/inventory-framework
- spark profiler: https://github.com/lucko/spark
- Paper Aikar flags / flags.sh: https://docs.papermc.io/paper/aikars-flags/, https://flags.sh/
- JPMS and PF4J: https://docs.oracle.com/javase/9/docs/api/java/lang/module/package-summary.html, https://github.com/pf4j/pf4j
- Configurate and Adventure: https://github.com/SpongePowered/Configurate, https://github.com/PaperMC/adventure
- Caffeine and JTS: https://github.com/ben-manes/caffeine, https://github.com/locationtech/jts
- Open plugin directories: https://www.spigotmc.org/wiki/list-of-open-source-plugins/, https://github.com/LiteDevelopers/awesome-minecraft
- Release platforms: https://modrinth.com/, https://hangar.papermc.io/, https://www.spigotmc.org/resources/
- Runtime CI and paid profilers: https://github.com/FN-FAL113/minecraft-plugin-runtime-test, https://www.yourkit.com/java/profiler/, https://www.ej-technologies.com/products/jprofiler/overview.html
