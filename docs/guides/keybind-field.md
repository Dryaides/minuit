# Keybind field

```java
// client side, with a KeyMapping your mod registers
add(new KeybindFieldComponent(at(new Box("key", 10, 30, 160, 20)), words, MinuitKeys.binding(openPets))
        .onChange(() -> saveOptions()));
```

The field shows the key as the Controls screen does, with a reset button while the binding is not the default.

- A click, Enter or Space starts listening; the next key or mouse button binds; Escape unbinds.
- On NeoForge and Forge, Ctrl, Shift or Alt held first makes a combo (`Ctrl + M`). Fabric binds the key alone.
- A modifier let go with no other key binds itself, so Left Shift stays bindable.
- A key another binding uses draws in red; the tooltip names the other bindings.
- `onChange` runs after every bind, unbind or reset.
