# KSP OSRS Server — world coordinator / TCP transport scaffold

Requires **JDK 21**. Set a 24+ character `KSP_ADMIN_TOKEN`, and execute `scripts/run.bat` (Windows) or `scripts/run.sh`. Dashboard: http://localhost:8087/admin.

## World lifecycle

- Configure F2P/P2P worlds in `config/worlds.csv`.
- Each running world owns a real **loopback-only TCP listener** at `KSP_WORLD_PORT_BASE + world ID` (default base 43000; World 301 = port 43301).
- Stop closes the listener; Start binds it. Binding failures return HTTP 500 and do not mark the world running.
- Availability persists across coordinator restarts at `data/world-states.properties` (overridable with `KSP_WORLD_STATE_FILE`).
- Dashboard and admin API show `listenerActive`, `tcpPort`, and rejected connection attempts; `population=0` until genuine game sessions exist.
- `GET /client/manifest` returns public read-only world states and `gameProtocolReady=false`; `javConfigReady=false`.
- Changes require bearer-token admin API access. Admin HTTP and TCP listeners bind only to loopback.

**Crucial:** TCP sockets immediately disconnect clients. OSRS login, encryption, packets, movement, cache and game world replication have NOT been implemented. Do not interpret open TCP ports as a playable private server. No RSProx is needed.

The earlier local bank dashboard prototype has **not** been integrated into this GitHub coordinator.

## Tick scaffolding

Each running world owns an isolated nominal 600 ms tick counter, exposed as `ticks` in world discovery. It is a heartbeat only; **no player actions, NPCs, or game simulation are implemented**. Counters reset when stopped.

## RuneLite world discovery

Public loopback endpoint `GET /client/worlds.js` returns a RuneLite-style `WorldResult` object containing only started/listening worlds. P2P worlds include the `MEMBERS` type; F2P worlds have no membership flag. This does **not** make the in-game world switcher or OSRS login protocol work yet.

## Admin bank records

Open `http://localhost:8087/admin/bank` with the same administrator token. Create a local player record, set/delete bank slots, and inspect saved values. Changes persist to `data/players/*.properties` and append audit events to `data/bank-audit.jsonl`. Each bank mutation requires the previous revision; stale writes return HTTP 409. The player records are **administrative test data only**, not live OSRS accounts or session bank contents. Existing player records are not synchronized to Microbot.
