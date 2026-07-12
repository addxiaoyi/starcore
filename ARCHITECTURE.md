# StarCore Architecture

This document describes the high-level architecture of StarCore.

## Overview

StarCore is a Paper/Nukkit plugin that provides a complete national strategy and policy engine for Minecraft servers. It features 46 core modules covering nation management, diplomacy, warfare, economics, and more.

## Technology Stack

| Category | Technology |
|----------|------------|
| Language | Java 21 |
| Platform | Paper 1.21.11+ / Nukkit |
| Build | Maven |
| Database | MySQL / SQLite / Redis |
| Cache | Caffeine |
| GUI | TriumphGUI 3.1.10 |
| Economy | Vault API |

## Module Architecture

### Core Modules (core/)

| Module | Description |
|--------|-------------|
| `StarCoreContext` | Global context, holds all service references |
| `ModuleManager` | Module lifecycle management (load/enable/disable) |
| `StarCoreEventBus` | Event bus, supports cross-module events |
| `DatabaseService` | Database connection pool (MySQL/SQLite/Redis) |
| `ServiceRegistry` | Service registry for dependency injection |
| `StarCoreScheduler` | Async task scheduling (Folia compatible) |
| `PersistenceService` | Configuration persistence service |

### Business Modules (module/)

| Module | Description |
|--------|-------------|
| `NationModule` | Nation system: create/dismiss nations, member management |
| `GovernmentModule` | Government system: monarchy/republic/dictatorship |
| `DiplomacyModule` | Diplomacy: alliances/truces/declarations |
| `WarModule` | War system: declarations, battles, peace treaties |
| `PolicyModule` | Policy system: national policy effects |
| `TechnologyModule` | Technology: research, upgrades |
| `ResourceModule` | Resource: collection, processing |
| `ResolutionModule` | Resolution: national voting decisions |
| `TreasuryModule` | Treasury: fund management |
| `OfficerModule` | Officer: appointment management |
| `ArmyModule` | Military: armies, formations |
| `MapModule` | Map: web map, markers |

### Feature Modules (功能子系统)

| Module | Description |
|--------|-------------|
| `RegionModule` | Region: display titles when entering regions |
| `TitleModule` | Title system: titles, badges |
| `EssentialsModule` | Essentials: homes, teleports, economy |
| `PvPModule` | PvP: duels, damage rules |
| `SocialModule` | Social: friends, mail |
| `AchievementModule` | Achievement: tasks, rewards |
| `StorageModule` | Storage: item storage |
| `AI/SeasonModule` | Season/AI system |

### Infrastructure (foundation/)

| Module | Description |
|--------|-------------|
| `player/` | Player data, profile services |
| `territory/` | Territory claiming, region protection |
| `economy/` | Economy balance storage |
| `message/` | Message service (i18n) |
| `epoch/` | Epoch/time system |
| `permission/` | Permission system |

## Dependency Injection

Uses `StarCoreContext` as the central container:

```java
public class StarCoreContext {
    private final StarCorePlugin plugin;
    private final PlatformAdapter platformAdapter;
    private final ConfigurationService configurationService;
    private final StarCoreScheduler scheduler;
    private final StarCoreEventBus eventBus;
    private final PersistenceService persistenceService;
    private final DatabaseService databaseService;
    private final InternalPermissionService permissionService;
    private final InternalEconomyService economyService;
    private final ModuleManager moduleManager;
    private final ServiceRegistry serviceRegistry;
}
```

## Database Architecture

### Supported Databases

- **MySQL**: Primary database for production
- **SQLite**: Embedded database for small servers
- **Redis**: Cross-server communication cache

### Migration Files

`src/main/resources/db/migration/`
- `V1__initial_schema.sql` - Initial table structure
- `V2__map_module.sql` - Map module extensions
- `V3__performance_indexes.sql` - Performance indexes

## Configuration System

### Main Configuration

`config.yml` - Plugin global configuration

### Feature Configuration

`src/main/resources/`
- `achievements.yml` - Achievement config
- `titles.yml` - Title config
- `badges.yml` - Badge config
- `technologies.yml` - Tech tree config
- `resources.yml` - Resource type config
- `seasons.yml` - Season config
- `nations/` - Nation menu config
- `quest/` - Quest config

### Language Files

`lang/messages_zh_cn.yml` - Chinese messages
`lang/messages_en.yml` - English messages

## API Design

### Public API

`api/StarCoreApi.java` - Public interface for other plugins

```java
public interface StarCoreApi {
    Optional<NationService> getNationService();
    Optional<GovernmentService> getGovernmentService();
    Optional<DiplomacyService> getDiplomacyService();
    // ... other services
}
```

### PlaceholderAPI Integration

`title/StarCorePlaceholderExpansion.java` - PAPI placeholders

## Module Development Guide

### Creating a New Module

1. Implement `StarCoreModule` interface
2. Register in `StarCorePlugin.onEnable()`
3. Annotate with `@ModuleMetadata`

```java
@ModuleMetadata(
    id = "my-module",
    name = "My Module",
    version = "1.0.0",
    authors = {"Author"}
)
public class MyModule implements StarCoreModule {
    @Override
    public void enable(StarCoreContext context) {
        // Initialization logic
    }
}
```

## Design Patterns

1. **Modular**: All features are independent modules, hot-swappable
2. **Service Locator**: Get services through ServiceRegistry
3. **Event-Driven**: Decouple modules through StarCoreEventBus
4. **Strategy**: Different database implementations are replaceable
5. **Observer**: Region enter/leave events

## Performance Optimization

- **Cache**: Use CacheManager for hot data
- **Async**: Use async threads for database/file I/O
- **Connection Pool**: HikariCP for database connections
- **Index**: Create indexes for key query fields

## Extension Integration

Supports integration with:
- PlaceholderAPI - Placeholder expansion
- Citizens - NPC integration
- Major land protection plugins - Through ExternalProtectionService

## Directory Structure

```
src/main/java/dev/starcore/starcore/
├── core/              # Core framework
│   ├── module/        # Module system
│   ├── database/      # Database services
│   ├── event/         # Event bus
│   ├── scheduler/     # Task scheduling
│   └── service/       # Core services
├── module/            # Business modules
│   ├── nation/        # Nation system
│   ├── government/    # Government system
│   ├── diplomacy/     # Diplomacy system
│   └── ...
├── foundation/        # Infrastructure
│   ├── player/        # Player data
│   ├── territory/     # Territory
│   └── ...
└── [feature modules]/ # Feature subsystems
```
