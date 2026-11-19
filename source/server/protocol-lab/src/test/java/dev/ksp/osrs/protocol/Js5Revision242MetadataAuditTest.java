package dev.ksp.osrs.protocol;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class Js5Revision242MetadataAuditTest {
    private static void check(boolean value) {
        if (!value) throw new AssertionError("Metadata audit test failed");
    }

    public static void main(String[] args) throws Exception {
        var ref = Js5Revision242MetadataAudit.REFERENCE;
        check(ref.size() == 25);
        check(ref.stream().mapToInt(Js5Revision242MetadataAudit.Entry::groups).sum() == 120374);
        check(ref.get(16).groups() == 0 && ref.get(23).groups() == 0);
        check(ref.get(24).groups() == 18);
        var observations = new ArrayList<Js5Revision242MetadataAudit.Observation>();
        for (var e : ref) {
            observations.add(new Js5Revision242MetadataAudit.Observation(
                e.archive(), e.groups() != 0, e.revision(), e.checksum(), e.groups()));
        }
        check(Js5Revision242MetadataAudit.compare(ref, observations).passed());
        // Model the previously audited local cache, with idx255 ending at 22.
        var truncated = new ArrayList<>(observations);
        truncated.set(23, new Js5Revision242MetadataAudit.Observation(23, false, 0, 0, 0));
        truncated.set(24, new Js5Revision242MetadataAudit.Observation(24, false, 0, 0, 0));
        var incomplete = Js5Revision242MetadataAudit.compare(ref, truncated);
        check(!incomplete.passed() && incomplete.matching() == 24);
        check(incomplete.differences().stream().anyMatch(d ->
            d.archive() == 24 && d.field().equals("presence")));
        var altered = new ArrayList<>(observations);
        altered.set(0, new Js5Revision242MetadataAudit.Observation(0, true, 1, 2, 3));
        var differences = Js5Revision242MetadataAudit.compare(ref, altered);
        check(differences.differences().size() == 3);
        boolean rejected = false;
        try {
            Js5Revision242MetadataAudit.compare(ref, List.of(observations.get(0), observations.get(0)));
        } catch (IOException expected) { rejected = true; }
        check(rejected);
        System.out.println("Js5Revision242MetadataAuditTest: PASS");
    }
}
