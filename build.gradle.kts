plugins {
    kotlin("jvm") version "2.2.21"
    id("com.gradleup.shadow") version "9.3.1"
}

group = "com.degoos"
version = "0.2.0"

val kayleVersion = "0.0.9"
val hytaleInstallationPath = properties["hytale.path"].toString()
val hytaleServerExecutablePath = "${hytaleInstallationPath}/Server/HytaleServer.jar"

repositories {
    mavenCentral()
    maven("https://dev.degoos.xyz/maven/repo")
}

dependencies {
    compileOnly(files(hytaleServerExecutablePath))
    compileOnly("com.degoos:kayle:$kayleVersion")

    testImplementation(kotlin("test"))
    testImplementation(files(hytaleServerExecutablePath))
    testImplementation("com.degoos:kayle:$kayleVersion")
}

kotlin {
    jvmToolchain(23)
}

tasks.withType<Test>().configureEach {
    // Native Hytale classes in the current server JAR require Java 25 at runtime.
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("DegoosMetrics")
    archiveClassifier.set("")

    dependencies {
        exclude(dependency("org.jetbrains.kotlin:.*"))
        exclude(dependency("org.jetbrains.kotlinx:.*"))
    }
}

tasks.named<ProcessResources>("processResources") {
    val replaceProperties = mapOf("plugin_version" to project.version)

    filesMatching("manifest.json") {
        expand(replaceProperties)
    }

    inputs.properties(replaceProperties)
}
