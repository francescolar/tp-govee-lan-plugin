# Commands

## Build

Full build with tests (from the repo root, SDK already on Maven Central):
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

## Build while SDK is not yet on Maven Central

Install the SDK into the local Maven cache first (run from the govee-lan-sdk folder):
```
docker run --rm -v "$HOME/.m2:/root/.m2" -v "$(pwd)/../govee-lan-sdk:/app" -w /app maven:3.9-eclipse-temurin-17 mvn install -DskipTests
```

Then build the plugin (from the tp-govee-lan-plugin folder):
```
docker run --rm -v "$HOME/.m2:/root/.m2" -v "$(pwd):/app" -w /app maven:3.9-eclipse-temurin-17 mvn package -DskipTests
```

## Output

The distributable files are in `target/`:
- `TouchPortalGoveeLANPlugin.tpp` — import this in Touch Portal
- `setup-firewall.ps1` — run this on Windows before installing the plugin (already in the repo root, not generated)
