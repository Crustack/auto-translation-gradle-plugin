package io.github.philkes.auto.translation.plugin.util

import io.github.philkes.auto.translation.plugin.config.AzureConfig
import io.github.philkes.auto.translation.plugin.config.DeepLConfig
import io.github.philkes.auto.translation.plugin.config.GoogleConfig
import io.github.philkes.auto.translation.plugin.config.LibreTranslateConfig
import io.github.philkes.auto.translation.plugin.config.OpenAIConfig
import io.github.philkes.auto.translation.plugin.config.ProviderConfig
import io.github.philkes.auto.translation.plugin.provider.TestTranslationService
import io.github.philkes.auto.translation.plugin.provider.TranslationService
import io.github.philkes.auto.translation.plugin.provider.azure.AzureTranslationService
import io.github.philkes.auto.translation.plugin.provider.deepl.DeepLTranslationService
import io.github.philkes.auto.translation.plugin.provider.google.GoogleTranslationService
import io.github.philkes.auto.translation.plugin.provider.libretranslate.LibreTanslateTranslationService
import io.github.philkes.auto.translation.plugin.provider.openai.OpenAITranslationService
import java.io.File
import java.util.Locale
import org.gradle.api.GradleException
import org.gradle.api.Task

fun String.toIsoLocale(): Locale? {
    if (isBlank()) return null
    // Normalize like "en-US" → "en", "US"
    val parts = split('-', '_')
    val language = parts.getOrElse(0) { "" }
    // Android values have a "r" before the region/country ISO Code, e.g. zh-rCN
    val country = parts.getOrElse(1) { "" }.replace("r", "")
    val variant = parts.getOrElse(2) { "" }
    val locale = Locale(language, country, variant)
    return Locale.getAvailableLocales().find {
        it.language == locale.language &&
            it.country == locale.country &&
            it.variant == locale.variant
    }
}

val Any.readableClassName: String
    get() {
        return javaClass.simpleName.removeSuffix("_Decorated")
    }

private const val SYSTEM_PROPERTY_IN_TEST = "RUNNING_UNIT_TEST"

val isUnitTest: Boolean
    get() = System.getProperty(SYSTEM_PROPERTY_IN_TEST) == "true"

fun setIsUnitTest(value: Boolean) {
    System.setProperty(SYSTEM_PROPERTY_IN_TEST, "" + value)
}

val Locale.androidCode: String
    get() = "${language}${if(country.isNullOrBlank()) "" else "-r${country}"}"

val Locale.isoCode: String
    get() = ("${language}${"-${country}".takeIf { !country.isNullOrBlank() } ?: ""}")

val DOLLAR = "\$"

fun File.listTxtFilesRecursively(): List<File> {
    return walkTopDown().filter { it.isFile && it.extension == "txt" }.toList()
}

fun File.listStringsXmlFilesRecursively(): List<File> {
    return walkTopDown()
        .filter { it.isFile && it.nameWithoutExtension == "strings" && it.extension == "xml" }
        .toList()
}

fun Task.createTranslationService(provider: ProviderConfig): TranslationService {
    if (isUnitTest) {
        return TestTranslationService()
    }
    try {
        logger.debug("Provider: ${provider.toLogString()}")
        return when (provider) {
            is DeepLConfig -> DeepLTranslationService(provider)
            is GoogleConfig -> GoogleTranslationService(provider)
            is AzureConfig -> AzureTranslationService(provider)
            is LibreTranslateConfig -> LibreTanslateTranslationService(provider)
            is OpenAIConfig -> OpenAITranslationService(provider)
        }
    } catch (e: Exception) {
        throw GradleException(
            "Configuration of Client for ${provider.readableClassName} failed: ${e.message}",
            e,
        )
    }
}
