plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // ONNX Runtime has the same Java API on Android and the desktop. The app brings the Android runtime, and the tests
    // run the desktop one, so the real model is checked here on the JVM.
    compileOnly(libs.onnxruntime)
    testImplementation(libs.onnxruntime)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
}

val bgeModel = layout.buildDirectory.file("third-party/bge-small-en-v1.5-quantized.onnx")
val downloadBge = tasks.register<VerifiedDownloadTask>("downloadBge") {
    url.set(providers.gradleProperty("pollenplot.bge.url"))
    sha256.set(providers.gradleProperty("pollenplot.bge.sha256"))
    output.set(bgeModel)
}

tasks.test {
    val vocab = rootProject.layout.projectDirectory.file("app/src/main/assets/ml/bge_small_en_vocab.txt")
    inputs.file(downloadBge.flatMap { it.output })
    inputs.file(vocab)
    systemProperty("bge.model", bgeModel.get().asFile.absolutePath)
    systemProperty("bge.vocab", vocab.asFile.absolutePath)
    val fixtures = rootProject.layout.projectDirectory.dir("tools/models/fixtures")
    inputs.dir(fixtures)
    systemProperty("biocap.fixtures", fixtures.asFile.absolutePath)
}

// The accurate pack, once it is published and pinned in gradle.properties, so its image encoder is run here and
// compared with the vectors the export made. Until then BioCapTest is skipped.
if (providers.gradleProperty("pollenplot.biocap.sha256").getOrElse("").isNotBlank()) {
    val downloadBiocap = tasks.register<VerifiedDownloadTask>("downloadBiocap") {
        url.set(providers.gradleProperty("pollenplot.biocap.url"))
        sha256.set(providers.gradleProperty("pollenplot.biocap.sha256"))
        output.set(layout.buildDirectory.file("third-party/biocap-pack.zip"))
    }
    val unpackBiocap = tasks.register<Sync>("unpackBiocap") {
        from(zipTree(downloadBiocap.flatMap { it.output }))
        into(layout.buildDirectory.dir("third-party/biocap"))
    }
    tasks.test {
        dependsOn(unpackBiocap)
        inputs.dir(unpackBiocap.map { it.destinationDir })
        systemProperty("biocap.dir", unpackBiocap.get().destinationDir.absolutePath)
    }
}
