# Live race maps

Install the updated mod on the server and viewing clients; this adds network payloads and changes the protocol version.

- `/owrmap` toggles both HUD maps. They start disabled and do not open automatically when joining a world or a race.
- `/owrmap on` and `/owrmap off` explicitly show or hide them.
- `/owrmap screen` opens larger maps and a scrollable driver roster. Escape returns to the HUD maps.

The HUD leaves movement and camera controls available for travelling and recovery work. The first view fits the circuit and all reported cars. The second is a north-up 200 m square centered on the configured start/finish line (100 m in each direction). Yellow marks the line; the green arrow indicates the configured racing direction. Each car also has a heading arrow.

Every driver receives a distinct color and numbered name label, independent of team livery. Colors and numbers remain stable for the viewing client's connection, including when drivers disappear or the maps are toggled. Crowded labels move apart with leader lines; if the HUD runs out of room, numbered markers correspond to the expanded view's roster.

Positions come from the server at up to 10 Hz, covering all loaded Openwheel cars in the viewer's dimension regardless of distance or client entity tracking range. Player-driven, AI, and empty cars are included. Empty cars use a bracketed entity ID. The maps do not load inactive chunks or show cars in other dimensions. A stalled feed displays a warning after two seconds. Turning the maps off stops position requests, and disconnecting resets them to disabled.

Track surfaces use the saved map from Race Director's auto-detection. The active track's surveyed route (or centerline when no survey is saved) is also drawn, so a surface map is optional. The finish view requires an active track with a configured start/finish line; without one it displays a setup message. Static geometry refreshes every five seconds while positions continue updating.

These maps show position evidence; they do not change lap detection, lap counting, or race classification.

Validation: `./gradlew build --offline` includes driver-color uniqueness/stability, map projection, and live-map packet round-trip tests with cars tens of kilometers apart. In-game checks should include two same-team drivers, a viewer outside entity tracking range, a packed grid, dimension changes, toggling, and travel with the HUD enabled.
