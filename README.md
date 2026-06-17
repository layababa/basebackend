# basebackend

Layababa backend shared SDK.

## Gradle

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.layababa:basebackend:v0.3.0'
}
```

## Scope

The SDK contains shared response DTOs, error contracts, business exceptions, utility extensions, low-coupling Spring backend infrastructure, and common Mongo models/repositories used by the backend services.

Feature services and controllers that depend on app-specific authentication, notification, or moderation policies stay in each backend. Keep those integrations in the consuming app and depend on SDK contracts/models instead.
