# Paper

A Paper plugin opens minuit forms with no mod loader. Players with the minuit mod see the form in their
theme; the others get a vanilla dialog.

Your plugin shades `minuit-paper` and `minuit-protocol` and relocates them into its own package. The two jars
come from minuit's own Maven, not Modrinth:

```groovy
repositories {
    maven { url = "<MINUIT_MAVEN_URL>" } // not published yet
}
dependencies {
    implementation "dev.dryaides:minuit-paper-26.1.2:2.0.1" // its POM pulls minuit-protocol
    compileOnly "io.papermc.paper:paper-api:<paper-api-version>"
}
```

Call `PaperForms.of(plugin)` once from `onEnable`, then open forms:

```java
FormServer<Player> forms = PaperForms.of(this).forms();
Form form = Form.custom("myplugin:settings")
        .title(Text.lit("Settings"))
        .toggle("pvp", Text.lit("PvP"), false)
        .button("save", Text.lit("Save"), Role.PRIMARY)
        .build();
forms.open(player, form, new FormHandler() {
    @Override public void pressed(FormSession session, String id) {
        if (id.equals("save")) save(session.value("pvp"));
    }
});
```

Form types live in `dev.dryaides.minuit.form`. See the server side section of [API.md](../../API.md).
