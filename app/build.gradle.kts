import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

/**
 * Copies the app's licence, and the licence and notice files that libraries ship inside their archives, into the
 * app's assets, so About › Licences can show them. The Apache License asks that these travel with the app.
 */
abstract class CollectLicencesTask : DefaultTask() {
    @get:InputFiles abstract val archives: ConfigurableFileCollection
    @get:InputFile abstract val appLicence: RegularFileProperty
    @get:OutputDirectory abstract val output: DirectoryProperty
    @get:javax.inject.Inject abstract val archiveOperations: ArchiveOperations

    @TaskAction
    fun collect() {
        val folder = output.get().dir("licences").asFile
        folder.deleteRecursively()
        folder.mkdirs()
        appLicence.get().asFile.copyTo(File(folder, "pollenplot-LICENSE.txt"))
        val wanted = setOf("LICENSE", "LICENSE.txt", "NOTICE", "NOTICE.txt", "THIRD_PARTY_NOTICE.txt")
        archives.files.forEach { archive ->
            // An archive such as litertlm-android-0.18.0.aar is named litertlm-android.
            val library = archive.nameWithoutExtension.replace(Regex("""-\d[^-]*$"""), "")
            archiveOperations.zipTree(archive).visit {
                val atTop = relativePath.segments.size == 1 || relativePath.pathString.startsWith("META-INF/")
                if (!isDirectory && atTop && name in wanted) copyTo(File(folder, "$library-${name.removeSuffix(".txt")}.txt"))
            }
        }
    }
}

/**
 * Downloads a file once and checks it against its published SHA-256, so a third-party model can be used without
 * committing it to the repository.
 */
abstract class VerifiedDownloadTask : DefaultTask() {
    @get:Input abstract val url: Property<String>
    @get:Input abstract val sha256: Property<String>
    @get:OutputFile abstract val output: RegularFileProperty

    @TaskAction
    fun download() {
        val target = output.get().asFile
        // Editing the build script makes Gradle run this again, so a file that is already right is kept.
        if (target.isFile && sha256Of(target) == sha256.get()) return
        target.parentFile.mkdirs()
        val partial = File(target.path + ".part")
        URI(url.get()).toURL().openStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
        val actual = sha256Of(partial)
        if (actual != sha256.get()) {
            partial.delete()
            throw GradleException("Checksum mismatch for ${url.get()}: expected ${sha256.get()}, got $actual")
        }
        Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * Takes a model out of its downloaded .tar.gz and puts it among the app's assets. The labels inside the model must
 * match the committed copy in assets/ml, which the tests read, so the two can never drift apart.
 */
abstract class ExtractModelTask : DefaultTask() {
    @get:InputFile abstract val archive: RegularFileProperty
    @get:Input abstract val entry: Property<String>
    @get:Input abstract val target: Property<String>
    @get:Input abstract val labelsEntry: Property<String>
    @get:InputFile abstract val expectedLabels: RegularFileProperty
    @get:OutputDirectory abstract val output: DirectoryProperty
    @get:javax.inject.Inject abstract val archiveOperations: ArchiveOperations

    @TaskAction
    fun extract() {
        val folder = output.get().asFile
        folder.deleteRecursively()
        val dest = File(folder, target.get()).apply { parentFile.mkdirs() }
        var found = false
        archiveOperations.tarTree(archiveOperations.gzip(archive.get().asFile)).visit {
            if (!isDirectory && name == entry.get()) {
                copyTo(dest)
                found = true
            }
        }
        if (!found) throw GradleException("${entry.get()} is not in ${archive.get().asFile.name}")
        val embedded = ZipFile(dest).use { zip -> zip.getInputStream(zip.getEntry(labelsEntry.get())).readBytes().decodeToString() }
        if (embedded != expectedLabels.get().asFile.readText()) {
            throw GradleException("The labels in ${dest.name} differ from ${expectedLabels.get().asFile.name}")
        }
    }
}

// AIY Plants V1 (Apache License 2.0): the fast, bundled plant classifier. Kaggle serves it as a .tar.gz.
val aiyPlantsArchive = layout.buildDirectory.file("third-party/aiy-plants-v1-3.tar.gz")
val downloadAiyPlants = tasks.register<VerifiedDownloadTask>("downloadAiyPlants") {
    url.set("https://www.kaggle.com/api/v1/models/google/aiy/tfLite/vision-classifier-plants-v1/3/download")
    sha256.set("7d9a44818d6685c51ba4f4ae398efa692f70853b16c593e03425bed92a7a50e7")
    output.set(aiyPlantsArchive)
}

// Release signing comes from environment variables so the keystore never lives in the repository.
val releaseKeystore: String? = System.getenv("PP_KEYSTORE_PATH")

android {
    namespace = "io.github.wkoller.pollenplot"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.wkoller.pollenplot"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("PP_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PP_KEY_ALIAS")
                keyPassword = System.getenv("PP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // ABI filters are set per build type: a build type's filters are added to defaultConfig's, not put in their place.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Phones are arm64; x86_64 is kept for emulators in debug builds.
            ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        }
        release {
            isMinifyEnabled = false
            ndk { abiFilters += listOf("arm64-v8a") }
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Models are memory-mapped from the APK, which needs them stored uncompressed.
        noCompress += "tflite"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("screenshots.dir", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
            it.maxHeapSize = "2g"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

room {
    schemaDirectory("$projectDir/schemas")
}

// The archives whose licence and notice files are shown in About › Licences. Libraries join this list as they are added.
val licenceArchives: Configuration = configurations.detachedConfiguration(
    dependencies.create("com.google.ai.edge.litert:litert:${libs.versions.litert.get()}@aar"),
).apply { isTransitive = false }

androidComponents {
    onVariants { variant ->
        val collectLicences = tasks.register<CollectLicencesTask>("collect${variant.name.replaceFirstChar { it.uppercase() }}Licences") {
            archives.from(licenceArchives)
            appLicence.set(rootProject.layout.projectDirectory.file("LICENSE"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(collectLicences, CollectLicencesTask::output)
        val extractModels = tasks.register<ExtractModelTask>("extract${variant.name.replaceFirstChar { it.uppercase() }}Models") {
            archive.set(downloadAiyPlants.flatMap { it.output })
            entry.set("3.tflite")
            target.set("ml/aiy_plants_v1.tflite")
            labelsEntry.set("probability-labels-en.txt")
            expectedLabels.set(layout.projectDirectory.file("src/main/assets/ml/aiy_plants_v1_labels.txt"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(extractModels, ExtractModelTask::output)
    }
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    // MaterialShapes are RoundedPolygons; material3 lists this library for run time only.
    implementation(libs.graphics.shapes)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.compose)
    implementation(libs.litert)
    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
