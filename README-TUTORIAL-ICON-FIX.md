# Tutorial Island: revision-242 root icon visibility

The supplied cache audit maps 41 sprite-only icon components across viewports 548 (Fixed), 161 (Classic Resizable) and 164 (Modern Resizable). Their corresponding root stone/background components are untouched.

## Installation
Stop both worlds first. With all revision 242 RSProt dependencies installed:

```powershell
$downloads = Join-Path $env:USERPROFILE 'Downloads'
$installer = Get-ChildItem -Path $downloads -Filter 'install-tutorial-tab-icons.ps1' -File -Recurse | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $installer) { throw 'Extract the ZIP into Downloads first.' }
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $installer.FullName
```

Proceed only after `KSP_TUTORIAL_ICONS_INSTALLED`. Start the world's `start-worlds.ps1` from the same folder using PowerShell `-ExecutionPolicy Bypass`.

## Exact cache mapping

* Fixed root 548: top icons 55..61; bottom icons 71..77; stone backgrounds 48..54 and 64..70.
* Classic root 161: top icons 50..56; bottom icons 66..72; stone backgrounds 43..49 and 59..65.
* Modern root 164: top icons 44..49 (tab order 0,1,2,4,5,6); bottom icons 59..65 (tab order 7..13). Corresponding stone backgrounds 38..43 and 52..58. Tab 3 inventory has no separate root icon in the supplied audit; do not guess a component.

These IDs were tested against the included `tutorial-viewport-widgets.tsv`, not inferred from the screenshots alone. Login and stage changes send `IfSetHide` for icons as well as managing the tab subinterfaces. The Settings stone is no longer hidden.

## Limits

The patch only fixes the tab-icon visibility. It does **not** fix the separate experience-selection modal, early tab-stage accuracy versus current OSRS, missing tutorial NPCs and later lessons, or guarantee perfect visual fidelity. Verify screenshots in all three layouts. Only the 41 audited sprites are controlled.
