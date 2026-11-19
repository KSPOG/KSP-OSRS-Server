# KSP RSPS revision 242 Tutorial Island sequence

Close the old world coordinator and Microbot. Extract this ZIP to Downloads.
It uses the existing revision 242 cache, libraries and pinned Microbot
checkout. Open PowerShell and start the server.

```powershell
$release = Join-Path $env:USERPROFILE 'Downloads\KSP-RSPS-242-Guide-Fix'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$release\start-worlds.ps1"
```

Open http://127.0.0.1:8087/register in a browser and choose a username and a
password. No email is requested. Registered accounts default to F2P.
The local dashboard at http://127.0.0.1:8087/admin links to account
management, membership controls, and the existing world controls and bank
administration. Use the token printed by the server to manage accounts.
Membership changes take effect at the next login.

Launch the client in a second PowerShell window. Log in with your new
username and password on World 301.

```powershell
$release = Join-Path $env:USERPROFILE 'Downloads\KSP-RSPS-242-Guide-Fix'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$release\start-local-microbot.ps1" -World 301
```

New accounts open the character creator on Tutorial Island. Editing the
appearance and pressing Confirm saves it. Tutorial progress saves at lesson
checkpoints. The original localtest login still starts in Lumbridge.

Finish character creation, speak to the Gielinor Guide and finish his
first conversation. The Settings wrench then appears. Click the wrench
tab, speak to him again, and finish that conversation to unlock the
starting house door. These steps save when completed. Use a registered
Tutorial Island account. The disposable localtest login starts in Lumbridge.

Brynna hands out a small fishing net first. Open Inventory, catch shrimp,
open Skills and return to Brynna for an axe and tinderbox. Chop logs,
light them and cook the shrimp. After bread, speak to the Quest Guide,
open the Quest List and speak to the guide again before taking the ladder.
Dezzick gives a pickaxe, then hands out the hammer when you return after
smelting a bronze bar. Smith the bar into a dagger at an anvil.

Existing profiles retain their checkpoints. A profile already holding the
hammer can continue with it. The current progression ends at the Combat
Instructor checkpoint. Adventure Paths, exact dialogue, combat and the
remaining island lessons are pending.

Public messages appear as named lines in the chatbox for the sender and
nearby players. Use the All or Public tab and press Enter to send. The
server prints KSP_PUBLIC_CHAT_DELIVERED on the first accepted public
message for a session.
