# Contributing to StarCore

Thank you for your interest in contributing to StarCore!

## Development Setup

### Prerequisites

- JDK 21 or higher
- Maven 3.8+
- Git
- A Minecraft 1.21.11+ server (Paper/Spigot) for testing

### Quick Start

```bash
# Clone the repository
git clone https://github.com/addxiaoyi/starcore.git
cd starcore

# Build the project
mvn clean package

# Run tests
mvn test
```

### IDE Setup

We recommend using IntelliJ IDEA with the following plugins:
- CheckStyle-IDEA
- Spotless
- Lombok

## Code Standards

### Formatting

We use [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) with Spotless.

```bash
# Format code
mvn spotless:apply

# Check formatting
mvn spotless:check
```

### Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Classes | PascalCase | `NationService` |
| Methods | camelCase | `getPlayerNation` |
| Constants | UPPER_SNAKE_CASE | `MAX_NATION_SIZE` |
| Config Keys | kebab-case | `enable-feature` |
| Packages | lowercase | `dev.starcore.starcore.module` |

### Commit Messages

We follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

Examples:
- `feat(nation): add capital election system`
- `fix(war): resolve TPS drop during battle`
- `docs(readme): update installation guide`

## Project Architecture

### Module Structure

```
module/
├── nation/        # Nation system
├── war/           # War system
├── army/          # Military system
├── diplomacy/     # Diplomacy system
├── treasury/      # Treasury system
├── technology/    # Technology system
├── policy/        # Policy system
└── ...
```

### Core Services

| Service | Description |
|---------|-------------|
| `StarCoreContext` | Global context holding all service references |
| `ModuleManager` | Module lifecycle management |
| `StarCoreEventBus` | Event bus for cross-module communication |
| `DatabaseService` | Database connection pool (MySQL/SQLite/Redis) |

## Testing

### Writing Tests

```java
class NationServiceTest {
    @Test
    void shouldCreateNation() {
        // Test implementation
    }
}
```

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=NationServiceTest

# Run with coverage
mvn test jacoco:report
```

## Pull Request Process

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Make your changes
4. Add tests for your changes
5. Ensure all tests pass: `mvn test`
6. Format code: `mvn spotless:apply`
7. Commit with clear message: `git commit -m "feat(scope): description"`
8. Push to your fork: `git push origin feature/your-feature`
9. Open a Pull Request

## Issue Guidelines

### Bug Reports

Include:
- Minecraft version
- Java version
- Plugin version
- Steps to reproduce
- Expected vs actual behavior
- Server logs (relevant parts)

### Feature Requests

Include:
- Use case description
- Proposed solution
- Alternative solutions considered
- Any additional context

## License

By contributing, you agree that your contributions will be licensed under the MIT License.
