package io.github.philkes.auto.translation.plugin.provider.azure

import com.azure.ai.translation.text.TextTranslationClient
import com.azure.ai.translation.text.models.TextType
import com.azure.ai.translation.text.models.TranslateOptions
import io.github.philkes.auto.translation.plugin.config.AzureConfig
import io.github.philkes.auto.translation.plugin.provider.TextFormat
import io.github.philkes.auto.translation.plugin.provider.TranslationService

class AzureTranslationService(private val service: TextTranslationClient) : TranslationService() {

    constructor(config: AzureConfig) : this(config.options.get().toActualBuilder().buildClient())

    override fun translateBatch(
        texts: List<String>,
        textFormat: TextFormat,
        sourceLanguage: String,
        targetLanguage: String,
    ): List<String> {
        val translateOptions: TranslateOptions =
            TranslateOptions()
                .setSourceLanguage(sourceLanguage)
                .addTargetLanguage(targetLanguage)
                .setTextType(
                    when (textFormat) {
                        TextFormat.TEXT -> TextType.PLAIN
                        TextFormat.HTML -> TextType.HTML
                    }
                )
        val translations = service.translate(texts, translateOptions)
        return translations.map { it.translations.first().text }
    }

    override fun getSupportedLanguages(): List<String> {
        return service.supportedLanguages.translation
            .map { (key, language) -> "$key (${language.name})" }
            .sorted()
    }
}
