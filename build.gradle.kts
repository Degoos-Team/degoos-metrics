plugins {
    kotlin("jvm") version "2.2.21"
    id("com.gradleup.shadow") version "9.3.1"
}

group = "com.degoos"
version = "0.3.0"

val hytaleInstallationPath = properties["hytale.path"].toString()
val hytaleServerExecutablePath = "${hytaleInstallationPath}/Server/HytaleServer.jar"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(files(hytaleServerExecutablePath))

    testImplementation(kotlin("test"))
    testImplementation(files(hytaleServerExecutablePath))
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

    // Standalone: the Kotlin standard library is bundled, trimmed to what is used and relocated, so it never clashes
    // with another plugin's Kotlin (for example Kayle's). The public API uses JDK types only.
    minimize()
    relocate("kotlin", "com.degoos.metrics.lib.kotlin")
    relocate("org.intellij.lang.annotations", "com.degoos.metrics.lib.annotations.intellij")
    relocate("org.jetbrains.annotations", "com.degoos.metrics.lib.annotations.jetbrains")
    exclude("META-INF/maven/**", "META-INF/*.kotlin_module", "META-INF/versions/**/module-info.class", "module-info.class")
}

tasks.named<ProcessResources>("processResources") {
    val replaceProperties = mapOf("plugin_version" to project.version)

    filesMatching("manifest.json") {
        expand(replaceProperties)
    }

    inputs.properties(replaceProperties)
}
