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

Run locally with Gradle 8.8 (Java 21, internet for dependencies). A Microbot wrapper may not exist in your local checkout:

```powershell
$repo = "D:\Users\Proje\Documents\GitHub\KSP-OSRS-Server"
& "C:\Users\Proje\.gradle\wrapper\dists\gradle-8.8-all\6gdy1pgp427xkqcjbxw3ylt6h\gradle-8.8\bin\gradle.bat" -p "$repo\server\protocol-lab" `
    auditCache '-PcachePath=C:\Users\Proje\.runelite\jagexcache\oldschool\LIVE' --no-daemon
```

This verifies local CRC consistency, not authoritative revision-242 cache compatibility.


## RSProt JS5 group provider prototype

`VerifiedJs5GroupProvider` implements `net.rsprot.protocol.api.js5.Js5GroupProvider`
as a **read-only prototype**; no network listener or live game-world connection
is wired to it. On initialization it decodes stored reference indexes (idx255).
Each requested normal group is read from disk and its raw CRC32 verified
against reference-table metadata before a fresh Netty ByteBuf is returned.
On-disk suffix bytes are removed before returning compressed JS5 container bytes.
Missing/invalid/CRC-mismatched groups return null. The special master index
`archive=255, group=255` is deliberately unsupported until implemented and tested.

Run synthetic provider tests with Gradle task `testJs5Provider`.
Verified local sample: 311 out of 311 groups matched their own stored reference
CRCs when excluding four trailing bytes. **This does not certify revision 242**
or the completeness of the cache. No cache bytes are included in the repository.

Remaining: validate actual provider against the local cache, construct
and certify the JS5 master index, implement handshake and network framing,
and enable server readiness only after protocol/asset compatibility testing.

## RSProt preframing and local provider smoke testing

RSProt 242 requires the Js5GroupProvider to return a **fully framed** byte
buffer, not just the compressed cache container. See the upstream
`Js5Service.prepareJs5Buffer` implementation: archive byte + group short,
509 bytes in the first block, 0xFF continuation markers at 512-byte offsets,
then up to 511 bytes per additional block. The `Js5WireFraming` helper now
follows that layout, and the provider emits framed data with per-request
CRC verification. Synthetic tests cover 509/510/511 and 1020/1021 boundaries.

Run the read-only check against a local cache with the provided PowerShell 5.1
bootstrap from `server/tools/run-js5-audit.ps1`; it uses a pinned source commit,
Gradle 8.8, and doesn't open server sockets or upload cache content.

`Js5MasterIndexCandidate` produces an offline **VERSIONED candidate** from
the reference tables; it is not served at 255/255 because the actual master
index format and signing requirements for OSRS revision 242 are not yet
certified. The provider continues to return null for 255/255. Do not mark
the server JS5-ready until the master index and handshake are verified.


## Local master-index candidate verification

`Js5MasterIndexAudit` independently rereads each reference-table container and
compares its CRC32 and reference revision to the offline VERSIONED candidate.
This verifies internal construction but **does not validate the client format**.
The candidate is not served for request (255,255), and no networking is enabled.

To audit a local game cache, use `server/tools/run-js5-audit.ps1` from the
`server` branch. It now invokes both `auditJs5Provider` and
`auditJs5MasterIndex`, against the user-selected local cache directory.
No private cache bytes are transmitted or committed.

The existing real-cache JS5 provider smoke check passed: 159 inspected,
159 valid preframed responses, zero unavailable or invalid.
Authoritative revision-242 master-index compatibility is still unverified.


## RSProt 242 JS5 remote-init decoder contract

Upstream `InitJs5RemoteConnectionDecoder` in `blurite/rsprot`
`protocol/osrs-242/osrs-242-shared` reads a 32-bit revision and four
32-bit seed values (20 payload bytes total).
`Js5HandshakePayload` now decodes that **payload only**, rejects incorrect
byte lengths, allows checking revision 242, and redacts seed words in logs.
Tests include revision rejection and defensive cloning of the seed.
This does **not** implement an opcode, request framing, protocol negotiation,
network transport, acknowledgement or actual game connection.

Upstream RSProt `Js5Service` requires `provider.provide(255,255)` for
its master index. Our provider still returns null for that special request.
No production JS5 network service may be enabled until the matching master
index format/signature and client bootstrapping are verified.

## Independently pinned master-index reference comparison

The VERSIONED 255/255 candidate is still **not** served to clients. To compare
it to a separately captured, independently sourced raw JS5 master-index
*container*, obtain the exact binary response bytes (not JS5 wire block
headers or a text/hex dump) and independently record its SHA-256 hash.
Do **not** use the locally generated candidate as the reference: that would
be a circular test. No reference fixture is bundled or automatically downloaded.

On Windows PowerShell 5.1, after checking out the feature branch:

```powershell
$repo = "D:\Users\Proje\Documents\GitHub\KSP-OSRS-Server"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$repo\server\tools\run-js5-reference-comparison.ps1" `
    -Repo $repo `
    -Reference "C:\path\to\independently-captured-master.bin" `
    -ReferenceSha256 "<actual-64-character-SHA256>"
```

The task runs offline on the local cache, verifies the file's supplied SHA-256
pin, and performs byte-for-byte comparison. If both inputs have the exact
classic uncompressed VERSIONED shape, it also counts per-entry CRC and
version differences and reports differing entry counts. Any byte difference
fails with exit status 3. Incorrect/missing pins and malformed fixtures fail
closed. If the reference is signed, compressed, uses an alternate layout,
or includes additional fields, the comparator does not claim to decode them.

Run synthetic tests separately with
`gradle -p server/protocol-lab testJs5MasterReferenceComparator`.
Passing these tests or matching a fixture is **not** OSRS revision-242
certification without independently established source provenance and client
acceptance. The read-only provider still rejects request (255,255);
all live network listeners remain disabled.

## Independent revision-242 metadata fingerprint (OpenRS2 cache 2757)

The Jagex-sourced OpenRS2 cache snapshot for oldschool/live/en revision 242,
dated 2026-11-03, lists 25 archive entries (0–24), of which archive 16 and
23 are absent. Archive 24 contains 18 groups and revision 1. The total listed
groups are 120,374: https://archive.openrs2.org/caches/runescape/2757

The previous local idx255 audit listed 23 slots (0–22) and 120,356
advertised groups. This is consistent with missing archive 24, but is
**not** evidence the full cache is intact. The new read-only metadata audit
independently compares each reference-container CRC32, reference revision, and
advertised group count. A missing archive is reported explicitly and fails
the task when the archived reference expects it. It does not modify the cache
or enable network listeners.

```powershell
gradle -p server/protocol-lab testRevision242MetadataAudit
gradle -p server/protocol-lab auditRevision242Metadata "-PcachePath=C:\Users\Proje\.runelite\jagexcache\oldschool\LIVE"
```

The comparator's source metadata is a checked-in transcription of OpenRS2's
published table; the archive's master-index *wire container* and client
signature requirements still have not been independently compared. Do not
substitute this metadata audit for client-acceptance certification.
