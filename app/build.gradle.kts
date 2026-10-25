plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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

// The archives whose licence and notice files are shown in About › Licences. Libraries join this list as they are added.
val licenceArchives: Configuration = configurations.detachedConfiguration().apply { isTransitive = false }

androidComponents {
    onVariants { variant ->
        val collectLicences = tasks.register<CollectLicencesTask>("collect${variant.name.replaceFirstChar { it.uppercase() }}Licences") {
            archives.from(licenceArchives)
            appLicence.set(rootProject.layout.projectDirectory.file("LICENSE"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(collectLicences, CollectLicencesTask::output)
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
