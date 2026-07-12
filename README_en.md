# 🌟 StarCore

**Paper/Nukkit Native National Strategy & Policy Engine** — Complete multi-player kingdom management, territory systems, diplomacy, war, finance, and technology gameplay.

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-green.svg)](https://www.minecraft.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Build](https://github.com/addxiaoyi/starcore/workflows/Build/badge.svg)](https://github.com/addxiaoyi/starcore/actions)
[![Issues](https://img.shields.io/github/issues/addxiaoyi/starcore)](https://github.com/addxiaoyi/starcore/issues)
[![Stars](https://img.shields.io/github/stars/addxiaoyi/starcore)](https://github.com/addxiaoyi/starcore)

---

## ✨ Core Features

| Module | Description |
|--------|-------------|
| **Nation** | Nation creation, member management, capital elections |
| **Territory** | Land claiming, lease, visual preview |
| **Treasury** | Treasury management, taxation, budget |
| **Diplomacy** | Diplomatic relations, alliances, truces |
| **War** | Declaration, war rules, peace treaties |
| **Army** | Army management, formations, doctrines |
| **Officer** | Officer appointments, permission management |
| **Policy** | National policies, effect application |
| **Technology** | Tech research, seasonal tech trees |
| **Resolution** | Voting system, referendums |
| **Resource** | Resource blocks, collection, processing |
| **Event** | Event recording, queries, statistics |
| **Government** | Government types (Monarchy/Republic/Dictatorship) |
| **Visualizer** | Block interaction visual preview |

---

## 🚀 Quick Start

### Build

```bash
mvn clean package
```

Output: `target/starcore-0.1.0-SNAPSHOT.jar`

### Requirements

- **Java** 21+
- **Minecraft** 1.21.11+ (Paper/Spigot)
- **Vault** (Required)

### Integration Support

| Integration | Description |
|-------------|-------------|
| Vault | Economy system |
| PlaceholderAPI | Placeholder expansion |
| squaremap/Pl3xMap/dynmap | Map rendering |
| ProtectorAPI/WorldGuard | Land protection |

---

## 📁 Project Structure

```
src/main/java/dev/starcore/starcore/
├── foundation/        # Infrastructure (economy, storage, feedback)
├── module/            # Core business modules
├── integration/       # Plugin integration
└── api/               # Public API
```

---

## 📊 Project Statistics

| Metric | Count |
|--------|-------|
| Java Classes | 677+ |
| Core Modules | 13 |
| Integration Plugins | 6 |

---

## 🤝 Contributing

Issues and Pull Requests are welcome!

### Development Environment

1. Install JDK 21+
2. Clone repo: `git clone https://github.com/addxiaoyi/starcore.git`
3. Build: `mvn clean package`
4. Copy JAR to server's plugins folder

### Development Standards

- Code Style: Follow Google Java Style Guide
- Commit Style: Use Conventional Commits
- Branch Strategy: feature/*, fix/*, refactor/*

---

## 📄 License

[MIT License](LICENSE)

---

## 🔗 Links

- [Report Issues](https://github.com/addxiaoyi/starcore/issues)
- [Feature Requests](https://github.com/addxiaoyi/starcore/discussions)
