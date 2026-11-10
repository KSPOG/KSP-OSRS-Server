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

## Exhaustive JS5 group validation gate

The sampled audits inspect only up to 16 CRCs per archive and 8 framed
responses per archive. The additional read-only `auditAllJs5Groups` task
visits **every group advertised** by all available reference tables. It
checks disk reads, CRC32 of the correct container bytes (supports 0/2/4-byte
cache suffixes), container boundaries, and the low 16 bits of group version
when a trailer exists. Missing or corrupt groups are counted by category.
With `-PverifyProvider=true`, it additionally checks the exact bytes,
header and continuation markers of **every** provider response (slower,
as each group is fetched a second time). No files are written or
network listeners created. Failures cause a nonzero process exit.

After synchronizing the sources and Gradle tasks:

```powershell
$repo = "D:\Users\Proje\Documents\GitHub\KSP-OSRS-Server"
$lab = Join-Path $repo "server\protocol-lab"
$cache = Join-Path $repo "server\cache-validation\openrs2-2757\extracted\cache"
& $gradle -p $lab testExhaustiveCacheAudit testJs5HandshakePayload --no-daemon
& $gradle -p $lab auditAllJs5Groups "-PcachePath=$cache" -PverifyProvider=true --no-daemon
```

Passing this gate plus `auditRevision242Metadata` establishes local archive
metadata and group consistency across the **advertised** cache, not the
revision-242 client's acceptance of JS5 255/255, response signing, login,
or world service. Do not enable sockets on this basis.

## Offline RSProt-242 request exchange

The `Js5OfflineExchange` harness follows RSProt osrs-242's fixed four-byte
JS5 client packets: opcode 0=prefetch, 1=urgent, 2=priority-high,
3=priority-low, 4=XOR change; each opcode has a three-byte payload.
Prefetch/urgent payloads contain a one-byte archive id and a big-endian
two-byte group id. XOR change uses the first payload byte and reserves the
other two. It rejects malformed packets, unsupported opcodes, nonzero XOR
and does not claim to implement queuing, scheduling, socket handshakes,
login, or actual master-index responses. The executable safely checks
an actual archive-24 group and its archive-255 reference table against the
local provider, while explicitly denying the unverified special 255/255.

Upstream protocol sources:
- https://github.com/blurite/rsprot/blob/master/protocol/osrs-242/osrs-242-shared/src/main/kotlin/net/rsprot/protocol/common/js5/incoming/prot/Js5ClientProtId.kt
- https://github.com/blurite/rsprot/blob/master/protocol/osrs-242/osrs-242-shared/src/main/kotlin/net/rsprot/protocol/common/js5/incoming/codec/PrefetchRequestDecoder.kt
- https://github.com/blurite/rsprot/blob/master/protocol/osrs-242/osrs-242-api/src/main/kotlin/net/rsprot/protocol/api/js5/Js5Service.kt

```powershell
& $gradle -p $lab testJs5OfflineExchange --no-daemon
& $gradle -p $lab auditJs5OfflineExchange "-PcachePath=$cache" --no-daemon
```

A successful replay verifies **only** this deterministic offline JS5
request/response subset. Client 255/255 master index and signature checking,
full live connection, and login/world/game protocol are separate gates.

## Master-index wire-fixture gate (not yet certified)

`Js5MasterWireFixtureAudit` is a strict, read-only decoder for a **genuine
on-wire JS5 (255,255) response** captured by a separate authorized client
capture tool. It expects the three-byte 0xff/0x00/0xff response prefix,
512-byte blocks with 0xff continuation markers, and an embedded JS5
container. It reports any excess bytes after the advertised container
payload (for example, possible signature material), and compares the
unframed container byte-for-byte against the local classic VERSIONED
candidate. A SHA-256 pin for the **unframed container bytes** is mandatory.
The implementation intentionally cannot generate a genuine fixture and it
does not validate RSA signatures or client acceptance. A locally generated
candidate must never be used as the "independent" fixture.

Once the user has an independently captured raw response and has recorded
its origin, connection/revision information, and SHA-256 of the unframed
container, the following offline tasks may be used:

```powershell
& $gradle -p $lab testJs5MasterWireFixtureAudit --no-daemon
& $gradle -p $lab compareJs5MasterWireFixture "-PcachePath=$cache" `
    "-PmasterWirePath=C:\path\to\captured-255-255.bin" `
    "-PmasterContainerSha256=<64-char-sha256-of-unframed-container>" --no-daemon
```

Upstream evidence: RSProx captures and stores the master-index response
per world and RSProt 242 requires a provider for request 255/255.
References:
- https://github.com/blurite/rsprox/blob/master/proxy/src/main/kotlin/net/rsprox/proxy/server/ServerJs5ResponseDecoder.kt
- https://github.com/blurite/rsprot/blob/master/protocol/osrs-242/osrs-242-api/src/main/kotlin/net/rsprot/protocol/api/js5/Js5Service.kt

If no authentic fixture exists, this gate stays **PENDING**. Passing
synthetic tests is not proof of client compatibility.

### Captured fixture inspection

Before comparing, inspect the genuine captured bytes to obtain a SHA-256
digest of the **unframed** container (the hash needed by the comparator):

```powershell
& $gradle -p $lab inspectJs5MasterWireFixture `
    "-PmasterWirePath=C:\path\to\captured-255-255.bin" --no-daemon
```

Retain the capture provenance and its own file SHA-256 separately. The
inspection command reports its structure but does **not** authenticate the
capture, demonstrate that revision 242 accepts it, or validate a signature.

## Localhost-only master-index capture proxy

`server/tools/js5_master_capture_proxy.py` is an **opt-in, one-session,
transparent TCP forwarder**, not part of the server's runtime. It listens
only on `127.0.0.1` and forwards both directions to an explicitly supplied
upstream. The binary observer excludes RSProt's one-byte successful JS5
login response (`0x00`) and collects only the first complete upstream
JS5 master-index response `255/255`. It tolerates fragmented TCP reads,
handles the 512-byte JS5 continuation markers, and writes only the master
response and a provenance JSON file with SHA-256. Client-to-server data is
never persisted. An upstream whose first response is not successful or
not the master response will not produce a capture.

For an **authorized** revision-242 client/server environment where you
control the client's JS5 host and port setting and upstream endpoint:

```powershell
$repo = "D:\Users\Proje\Documents\GitHub\KSP-OSRS-Server"
$proxy = Join-Path $repo "server\tools\js5_master_capture_proxy.py"
$wire = Join-Path $repo "server\cache-validation\captured-255-255.bin"
python $proxy --upstream-host <AUTHORIZED_JS5_HOST> --upstream-port <PORT> `
    --listen-port 43595 --output $wire
```

Connect the authorized client **to 127.0.0.1:43595**. This script does not
change client configuration or reroute official traffic; it captures
nothing until you make that explicit local connection. Do not use it on a
third-party server without permission. Do not attempt a real client
connection until its endpoints and revision are established.

Then run:
```powershell
& $gradle -p $lab inspectJs5MasterWireFixture "-PmasterWirePath=$wire" --no-daemon
```

The unframed container SHA-256 printed by inspection is suitable for the
separate pinned comparison task. Preserve the `.provenance.json` sidecar
and the actual client build/server endpoint records. The proxy cannot
authenticate the source or determine signature validity by itself.

Python regression:
`python -m unittest -v server/tools/test_js5_master_capture_proxy.py`
(from the `server/tools` directory, run
`python -m unittest -v test_js5_master_capture_proxy.py`).

## Local diagnostic JS5 socket (NOT a real revision-242 listener)

`Js5LocalDiagnosticServer` is a loopback-only, single-session,
post-handshake diagnostic transport. It accepts only complete, fixed
four-byte prefetch/urgent JS5 requests, serves validated group bytes,
and closes **without a response** for missing groups, unimplemented
control packets, or the unverified special master index `255/255`.
It does not perform login negotiation, account authentication, client
revision checks, XOR, asynchronous scheduling, priority queues or any
other prerequisite to a real OSRS client session.

The default test uses an ephemeral IPv4 loopback socket, sends a
synthetic urgent request for **reference index 255/24**, checks the
returned bytes against the local verified provider, and proves
a subsequent request for 255/255 is rejected:

```powershell
& $gradle -p $lab auditJs5LocalDiagnostic "-PcachePath=$cache" --no-daemon
```

There is an **optional, explicit** one-session diagnostic listener for
synthetic clients only:

```powershell
& $gradle -p $lab runJs5LocalDiagnostic "-PcachePath=$cache" "-PdiagnosticPort=43596" --no-daemon
```

Never point the master-index capture proxy at this diagnostic listener:
it intentionally cannot serve 255/255 or an actual client handshake,
so that would not obtain authentic capture evidence. It is useful for
isolated socket-delivery checks, not as a substitute for a client-ready
revision-242 JS5 server.

## Synthetic 255/255 master-index socket audit

The JS5 diagnostic server now exposes a **package-private test-only** override
that can frame the locally generated classic VERSIONED master-index candidate
in a localhost loopback socket test. The normal diagnostic server continues
to **reject all 255/255 requests**. No flag in its public command-line
interface enables candidate serving.

`Js5MasterCandidateSocketAudit` requests 255/255 from the test-only
loopback instance, checks that every transmitted byte matches the expected
framed candidate, decodes it back into the same candidate container, and
prints its SHA-256 fingerprint. It does not authenticate a captured fixture,
verify client signature requirements, or test a real revision-242 client.

```powershell
& $gradle -p $lab auditJs5MasterCandidateSocket "-PcachePath=$cache" --no-daemon
```

Independent source for cache format, count and archive-level CRC/revisions:
https://archive.openrs2.org/caches/runescape/2757

## Revision-242 JS5 initial connection diagnostic

The **experimental localhost-only** `Js5HandshakeDiagnosticServer` accepts
the exact RSProt-242 JS5 initialization packet: opcode 15, 20-byte fixed
payload with revision 242 and four 32-bit seed words. It sends a successful
single-byte `0x00` acknowledgment only on a valid revision, then processes
bounded 4-byte JS5 requests through the verified cache provider. It handles
priority control messages as no-ops and the zero XOR key only. Unsupported
opcodes, nonzero XOR, missing groups and master `255/255` fail closed.
It stores neither credentials nor the seed. No production service or
non-loopback listener is registered.

The CI synthetic-cache test covers successful initialization, byte-exact
reference response, wrong revision/opcode and incomplete initialization.
The real-cache loopback audit additionally checks the archive-24 reference
response, safe zero-key controls and refusal to serve `255/255`.

```powershell
& $gradle -p $lab testJs5HandshakeDiagnostic --no-daemon
& $gradle -p $lab auditJs5HandshakeDiagnostic "-PcachePath=$cache" --no-daemon
```

This validates only an initial packet/response subset based on RSProt and
RSProx. It **does not verify** the client-specific master-index format,
server signature keys, full scheduling/authentication, or an actual OSRS
revision-242 client connection.

- https://github.com/blurite/rsprot/blob/master/protocol/osrs-242/osrs-242-shared/src/main/kotlin/net/rsprot/protocol/common/loginprot/incoming/prot/LoginClientProt.kt
- https://github.com/blurite/rsprot/blob/master/protocol/osrs-242/osrs-242-shared/src/main/kotlin/net/rsprot/protocol/common/loginprot/incoming/codec/InitJs5RemoteConnectionDecoder.kt
- https://github.com/blurite/rsprox/blob/master/proxy/src/main/kotlin/net/rsprox/proxy/server/prot/LoginServerProtId.kt

## Request-stream robustness gate

`testJs5HandshakeRequestStream` runs socket tests against a temporary
synthetic cache and revision-242 login acknowledgment. It checks split
four-byte client messages, both priority controls, zero/nonzero XOR,
prefetch reference-table delivery, missing groups, unknown opcodes,
incomplete requests, and the explicit 32-request session cap. All
unsupported inputs must terminate without returning fabricated data.

```powershell
& $gradle -p $lab testJs5HandshakeRequestStream --no-daemon
```

These tests do not enlarge the supported protocol surface: the harness
remains a single-session diagnostic, not an authenticated production
revision-242 JS5 service. Authenticated 255/255 delivery and actual client
acceptance are still independent, unverified gates.
