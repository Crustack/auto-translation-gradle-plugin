package io.github.philkes.auto.translation.plugin.provider

/** Mocked TranslationService for test purposes. */
internal class TestTranslationService() : TranslationService() {

    override fun getSupportedLanguages(): List<String> {
        return listOf()
    }

    override fun translateBatch(
        texts: List<String>,
        textFormat: TextFormat,
        sourceLanguage: String,
        targetLanguage: String,
    ): List<String> {
        return texts.map { "$it [${targetLanguage.uppercase().replace('-', '_')}]" }
    }
}
