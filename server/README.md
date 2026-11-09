# KSP OSRS World Coordinator

Java 21: set `KSP_ADMIN_TOKEN` (at least 24 characters), then run `scripts/run.bat` on Windows or `scripts/run.sh` on Linux. Dashboard: http://localhost:8087/admin.

- Start or stop **world availability flags** for independently configured F2P/P2P world definitions.
- Persist world states to `data/world-states.properties` (override path using `KSP_WORLD_STATE_FILE`).
- Public read-only discovery: `GET /client/manifest` returns world definitions plus honest protocol-readiness flags.
- Authenticated controls: `GET /admin/api/worlds`; `POST /admin/api/worlds/{id}/start` and `.../stop`.
- Dashboard and API bind to loopback only. Do not expose port 8087 to external networks.

**Not playable:** `gameProtocolReady=false`. Login, RS game packets, world simulation, bank synchronization, and Microbot login are not yet implemented. The old bank editor is a separate prototype and is not wired to this coordinator.

CI verifies compilation, auth, world lifecycle state, persistence across restart, and discovery.
