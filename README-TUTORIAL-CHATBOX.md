# KSP Tutorial Island — chatbox visibility correction

## Implemented

- Hide standard chatbox while character creation, experience selection or normal tutorial tasks are active.
- Reveal chatbox during NPC dialogue or banker's native chat choices; hide it again after closing dialogue.
- Restore chatbox for non-tutorial players.
- Stop sending each tutorial objective into the normal chat history. Objectives are logged as `KSP_TUTORIAL_OBJECTIVE` and stage progress is synchronized through varp 281.
- Add `KSP_TUTORIAL_CHATBOX` diagnostics for each change.

## Known limitation

The game-knowledge selection currently opens chatmenu 219 in the main viewport and **is not the authentic OSRS October 2025 interface**. The authentic interface group, buttons and progress pips require a verified revision-242 cache interface mapping. This package does NOT claim to repair that visual mismatch or to make Tutorial Island 1:1. The tutorial controls guide itself is still incomplete.

## Install (PowerShell 5.1)

Stop server. From the folder containing this README:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\install-tutorial-chatbox.ps1
```

Then restart using `start-worlds.ps1`, and log in with a new tutorial account. The installer compiles the matched gameplay classes and backs up `world-server.jar` before updating; it fails without replacing the JAR when compilation fails.

## Source

- `source/server/protocol-lab/src/main/java/dev/ksp/osrs/protocol/LocalGameListener.java`
- `NewPlayerProfile.java`, `LocalBank.java` compiled together for compatibility

## External reference

https://oldschool.runescape.wiki/w/Tutorial_Island — October 22, 2025 UI and contextual controls guide changes.
