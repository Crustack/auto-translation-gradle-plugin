package io.github.philkes.auto.translation.plugin.task

import io.github.philkes.auto.translation.plugin.config.ProviderConfig
import io.github.philkes.auto.translation.plugin.provider.TranslationService
import io.github.philkes.auto.translation.plugin.provider.openai.OpenAITranslationService
import io.github.philkes.auto.translation.plugin.util.createTranslationService
import io.github.philkes.auto.translation.plugin.util.readableClassName
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskAction

abstract class ListSupportedLanguagesTask @Inject constructor() : DefaultTask() {

    /** Specify which translation provider to use and set it's options. */
    @get:Nested abstract val provider: Property<ProviderConfig>

    init {
        description = "List supported languages for configured translation provider"
        group = "translations"
    }

    @TaskAction
    fun listSupportedLanguages() {
        val provider = provider.get()
        if (!provider.isValid()) {
            throw GradleException(
                "Parameter 'providerConfig': ${provider.readableClassName} is invalid: ${provider.getConstraints()}"
            )
        }
        val translationService: TranslationService = createTranslationService(provider)
        if (translationService is OpenAITranslationService) {
            logger.warn(
                """
                    Selected provider is OpenAI, be aware that since this is not an explicit translation provider but rather
                     leverages system prompts and structured outputs, the result can be incomplete/wrong.
                """
                    .trimIndent()
            )
        }
        try {
            logger.lifecycle("Supported languages: ${translationService.getSupportedLanguages()}")
        } catch (e: Exception) {
            throw GradleException(
                "Listing supported languages for ${provider.readableClassName} failed: ${e.message}",
                e,
            )
        }
    }
}
