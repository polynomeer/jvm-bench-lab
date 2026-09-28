plugins {
    java
    id("me.champeau.jmh") version "0.7.2"
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // The naive harnesses deliberately use nothing but the JDK. Adding a benchmarking library to
    // them would be answering the question this repository is asking.
    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmhAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

jmh {
    // Defaults only; a scenario overrides them on the command line and records what it used.
    resultFormat.set("JSON")
    resultsFile.set(layout.buildDirectory.file("results/jmh/results.json"))
}

tasks.register<Jar>("naiveJar") {
    archiveBaseName.set("naive")
    manifest { attributes["Main-Class"] = "io.benchlab.naive.NaiveRunner" }
    from(sourceSets.main.get().output)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
