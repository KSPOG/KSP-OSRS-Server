# KSP RSPS revision 242 - Tutorial skilling animation patch

This source patch is for the custom local revision 242 server in your current release, **not** a standalone server and **not** a complete 1:1 OSRS implementation.

## Changes

- `PlayerAvatarExtendedInfo.setSequence(animation, 0)` is sent when a skill action starts. This uses RSProt player-info extended updates.
- Woodcutting uses animation 879, small-net fishing 621, bronze pickaxe mining 625, firemaking 733, fire cooking 897, range baking 896, smelting 899.
- Resource items and tutorial checkpoint changes occur **after** the timer, not at click time.
- 600-ms game tick based delays: woodcutting 4, fishing 4, mining 4, firemaking/cooking/baking/smelting 3. These are provisional tutorial timings, not verified 1:1 OSRS rates.
- Checks for logged-in account, unchanged tile/plane, reachable object/NPC, correct tutorial stage, required tool and item, and free inventory at reward time. Moving/talking cancels a pending action. A later action supersedes the old token.
- The installer builds `LocalGameListener`, `NewPlayerProfile`, `LocalBank`, `LumbridgeNpcs`, and `TutorialIslandMilestones` **together**. This addresses the mixed-version missing-symbol errors shown in the last log.

## Install (PowerShell 5.1)

1. Stop the server and client. Extract this ZIP into your Downloads folder.
2. Run:

```powershell
$release = "$env:USERPROFILE\Downloads\KSP-RSPS-242-Animated-Skilling\KSP-RSPS-242-Guide-Fix"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$release\install-animated-skilling.ps1"
```

3. Only if `KSP_ANIMATED_SKILLING_INSTALLED` appears, run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$release\start-worlds.ps1"
```

4. From the **second** PowerShell window start the client through `start-local-microbot.ps1` if the custom checkout has been restored.

## Verify

On a fresh Tutorial Island account, go to Brynna and receive the small net. Check that using the fishing spot plays animation 621, followed by the item reward, not the reverse. Repeat with bronze axe / tree (879), bronze pickaxe / ore (625). Move during each action: no item should be awarded. Check console markers:

- `KSP_RESOURCE_START player=... skill=fishing anim=621 ...`
- `KSP_RESOURCE_FINISH player=... skill=fishing ...`
- `KSP_RESOURCE_CANCEL player=... reason=walk`
- `KSP_OBJECT_CLICK` or `KSP_OBJECT_UNIMPLEMENTED` for scene-object diagnostics

Known limitations: Only the implemented Tutorial Island skills use timed actions. General-world resource gathering, exact depletion and respawn rules, and the unfinished tutorial lessons still need independent implementation. The patch is **source-level tested**, but not compiled or run against your Windows RSProt runtime in this workspace.
