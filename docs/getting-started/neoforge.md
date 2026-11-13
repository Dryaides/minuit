# NeoForge

Add the Modrinth Maven to `build.gradle`:

```groovy
repositories {
    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = "https://api.modrinth.com/maven"
            }
        }
        filter { includeGroup "maven.modrinth" }
    }
}
```

```groovy
dependencies {
    implementation "maven.modrinth:minuit:2.0.1+26.1.2-neoforge"
}
```

Declare it in `neoforge.mods.toml`:

```toml
[[dependencies.mymod]]
modId = "minuit"
type = "required"
versionRange = "[2.0,3.0)"
ordering = "NONE"
side = "BOTH"
```

Register your theme from your client setup:

```java
Themes.register(Themes.defaultTheme().as("mymod"));
```

Next: [a simple screen](../guides/simple-screen.md).
