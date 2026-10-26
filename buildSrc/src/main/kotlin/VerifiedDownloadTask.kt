import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

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
