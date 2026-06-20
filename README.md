# basebackend

Layababa backend shared SDK.

## Gradle

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    // Pin to a commit SHA for reproducible builds (avoid the drifting main-SNAPSHOT).
    implementation 'com.github.layababa:basebackend:<commit-sha>'
}
```

## Scope

The SDK contains shared response DTOs, error contracts, business exceptions, utility extensions, low-coupling Spring backend infrastructure, common Mongo models/repositories, and reusable optional feature contracts used by the backend services.

Feature services and controllers that depend on app-specific authentication, notification, or moderation policies stay in each backend. Keep those integrations in the consuming app and depend on SDK contracts/models instead.

## Integration

See [docs/INTEGRATION.md](docs/INTEGRATION.md) for the full integration guide (依赖引入、包名自动装配、配置项清单、Boot 4 / Jackson 3 注意事项、可观测性、排错).
