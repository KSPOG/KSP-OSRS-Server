# Tutorial Island: experience screen correction

Reference: OSRS Wiki Tutorial Island and Learning the Ropes, October 22 2025 update.

## Correct sequence
1. Character appearance is confirmed.
2. Separate experience-selection modal appears; choice persisted in `NewPlayerProfile`.
3. Selection closes the modal and unlocks the Gielinor Guide interaction.
4. First Guide conversation exposes Settings wrench.
5. Wrench tab is clicked; second Guide conversation unlocks the exit.

## Implemented in source
- `EXPERIENCE_SELECTION=24` is a new persisted checkpoint. Existing checkpoint numbers are not renumbered.
- Character creator now advances to the new checkpoint, not directly to Guide.
- Only the experience modal accepts and persists the selection.
- Guide NPC no longer presents the experience question.
- First guide conversation unlocks Settings; remaining guide sequence unchanged.
- Modal is reopened after login/map-build, including reconnect at selection checkpoint.

## Important limitation
The selection modal currently mounts the already known revision-242 three-option interface group 219 in the main modal slot as a provisional replacement. **This is NOT a verified reproduction of the October 2025 native experience-selection UI (distinct full-screen panel, three large buttons and progress pips)**. Its exact group ID, child actions and appearance remain unverified without a client/cache trace, and the pack is SOURCE-ONLY for this update. Do not treat this as a tested Windows client release or a 1:1 implementation.

The installer from the previous package can compile the updated source (requires Java 21 and complete RSProt classpath) and patch the local server JAR. Stop the server before installation. Back up saved data.
