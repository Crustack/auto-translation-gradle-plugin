package io.github.philkes.auto.translation.plugin.provider.deepl

import com.deepl.api.DeepLClient
import com.deepl.api.TextResult
import com.deepl.api.TextTranslationOptions
import io.github.philkes.auto.translation.plugin.config.DeepLConfig
import io.github.philkes.auto.translation.plugin.provider.TextFormat
import io.github.philkes.auto.translation.plugin.provider.TranslationService
import io.github.philkes.auto.translation.plugin.util.isoCode
import java.util.Locale

class DeepLTranslationService(
    private val client: DeepLClient,
    private val options: TextTranslationOptions,
) : TranslationService() {

    constructor(
        config: DeepLConfig
    ) : this(
        DeepLClient(config.authKey.get()),
        config.options.get().toActualBuilder().apply {
            if (tagHandling.isNullOrBlank()) {
                setTagHandling("xml")
            }
        },
    )

    override fun translateBatch(
        texts: List<String>,
        textFormat: TextFormat,
        sourceLanguage: String,
        targetLanguage: String,
    ): List<String> {
        // TODO: is there text format option?
        val results: List<TextResult> =
            client.translateText(texts, sourceLanguage, targetLanguage, options)
        return results.map { it.text }
    }

    override fun getSupportedLanguages(): List<String> {
        return client.targetLanguages
            .sortedBy { it.code }
            .map { language -> "${language.code} (${language.name})" }
    }

    /**
     * See
     * [DeepL Supported Languages](https://developers.deepl.com/docs/getting-started/supported-languages)
     */
    override fun localeToApiString(locale: Locale, isSourceLang: Boolean): String {
        val isoCodeUppercase =
            when (locale) {
                Locale.SIMPLIFIED_CHINESE -> "ZH-HANS"
                Locale.TRADITIONAL_CHINESE -> "ZH-HANT"
                else -> locale.isoCode.uppercase()
            }
        return if (isSourceLang) isoCodeUppercase.substringBefore("-") else isoCodeUppercase
    }
}
