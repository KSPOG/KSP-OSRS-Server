# Microbot 2.6.55: private-server bootstrap contract

Inspected from the uploaded Microbot source:

- `MicrobotClientLoader.java` loads injected client configuration and instantiates a client class.
- `MicrobotClientConfigLoader.java` accepts line-oriented `key=value` entries; `param=name=value` entries become applet parameters.
- `MicrobotRSConfig.java` exposes `codebase`, `initial_jar`, `initial_class`, and the `runelite.worldparam` metadata.
- `MicrobotRSAppletStub.java` forwards the codebase and applet parameters to the injected client.
- `WorldService.java` retrieves world lists from RuneLite's world service.

## Missing for a playable system

1. Pin the exact OSRS gamepack/client **revision**, distinct from Microbot's application version.
2. Provide legitimate compatible gamepack and `jav_config` plus update (JS5) cache delivery.
3. Implement revision-specific login cryptography and packet transport.
4. Implement player spawn, regions, movement, widgets, varps/varbits, NPCs and interactions.
5. Expose compatible RuneLite/Microbot game state and validate plugin behavior.
6. Connect live account/bank persistence and membership restrictions to world sessions.

The currently implemented loopback TCP listeners immediately close sockets. The discovery manifest correctly reports `gameProtocolReady=false` and `javConfigReady=false`. Do not flip those flags until functionality has passed integration tests. RSProx is not required.
