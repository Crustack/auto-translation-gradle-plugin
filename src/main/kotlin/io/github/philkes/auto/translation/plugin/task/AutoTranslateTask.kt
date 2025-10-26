package io.github.philkes.auto.translation.plugin.task

import io.github.philkes.auto.translation.plugin.config.FastlaneTranslationConfig
import io.github.philkes.auto.translation.plugin.config.ProviderConfig
import io.github.philkes.auto.translation.plugin.config.StringsXmlTranslationConfig
import io.github.philkes.auto.translation.plugin.provider.TranslationService
import io.github.philkes.auto.translation.plugin.util.createTranslationService
import io.github.philkes.auto.translation.plugin.util.listStringsXmlFilesRecursively
import io.github.philkes.auto.translation.plugin.util.listTxtFilesRecursively
import io.github.philkes.auto.translation.plugin.util.readableClassName
import io.github.philkes.auto.translation.plugin.util.toIsoLocale
import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.logging.LogLevel
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
    @get:Input @get:Optional abstract val sourceLanguage: Property<String>

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
    @get:Input @get:Optional abstract val targetLanguages: SetProperty<String>

    /**
     * Optionally exclude languages (ISO-Codes) that are present in the project but should not be
     * translated.
     */
    @get:Input @get:Optional abstract val excludeLanguages: SetProperty<String>

    /**
     * strings.xml translation configuration wrapper.
     *
     * By default strings.xml translation is enabled
     */
    @get:Nested
    @get:Optional
    abstract val translateStringsXml: Property<StringsXmlTranslationConfig>

    /** Fastlane translation configuration wrapper. */
    @get:Nested @get:Optional abstract val translateFastlane: Property<FastlaneTranslationConfig>

    /** Specify which translation provider to use and set it's options. */
    @get:Nested abstract val provider: Property<ProviderConfig>

    @get:Optional @get:OutputFiles abstract val changedStringsXmls: ListProperty<File>

    @get:Optional @get:OutputFiles abstract val changedFastlaneFiles: ListProperty<File>

    init {
        description = "Auto-translate Android strings.xml and fastlane files"
        group = "translations"
        // Defaults
        sourceLanguage.convention("en-US")
        targetLanguages.convention(emptySet<String>())
        excludeLanguages.convention(emptySet<String>())
        translateStringsXml.convention(StringsXmlTranslationConfig.default(objects, project, this))
        translateFastlane.convention(FastlaneTranslationConfig.default(objects, project, this))
        changedStringsXmls.convention(
            translateStringsXml.flatMap {
                it.resDirectory.map { resDir -> resDir.asFile.listStringsXmlFilesRecursively() }
            }
        )
        changedFastlaneFiles.convention(
            translateFastlane.flatMap {
                it.metadataDirectory.map { metadataDirectory ->
                    metadataDirectory.asFile.listTxtFilesRecursively()
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
        logger.lifecycle("translateStringsXml: ${translateStringsXml.get()}")
        // Strings.xml translation via wrapper config (enabled by default)
        translateStringsXml.orNull?.let { stringsXmlConfig ->
            val stringsEnabled = stringsXmlConfig.enabled.orNull ?: true
            if (stringsEnabled) {
                val resDir =
                    (stringsXmlConfig.resDirectory.orNull
                            ?: project.layout.projectDirectory.dir("src/main/res"))
                        .asFile
                if (!resDir.exists()) {
                    throw GradleException(
                        "Provided translateStringsXml 'resDirectory' does not exist: $resDir"
                    )
                }
                val stringsXmlTargetLanguages = targetLanguages.get()
                logger.log(
                    LogLevel.LIFECYCLE,
                    "translateStringsXml: provider=${provider.readableClassName}, resDirectory=${resDir.absolutePath}, sourceLanguage=$srcLang, targetLanguages=${stringsXmlTargetLanguages}, excludeLanguages=$taskExcludeLanguages",
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
        translateFastlane.orNull?.let { fastlaneConfig ->
            val fastlaneEnabled = fastlaneConfig.enabled.orNull ?: false
            if (fastlaneEnabled) {
                val metaDir =
                    (fastlaneConfig.metadataDirectory.orNull
                            ?: project.layout.projectDirectory.dir("fastlane/metadata/android"))
                        .asFile
                if (!metaDir.exists()) {
                    throw GradleException(
                        "Provided translateFastlane 'metadataDirectory' does not exist: $metaDir"
                    )
                }
                val fastlaneSrcLang =
                    (fastlaneConfig.sourceLanguage.orNull ?: sourceLanguage.get()).toIsoLocale()
                if (fastlaneSrcLang == null) {
                    throw GradleException(
                        "Non ISO Code fastlaneConfig 'sourceLanguage' provided: '${fastlaneConfig.sourceLanguage.orNull}'"
                    )
                }
                val fastlaneTargetLanguages =
                    fastlaneConfig.targetLanguages.orNull ?: targetLanguages.get()
                logger.log(
                    LogLevel.LIFECYCLE,
                    "translateFastlane: provider=${provider.readableClassName}, metadataDirectory=${metaDir}, sourceLanguage=$srcLang, targetLanguages=$fastlaneTargetLanguages, excludeLanguages=$taskExcludeLanguages",
                )
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
    }
}
