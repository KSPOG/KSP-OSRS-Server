#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")/.."

MENU_ACTION=runelite-client/src/main/resources/net/runelite/client/plugins/microbot/util/reflection/menu-action-info.properties
GETTERS=docs/obfuscation/injected-getters.tsv
HISTORY=hwid-agent/historical-clients.txt

CURRENT=$(sed -n 's/^runelite\.injected-client\.version=//p' gradle.properties)
PREVIOUS=$(sed -n '1s/^# injected-client \([^ ]*\) .*/\1/p' "$GETTERS" 2>/dev/null || true)
if [ -n "$PREVIOUS" ] && [ "$PREVIOUS" != "$CURRENT" ] && ! grep -qxF "$PREVIOUS" "$HISTORY"; then
	echo "$PREVIOUS" >> "$HISTORY"
fi

./gradlew :client:seedMenuActionInfo :hwid-agent:obfuscationReport :hwid-agent:test -q

if ! git diff -U0 -- "$MENU_ACTION" | grep -q '^[-+][^-+#]'; then
	git checkout -- "$MENU_ACTION"
fi

git --no-pager diff --stat -- docs/DETECTION_HARDENING.md "$GETTERS" "$HISTORY" "$MENU_ACTION"
