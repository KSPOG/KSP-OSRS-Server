# KSP Microbot Private Server Integration

This is an **opt-in** private profile in `MicrobotClientLoader`, designed to leave the normal RuneLite/Microbot connection untouched.

On Windows PowerShell:

```powershell
$env:KSP_OSRS_PRIVATE_SERVER = "true"
$env:KSP_OSRS_DISCOVERY_URL = "http://127.0.0.1:8087/client/manifest"
$env:KSP_OSRS_PRIVATE_JAV_CONFIG_URL = "http://127.0.0.1:8090/jav_config.ws"
```

The client checks the server's discovery manifest before downloading `jav_config` and refuses to enter an incomplete private mode. Both discovery and configuration addresses are restricted to HTTP loopback for this development phase. The server currently advertises `gameProtocolReady=false` and `javConfigReady=false`, so **private Microbot login is intentionally blocked** with a descriptive error.

Port 8087 is the local administration/discovery HTTP service, not an OSRS game endpoint. Port 8090 above is reserved for a future configuration service, which is not yet implemented. Do not point clients at 8087 for game networking.

Remove the environment variables to use normal Microbot startup. RSProx is not required. Remaining work: pin matching revision, serve a working private `jav_config`, implement OSRS login/game packets and world synchronization, then verify gameplay with Microbot.

## Local world list integration

In opt-in private mode, RuneLite `WorldClient.lookupWorlds()` now reads from `http://127.0.0.1:8087/client/worlds.js` instead of the official RuneLite world-directory service. Override using `KSP_OSRS_WORLDS_URL` (only HTTP loopback is permitted). The API returns F2P/P2P flags and omits stopped worlds.

This changes **the RuneLite world service** only. The injected game client's internal world list, game protocol, cache/JS5 update service, and private login remain outstanding. No login success is claimed.

## Verify actual OSRS revision (required before protocol activation)

The uploaded Microbot source reports application version 2.6.55, which is **not** proof of an OSRS game protocol revision. A diagnostic probe now logs the active injected client's `getRevision()` when opted in:

```powershell
$env:KSP_OSRS_LOG_REVISION = "true"
Remove-Item Env:KSP_OSRS_PRIVATE_SERVER -ErrorAction SilentlyContinue
# Launch the locally compiled Microbot client as normal.
```

Look for `KSP_OSRS_REVISION | revision=<number> buildId=<id> privateProfile=false` in Microbot's log. Use a build from the **client branch** with the diagnostic commit, not an older released Microbot JAR. Share only the revision/build ID line; redact usernames, authentication details and session tokens. A verified matching gamepack and cache are still required for actual integration tests.

RSProt revision 242 is available under MIT, but must **not** be activated unless the OSRS client revision and cache are known to match. The server's protocol laboratory is deliberately isolated from live listeners.

## Standard client bootstrap now instrumented

The normal `:client:run` entry point uses `net.runelite.client.rs.ClientLoader`, **not** `MicrobotClientLoader`. Both loaders now implement the opt-in private profile, check readiness, and can log `KSP_OSRS_REVISION` with `KSP_OSRS_LOG_REVISION=true`. No official-client fallback occurs in private mode. This fixes the previously missing revision log for the standard launcher. Private gameplay remains blocked while the server advertises `gameProtocolReady=false`.

## Delayed revision probe (commit 4ba2a8d)

The early `ClientLoader` diagnostic can legitimately print `revision=0` before the injected game has initialized. The Microbot plugin now additionally logs:

- `KSP_OSRS_REVISION_READY | revision=... buildId=... state=... privateProfile=...` when `Client.getRevision()` becomes positive.
- `KSP_OSRS_REVISION_UNAVAILABLE | revision=0 buildId=... state=LOGIN_SCREEN` if the login screen is reached while the method still returns zero.

The injected build ID suffix (for example `.307`) is not, by itself, certified proof of the wire-protocol/cache revision. Do not activate RSProt 242 or another implementation from that suffix alone.

To apply this diagnostic from GitHub without Git CLI, update **both** `MicrobotPlugin.java` and `KspRuntimeRevisionProbe.java` from the same client branch commit. Recompile using the existing revision probe script. Copy only the `KSP_OSRS_REVISION_READY` or `KSP_OSRS_REVISION_UNAVAILABLE` log line, not the full client log containing account metadata.

## Verified OSRS revision 242 (10 Nov 2026)

The actual injected game client's `Client.getRevision()` returned **242** at `LOGIN_SCREEN`, with build ID `41605773992.307`. This confirms the target protocol revision for this pinned client build. `KspServerDiscovery` rejects mismatched or unverified revisions before checking protocol readiness. The server still has `gameProtocolReady=false` and `javConfigReady=false`, so a private-game login is intentionally unavailable until those systems really exist and are verified.

## JS5-only transport diagnostic (separate from private login)

The server branch includes a **one-session, loopback-only** verified-master
JS5 probe bound on `127.0.0.1:43596`. This client branch includes
`KspJs5ConnectionProbe.java` as an **explicit standalone Java main class**
and the Gradle task `:client:probeKspJs5Connection`.

This diagnostic verifies the revision-242 21-byte JS5 handshake
(opcode 15 + revision 242 and four 32-bit words), the acknowledged
`255/255` master response exactly against a SHA-256 pinned independent
OpenRS2 archived master, and a subsequent XOR-wrapped `255/24` response.
It does NOT inject network hooks into the gamepack, configure the
injected client connection, or activate private login. Regular RuneLite
startup and `KspServerDiscovery.verifyReady()` are unchanged.

The accompanying server-branch script
`server/tools/test-js5-client-branch.ps1` creates a short-lived
loopback JS5 listener and runs this client-branch diagnostic automatically.
It also cleans up the listener it creates. The test cannot establish
acceptance by the injected game client and must never be represented
as a successful game login.

To test the **real injected game client**, a separate safe, opt-in
configuration or tested network routing mechanism will be required.
The documented private profile still fails closed until actual game
protocol readiness and private `jav_config` exist.

## Experimental injected-gamepack JS5 endpoint test (not private login)

The `client` branch now has an **independent, opt-in** endpoint-hint
override for the injected game client's `ClientConfiguration`:

- `KSP_OSRS_JS5_GAMEPACK_PROBE=true` activates it for this client process.
- `KSP_OSRS_JS5_PROBE_PORT=43596` controls the loopback port.
- The standard `RSAppletStub` and `MicrobotRSAppletStub` return
  `http://127.0.0.1:43596/` as their applet codebase and redirect
  applet parameters named `server`, `port1`, and `port2` to
  `127.0.0.1`, `43596`, and `43596` respectively.
- All other parameters remain unchanged. The default applet configuration
  remains unchanged when the environment flag is absent.
- `KSP_OSRS_PRIVATE_SERVER=true` cannot be combined with probe mode.
  Discovery/game-protocol readiness checks remain intact.
- The two loader entry points log a warning with
  `KSP_JS5_GAMEPACK_PROBE` when the experimental profile is enabled.

**Important:** This is a candidate OSRS gamepack endpoint mechanism, not
yet a verified mapping of revision-242 native JS5 host/port selection.
Gamepack networking and application login may obtain host data through
other code. Do not enter live account credentials during these tests.
No proxy, gamepack transformation, OSRS world login, or authentication
bypass is implemented.

The server development branch includes
`server/tools/prepare-js5-gamepack-client.ps1` to download a pinned
isolated client snapshot when needed, install only the checked-in probe
source into that workspace, and compile the real client. The
`-LaunchClient` switch additionally starts an ephemeral, one-session
localhost-only verified JS5 probe and opens the client in its own
PowerShell window with the probe environment set. The launcher
does not click, authenticate, or interact with the game.

The next Windows run must establish whether the **injected gamepack**
actually sends an opcode-15 revision-242 request to the loopback JS5
listener. Until such network evidence exists, native client acceptance
remains UNVERIFIED.
