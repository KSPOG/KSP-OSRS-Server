# KSP Private Server — Microbot profile (scaffold)

This opt-in profile is integrated into `MicrobotClientLoader`. Existing official Microbot behavior is unchanged by default.

Windows PowerShell:

```powershell
$env:KSP_OSRS_PRIVATE_SERVER = "true"
$env:KSP_OSRS_PRIVATE_JAV_CONFIG_URL = "http://127.0.0.1:8090/jav_config.ws"
```

This requires a **separate compatible private jav_config and game endpoint**, which are **not implemented** in the current server branch. The server admin UI on port 8087 is NOT a jav_config endpoint and cannot accept OSRS game traffic. Do not point this configuration at port 8087.

The private profile fails closed when its config is unavailable, instead of silently connecting to the official game's configuration. Removing these environment variables restores the default official-client loading path.

Next tasks: pin actual revision, provide the private jav_config and matching cache/gamepack, implement OSRS login/game protocol, world selection and in-client integration tests. No RSProx dependency.
