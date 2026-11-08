import com.android.build.api.dsl.ApplicationExtension
import java.time.Instant

plugins {
    id("com.android.application")
}

fun sourceGit(vararg arguments: String): String? = try {
    val command = listOf("git", "-c", "safe.directory=${rootDir.absolutePath.replace('\\', '/')}") + arguments
    val process = ProcessBuilder(command).directory(rootDir).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText().trim() }
    if (process.waitFor() == 0) output else null
} catch (_: java.io.IOException) { null }

val sourceBranch = sourceGit("rev-parse", "--abbrev-ref", "HEAD") ?: "unknown"
val sourceCommit = sourceGit("rev-parse", "--short=12", "HEAD") ?: "unknown"
val sourceStatus = sourceGit("status", "--porcelain", "--untracked-files=normal")
val sourceState = when { sourceStatus == null -> "unknown"; sourceStatus.isEmpty() -> "clean"; else -> "modified" }
val buildTimeUtc = Instant.now().toString()
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

providers.gradleProperty("ptrackBuildRoot").orNull?.let {
    layout.buildDirectory.set(file("$it/app"))
}

configure<ApplicationExtension> {
    namespace = "cat.ptrack"
    compileSdk = 37
    defaultConfig {
        applicationId = "cat.ptrack"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "0.2.6"
        manifestPlaceholders["ptrackAppLabel"] = "PTrack"
        testInstrumentationRunner = "cat.ptrack.SmokeInstrumentation"
        buildConfigField("String", "SOURCE_BRANCH", quoted(sourceBranch))
        buildConfigField("String", "SOURCE_COMMIT", quoted(sourceCommit))
        buildConfigField("String", "SOURCE_STATE", quoted(sourceState))
        buildConfigField("String", "BUILD_TIME_UTC", quoted(buildTimeUtc))
        buildConfigField("boolean", "CAR_ENABLED", "true")
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        // Separate identity: Play Internal App Sharing re-signs uploads. Keep the
        // installed app and its trip database intact while testing distribution.
        create("carTest") {
            initWith(getByName("release"))
            applicationIdSuffix = ".cartest"
            versionNameSuffix = "-car-test"
            manifestPlaceholders["ptrackAppLabel"] = "PTrack Prova cotxe"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // AGP 9 built-in Kotlin does not compile Kotlin files added to java directories.
    sourceSets.getByName("test").kotlin.directories.add(rootProject.file("core-tests").absolutePath)
    testOptions.unitTests.isReturnDefaultValues = false
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.car.app:app:1.7.0")
    implementation("io.github.rallista:valhalla-mobile:0.6.4")
    implementation("io.github.rallista:valhalla-models-config:0.6.0")
    implementation("org.maplibre.gl:android-sdk:13.6.1")
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test>().configureEach {
    testLogging { showStandardStreams = true; events("passed", "failed", "skipped") }
}
