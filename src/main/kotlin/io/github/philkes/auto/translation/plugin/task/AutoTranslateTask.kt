package io.github.philkes.auto.translation.plugin.task

import io.github.philkes.auto.translation.plugin.config.FastlaneTranslationConfig
import io.github.philkes.auto.translation.plugin.config.ProviderConfig
import io.github.philkes.auto.translation.plugin.config.StringsXmlTranslationConfig
import io.github.philkes.auto.translation.plugin.provider.TranslationService
import io.github.philkes.auto.translation.plugin.util.createTranslationService
import io.github.philkes.auto.translation.plugin.util.readableClassName
import io.github.philkes.auto.translation.plugin.util.toIsoLocale
import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction

/** Translates all missing strings.xml Strings via external translation provider. */
abstract class AutoTranslateTask @Inject constructor(objects: ObjectFactory) : DefaultTask() {

    /**
     * Language ISO-Code of the source strings (`src/main/res/values/strings.xml`).
     *
     * Defaults to: `en-US` (English USA)
     */
    @get:Input
    @get:Optional
    val sourceLanguage: Property<String> = objects.property(String::class.java)

    /**
     * Language ISO-Codes (e.g.: `en-US`) to translate.
     *
     * Note that Android uses a different formatting for the country/region value in their `values`
     * folder naming: `values-{languageCode}-r{countryCode}`, whereas the ISO-Code uses:
     * `{languageCode-countrCode}`.
     *
     * By default detects all available targetLanguages from the
     * [StringsXmlTranslationConfig.resDirectory] `values` folders.
     */
    @get:Input
    @get:Optional
    val targetLanguages: SetProperty<String> = objects.setProperty(String::class.java)

    /**
     * Optionally exclude languages (ISO-Codes) that are present in the project but should not be
     * translated.
     */
    @get:Input
    @get:Optional
    val excludeLanguages: SetProperty<String> = objects.setProperty(String::class.java)

    /**
     * strings.xml translation configuration wrapper.
     *
     * By default strings.xml translation is enabled
     */
    @get:Nested
    @get:Optional
    val translateStringsXml: Property<StringsXmlTranslationConfig> =
        objects.property(StringsXmlTranslationConfig::class.java)

    /** Fastlane translation configuration wrapper. */
    @get:Nested
    @get:Optional
    val translateFastlane: Property<FastlaneTranslationConfig> =
        objects.property(FastlaneTranslationConfig::class.java)

    /** Specify which translation provider to use and set it's options. */
    @get:Nested
    val provider: Property<ProviderConfig> = objects.property(ProviderConfig::class.java)

    @get:Optional
    @get:OutputFiles
    val changedStringsXmls: ListProperty<File> = objects.listProperty(File::class.java)

    @get:Optional
    @get:OutputFiles
    val changedFastlaneFiles: ListProperty<File> = objects.listProperty(File::class.java)

    init {
        description = "Auto-translate Android strings.xml and fastlane files"
        group = "translations"
        // Defaults
        sourceLanguage.convention("en-US")
        targetLanguages.convention(emptySet<String>())
        excludeLanguages.convention(emptySet<String>())
        translateStringsXml.convention(StringsXmlTranslationConfig.default(project))
        translateFastlane.convention(
            FastlaneTranslationConfig.default(project, sourceLanguage.get(), targetLanguages.get())
        )
        changedStringsXmls.set(
            translateStringsXml.flatMap { stringsXmlConfig ->
                targetLanguages.flatMap { targetLangs ->
                    excludeLanguages.map { excludeLangs ->
                        StringsXmlTranslator(logger)
                            .resolveTargets(
                                stringsXmlConfig.resDirectory.asFile,
                                targetLangs,
                                false,
                                excludeLangs,
                            )
                            .values
                    }
                }
            }
        )
        changedFastlaneFiles.set(
            translateFastlane.flatMap { fastlaneConfig ->
                excludeLanguages.map { excludeLangs ->
                    val metadataDir = fastlaneConfig.metadataDirectory
                    val sourceLang = fastlaneConfig.sourceLanguage
                    val targetLangs = fastlaneConfig.targetLanguages
                    FastlaneTranslator(logger)
                        .resolveTargets(
                            metadataDir.asFile,
                            sourceLang.toIsoLocale()!!,
                            targetLangs,
                            excludeLangs,
                            false,
                        )
                }
            }
        )
    }

    @TaskAction
    fun translate() {
        val provider = provider.get()
        if (!provider.isValid()) {
            throw GradleException(
                "Parameter 'providerConfig': ${provider.readableClassName} is invalid: ${provider.getConstraints()}"
            )
        }
        val srcLang = sourceLanguage.get().toIsoLocale()
        if (srcLang == null) {
            throw GradleException("Found non ISO Code sourceLanguage: '${sourceLanguage.get()}'")
        }
        val translationService: TranslationService = createTranslationService(provider)
        val taskExcludeLanguages = excludeLanguages.get()
        // Strings.xml translation via wrapper config (enabled by default)

        logger.debug("translateStringsXml: {}", translateStringsXml.get())
        translateStringsXml.get().let { stringsXmlConfig ->
            val stringsEnabled = stringsXmlConfig.enabled
            if (stringsEnabled) {
                val resDir = stringsXmlConfig.resDirectory.asFile
                if (!resDir.exists()) {
                    throw GradleException(
                        "Provided translateStringsXml 'resDirectory' does not exist: $resDir"
                    )
                }
                val stringsXmlTargetLanguages = targetLanguages.get()
                logger.lifecycle(
                    "translateStringsXml: provider=${provider.readableClassName}, resDirectory=${resDir.absolutePath}, sourceLanguage=$srcLang, targetLanguages=${stringsXmlTargetLanguages}, excludeLanguages=$taskExcludeLanguages"
                )
                StringsXmlTranslator(logger)
                    .translate(
                        resDirectory = resDir,
                        service = translationService,
                        srcLang = srcLang,
                        targetLanguages = stringsXmlTargetLanguages,
                        excludeLanguages = taskExcludeLanguages,
                    )
            } else {
                logger.debug("Skipping translateStringsXml because it is disabled")
            }
        }

        // Fastlane metadata translation (optional via wrapper config)
        logger.debug("translateFastlane: {}", translateFastlane.get())
        translateFastlane.get().let { fastlaneConfig ->
            val fastlaneEnabled = fastlaneConfig.enabled
            if (fastlaneEnabled) {
                val metaDir = fastlaneConfig.metadataDirectory.asFile
                if (!metaDir.exists()) {
                    throw GradleException(
                        "Provided translateFastlane 'metadataDirectory' does not exist: $metaDir"
                    )
                }
                val fastlaneSrcLang = fastlaneConfig.sourceLanguage.toIsoLocale()
                if (fastlaneSrcLang == null) {
                    throw GradleException(
                        "Non ISO Code fastlaneConfig 'sourceLanguage' provided: '${fastlaneConfig.sourceLanguage}'"
                    )
                }
                val fastlaneTargetLanguages = fastlaneConfig.targetLanguages
                FastlaneTranslator(logger)
                    .translate(
                        metadataRoot = metaDir,
                        service = translationService,
                        srcLang = fastlaneSrcLang,
                        targetLanguages = fastlaneTargetLanguages,
                        excludeLanguages = taskExcludeLanguages,
                    )
            } else {
                logger.debug("Skipping translateFastlane because it is disabled")
            }
        }
        logger.debug(
            "changedStringsXml: {}",
            changedStringsXmls.get().map { it.relativeTo(project.projectDir) },
        )
        logger.debug(
            "changedFastlaneFiles: {}",
            changedFastlaneFiles.get().map { it.relativeTo(project.projectDir) },
        )
    }
}
