# Repository Guidelines

## Project Structure & Module Organization

Openwheel Racing is a Java 21 NeoForge mod. Production code lives under `src/main/java/com/openwheelracing/`; keep client-only features in `client/`, registrations in `registry/`, networking in `network/`, and gameplay systems in `content/`. Assets and data packs are in `src/main/resources/assets/openwheelracing/` and `src/main/resources/data/`. Generated data belongs in `src/generated/resources/` and should be produced through Gradle rather than edited casually.

Standard tests mirror production packages under `src/test/java/`. The standalone livery renderer and its tests use `src/liveryPrototype/java/` and `src/liveryPrototypeTest/java/`. Design specifications are in `docs/mechanism-specs/`; consult them before changing balance, progression, or physics.

## Build, Test, and Development Commands

- `./gradlew build` compiles, runs all checks, and creates the mod JAR in `build/libs/`.
- `./gradlew test` runs the standard JUnit 5 suite.
- `./gradlew liveryPrototypeTest` tests the isolated livery renderer.
- `./gradlew runClient` launches a local client in `run/` and requires a GUI display.
- `./gradlew runServer` or `./gradlew runGameTestServer` starts server validation.
- `./gradlew runData` regenerates resources in `src/generated/resources/`.

Use `./gradlew test --tests 'com.openwheelracing.content.ai.BasicAiGripModelTest'` for a focused test.

## Coding Style & Naming Conventions

Follow existing Java conventions: four-space indentation, braces on the same line, `UpperCamelCase` types, `lowerCamelCase` methods/fields, and `UPPER_SNAKE_CASE` constants. Keep packages lowercase beneath `com.openwheelracing`. Name registries with the `OWR` prefix where established. No formatter or lint task is configured, so match surrounding code and keep imports clean. Use UTF-8.

## Testing Guidelines

JUnit Jupiter 5.11.4 is configured. Name test classes `*Test` and test methods after observable behavior. Add focused unit tests for physics, geometry, timing, AI, and serialization changes. Run `./gradlew build` before submitting; it includes both standard and livery prototype tests.

## Assets, Localization, and Configuration

Keep `en_us.json` and `zh_cn.json` key sets aligned. Author directional textures north-facing and use blockstate rotations. Do not commit runtime noise from `run/` or `run-data/`. Never store credentials or machine-specific paths in tracked configuration.

## Commit & Pull Request Guidelines

Recent commits use concise release summaries such as `v1.15.2 AI consolidate`. Prefer short, imperative subjects; include a version prefix only for release commits. Pull requests should explain behavior changes, list validation commands, link relevant issues/specs, and include screenshots or clips for UI, model, texture, or rendering changes.
