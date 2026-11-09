plugins {
    java
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
