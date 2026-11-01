import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
}

// One engine is selected per build; -PreaderEngine=kuromoji creates a rollback build.
val readerEngine = providers.gradleProperty("readerEngine").orElse("sudachi").get()
require(readerEngine in setOf("sudachi", "kuromoji")) { "readerEngine must be sudachi or kuromoji" }
val sudachiTrial = providers.gradleProperty("sudachiTrial").orNull == "true"
val dictionarySpec = Properties().apply {
    rootProject.file("gradle/sudachi-dictionary.properties").inputStream().use { load(it) }
}

abstract class PrepareSudachiDictionary : DefaultTask() {
    @get:Input abstract val downloadUrl: Property<String>
    @get:Input abstract val archiveSha256: Property<String>
    @get:Input abstract val dictionarySha256: Property<String>
    @get:Input abstract val dictionaryBytes: Property<Long>
    @get:Input abstract val offline: Property<Boolean>
    @get:InputFile @get:Optional abstract val suppliedArchive: RegularFileProperty
    @get:OutputFile abstract val downloadedArchive: RegularFileProperty
    @get:OutputDirectory abstract val assetsDirectory: DirectoryProperty

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @TaskAction fun prepare() {
        val archive = downloadedArchive.get().asFile
        if (!archive.isFile || sha256(archive) != archiveSha256.get()) {
            archive.parentFile.mkdirs()
            val temporary = File(archive.parentFile, archive.name + ".tmp")
            try {
                val supplied = suppliedArchive.orNull?.asFile
                if (supplied != null) supplied.copyTo(temporary, overwrite = true)
                else {
                    check(!offline.get()) { "Sudachi dictionary is not cached. Build online once or supply -PsudachiArchive=<official ZIP>." }
                    logger.lifecycle("Downloading pinned Sudachi dictionary (build time only)")
                    val connection = URI(downloadUrl.get()).toURL().openConnection().apply {
                        connectTimeout = 30_000
                        readTimeout = 120_000
                    }
                    connection.getInputStream().buffered().use { input ->
                        temporary.outputStream().buffered().use { output -> input.copyTo(output) }
                    }
                }
                check(sha256(temporary) == archiveSha256.get()) { "Sudachi archive SHA-256 mismatch" }
                Files.move(temporary.toPath(), archive.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally {
                temporary.delete()
            }
        }
        val destination = File(assetsDirectory.get().asFile, "sudachi").apply { mkdirs() }
        ZipFile(archive).use { zip ->
            // Extract only explicit filenames; never use archive paths as output paths.
            for (name in listOf("system_full.dic", "LEGAL", "LICENSE-2.0.txt")) {
                val entries = zip.entries().asSequence().filter { it.name.substringAfterLast('/') == name }.toList()
                check(entries.size == 1) { "Expected exactly one $name in Sudachi archive" }
                val output = File(destination, name)
                zip.getInputStream(entries.single()).use { input ->
                    output.outputStream().buffered().use { target -> input.copyTo(target) }
                }
            }
        }
        val dictionary = File(destination, "system_full.dic")
        check(dictionary.length() == dictionaryBytes.get() && sha256(dictionary) == dictionarySha256.get()) {
            "Sudachi dictionary integrity check failed"
        }
    }
}

val prepareSudachiDictionary = tasks.register<PrepareSudachiDictionary>("prepareSudachiDictionary") {
    downloadUrl.set(dictionarySpec.getProperty("url"))
    archiveSha256.set(dictionarySpec.getProperty("archiveSha256"))
    dictionarySha256.set(dictionarySpec.getProperty("dictionarySha256"))
    dictionaryBytes.set(dictionarySpec.getProperty("dictionaryBytes").toLong())
    offline.set(gradle.startParameter.isOffline)
    suppliedArchive.set(providers.gradleProperty("sudachiArchive").map { layout.projectDirectory.file(it) })
    downloadedArchive.set(layout.buildDirectory.file("downloads/sudachi/${dictionarySpec.getProperty("version")}.zip"))
    assetsDirectory.set(layout.buildDirectory.dir("generated/sudachiAssets"))
}

androidComponents.onVariants { variant ->
    if (readerEngine == "sudachi") {
        variant.sources.assets?.addGeneratedSourceDirectory(prepareSudachiDictionary) { it.assetsDirectory }
    }
}

android {
    namespace = "com.smyddevstudio.kanako"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.smyddevstudio.kanako"
        minSdk = 26
        buildConfigField("String", "READER_ENGINE", "\"$readerEngine\"")
        buildConfigField("String", "SUDACHI_DICTIONARY_VERSION", "\"${dictionarySpec.getProperty("version")}\"")
        buildConfigField("String", "SUDACHI_DICTIONARY_SHA256", "\"${dictionarySpec.getProperty("dictionarySha256")}\"")
        buildConfigField("long", "SUDACHI_DICTIONARY_BYTES", "${dictionarySpec.getProperty("dictionaryBytes")}L")
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (sudachiTrial) {
        sourceSets["test"].kotlin.directories += listOf("src/sudachiTrial/shared", "src/sudachiTrial/unit")
        sourceSets["androidTest"].kotlin.directories += listOf("src/sudachiTrial/shared", "src/sudachiTrial/device")
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    packaging {
        resources {
            excludes += "META-INF/CONTRIBUTORS.md"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/NOTICE.md"
        }
    }
}

tasks.withType<Test>().configureEach {
    dependsOn(prepareSudachiDictionary)
    val dictionaryFile = prepareSudachiDictionary.flatMap { it.assetsDirectory.file("sudachi/system_full.dic") }
    inputs.file(dictionaryFile).withPropertyName("sudachiDictionary")
    systemProperty("sudachi.dictionary", dictionaryFile.get().asFile.absolutePath)
    if (sudachiTrial) {
        val reportFile = layout.buildDirectory.file("reports/sudachiTrial/jvm.txt")
        outputs.file(reportFile)
        systemProperty("sudachi.report", reportFile.get().asFile.absolutePath)
    }
}

dependencies {
    implementation(libs.sudachi)
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.mlkit.vision)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.coil.compose)
    implementation(libs.converter.moshi)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.logging.interceptor)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.play.services.location)
    implementation(libs.mlkit.text.recognition.japanese)
    implementation(libs.retrofit)
    testImplementation(libs.androidx.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    "ksp"(libs.androidx.room.compiler)
    "ksp"(libs.moshi.kotlin.codegen)
    implementation(libs.kuromoji.ipadic)
}