# KSP RSPS 242 — Client, Server and MCP

This Windows ZIP contains the Guide-Fix world server and dashboard, the pinned Microbot client source with the local launch overrides, the server runtime JARs, and the Python MCP bridge. The compiled server includes the Tutorial Island tab-icon patch. The MCP bridge is version 1.1.0 and has port 8082 preconfigured.

Use Windows PowerShell 5.1, JDK 21 and Python 3. The client builds and runs through its included Gradle wrapper. The game cache, Gradle downloads, local keys, and player data live outside the release directory.

## Extract

Stop the old coordinator and client. Extract the ZIP into a clean folder. The ZIP has one top-level directory, `KSP-RSPS-242-Guide-Fix`. The commands below assume that folder is on your Desktop. Adjust `$release` if you extract elsewhere.

Existing installations reuse these locations:

- Server cache and player data: `%USERPROFILE%\Downloads\KSP-RSPS-242-Local-Game-Windows`
- Client profile: `%USERPROFILE%\Downloads\KSP-Microbot-242-Test\local-login\profile`
- Local RSA keys: `%USERPROFILE%\ksp-rsps-local-keys`
- Client runtime dependencies: the normal Gradle user cache, or your `GRADLE_USER_HOME`

On a fresh machine or if the cache, keys, or pinned gamepack are missing, run this once. It downloads revision 242 cache 2757 and prepares the client dependencies with Gradle. Working existing keys and cache files are reused.

```powershell
$release = 'C:\Users\Proje\Desktop\KSP-RSPS-242-Guide-Fix'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$release\prepare-dependencies.ps1"
```

## Start the server and client

Open PowerShell and start the server. Enter the disposable localtest password when prompted and keep the window open.

```powershell
$release = 'C:\Users\Proje\Desktop\KSP-RSPS-242-Guide-Fix'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$release\start-worlds.ps1"
```

After World 301 reports `KSP_LOCAL_GAME_LISTENER_READY`, launch the client in a separate PowerShell window.

```powershell
$release = 'C:\Users\Proje\Desktop\KSP-RSPS-242-Guide-Fix'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$release\start-local-microbot.ps1" -World 301
```

World 301 is F2P. World 302 is members. Create a local account at http://127.0.0.1:8087/register to start Tutorial Island. The admin dashboard is at http://127.0.0.1:8087/admin. See the included Tutorial Island notes for the implemented steps.

## Connect KSP Browser Agent

In the running Microbot client, enable **Agent Server**, use **TCP**, set **Port** to **8082**, and turn **Stealth bind** off. Toggle the plugin off and on after changing its port.

```powershell
$release = 'C:\Users\Proje\Desktop\KSP-RSPS-242-Guide-Fix'
$mcp = Join-Path $release 'KSP-Microbot-242-Agent-MCP\ksp_agent_mcp.py'
py -3 $mcp --check
```

In Browser Agent's MCP Integration panel, enable the project setting and choose the same Python file. Select **Python**, provide the full Python executable if required, and set **Runtime arguments** to `[]`. Leave **Working directory** blank. Use this allowlist:

```text
client_health, client_state, client_skills, client_inventory, client_bank, client_dialogue, client_npcs, client_objects, client_widgets, client_widget_search, client_widget, client_varp, client_varbit
```

Save, test and connect. The bridge exposes 13 tools and an empty resource list. Call `client_health` to confirm that the connection uses port 8082.
