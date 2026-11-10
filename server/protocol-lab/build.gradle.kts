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
