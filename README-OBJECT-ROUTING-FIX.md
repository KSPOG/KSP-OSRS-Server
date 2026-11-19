# Revision 242 Object Routing Diagnostics

This update repairs tree-action dispatch and adds detailed object click diagnostics. It does not implement all scene object behaviours.

## Log events
- `KSP_OBJECT_CLICK`: decoded scene object ID, name, cache action, menu slot, tile, tutorial stage.
- `KSP_OBJECT_REJECT reason=not_in_scene`: object ID or tile did not match the active cache scene.
- `KSP_OBJECT_REJECT reason=missing_menu_action`: cache has no action at the reported slot.
- `KSP_OBJECT_UNIMPLEMENTED`: the click was valid but no gameplay handler exists.
- `KSP_TUTORIAL_TREE_START` and `KSP_TUTORIAL_TREE_SUCCESS`: tree action reached the validated tutorial woodcut handler.

A tutorial tree requires the bronze axe and the CHOP_TREE checkpoint; moving or disconnecting interrupts the four-tick action. The server still has to implement animations, depletion, respawn, and general-purpose Woodcutting outside Tutorial Island.

## Install
Close server and client. Run `install-object-routing-fix.ps1` from this release. It compiles `LocalGameListener.java` against installed Java 21, RSProt and RuneLite cache libraries, updates `world-server.jar` with a backup, and does not silently overwrite on compilation failure.
