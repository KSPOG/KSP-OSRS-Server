# Local OSRS JS5 cache validation — revision 242

The user found a classic cache layout at
`C:\Users\Proje\.runelite\jagexcache\oldschool\LIVE` with:
- `main_file_cache.dat2` (250812228 bytes)
- 25 numbered index files plus `idx255` (27 recognized files total)
- Layout fingerprint `adb952a2315a423892601bdc593f4f384aad2aab213d35e2f1ad46d27e7b9d74`

The inventory reports filenames and sizes **only** and cannot prove revision 242.
The injected OSRS client revision **is** confirmed to be 242 at login screen.

A strict Java 21 read-only `IndexedCacheReader` now validates dat2/idxN sector
chains with both standard and extended group headers, including identity,
sequence, cycle detection, length bounds and truncation checks. Synthetic tests
cover valid groups and corruption.

To validate the real local JS5 master-index storage without uploading cache
bytes, run from `server/protocol-lab`:

```powershell
gradle inspectCache '-PcachePath=C:\Users\Proje\.runelite\jagexcache\oldschool\LIVE'
```

If Gradle is not installed, use the pinned Gradle 8.8 via your local build setup.
This command reads the reference-table container `archive 255 / group 0` and
reports header/sector validity, *not* full cache compatibility. The synthetic master index requested on JS5 archive 255/group 255 must be built from archive reference-table metadata. Given the user's idx255 size of 138 bytes (23 entries), group 255 is not present on disk.

**No live JS5 service is connected yet.** RSProt requires correctly formatted
JS5 groups and verified archive reference tables and a synthesized master index; using the raw `IndexedCacheReader`
directly as a live network provider without framing/checksums would be incorrect.
No Jagex cache assets or credentials are committed.


## Decoded reference tables and sampled CRC audit

CacheMetadata decodes RuneLite reference-index protocols 5-7 and bounded Jagex BZip2/GZip containers.
CacheIntegrityAudit inspects all available reference tables and checks up to 16 group CRCs per index.
Absent groups are reported separately, and do not automatically fail the check.
A mismatched CRC or invalid reference structure fails with exit code 3.
All cache reads are local and read-only. No asset data is published.

Run locally using the Microbot Gradle 8.8 wrapper (Java 21, internet for dependencies):

```powershell
$repo = "D:\Users\Proje\Documents\GitHub\KSP-OSRS-Server"
& "$repo\Microbot-main\gradlew.bat" -p "$repo\server\protocol-lab" `
    auditCache '-PcachePath=C:\Users\Proje\.runelite\jagexcache\oldschool\LIVE' --no-daemon
```

This verifies local CRC consistency, not authoritative revision-242 cache compatibility.
