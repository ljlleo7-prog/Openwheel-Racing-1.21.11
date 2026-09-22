# Forge branch maintenance

This branch targets Minecraft 1.21.11 on Forge 61.1.0 with Java 21.

## Recommended branch workflow

Keep loader integration changes on a long-lived Forge branch. Bring gameplay-only
commits across from the NeoForge branch with `git cherry-pick`; avoid repeatedly
rewriting the entire source tree. If a commit touches loader-facing code, port that
part deliberately and run the verification script.

The loader-facing boundary currently consists of:

- `build.gradle`, `settings.gradle`, and `gradle.properties`
- `src/main/resources/META-INF/mods.toml`
- registrations under `registry/`
- mod initialization and event subscriptions
- `network/OWRNetwork.java`
- client frame passes and GUI layers
- AI chunk forcing
- Forge biome modifiers under `data/openwheelracing/forge/`

Most gameplay, physics, serialization, and rendering calculations remain loader
independent and should merge or cherry-pick without modification.

## Verification

Run:

```sh
./scripts/verify_forge_port.sh
```

The script rejects stale NeoForge namespaces or metadata and then runs the complete
Gradle build. ForgeGradle may provision a Java 8 helper for its mapping conversion;
the project compilation and runtime toolchains are still explicitly Java 21.

## Updating Forge

Change the single `net.minecraftforge:forge` coordinate in `build.gradle`, then run
the verification script. Check Forge release notes before moving between minor Forge
lines because event, networking, and rendering APIs are not safely convertible by a
global package-name replacement.
