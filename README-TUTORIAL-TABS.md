# KSP RSPS 242 – Tutorial tab progression update

## Scope

This package closes **locked side-panel subinterfaces** at login and on every
persisted Tutorial Island checkpoint, reopening only the panels whose lessons
have been reached. It preserves the native stone/background and rejects clicks
on known locked tab buttons. It supports fixed, classic and modern resizable
root interfaces (548, 161, 164).

**This is not yet a 1:1 graphical tab implementation.** The root-widget icon
sprites (separate from the side panels) still require verified cache component
mappings; they may remain visible, as in the user's screenshot. Do not claim
that locked icons are now hidden.

## Install

Stop both worlds, extract this archive to Downloads, then run in PowerShell 5.1:

```powershell
$base = "$env:USERPROFILE\Downloads"
$script = Get-ChildItem $base -Filter install-tutorial-tab-progression.ps1 -Recurse -File | Select-Object -First 1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $script.FullName
```

Only restart when `KSP_TUTORIAL_TABS_INSTALLED` is printed. The installer compiles
matched classes together, stages changes and backs up the previous JAR.

## Verifications

The standalone policy test validates the exact tab unlock mask for all 25
current tutorial checkpoints, and protects against accidentally unlocking the
Prayer/Magic tabs before those lessons exist. No graphical Windows test is
included or implied. The native interface icons still need cache extraction
and an on-client screenshot review.

## RuneLite cache component audit

The package contains `audit-tutorial-widgets.ps1`. This **read-only** script
extracts root widget component positions/sprite IDs for the fixed, classic, and
modern viewports from the **user's installed revision 242 cache**. These data
are necessary to distinguish the graphic-only tab icon sprites from stone
background sprites. The TSV is not generated in this build environment because
the revision 242 cache is not available here.

```powershell
$release = "$env:USERPROFILE\Downloads\KSP-RSPS-242-Tab-Progression\KSP-RSPS-242-Guide-Fix"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$release\audit-tutorial-widgets.ps1"
```

After obtaining `tutorial-viewport-widgets.tsv`, compare its component IDs
against in-client screenshots. Do not guess component IDs.
