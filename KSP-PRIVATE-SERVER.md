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
