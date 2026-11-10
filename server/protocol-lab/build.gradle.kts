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
