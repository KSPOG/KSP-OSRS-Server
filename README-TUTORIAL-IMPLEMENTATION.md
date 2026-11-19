# KSP revision 242 — Tutorial Island implementation status

**This update is not a complete 1:1 Tutorial Island.** The exact current reference is:
- https://oldschool.runescape.wiki/w/Tutorial_Island
- https://oldschool.runescape.wiki/w/Learning_the_Ropes

## Implemented in this incremental source patch

- New players now answer the Gielinor Guide's experience question in a three-choice native dialogue before the Settings step.
- Experience answer is persisted in the existing tutorial profile; old `stage` numbers remain stable.
- The wrench is only revealed after the opening guide conversation, and the guide must be spoken to a second time after opening Settings.
- Added `TutorialIslandMilestones.java`: a **51-event acceptance contract**, documenting the complete gameplay event sequence. This is a validation specification, **not a wired combat, equipment or spellcasting implementation**.
- Kept the existing bank/inventory dashboard update from the last package.

## Existing capabilities (not fully verified against the original client)
Character creation, early guide, Survival Expert, Master Chef, Quest Guide and Mining/Smithing stages are partly implemented. Protocol-level tests from previous builds are not equivalent to end-to-end Windows client tests.

## Missing / not 1:1
- Reliable exact NPC spawn coordinates and dynamic movement for all tutorial NPCs.
- Fully correct dialogue branches, camera/hint/UI unlock flows, contextual hints and Adventure Paths rewards.
- Native equipment screen and real combat engine for two separate Giant rat kills with distinct melee/ranged validation.
- The tutorial bank and poll booth progression and Account Guide account-tab events.
- Brother Brace prayer lesson; Magic Instructor, runes, Wind Strike actual chicken combat and resupply.
- Ironman restrictions and mode selection, Learning the Ropes quest completion, Lumbridge teleport and graduation rewards.
- Actual client behaviour regression test for the updated guide answer.

## Install (Windows PowerShell 5.1)
Stop any running server, then invoke:

```powershell
$release = "$env:USERPROFILE\Downloads\KSP-RSPS-242-Guide-Experience-Update\KSP-RSPS-242-Guide-Fix"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$release\install-tutorial-guide-update.ps1"
```

**Requires:** JDK 21, RSProt dependencies already downloaded into `KSP-RSPS-242-Local-Game-Windows\lib`, and a working revision 242 cache. The installer compiles sources and patches a copy of the JAR, keeping a backup. It will fail safely if dependencies are missing.

Start the coordinator using `start-worlds.ps1` only after the installer prints `KSP_TUTORIAL_GUIDE_UPDATE_INSTALLED`.

## Acceptance checks

The standalone event-catalog test can be executed using JDK 21:

```powershell
$root = "$release\source\server\protocol-lab"
$test = "$release\tutorial-tests\TutorialSequenceTest.java"
$classes = Join-Path $release '.catalog-tests'
New-Item -ItemType Directory $classes -Force | Out-Null
javac --release 21 -d $classes "$root\src\main\java\dev\ksp\osrs\protocol\TutorialIslandMilestones.java" $test
java -cp $classes dev.ksp.osrs.protocol.TutorialSequenceTest
```

The catalog test passing only means the ordered list is internally consistent. **It is not evidence that the full tutorial works in-game.**
