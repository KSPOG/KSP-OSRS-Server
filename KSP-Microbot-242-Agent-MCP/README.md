# KSP Microbot Agent Server and MCP

Start the revision 242 worlds, then launch KSP Microbot without `-SafeMode`.
In Microbot's plugin list, enable **Agent Server**. In its settings, select
**TCP**, set the port to **8081**, and turn off **Stealth bind**.

Extract this ZIP to `Downloads\KSP-Microbot-242-Agent-MCP`. From Windows
PowerShell run:

```powershell
$mcp = Join-Path $env:USERPROFILE 'Downloads\KSP-Microbot-242-Agent-MCP\ksp_agent_mcp.py'
py -3 $mcp --check
```

If Python is installed without the `py` launcher, use `python $mcp --check`.
No third-party Python packages are required.

The default token file comes from the KSP local login launcher's default
profile, `Downloads\KSP-Microbot-242-Test\local-login\profile\.runelite\.agent-token`
under your Windows user folder. If your launcher uses another profile, set the
token file path and port only as needed:

```powershell
$env:MICROBOT_TOKEN_FILE = 'C:\path\to\client\profile\.runelite\.agent-token'
$env:MICROBOT_PORT = '8081'
py -3 $mcp --check
```

Add a new MCP entry in KSP Browser Agent. Use the **Python** runtime and
select the full path to `ksp_agent_mcp.py`. A host with explicit transport
settings should use stdio. Do not put `--check` in the MCP entry because
that mode exits after testing connectivity. If the host does not expose
environment settings, launch it from the PowerShell session where you set
the variables so it can inherit them. Its MCP process needs to run on the
same Windows PC as the client.

Call `client_health` in Browser Agent first. Then log in and call
`client_state`. For Tutorial Island inspection, use `client_varp` with
`{"id":281}`, `client_npcs`, `client_dialogue`, and `client_widgets`.
The native HTTP Agent Server and this read-only MCP entry can run together.


## Browser Agent settings for port 8082

If the client listens on 8082, create a port file alongside the Python script:

```powershell
$mcp = 'C:\Users\Proje\Desktop\KSP-RSPS-242-Guide-Fix\KSP-Microbot-242-Agent-MCP\ksp_agent_mcp.py'
Set-Content -LiteralPath ([IO.Path]::ChangeExtension($mcp, '.port')) -Value '8082' -Encoding Ascii
py -3 $mcp --check
```

Select **Python** explicitly and leave Browser Agent's **Runtime arguments**
as `[]`. The script reads `ksp_agent_mcp.port` at startup. An explicit
`--port` flag or inherited `MICROBOT_PORT` takes precedence. Version 1.1.0
supports discovery of MCP tools and reports an empty resource list.
