# KSP OSRS World Coordinator

Run with JDK 21: set `KSP_ADMIN_TOKEN` to a random 24+ character value, then use `scripts/run.bat` on Windows or `scripts/run.sh` on Linux. Open `http://localhost:8087/admin`.

The dashboard can start/stop F2P and P2P world **availability flags**. The changes currently live in memory only (restart reloads `config/worlds.csv`). There are no connected players or game listeners yet. Admin API is token-authenticated and bound to loopback.

**Not playable:** OSRS login, game protocol, movement, bank synchronization and Microbot gameplay connections are not yet implemented. Existing dashboard bank-editor v0.2.1 is a separate prototype, not included in this incremental GitHub coordinator.
