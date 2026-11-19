# Cooperative Break Preparation

Plugins that must leave a dangerous area before a scheduled break can register a `BreakPreparation.Handle`. Both break handlers request preparation before stopping a configured plugin, pausing scripts or logging out. Existing hard plugin locks remain respected.

```java
private BreakPreparation.Handle preparation;

// On start:
preparation = BreakPreparation.register("unique-plugin-owner");

// In the plugin's normal nonblocking state machine:
if (preparation.isRequested()) {
    // Heal, finish critical interaction and reach safety over normal loop iterations.
    if (safeToPauseOrLogout()) {
        preparation.ready();
    }
}

// On stop, including hot reload:
preparation.close();
```

All registered owners must report readiness. Closing an unready owner during preparation cancels that request instead of claiming the player is safe. A 120-second timeout also cancels the request. Handles are identity checked, so an old handle cannot unregister or release its replacement.

Readiness is reset when a break finishes or is cancelled, and handlers clear stale request state on restart. Plugins should resume their saved workflow after `isRequested()` becomes false. Enable only one break handler at a time.

An active no-logout break is allowed to finish even if a late participant registers and times out. Cancelling a pending preparation releases the global script pause and schedules the next break; a restored pause break that still needs preparation follows this cancellation path too.

Blackjack uses this to secure its curtain and leave the NPC before allowing a break; repeated failed Knock-Out hits must not be treated as a reason to pause in place. The API is independent of Blackjack and leaves existing behavior unchanged when no participant is registered.
