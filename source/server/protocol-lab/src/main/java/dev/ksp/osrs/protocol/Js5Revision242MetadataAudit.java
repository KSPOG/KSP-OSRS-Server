package dev.ksp.osrs.protocol;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Read-only comparison against the independently archived OSRS 242 cache
 * metadata published at https://archive.openrs2.org/caches/runescape/2757 .
 *
 * Entries are CRC32 of reference-table containers (archive 255, group N),
 * NOT the CRCs of individual game groups. Does not establish client acceptance
 * or the signed/unsigned master-index wire representation.
 */
public final class Js5Revision242MetadataAudit {
    private Js5Revision242MetadataAudit() {}

    public record Entry(int archive, int revision, int checksum, int groups) {}
    public record Observation(int archive, boolean present, int revision, int checksum, int groups) {}
    public record Difference(int archive, String field, String expected, String actual) {}
    public record Report(int checked, int matching, List<Difference> differences) {
        public Report { differences = List.copyOf(differences); }
        public boolean passed() { return differences.isEmpty(); }
    }

    // Independently published Jagex-sourced OpenRS2 archive 2757, 2026-11-03.
    // The source reports signed CRC32 decimals; equality is bit-exact in int.
    public static final List<Entry> REFERENCE = List.of(
        new Entry(0,1793635657,1319971744,11314),
        new Entry(1,1793635793,-1646919863,2787),
        new Entry(2,1793635797,-1577378969,41),
        new Entry(3,1793635800,1942888009,992),
        new Entry(4,1793635801,-1466267770,12855),
        new Entry(5,1793635803,174413765,2950),
        new Entry(6,1793635804,134757627,893),
        new Entry(7,1793635810,-860895463,62769),
        new Entry(8,1793635948,-542627223,8822),
        new Entry(9,1793635948,1312015841,1),
        new Entry(10,36,-2113105603,4),
        new Entry(11,1792425506,934224548,316),
        new Entry(12,1793635841,-771754791,10304),
        new Entry(13,1793635800,-1337468202,21),
        new Entry(14,1792425662,901523151,612),
        new Entry(15,1792425546,-1325981569,192),
        new Entry(16,0,0,0),
        new Entry(17,1793635799,1503329197,2),
        new Entry(18,1793635992,2091408769,2106),
        new Entry(19,1793635993,-78574465,54),
        new Entry(20,1793635992,569801439,2106),
        new Entry(21,1793635799,668203022,156),
        new Entry(22,1793635760,990670339,1059),
        new Entry(23,0,0,0),
        new Entry(24,1,-1931708361,18)
    );

    public static Report compare(List<Entry> expected, List<Observation> actual) throws IOException {
        if (expected == null || actual == null || expected.isEmpty())
            throw new IOException("Reference or observations absent");
        var seen = new java.util.HashMap<Integer, Observation>();
        for (var item : actual) {
            if (item.archive() < 0 || item.archive() > 254
                    || seen.putIfAbsent(item.archive(), item) != null)
                throw new IOException("Invalid/duplicate observed archive");
        }
        List<Difference> diff = new ArrayList<>();
        int matching = 0;
        for (int i = 0; i < expected.size(); i++) {
            Entry e = expected.get(i);
            if (e.archive() != i) throw new IOException("Reference archive sequence invalid");
            Observation a = seen.remove(i);
            if (a == null) {
                diff.add(new Difference(i, "entry", "present in reference", "not observed"));
                continue;
            }
            int before = diff.size();
            // Zeroed reference entries are absent: local absence matches.
            boolean expectedPresent = e.groups() > 0;
            if (a.present() != expectedPresent) {
                diff.add(new Difference(i, "presence", String.valueOf(expectedPresent), String.valueOf(a.present())));
            } else if (expectedPresent) {
                if (a.checksum() != e.checksum()) diff.add(new Difference(i, "reference CRC32",
                    Integer.toUnsignedString(e.checksum()), Integer.toUnsignedString(a.checksum())));
                if (a.revision() != e.revision()) diff.add(new Difference(i, "revision",
                    String.valueOf(e.revision()), String.valueOf(a.revision())));
                if (a.groups() != e.groups()) diff.add(new Difference(i, "group count",
                    String.valueOf(e.groups()), String.valueOf(a.groups())));
            }
            if (diff.size() == before) matching++;
        }
        for (var id : seen.keySet()) diff.add(new Difference(id, "unexpected archive", "none", "present"));
        return new Report(expected.size(), matching, diff);
    }

    public static List<Observation> readCache(Path cache) throws IOException {
        Path idx = cache.resolve("main_file_cache.idx255");
        if (!Files.isRegularFile(idx) || Files.isSymbolicLink(idx))
            throw new IOException("Missing idx255");
        long len = Files.size(idx);
        if (len <= 0 || len % 6 != 0 || len > 255 * 6L)
            throw new IOException("Invalid idx255 length");
        List<Observation> observations = new ArrayList<>();
        try (IndexedCacheReader reader = new IndexedCacheReader(cache)) {
            // Read up to the authoritative archive count. Archives outside the
            // local idx255 range remain explicitly absent observations.
            for (int i = 0; i < REFERENCE.size(); i++) {
                byte[] raw = i < len / 6 ? reader.read(255, i) : null;
                if (raw == null) {
                    observations.add(new Observation(i, false, 0, 0, 0));
                    continue;
                }
                var info = CacheStructureCheck.validateContainer(raw);
                if (info.crcLength() != raw.length)
                    throw new IOException("Bad reference container at archive " + i);
                var ref = CacheMetadata.parse(i, CacheMetadata.unpack(raw));
                CRC32 crc = new CRC32();
                crc.update(raw);
                observations.add(new Observation(i, true, ref.revision(), (int)crc.getValue(), ref.groups().size()));
            }
        }
        return observations;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: Js5Revision242MetadataAudit <cache-dir>");
            System.exit(2);
        }
        Report report = compare(REFERENCE, readCache(Path.of(args[0])));
        System.out.println("OpenRS2 independent reference: oldschool/live/en build=242 cache=2757 entries=25");
        System.out.println("Metadata match: " + report.matching() + "/" + report.checked());
        for (Difference d : report.differences())
            System.out.println("MISMATCH archive=" + d.archive() + " field=" + d.field()
                + " expected=" + d.expected() + " actual=" + d.actual());
        System.out.println("Revision-242 cache metadata: " + (report.passed() ? "MATCH" : "MISMATCH"));
        System.out.println("Master-index wire format/client acceptance: UNVERIFIED");
        System.out.println("Network listeners: DISABLED");
        if (!report.passed()) System.exit(3);
    }
}
