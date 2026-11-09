# Experimental OSRS revision-242 protocol adapter

This is a standalone Gradle laboratory, not part of the running coordinator. It pins the **published** RSProt 242 API artifact `net.rsprot:osrs-242-api:1.0.0-ALPHA-20261106` under the MIT license.

Run from `server/protocol-lab` with Java 21 and Gradle 8.8+: `gradle compileJava verifyProtocolDependencies`.

No RSProx dependency is present.

## Integration gates before this becomes the server protocol

1. Pin actual OSRS revision and subrevision of Microbot's injected gamepack. Do **not** infer revision 242 solely from its Microbot application version.
2. Implement a compatible update/cache service and legitimate private `jav_config`.
3. Configure RSProt's revision-specific network service, keys, login handshakes, packet codecs and session lifecycle.
4. Make the game tick scheduler dispatch decoded game packets and generate region/player updates.
5. Test a private Microbot login against a real world listener and verify runeLite game state.
6. Preserve F2P/P2P flags, membership policy, bank persistence and admin lifecycle in gameplay sessions.

The current `WorldSocketManager` continues to reject client connections. This laboratory confirms only that the required protocol dependency resolves and a Java adapter module compiles.
