plugins {
    java
    application
}

group = "dev.ksp.osrs"
version = "0.1.0-SNAPSHOT"

repositories { mavenCentral() }

dependencies {
    // RSProt: open-source protocol library. This is NOT RSProx.
    implementation("net.rsprot:osrs-242-api:1.0.0-ALPHA-20261106")
    implementation("org.apache.commons:commons-compress:1.27.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.register("verifyProtocolDependencies") {
    group = "verification"
    description = "Resolve the exact revision-242 API dependency for the experimental adapter."
    doLast {
        val artifacts = configurations.runtimeClasspath.get().resolvedConfiguration.resolvedArtifacts
        check(artifacts.any {
            it.moduleVersion.id.group == "net.rsprot" && it.name == "osrs-242-api"
                && it.moduleVersion.id.version == "1.0.0-ALPHA-20261106"
        }) { "RSProt revision 242 artifact missing" }
        println("RSProt revision 242 dependency resolved. This does not mean login is implemented.")
    }
}

application {
    mainClass.set("dev.ksp.osrs.protocol.ProtocolApiCheck")
}

tasks.register<JavaExec>("inspectCache") {
    group = "verification"
    description = "Validate the local JS5 master index sector chain without copying assets."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.ksp.osrs.protocol.CacheStructureCheck")
    doFirst {
        val cache = providers.gradleProperty("cachePath").orNull
        require(!cache.isNullOrBlank()) { "Provide -PcachePath=<local-cache-folder>" }
        args(cache)
    }
}

tasks.register<JavaExec>("testCacheReader") {
    group = "verification"
    description = "Test standard/extended cache sectors and corruption handling."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.ksp.osrs.protocol.IndexedCacheReaderTest")
}

tasks.register<JavaExec>("testReferenceTables") {
    group = "verification"
    description = "Validate reference table container parsing and safe failure."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.ksp.osrs.protocol.CacheStructureCheckTest")
}

tasks.register<JavaExec>("auditCache") {
    group = "verification"
    description = "Read-only decode of JS5 reference tables and sampled group CRCs; revision remains unverified."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.ksp.osrs.protocol.CacheIntegrityAudit")
    doFirst {
        val cache = providers.gradleProperty("cachePath").orNull
        require(!cache.isNullOrBlank()) { "Provide -PcachePath=<local-cache-folder>" }
        args(cache)
    }
}

tasks.register<JavaExec>("testCacheMetadata") {
    group = "verification"
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.ksp.osrs.protocol.CacheMetadataTest")
}

tasks.register<JavaExec>("testCacheIntegrity") {
    group = "verification"
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.ksp.osrs.protocol.CacheIntegrityAuditTest")
}

tasks.register<JavaExec>("testJs5Provider") {
    group = "verification"
    description = "Test read-only RSProt JS5 provider, suffix framing and corruption rejection."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.ksp.osrs.protocol.VerifiedJs5GroupProviderTest")
}

tasks.register<JavaExec>("testJs5WireFraming") {
    group = "verification"
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.ksp.osrs.protocol.Js5WireFramingTest")
}

tasks.register<JavaExec>("auditJs5Provider") {
    group = "verification"
    description = "Read-only sample of actual cache groups via RSProt framed provider."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.ksp.osrs.protocol.Js5ProviderAudit")
    doFirst {
        val cache = providers.gradleProperty("cachePath").orNull
        require(!cache.isNullOrBlank()) { "Provide -PcachePath=<local-cache-folder>" }
        args(cache)
    }
}

tasks.register<JavaExec>("auditJs5MasterIndex") {
    group = "verification"
    description = "Check offline VERSIONED master-index candidate against local reference tables; never enables network delivery."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.ksp.osrs.protocol.Js5MasterIndexAudit")
    doFirst {
        val cache = providers.gradleProperty("cachePath").orNull
        require(!cache.isNullOrBlank()) { "Provide -PcachePath=<local-cache-folder>" }
        args(cache)
    }
}

tasks.register<JavaExec>("testJs5HandshakePayload") {
    group = "verification"
    description = "Test RSProt 242 JS5 initialization message parsing without opening sockets."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.ksp.osrs.protocol.Js5HandshakePayloadTest")
}

tasks.register<JavaExec>("testJs5MasterReferenceComparator") {
    group = "verification"
    description = "Synthetic tests for SHA-256-pinned offline JS5 master reference comparison."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.ksp.osrs.protocol.Js5MasterReferenceComparatorTest")
}

tasks.register<JavaExec>("compareJs5MasterReference") {
    group = "verification"
    description = "Byte-exact offline comparison to an independently captured pinned master-index container."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.ksp.osrs.protocol.Js5MasterReferenceComparator")
    doFirst {
        val cache = providers.gradleProperty("cachePath").orNull
        val reference = providers.gradleProperty("masterReferencePath").orNull
        val sha = providers.gradleProperty("masterReferenceSha256").orNull
        require(!cache.isNullOrBlank()) { "Provide -PcachePath=<local-cache-folder>" }
        require(!reference.isNullOrBlank()) { "Provide -PmasterReferencePath=<fixture.bin>" }
        require(!sha.isNullOrBlank()) { "Provide -PmasterReferenceSha256=<64 hex digits>" }
        args(cache, reference, sha)
    }
}

tasks.register<JavaExec>("auditRevision242Metadata") {
    group = "verification"
    description = "Offline comparison to Jagex-sourced OpenRS2 archive 2757 reference metadata."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.ksp.osrs.protocol.Js5Revision242MetadataAudit")
    doFirst {
        val cache = providers.gradleProperty("cachePath").orNull
        require(!cache.isNullOrBlank()) { "Provide -PcachePath=<local-cache-folder>" }
        args(cache)
    }
}

tasks.register<JavaExec>("testRevision242MetadataAudit") {
    group = "verification"
    description = "Synthetic checks for authoritative reference completeness and mismatch reporting."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.ksp.osrs.protocol.Js5Revision242MetadataAuditTest")
}

tasks.register<JavaExec>("testExhaustiveCacheAudit") {
    group = "verification"
    description = "Synthetic CRC, trailer and container failure-mode tests for the exhaustive JS5 audit."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.ksp.osrs.protocol.Js5ExhaustiveCacheAuditTest")
}

tasks.register<JavaExec>("auditAllJs5Groups") {
    group = "verification"
    description = "Read-only full-group CRC/trailer/container audit, optionally including every provider wire frame."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.ksp.osrs.protocol.Js5ExhaustiveCacheAudit")
    doFirst {
        val cache = providers.gradleProperty("cachePath").orNull
        require(!cache.isNullOrBlank()) { "Provide -PcachePath=<cache-folder>" }
        args(cache)
        if (providers.gradleProperty("verifyProvider").orNull == "true") args("--verify-provider")
    }
}

tasks.register<JavaExec>("testJs5OfflineExchange") {
    group = "verification"
    description = "Synthetic RSProt revision-242 JS5 request parsing tests, no network IO."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.ksp.osrs.protocol.Js5OfflineExchangeTest")
}

tasks.register<JavaExec>("auditJs5OfflineExchange") {
    group = "verification"
    description = "Replay a safe offline JS5 request sequence against an isolated cache."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.ksp.osrs.protocol.Js5OfflineExchange")
    doFirst {
        val cache = providers.gradleProperty("cachePath").orNull
        require(!cache.isNullOrBlank()) { "Provide -PcachePath=<cache-folder>" }
        args(cache)
    }
}

tasks.register<JavaExec>("testJs5MasterWireFixtureAudit") {
    group = "verification"
    description = "Synthetic captured 255/255 JS5 wire decode and SHA-256 fixture tests."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.ksp.osrs.protocol.Js5MasterWireFixtureAuditTest")
}

tasks.register<JavaExec>("compareJs5MasterWireFixture") {
    group = "verification"
    description = "Compare a previously captured 255/255 on-wire response with the local VERSIONED candidate."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.ksp.osrs.protocol.Js5MasterWireFixtureAudit")
    doFirst {
        val cache = providers.gradleProperty("cachePath").orNull
        val fixture = providers.gradleProperty("masterWirePath").orNull
        val sha = providers.gradleProperty("masterContainerSha256").orNull
        require(!cache.isNullOrBlank()) { "Provide -PcachePath=<cache-folder>" }
        require(!fixture.isNullOrBlank()) { "Provide -PmasterWirePath=<captured-255-255.bin>" }
        require(!sha.isNullOrBlank()) { "Provide -PmasterContainerSha256=<64 hex chars>" }
        args(cache, fixture, sha)
    }
}

tasks.register<JavaExec>("inspectJs5MasterWireFixture") {
    group = "verification"
    description = "Read-only report and SHA-256 pin for an externally captured JS5 255/255 response."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.ksp.osrs.protocol.Js5MasterWireFixtureAudit")
    doFirst {
        val fixture = providers.gradleProperty("masterWirePath").orNull
        require(!fixture.isNullOrBlank()) { "Provide -PmasterWirePath=<captured-255-255.bin>" }
        args("--inspect", fixture)
    }
}

tasks.register<JavaExec>("auditJs5LocalDiagnostic") {
    group = "verification"
    description = "Loopback-only TCP socket round trip to the verified cache provider; no OSRS client handshake."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.ksp.osrs.protocol.Js5LocalDiagnosticAudit")
    doFirst {
        val cache = providers.gradleProperty("cachePath").orNull
        require(!cache.isNullOrBlank()) { "Provide -PcachePath=<verified-cache-directory>" }
        args(cache)
    }
}

tasks.register<JavaExec>("runJs5LocalDiagnostic") {
    group = "verification"
    description = "Single-client post-handshake-only diagnostic socket on localhost, no master 255/255."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.ksp.osrs.protocol.Js5LocalDiagnosticServer")
    doFirst {
        val cache = providers.gradleProperty("cachePath").orNull
        val port = providers.gradleProperty("diagnosticPort").orNull ?: "43596"
        require(!cache.isNullOrBlank()) { "Provide -PcachePath=<verified-cache-directory>" }
        val parsedPort = port.toIntOrNull()
        require(parsedPort != null && parsedPort in 1..65535) { "Invalid diagnostic port" }
        args(cache, port, "--one-session")
    }
}

tasks.register<JavaExec>("auditJs5MasterCandidateSocket") {
    group = "verification"
    description = "Synthetic localhost-only 255/255 master candidate framing; NOT a real client compatibility check."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.ksp.osrs.protocol.Js5MasterCandidateSocketAudit")
    doFirst {
        val cache = providers.gradleProperty("cachePath").orNull
        require(!cache.isNullOrBlank()) { "Provide -PcachePath=<verified-cache-directory>" }
        args(cache)
    }
}
