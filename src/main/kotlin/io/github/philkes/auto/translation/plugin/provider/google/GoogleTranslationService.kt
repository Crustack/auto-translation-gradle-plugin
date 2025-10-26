package io.github.philkes.auto.translation.plugin.provider.google

import com.google.cloud.translate.Translate
import io.github.philkes.auto.translation.plugin.config.GoogleConfig
import io.github.philkes.auto.translation.plugin.provider.TextFormat
import io.github.philkes.auto.translation.plugin.provider.TranslationService

class GoogleTranslationService(private val service: Translate, private val model: String?) :
    TranslationService() {

    constructor(
        config: GoogleConfig
    ) : this(config.options.get().toActualBuilder().build().service, config.model.orNull)

    override fun translateBatch(
        texts: List<String>,
        textFormat: TextFormat,
        sourceLanguage: String,
        targetLanguage: String,
    ): List<String> {
        var options =
            arrayOf(
                Translate.TranslateOption.sourceLanguage(sourceLanguage),
                Translate.TranslateOption.targetLanguage(targetLanguage),
                Translate.TranslateOption.format(
                    when (textFormat) {
                        TextFormat.TEXT -> "text"
                        TextFormat.HTML -> "html"
                    }
                ),
            )
        if (!model.isNullOrBlank()) {
            options += arrayOf(Translate.TranslateOption.model(model))
        }
        if (texts.isEmpty()) return emptyList()

        // Google Cloud Translation API limits translate(List<String>) to a maximum of 128 texts per
        // call.
        // Split into batches to respect this limit and preserve order in the aggregated result.
        val results = ArrayList<String>(texts.size)
        for (batch in texts.chunked(128)) {
            val translated = service.translate(batch, *options)
            translated.mapTo(results) { it.translatedText }
        }
        return results
    }

    override fun getSupportedLanguages(): List<String> {
        return service
            .listSupportedLanguages()
            .sortedBy { it.code }
            .map { language -> "${language.code} (${language.name})" }
    }
}
