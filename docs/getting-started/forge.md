# Forge

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
    // ForgeGradle
    implementation fg.deobf("maven.modrinth:minuit:2.0.0+1.20.1-forge")
}
```

Declare it in `mods.toml`:

```toml
[[dependencies.mymod]]
modId = "minuit"
mandatory = true
versionRange = "[2.0,3.0)"
ordering = "NONE"
side = "BOTH"
```

Register your theme from `FMLClientSetupEvent`:

```java
Themes.register(Themes.defaultTheme().as("mymod"));
```

Next: [a simple screen](../guides/simple-screen.md).
