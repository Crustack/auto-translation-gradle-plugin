package io.github.philkes.auto.translation.plugin.task

import io.github.philkes.auto.translation.plugin.provider.TextFormat
import io.github.philkes.auto.translation.plugin.provider.TranslationService
import io.github.philkes.auto.translation.plugin.util.listTxtFilesRecursively
import io.github.philkes.auto.translation.plugin.util.toIsoLocale
import java.io.File
import java.util.Locale
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger

/** Encapsulates translation of Fastlane metadata text files. */
class FastlaneTranslator(private val logger: Logger) {

    // Represents a mapping between a source Fastlane file and its target output location
    private data class FileMapping(val source: File, val relative: String, val out: File)

    /**
     * Resolve target Fastlane files that will be created (missing outputs) for the given config.
     * Returns a flat list of output files under the metadataRoot that are currently missing and
     * would be written by translate(). If configuration is invalid or nothing to do, returns empty.
     */
    fun resolveTargets(
        metadataRoot: File,
        srcLang: Locale,
        targetLanguages: Set<String>,
        excludeLanguages: Set<String> = emptySet(),
        log: Boolean = true,
    ): List<File> {
        if (!metadataRoot.exists()) {
            if (log)
                logger.lifecycle(
                    "Fastlane metadata directory does not exist: ${metadataRoot.absolutePath}. Skipping."
                )
            return emptyList()
        }
        val allLocaleDirs = metadataRoot.listFiles()?.filter { it.isDirectory } ?: emptyList()
        if (allLocaleDirs.isEmpty()) {
            if (log) logger.lifecycle("No folders found in Fastlane metadata directory. Skipping.")
            return emptyList()
        }
        val sourceDir = allLocaleDirs.findLocaleFolder(srcLang)
        if (sourceDir == null) {
            if (log)
                logger.lifecycle(
                    "No Fastlane source folder matching sourceLanguage=${srcLang} found. Skipping Fastlane translation."
                )
            return emptyList()
        }
        val sourceFiles = sourceDir.listTxtFilesRecursively()
        if (sourceFiles.isEmpty()) {
            if (log)
                logger.lifecycle(
                    "No .txt files found in Fastlane source folder '${sourceDir.name}'. Nothing to translate."
                )
            return emptyList()
        }

        // Determine targets: either explicit or from metadata folders
        val targetCodes: List<String> =
            if (targetLanguages.isNotEmpty()) targetLanguages.sorted()
            else allLocaleDirs.map { it.name }.filterNot { it in excludeLanguages }.sorted()
        val targets: List<String> =
            targetCodes.mapNotNull { code ->
                val loc = code.toIsoLocale()
                if (loc == null) {
                    if (log) logger.warn("[Fastlane:$code] Not a valid ISO locale. Skipping.")
                    null
                } else {
                    code
                }
            }
        val outputFiles = ArrayList<File>()
        targets.forEach { targetCode ->
            val mappings =
                sourceFiles.map { src ->
                    val relative = src.relativeTo(sourceDir).path
                    val outFile = File(metadataRoot, "$targetCode/$relative")
                    outFile
                }
            outputFiles += mappings
        }
        return outputFiles
    }

    /**
     * Translate all .txt files from the Fastlane source locale folder into target locale folders.
     * - metadataRoot: root directory containing locale subfolders like "en-US"
     * - service: translation service to use
     * - srcLang: source locale
     * - targetLanguages: explicit target folder codes (when empty, autodetect from directories)
     * - excludeLanguages: optional list of folder codes to exclude when autodetecting
     */
    fun translate(
        metadataRoot: File,
        service: TranslationService,
        srcLang: Locale,
        targetLanguages: Set<String>,
        excludeLanguages: Set<String> = emptySet(),
    ) {
        if (!metadataRoot.exists()) {
            throw GradleException(
                "Fastlane metadata directory does not exist: ${metadataRoot.absolutePath}. Skipping."
            )
        }
        val allLocaleDirs = metadataRoot.listFiles()?.filter { it.isDirectory } ?: emptyList()
        if (allLocaleDirs.isEmpty()) {
            logger.lifecycle("No folders found in Fastlane metadata directory. Skipping.")
            return
        }
        val sourceDir = allLocaleDirs.findLocaleFolder(srcLang)
        if (sourceDir == null) {
            logger.lifecycle(
                "No Fastlane source folder matching sourceLanguage=${srcLang} found. Skipping Fastlane translation."
            )
            return
        }
        val sourceCode = sourceDir.name
        val sourceFiles = sourceDir.listTxtFilesRecursively()
        if (sourceFiles.isEmpty()) {
            logger.lifecycle(
                "No .txt files found in Fastlane source folder '${sourceDir.name}'. Nothing to translate."
            )
            return
        }

        // Determine targets: either explicit or from metadata folders
        val targetCodes: List<String> =
            if (targetLanguages.isNotEmpty()) targetLanguages.sorted()
            else allLocaleDirs.map { it.name }.filterNot { it in excludeLanguages }.sorted()

        val targets: List<Pair<Locale, String>> =
            targetCodes
                .mapNotNull { code ->
                    val loc = code.toIsoLocale()
                    if (loc == null) {
                        logger.warn("[Fastlane:$code] Not a valid ISO locale. Skipping.")
                        null
                    } else {
                        Pair(loc, code)
                    }
                }
                .filter { (_, code) -> code != sourceCode }

        // For each target, translate only missing files and write
        targets.forEach { (targetLocale, targetCode) ->
            // Build list of source -> target file mappings
            val mappings =
                sourceFiles.map { src ->
                    val relative = src.relativeTo(sourceDir).path
                    val outFile = File(metadataRoot, "$targetCode/$relative")
                    FileMapping(src, relative, outFile)
                }
            val missing = mappings.filter { (_, _, out) -> !out.exists() }
            if (missing.isEmpty()) {
                logger.lifecycle(
                    "[Fastlane:$targetCode] All ${sourceFiles.size} .txt files already exist. Skipping."
                )
                return@forEach
            }

            val contents = missing.map { (src, _, _) -> src.readText() }
            val translated: List<String> =
                try {
                    service.translateBatch(contents, TextFormat.TEXT, srcLang, targetLocale)
                } catch (e: Exception) {
                    logger.error("[Fastlane:$targetCode] Translation failed: ${e.message}", e)
                    return@forEach
                }
            // Write outputs for missing files only
            translated.forEachIndexed { index, text ->
                val (_, relative, outFile) = missing[index]
                outFile.parentFile?.mkdirs()
                outFile.writeText(text)
            }
            logger.lifecycle(
                "[Fastlane:$targetCode] Wrote ${translated.size} missing translated .txt files (out of ${sourceFiles.size})."
            )
        }
    }

    private fun List<File>.findLocaleFolder(srcLang: Locale): File? {
        return firstOrNull { dir -> dir.name.toIsoLocale()?.equals(srcLang) == true }
    }
}
