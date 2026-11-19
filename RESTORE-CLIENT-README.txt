KSP RSPS revision 242 - pinned client recovery

1. Extract this ZIP to Downloads.
2. Run in PowerShell:
   $release = "$env:USERPROFILE\Downloads\KSP-RSPS-242-Client-Recovery\KSP-RSPS-242-Guide-Fix"
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$release\restore-client.ps1"
3. Start the server using the separate start-worlds.ps1 launcher (server dependencies required).
4. Launch the client with:
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$release\start-local-microbot.ps1" -World 301

The restore script downloads source from the pinned commit, verifies key file SHA256 values against bundled manifest, and places source in the expected original directory. It does not make a standalone executable. The client will compile on first launch via Gradle.
