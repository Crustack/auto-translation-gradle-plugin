package io.github.philkes.auto.translation.plugin.task

import io.github.philkes.auto.translation.plugin.provider.TextFormat
import io.github.philkes.auto.translation.plugin.provider.TranslationService
import io.github.philkes.auto.translation.plugin.util.StringsXmlHelper
import io.github.philkes.auto.translation.plugin.util.androidCode
import io.github.philkes.auto.translation.plugin.util.isoCode
import io.github.philkes.auto.translation.plugin.util.toIsoLocale
import java.io.File
import java.util.Locale
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger

/** Encapsulates translation of Android strings.xml resources. */
class StringsXmlTranslator(private val logger: Logger) {

    fun translate(
        resDirectory: File,
        service: TranslationService,
        srcLang: Locale,
        targetLanguages: Set<String>,
        excludeLanguages: Set<String> = emptySet(),
    ) {
        val valuesParentFolder = resDirectory
        val xml = StringsXmlHelper(logger)
        val targets = resolveTargets(valuesParentFolder, targetLanguages, true, excludeLanguages)
        if (targets.isEmpty()) {
            logger.lifecycle("No targetLanguages resolved. Nothing to translate.")
            return
        } else {
            logger.lifecycle("Translating for targetLanguages: ${targets.map { it.key.isoCode }}")
        }
        val sourceDir = File(valuesParentFolder, "values")
        val sourceStringsFile = File(sourceDir, "strings.xml")
        if (!sourceStringsFile.exists()) {
            throw GradleException(
                "Source file '${sourceStringsFile.absolutePath}' does not exist. Ensure 'resDirectory' is configured correctly (currently: '${sourceDir.absolutePath}')."
            )
        }
        val sourceStrings = xml.parse(sourceStringsFile)
        if (sourceStrings.isEmpty()) {
            logger.lifecycle(
                "No translatable <string> entries found in base strings.xml. Nothing to do."
            )
            return
        }
        targets.forEach { (locale, targetStringsFile) ->
            val existing =
                if (targetStringsFile.exists()) xml.parse(targetStringsFile) else emptyMap()
            val missingKeys = sourceStrings.keys.filter { it !in existing.keys }
            if (missingKeys.isEmpty()) {
                logger.lifecycle(
                    "[${locale.isoCode}] All strings already present (${existing.size}). Skipping."
                )
                return@forEach
            }

            val textsToTranslate = missingKeys.map { key -> sourceStrings[key] ?: "" }

            // Protect Android printf-style placeholders (e.g., %1$s)
            val maskedTexts = textsToTranslate.map { xml.maskPlaceholders(it) }

            val translatedMasked =
                try {
                    service.translateBatch(maskedTexts, TextFormat.HTML, srcLang, locale)
                } catch (e: Exception) {
                    logger.error("[${locale.isoCode}] Translation failed: ${e.message}", e)
                    return@forEach
                }

            // Restore placeholders
            val translated = translatedMasked.map { xml.unmaskPlaceholders(it) }

            val additions = missingKeys.zip(translated).toMap()
            val merged =
                LinkedHashMap<String, String>().apply {
                    putAll(existing)
                    additions.forEach { (k, v) -> this[k] = v }
                }
            val targetDir = targetStringsFile.parentFile
            if (!targetDir.exists()) targetDir.mkdirs()
            xml.write(targetStringsFile, merged)
            logger.lifecycle(
                "[${locale.isoCode}] Wrote ${additions.size} new translations. Total strings now: ${merged.size}."
            )
        }
    }

    fun resolveTargets(
        valuesParentFolder: File,
        languages: Set<String>,
        log: Boolean = true,
        excludeLanguages: Set<String> = emptySet(),
    ): Map<Locale, File> {
        fun localesFrom(codes: Collection<String>): List<Locale> =
            codes.sorted().mapNotNull { code ->
                val locale = code.toIsoLocale()
                if (locale == null) {
                    if (log)
                        logger.warn(
                            "Found non ISO Code values folder: '$code', will skip translation for it"
                        )
                    null
                } else {
                    locale
                }
            }
        if (languages.isNotEmpty()) {
            return localesFrom(languages).associateWith { locale ->
                File(valuesParentFolder, "values-${locale.androidCode}/strings.xml")
            }
        }
        if (log) logger.lifecycle("Auto. detecting targetLanguages...")
        val rawCodes =
            valuesParentFolder.listFiles()?.mapNotNull { dir ->
                if (dir.isDirectory && dir.name.startsWith("values-"))
                    dir.name.removePrefix("values-")
                else null
            } ?: emptyList()
        val codes = rawCodes.filterNot { it in excludeLanguages }
        if (log && excludeLanguages.isNotEmpty()) {
            logger.lifecycle("Excluding languages from autodetect: ${excludeLanguages}")
        }
        return localesFrom(codes).associateWith { locale ->
            File(valuesParentFolder, "values-${locale.androidCode}/strings.xml")
        }
    }
}
