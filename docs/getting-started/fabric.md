# Fabric

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

Depend on the build for your game version:

```groovy
dependencies {
    // 1.21.1 and older (Loom remaps the jar)
    modImplementation "maven.modrinth:minuit:2.0.1+1.21.1-fabric"
    // 26.x
    implementation "maven.modrinth:minuit:2.0.1+26.1.2-fabric"
}
```

Declare it in `fabric.mod.json` so the launcher asks the player for minuit:

```json
"depends": { "minuit": ">=2.0.0 <3.0.0" }
```

Register your theme in your client entry point, then open a screen:

```java
public class MyModClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        Themes.register(Themes.defaultTheme().as("mymod"));
    }
}
```

On Fabric a keybind field binds the key alone, without Ctrl, Shift or Alt (see [keybind field](../guides/keybind-field.md)).
Next: [a simple screen](../guides/simple-screen.md).
