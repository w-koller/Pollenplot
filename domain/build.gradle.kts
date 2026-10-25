plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // The knowledge base and the semantic export are JSON. This library is pure Kotlin, so the module stays free of Android.
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

tasks.test {
    // KnowledgeBaseFilesTest reads the knowledge base the app ships, so it is checked on every test run.
    systemProperty("kb.dir", rootProject.layout.projectDirectory.dir("app/src/main/assets/kb").asFile.absolutePath)
    inputs.dir(rootProject.layout.projectDirectory.dir("app/src/main/assets/kb"))
    // The fast classifier's labels, to check how many of them the knowledge base can name.
    systemProperty("ml.dir", rootProject.layout.projectDirectory.dir("app/src/main/assets/ml").asFile.absolutePath)
    inputs.dir(rootProject.layout.projectDirectory.dir("app/src/main/assets/ml"))
}
