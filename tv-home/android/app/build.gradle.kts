import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The release signing key (COUCHTV_KEYSTORE) and, for a fork's permanent key, its private
// <keystore>.properties file next to it. Checked before every release build (requireReleaseKey, below).
val fixedKey: File? = System.getenv("COUCHTV_KEYSTORE")?.let { file(it) }?.takeIf { it.isFile }
val fixedProps: Properties? = fixedKey?.let { key -> File(key.path + ".properties") }?.takeIf { it.isFile }
    ?.let { f -> Properties().apply { f.inputStream().use { input -> load(input) } } }

android {
    namespace = "app.couchtv.launcher"
    compileSdk = 34
    defaultConfig {
        applicationId = "app.couchtv.launcher"
        minSdk = 23          // Android 6.0 (older Mi TVs); Fire OS 6 (7.1) sticks install it too
        targetSdk = 34
        versionCode = 15
        versionName = "0.13-bravia.1"   // bravia-custom fork build (mrkd19/couch-tv), not an upstream release
        // Where the bridge might be: ./gradlew assembleRelease -PbridgeUrls=http://my-computer.local:5400
        // (comma separated, more than one is fine). The last one that answered is remembered on the TV.
        // Without it, apply.sh tells the app the address when it installs (the "bridge" extra), and
        // with no address at all (option A) the copy of the home built into the app is the home.
        val urls = (project.findProperty("bridgeUrls") as String? ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
        buildConfigField("String[]", "BRIDGES", "{" + urls.joinToString(", ") { "\"" + it + "\"" } + "}")
        // Whose GitHub releases Home settings, Updates offers: -PupdateRepo=owner/name for a fork
        // with releases of its own, -PupdateRepo= (empty) for none. Updater.kt checks the format.
        val updateRepo = (project.findProperty("updateRepo") as String? ?: "Seagey/couch-tv").trim()
        buildConfigField("String", "UPDATE_REPO", "\"" + updateRepo.filter { it.isLetterOrDigit() || it in "/._-" } + "\"")
    }
    buildFeatures { buildConfig = true }
    // Test builds: when COUCHTV_KEYSTORE names a keystore (a debug-style key, alias androiddebugkey,
    // password android), release builds are signed with it, so one test build can update the last (#4).
    // A fork's own permanent key keeps its passwords and alias out of the repo, in a file next to the
    // keystore named <keystore>.properties (storePassword, keyPassword, keyAlias). Without that file
    // the test-key values above are used, as before. (fixedKey and fixedProps are read at the top.)
    signingConfigs {
        if (fixedKey != null) create("fixed") {
            storeFile = fixedKey
            storePassword = fixedProps?.getProperty("storePassword") ?: "android"
            keyAlias = fixedProps?.getProperty("keyAlias") ?: "androiddebugkey"
            keyPassword = fixedProps?.getProperty("keyPassword") ?: "android"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("fixed") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

/**
 * Copies tv-home/web into the app as assets/www/tv, plus a config.js made from config.json, so the
 * home works with no computer at all (option A). The settings file is -PconfigJson=<path>, else
 * config.json in the repo root, else config.example.json. apply.sh can hand the TV newer settings
 * later without a rebuild. Only the parts the TV home shows are copied: people, weather, the
 * home's look and each TV's quick buttons. Never secrets.
 */
abstract class BundleTvWeb : DefaultTask() {
    @get:InputDirectory
    abstract val web: DirectoryProperty

    @get:InputFile
    abstract val configJson: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        val dest = File(out, "www/tv")
        val src = web.get().asFile
        src.walkTopDown().filter { it.isFile }.forEach { f ->
            val rel = f.relativeTo(src).path
            // development only, or not part of the page
            if (rel == "mock.js" || rel.endsWith(".md") || f.name.startsWith(".")) return@forEach
            f.copyTo(File(dest, rel), overwrite = true)
        }
        File(dest, "config.js").writeText("window.COUCH_CONFIG = " + JsonOutput.toJson(publicConfig(configJson.get().asFile)) + ";\n")
    }

    private fun slug(s: Any?, fallback: String): String =
        Regex("[^a-z0-9]+").replace(s.toString().lowercase(), "-").trim('-').take(32).ifEmpty { fallback }

    @Suppress("UNCHECKED_CAST")
    private fun publicConfig(file: File): Map<String, Any?> {
        val c = JsonSlurper().parse(file) as Map<String, Any?>
        val people = mutableListOf<Map<String, Any?>>()
        for (p in (c["people"] as? List<Any?>) ?: emptyList<Any?>()) {
            val m: Map<String, Any?> = when (p) {
                is String -> mapOf("name" to p)
                is Map<*, *> -> p as Map<String, Any?>
                else -> continue
            }
            val name = m["name"]?.toString()?.trim().orEmpty()
            if (name.isEmpty() || people.size >= 12) continue
            val item = linkedMapOf<String, Any?>("id" to ((m["id"] as? String) ?: slug(name, "person")), "name" to name)
            (m["colour"] as? String)?.let { item["colour"] = it }
            people.add(item)
        }
        val tvs = (c["tvs"] as? List<Map<String, Any?>> ?: emptyList()).map { t ->
            mapOf("id" to t["id"], "name" to t["name"], "quick" to (t["quick"] as? List<Any?> ?: listOf("sleep")),
                "picture_modes" to emptyList<String>())
        }
        return mapOf(
            "people" to people,
            "weather" to c["weather"],
            "home" to (c["home"] ?: emptyMap<String, Any?>()),
            "tvs" to tvs,
            "default_tv" to (c["default_tv"] ?: tvs.firstOrNull()?.get("id")),
            "ask" to mapOf("enabled" to false),
            "phone_remote" to false,
        )
    }
}

dependencies {
    // Unit tests for the plain Kotlin rules (FireWatchTest), run with testDebugUnitTest (EPL 1.0).
    testImplementation("junit:junit:4.13.2")
}

val repoRoot = rootProject.projectDir.resolve("../..")
val configFile: File = (project.findProperty("configJson") as String?)?.let { file(it) }
    ?: repoRoot.resolve("config.json").takeIf { it.isFile }
    ?: repoRoot.resolve("config.example.json")

val bundleTvWeb = tasks.register<BundleTvWeb>("bundleTvWeb") {
    web.set(rootProject.layout.projectDirectory.dir("../web"))
    configJson.set(configFile)
    outputDir.set(layout.buildDirectory.dir("generated/tvweb"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(bundleTvWeb, BundleTvWeb::outputDir)
    }
}

// Release signing guard (bravia-custom). With requireReleaseKey=true (gradle.properties) a release
// build never falls back to the debug key: it stops unless COUCHTV_KEYSTORE names the permanent key,
// its <keystore>.properties holds storePassword, keyPassword and keyAlias, and the key's certificate
// SHA-256 is releaseCertSha256. Debug builds and unit tests are not affected. -PallowTestSigning=true
// lets a deliberate test build (CI, a throwaway key) through, with a warning. No password is printed.
fun checkReleaseKey() {
    if ((project.findProperty("requireReleaseKey") as String?)?.toBoolean() != true) return
    if ((project.findProperty("allowTestSigning") as String?)?.toBoolean() == true) {
        logger.warn("allowTestSigning=true: this release build is NOT checked for the permanent signing key")
        return
    }
    val help = "Set COUCHTV_KEYSTORE to the fork's permanent key (C:/dev/tools/signing/couchtv-bravia.jks, " +
        "see README-SIGNING.md next to it). Release builds of this fork are never signed with the debug key."
    val key = fixedKey ?: throw GradleException("Release signing: COUCHTV_KEYSTORE is not set or not a file. $help")
    val props = fixedProps ?: throw GradleException("Release signing: ${key.name}.properties is missing next to the keystore. $help")
    val missing = listOf("storePassword", "keyPassword", "keyAlias").filter { props.getProperty(it).isNullOrBlank() }
    if (missing.isNotEmpty()) throw GradleException("Release signing: ${key.name}.properties lacks ${missing.joinToString()}. $help")
    val ks = try {
        KeyStore.getInstance(key, props.getProperty("storePassword").toCharArray())
    } catch (e: Exception) {
        throw GradleException("Release signing: ${key.name} could not be opened with the password in its .properties file (${e.javaClass.simpleName}).")
    }
    val cert = ks.getCertificate(props.getProperty("keyAlias"))
        ?: throw GradleException("Release signing: alias ${props.getProperty("keyAlias")} is not in ${key.name}.")
    val actual = MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString(":") { b -> "%02X".format(b) }
    val expected = (project.findProperty("releaseCertSha256") as String? ?: "").trim().uppercase()
    if (expected.isEmpty()) throw GradleException("Release signing: releaseCertSha256 is not set in gradle.properties.")
    if (actual != expected) throw GradleException("Release signing: ${key.name} has certificate SHA-256 $actual, expected $expected. Wrong key: nothing was signed.")
    logger.lifecycle("Release signing: permanent key ${key.name}, certificate SHA-256 $actual")
}
tasks.configureEach {
    if (name == "validateSigningRelease" || name == "packageRelease") doFirst { checkReleaseKey() }
}
