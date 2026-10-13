import java.io.ByteArrayOutputStream

plugins {
    kotlin("jvm") version "2.2.21"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.json:json:20250517")
    testImplementation(kotlin("test-junit5"))
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
}

java {
    withSourcesJar()
}

application {
    mainClass = "MainKt"
    applicationName = "address-standardizer"
}

distributions {
    main {
        contents {
            from("LICENSE", "README.md")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

val java21 = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(21)
}

val packagedCliSmoke by tasks.registering(Exec::class) {
    group = "verification"
    description = "Checks the installed CLI and bundled model from another working directory."
    dependsOn(tasks.installDist)

    val output = ByteArrayOutputStream()
    standardOutput = output
    workingDir(layout.buildDirectory.dir("tmp/packaged-cli-smoke"))
    environment("JAVA_HOME", java21.get().metadata.installationPath.asFile.absolutePath)

    val launcher = tasks.installDist.get().destinationDir.resolve("bin/address-standardizer")
    val address = "123 Main Street Springfield IL 62704"
    if (System.getProperty("os.name").startsWith("Windows")) {
        commandLine("cmd", "/c", "$launcher.bat", address)
    } else {
        commandLine(launcher.absolutePath, address)
    }
    doFirst {
        workingDir.mkdirs()
    }
    doLast {
        check(output.toString(Charsets.UTF_8).trim() == "123 MAIN ST SPRINGFIELD IL 62704") {
            "Installed CLI returned unexpected output: $output"
        }
    }
}

tasks.check {
    dependsOn(packagedCliSmoke)
}

val jmh by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

configurations[jmh.implementationConfigurationName].extendsFrom(configurations.implementation.get())

dependencies {
    add(jmh.implementationConfigurationName, "org.openjdk.jmh:jmh-core:1.37")
    add(jmh.annotationProcessorConfigurationName, "org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

tasks.register<JavaExec>("benchmark") {
    group = "verification"
    description = "Runs JMH throughput and allocation benchmarks; reports stay in agent_work/benchmarks."
    dependsOn(jmh.classesTaskName)
    classpath = jmh.runtimeClasspath
    mainClass = "org.openjdk.jmh.Main"
    javaLauncher = java21

    val reportDirectory = layout.projectDirectory.dir("agent_work/benchmarks").asFile
    args("-foe", "true", "-prof", "gc", "-rf", "json", "-rff", reportDirectory.resolve("results.json"))
    providers.gradleProperty("benchmarkArgs").orNull?.let {
        args(it.split(Regex("\\s+")).filter(String::isNotEmpty))
    }
    doFirst {
        reportDirectory.mkdirs()
    }
}
