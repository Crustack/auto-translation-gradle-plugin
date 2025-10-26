package io.github.philkes.auto.translation.plugin.provider

import io.github.philkes.auto.translation.plugin.util.isoCode
import java.util.Locale

abstract class TranslationService {

    fun translateBatch(
        texts: List<String>,
        textFormat: TextFormat,
        sourceLanguage: Locale,
        targetLanguage: Locale,
    ): List<String> {
        return translateBatch(
            texts,
            textFormat,
            localeToApiString(sourceLanguage, true),
            localeToApiString(targetLanguage, false),
        )
    }

    /** List all supported languages of the Translation Provider */
    abstract fun getSupportedLanguages(): List<String>

    /** Translate a batch of texts; implementations may optimize roundtrips. */
    protected abstract fun translateBatch(
        texts: List<String>,
        textFormat: TextFormat,
        sourceLanguage: String,
        targetLanguage: String,
    ): List<String>

    /** Convert the given `Locale` to the API specific String */
    protected open fun localeToApiString(locale: Locale, isSourceLang: Boolean): String {
        return locale.isoCode
    }
}
