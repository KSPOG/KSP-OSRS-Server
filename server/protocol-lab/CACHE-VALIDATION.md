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
priority control messages as no-ops and per-session XOR-change keys for framed responses. Unsupported
opcodes, missing groups and master `255/255` fail closed.
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
four-byte client messages, both priority controls, nonzero XOR and reset-to-zero,
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

### XOR wire compatibility

RSProt revision-242 `XorChangeDecoder` uses only the first of the three
payload bytes as its XOR key; the remaining two bytes are ignored. The
`Js5GroupResponseEncoder` applies the key to **every byte** of the
already-framed response, including the archive/group prefix and continuation
markers. The handshake diagnostic now mirrors this behavior per connection.
The synthetic socket tests verify nonzero key `0x5A`, ignored trailing bytes
and a reset to key zero. This is still not a live-client acceptance test.

Authoritative implementation:
https://github.com/blurite/rsprot/blob/master/protocol/osrs-242/osrs-242-shared/src/main/kotlin/net/rsprot/protocol/common/js5/incoming/codec/XorChangeDecoder.kt
https://github.com/blurite/rsprot/blob/master/protocol/osrs-242/osrs-242-shared/src/main/kotlin/net/rsprot/protocol/common/js5/outgoing/codec/Js5GroupResponseEncoder.kt

## Independent master-index archive retrieval (revision 242)

RSProx's source confirms that OpenRS2's archive API supports downloading the
master-index container as group `255/255`, separate from any local candidate:
`https://archive.openrs2.org/caches/runescape/2757/archives/255/groups/255.dat`.
The OpenRS2 archive identifies cache `2757` as oldschool/live/en build `242`
dated 2026-11-03. This is the documented **raw JS5 container** endpoint;
do **not** feed it to the existing network-wire decoder, which expects a
three-byte archive/group prefix.

To retrieve and compare it on Windows PowerShell 5.1 without using Git,
run `server/tools/verify-openrs2-master-255-255.ps1`. The script downloads
no more than 1 MiB, preserves prior fixture data, prints a SHA-256,
downloads the minimum new source set with backups, runs the new synthetic
regression and compares actual bytes against the locally derived candidate.
If the endpoint returns an error, the script stops without fabricating data.
If bytes differ or include an unrecognized extension, the audit exits
nonzero and **preserves the external fixture** for investigation.

A byte-for-byte match would verify the candidate against this independently
archived container. It **would not** establish that the official
revision-242 client accepts the content or prove its signature trust chain.
The OpenRS2 endpoint was subsequently downloaded and byte-verified on
Windows; see the VERIFIED section below.

References:
- https://archive.openrs2.org/api
- https://archive.openrs2.org/caches/runescape/2757
- https://github.com/blurite/rsprox/blob/master/cache/src/main/kotlin/net/rsprox/cache/util/OpenRs2ApiExtensions.kt
- https://github.com/blurite/rsprox/blob/master/cache/src/main/kotlin/net/rsprox/cache/identifier/OpenRs2CacheIdentifier.kt

## VERIFIED revision-242 OpenRS2 master-group container (Windows, 2026-11-10)

The user's Windows PowerShell run of
`verify-openrs2-master-255-255.ps1` successfully downloaded
`https://archive.openrs2.org/caches/runescape/2757/archives/255/groups/255.dat`.
The downloaded **independent raw container** (not a captured TCP packet)
was **byte-identical** to `Js5MasterIndexCandidate.build(cache)`:

- Cache: OpenRS2 2757; build revision 242.
- Container: 205 bytes, compression 0, 200-byte packed payload.
- Entries: 25 classic VERSIONED CRC32 + revision pairs.
- CRC32 differences: 0; revision differences: 0; extra trailing bytes: 0.
- SHA-256:
  `a5ab2044f9845e7beffa3bc8a338f0bd21a1156ba1819a438378c4f2d8328e38`.

This independently confirms **master-index content compatibility** with
that archival reference. It does not establish the official client
signature-validation trust chain or live client acceptance.

## Explicitly gated localhost master-index client probe

The opt-in `Js5VerifiedMasterProbe` now permits group `255/255`
**only after** its raw archived container matches the above fixed SHA-256
**and** the bytes computed from the supplied local cache. The existing
`Js5HandshakeDiagnosticServer.serveOne(listener,cache)` still blocks
255/255 by default. Only the explicit probe executable uses admitted
master bytes. Any incorrect archive file prevents the listener from
starting.

The probe is intentionally limited to **127.0.0.1**, one session,
32 JS5 requests, 180 seconds to connect and 7 seconds per read.
This is a diagnostic service only; no game login/scene/player state or
public endpoint. It records request opcode/archive/group identifiers, response byte counts,
aggregate counts and termination status, but never seed words or account data.

Windows PowerShell 5.1, Git-free preflight (does NOT listen):

```powershell
& "$repo\\server\\tools\\prepare-js5-client-probe.ps1"
```

To explicitly open the probe for a separately configured and controlled
revision-242 test client, launch:

```powershell
& "$repo\\server\\tools\\prepare-js5-client-probe.ps1" -Listen
```

The client's JS5 upstream must point to `127.0.0.1:43596` and the
client must initiate RSProt opcode 15 + 20-byte payload. Starting the
probe **does not** reconfigure or launch the client. A valid handshake
and cache responses alone do not prove that the client successfully
reaches a world or that signature constraints have been met. The PR
must remain a draft until a real client session establishes more.

## Client-branch smoke integration and empty-listener fix (2026-11-12)

The user's `prepare-js5-client-probe.ps1 -Listen` correctly passed all
preflight checks but failed after the 180-second accept window because
no client had connected to `127.0.0.1:43596`.
This is an expected **`NO_CLIENT_CONNECTED`** outcome, not evidence
of malformed cache data. `Js5HandshakeDiagnosticServer` now returns
a bounded diagnostic report on accept timeout; `Js5VerifiedMasterProbe`
prints a clear `JS5_NO_CLIENT_CONNECTED` status and finishes without
a Java stack trace. The new `testJs5ProbeAcceptTimeout` CI task covers
this condition and retains all existing 255/255 SHA-verification gates.

The repo's separate `client` branch contains Microbot client source,
but its current private-server loader alters the HTTP `jav_config`
and RuneLite world-directory endpoints, **not** the injected game
client's native JS5 socket address. The unconfigured client cannot
be expected to contact the probe automatically.

For a no-manual-client connection smoke test, use the Git-free
server-branch `server/tools/test-js5-client-branch.ps1`. It starts the
existing bounded verified-master probe and runs the **client-branch
standalone Java transport diagnostic** against it, then closes the
test process. It checks the pinned OpenRS2 master against the TCP
response and exercises revision-242 initialization plus XOR response
framing. It does not modify the real game client's networking or
prove injected gamepack acceptance. The real client connection gate
remains open.

## Cross-branch smoke runner shutdown verification (2026-11-12)

The Windows cross-branch runner previously tested whether the *wrapping*
Gradle/PowerShell process exited within 15 seconds after the Java socket
client had completed. This could cause the generic
`Server did not finish a clean one-session diagnostic` error even after
a valid response, because Gradle may still be completing shutdown.
The runner now waits up to 90 seconds **after client completion**, then
requires the single-server session report to show
`handshakeAccepted=true`, `requests=3`, `responses=2`,
`termination=CLIENT_CLOSED`, *and* a clean wrapper exit.
It preserves complete stdout/stderr and prints useful tails if either
protocol verification or process completion fails. It does not treat
a successful client-side check alone as proof of a clean server run.

To use the corrected Git-free runner, download the newest
`server/tools/test-js5-client-branch.ps1` and invoke it directly
without manually launching a separate listener. The existing SHA-256
archive pin, loopback-only binding, local-cache admission, request
limits and client branch selection are preserved.

The smoke still uses a **standalone client-branch socket tester**,
not the real injected game client. Therefore real gamepack JS5
acceptance and game login remain unverified.

## Cross-branch Windows verification and nullable child process exit code

On 2026-11-12, a full Windows PowerShell 5.1 transcript conclusively
verified the standalone client-branch TCP diagnostic:

- Client: `KSP_JS5_CLIENT_BRANCH_DIAGNOSTIC_PASS`
- Server: `handshakeAccepted=true requests=3 responses=2
  responseBytes=2939 termination=CLIENT_CLOSED`.
- 208 bytes were returned for master `255/255`; 2,731 bytes for
  XOR-protected reference `255/24`.
- The server Gradle process printed `BUILD SUCCESSFUL in 25s`.

The old runner then incorrectly failed because the wrapping PowerShell
process object reported a **null** `ExitCode` after shutdown. This
is independent of JS5 wire success. The updated
`server/tools/test-js5-client-branch.ps1` accepts a null exit code
only when the process did exit, Gradle emitted `BUILD SUCCESSFUL`,
no `BUILD FAILED` marker exists, and the exact successful server
session report is present. Nonzero exit codes or any missing evidence
still fail the test. This fixes result classification without
weakening protocol checks or touching game-client networking.

**The cross-branch protocol milestone is verified by the user's
original logs.** Actual injected RuneScape client acceptance and
private game login remain **unverified**.

## Native injected-gamepack endpoint experiment (client branch, 2026-11-12)

The separate `client` branch now implements an opt-in injected gamepack
applet-endpoint experiment, **in addition to** the previously verified
standalone socket diagnostic:

- `KSP_OSRS_JS5_GAMEPACK_PROBE=true` must be explicitly enabled.
- `KSP_OSRS_JS5_PROBE_PORT=43594` chooses the local test port.
- Standard RuneLite and Microbot applet stubs provide a loopback codebase
  and candidate `server`, `port1`, `port2` endpoint parameters.
- The **normal production client path remains unchanged when disabled**.
  `KSP_OSRS_PRIVATE_SERVER=true` is incompatible with the experimental
  probe; existing discovery and readiness protections are not bypassed.
- One-time client logs `KSP_JS5_GAMEPACK_CODEBASE` and
  `KSP_JS5_GAMEPACK_PARAM` indicate which endpoint hints the injected
  gamepack actually queries. Only parameter names, not account/session
  data, are logged by these diagnostics.

The `server/tools/prepare-js5-gamepack-client.ps1` script is the
Git-free Windows entry point. It stages a pinned isolated client branch
checkout if one is not already present, applies the probed source with
backups, and compiles the full client without opening a listener by
default. `-LaunchClient` explicitly starts the verified 255/255 JS5
server at the same **43594** port and launches the full injected game
client in a separate PowerShell window. The listener remains one-session,
time-limited and loopback-only; the script captures separate server and
client logs. Do not enter account credentials during this experiment.

The 43594 choice follows the documented default game-server port used
by RSProx, but **actual revision-242 native JS5 port selection is not
confirmed**; a clean `NO_CLIENT_CONNECTED` result would be evidence
that the endpoint hints did not cause a connection under that test.
The unrelated standalone, byte-exact JS5 test on port **43596**
remains verified and unchanged.

This is explicitly **not** complete private login, server-world sync,
client signature authentication, or production gamepack acceptance.

## Real injected revision-242 gamepack handshake CONFIRMED (2026-11-12)

A user-supplied Windows PowerShell run of
`server/tools/prepare-js5-gamepack-client.ps1 -LaunchClient` recorded
**actual native gamepack** socket traffic on `127.0.0.1:43594`:

- `JS5_HANDSHAKE_ACCEPTED revision=242`
- First request was priority control opcode `3`.
- Next request `(255,255)` received the externally pinned **208-byte**
  JS5-framed archive master reference.
- Archive reference tables `(255,4)`, `(255,6)`, `(255,8)`,
  `(255,10)`, `(255,13)`, `(255,14)`, `(255,15)`, and
  `(255,17)` were served successfully.
- Real gamepack subsequently requested and received archive `4`
  groups `0` through `21`.
- **32 requests; 31 responses; 202,248 response bytes**.
- Termination reason: **`REQUEST_LIMIT`**—the diagnostic's
  configured 32-request cap, NOT a cache validation failure.

This confirms the opt-in client applet endpoint mechanism is effective
enough for the injected revision-242 gamepack to reach the localhost
JS5 service and retrieve early cached resources. It does NOT yet prove
that the gamepack finishes downloading, accepts every resource, reaches
a complete login screen, or can use any world server.

### Follow-up: extended, bounded gamepack JS5 diagnostics

The default `Js5HandshakeDiagnosticServer.serveOne` and standalone
verified-master tests retain their **32-request cap**. A new
`--extended-client-session` argument to
`Js5VerifiedMasterProbe` enables a deliberately bounded
**8,192-request, 10-minute** one-session capture, with the
same pinned SHA-256 and exact local-cache admission, loopback-only
listener, 7-second socket idle read timeout, and opt-in startup.
`Js5ExtendedSessionTest` tests more than 32 real request packets and
the configured limit. The Windows
`prepare-js5-client-probe.ps1 -ExtendedClientSession` activates the
extended Gradle flag, while `prepare-js5-gamepack-client.ps1` now
passes it automatically when explicitly launched using `-LaunchClient`.

The next Windows result should reveal whether the native gamepack
completes JS5 loading or encounters any further protocol problems.
Use the server's `termination` report to distinguish a benign idle
timeout from `REQUEST_LIMIT`, `GROUP_UNAVAILABLE` or a real client
protocol error. Private login is still intentionally out of scope.

## Native-gamepack second Windows run: GPU off; 8192-limit hit (2026-11-12)

The subsequent Windows run proved that RuneLite actually started with
`args: --safe-mode`; `KSP_JS5_GPU_DISABLED | probeOnly=true
discoveredGpuPlugins=0 softwareRenderer=true` appeared, with no GPU
device initialization in the provided client log. The gamepack still
printed `error_game_js5connect`, and the *server* explicitly recorded:

`JS5_CONTROLLED_PROBE_REPORT handshakeAccepted=true requests=8192
responses=8191 responseBytes=7607380 termination=REQUEST_LIMIT`

The one extra request is a control opcode. Successful responses are
visible through the final group, e.g. archive `4` group `5435`.
The attached tail of the native server transcript contains distinct
group IDs from archives `3`, `4` and `12`; there is no
`GROUP_UNAVAILABLE` termination in the session. This confirms
high-volume cache delivery, **not** client-side acceptance of every
group or resolution of the generic JS5 error.

### Further bounded capture

The 8,192-request cap was too short for this client's observed bulk
cache download; simply raising the cap is a *diagnostic extension*, not
a proven fix for `error_game_js5connect`.

An explicitly selected native-client extended mode now has these
limits: **150,000** requests, **20 minutes** elapsed session time,
**30 seconds** socket idle timeout and exactly one localhost-only
connection. A hard ceiling of **200,000** requests remains enforced
on any caller. Ordinary synthetic mode stays at 32 requests with its
original 7-second idle timeout. This gives room to observe whether
the native gamepack proceeds through the complete cache download
without silently making the listener unbounded.

To prevent an enormous transcript, extended mode logs the first
32 request/response events and then every 1,000th event. It still
counts every request and byte, and reports any precise termination.
The regression test checks 1,100 packets plus request event sampling,
configured 64-request cap and the hard-ceiling rejection.

The Windows wrapper previously ended its observation at 210 seconds;
it now honors the 20-minute server session, with a bounded 22-minute
observation limit after the listener becomes ready. Without this
change, the wrapper could kill a healthy session prematurely.

The open question is still whether the injected client fully accepts
the cache and progresses, and if not, which protocol or configuration
mismatch produces the client-side error. Do not infer a firewall fault
from the generic update-server text. No account login is authorized.

## Isolation correction: cache JS5 connectivity is not a private game (2026-11-12)

The original injected Microbot gamepack experiment redirected the JS5
codebase to localhost but **continued to obtain the official
`jav_config`**. That configuration exposes additional numbered
service URLs, including official authentication and world endpoints.
Consequently, the >119,000 native JS5 cache requests observed in the
previous logs demonstrate **cache transport only**; no successful
private world selection, account login or independent gameplay.

The separate client branch now requires an explicitly configured
loopback `KSP_OSRS_JS5_LOCAL_JAV_CONFIG_URL`. Its two loader paths
fetch that URL **without the official retry, backup-config or
world-list fallback**, and reject off-local URLs/known production
domain strings in the config values. Probe mode refuses to start
if the local URL is missing.

The dedicated Java 21 gamepack test task activates a diagnostic
`KspJs5LoopbackGuard` via
`-Djava.security.manager=allow` **before RuneLite bootstrap**.
Java socket permission checks are restricted to localhost addresses;
blocked nonlocal destinations are logged in a bounded fashion.
Regression tests exercise an actual blocked official-host
`checkConnect` and allowed local endpoint. Other Microbot startup
paths are unchanged, and `--safe-mode` still prevents GPU loading.

The server-branch `server/tools/serve-js5-local-jav-config.ps1`
serves a reviewed local configuration from `127.0.0.1:43595` for
one bounded diagnostic run. The Windows gamepack launcher starts
this service **before** starting the cached JS5 server and the
injected client. It includes a deliberately minimal localhost
`jav_config.local.template.ws`; **numeric applet parameters for
revision 242 remain unverified, so this template is not a working
login/world configuration**.

**Security boundary:** A Java SecurityManager is only a Java-level
socket guard, not a complete process/OS network sandbox. Native
networking, child processes and external programs require separate
OS-level isolation (e.g. a dedicated VM with outbound network denied).
No account login is authorized or implemented. Reconstructing a
complete local world directory, authentication and login service
is the next development task after proving client-config isolation.
