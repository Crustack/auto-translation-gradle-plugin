package io.github.philkes.auto.translation.plugin.provider.libretranslate

import io.github.philkes.auto.translation.plugin.config.LibreTranslateConfig
import io.github.philkes.auto.translation.plugin.provider.TextFormat
import io.github.philkes.auto.translation.plugin.provider.TranslationService
import io.github.philkes.auto.translation.plugin.util.isoCode
import java.util.Locale

class LibreTanslateTranslationService(private val client: LibreTranslateClient) :
    TranslationService() {

    constructor(
        config: LibreTranslateConfig
    ) : this(LibreTranslateClient(config.baseUrl.get(), config.apiKey.orNull))

    override fun translateBatch(
        texts: List<String>,
        textFormat: TextFormat,
        sourceLanguage: String,
        targetLanguage: String,
    ): List<String> {
        // TODO: is there text format option?
        return texts.map { text ->
            val response = client.translate(text, sourceLanguage, targetLanguage)
            response.error?.let { error -> throw IllegalArgumentException(error) }
            response.translatedText!!
        }
    }

    override fun getSupportedLanguages(): List<String> {
        return client
            .getSupportedLanguages()
            .sortedBy { it.code }
            .map { language -> "${language.code} (${language.name})" }
    }

    /**
     * see [Libretranslate API Languages](https://docs.libretranslate.com/api/operations/languages/)
     */
    override fun localeToApiString(locale: Locale, isSourceLang: Boolean): String {
        return when (locale) {
            Locale.SIMPLIFIED_CHINESE -> "zh-Hans"
            Locale.TRADITIONAL_CHINESE -> "zh-Hant"
            else -> if (locale.language == "pt") locale.isoCode else locale.language
        }
    }
}
