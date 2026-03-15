package io.github.philkes.auto.translation.plugin.provider.deepl

import com.deepl.api.DeepLClient
import com.deepl.api.TextResult
import com.deepl.api.TextTranslationOptions
import io.github.philkes.auto.translation.plugin.config.DeepLConfig
import io.github.philkes.auto.translation.plugin.provider.TextFormat
import io.github.philkes.auto.translation.plugin.provider.TranslationService
import io.github.philkes.auto.translation.plugin.util.isoCode
import java.util.*

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
    /**
     * See
     * [DeepL Supported Languages](https://developers.deepl.com/docs/getting-started/supported-languages)
     */
    override fun localeToApiString(locale: Locale, isSourceLang: Boolean): String {
        if(locale.language == Locale.ENGLISH.language && isSourceLang) {
            return Locale.ENGLISH.language
        }
        val isoCodeUppercase =
            when  {
                locale == Locale.SIMPLIFIED_CHINESE -> "ZH-HANS"
                locale == Locale.TRADITIONAL_CHINESE -> "ZH-HANT"
                locale in setOf(Locale.UK, Locale.US, ES_LATAM) -> locale.isoCode.uppercase()
                locale.language == "pt" -> "${locale.language}-${locale.country}".uppercase()
                else -> locale.isoCode.uppercase().substringBefore("-")
            }
        return isoCodeUppercase
    }

    companion object{
        private val ES_LATAM: Locale? = Locale.forLanguageTag("es-419")
    }
}
