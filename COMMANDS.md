# Commands

## Build

Full build with tests:
```
docker run --rm -v "$HOME/.m2:/root/.m2" -v "$(pwd):/app" -w /app maven:3.9-eclipse-temurin-17 mvn package
```

Skip tests:
```
docker run --rm -v "$HOME/.m2:/root/.m2" -v "$(pwd):/app" -w /app maven:3.9-eclipse-temurin-17 mvn package -DskipTests
```

Tests only:
```
docker run --rm -v "$HOME/.m2:/root/.m2" -v "$(pwd):/app" -w /app maven:3.9-eclipse-temurin-17 mvn test
```

## Output

The distributable files are in `target/`:
- `TouchPortalGoveeLANPlugin.tpp` — import this in Touch Portal
- `setup-firewall.ps1` — run this on Windows before installing the plugin (in the repo root, not generated)
