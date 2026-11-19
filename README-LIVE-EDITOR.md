# KSP RSPS 242 — Online player bank and inventory editor

## Update overview

The previous `/admin/bank` editor used `data/players` (an isolated test record store). This release reads and edits the actual `data/gameplay/<account>.properties` files that are used by the game.

- The player dropdown lists **registered local RSPS accounts** with **online accounts first**, showing their current world.
- Online status is derived from authenticated game session join/leave notifications emitted by each world process. It is not inferred from account registration.
- Select **Bank** (800 slots) or **Inventory** (28 slots). Enter a slot, numeric item ID, and quantity to set or delete it.
- Every modification uses the same gameplay `.lock` file as the in-game inventory/bank, with optimistic revision checks and an admin audit log at `data/gameplay-admin-audit.jsonl`.
- Active clients poll for changed saved containers and receive inventory updates without relogging (bank updates while the bank window is open).

## Install (PowerShell 5.1)

1. Stop your server and Microbot clients.
2. Extract this ZIP to `Downloads`. **Do not overwrite the persistent** `KSP-RSPS-242-Local-Game-Windows/data` folder.
3. Make sure the matching revision 242 cache, RSA key, and **all RSProt runtime dependencies** are installed in `Downloads/KSP-RSPS-242-Local-Game-Windows`, as in your previous setup. Merely having the `osrs-242-api` JAR is insufficient.
4. In a PowerShell window, run:

```powershell
$release = "$env:USERPROFILE\Downloads\KSP-RSPS-242-Live-Editor\KSP-RSPS-242-Guide-Fix"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$release\install-live-editor.ps1"
```

5. Only after seeing `KSP_LIVE_EDITOR_INSTALLED`, start the server:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$release\start-worlds.ps1"
```

6. Browse to `http://127.0.0.1:8087/admin/bank`, authenticate using the **new** admin token printed in your server console, and test a disposable account first.

Client: run `start-local-microbot.ps1 -World 301` in a separate PowerShell window. If that launcher still expects an old Microbot checkout, use `restore-client.ps1` from this ZIP before launching it.

## Verification / limitations

- Verified in this execution environment: admin Java sources compiled with Java 21, HTTP API smoke tested (account listing, online world ID in roster, bank & inventory reads, updates, stale-revision rejection).
- **Not verified here:** compilation of the modified game-world classes against the full RSProt dependencies and live testing with the Windows Microbot game canvas. The installer deliberately fails without patching the JAR when this step fails.
- This is a local server editor only; it does not modify official OSRS accounts.
- The old admin-only `/admin/api/players` endpoint remains for compatibility, but the new dashboard does not use that test store.
