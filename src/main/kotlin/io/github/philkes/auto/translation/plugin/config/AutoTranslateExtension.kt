package io.github.philkes.auto.translation.plugin.config

import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty

open class AutoTranslateExtension(
    private val objects: ObjectFactory,
    private val project: Project,
) {

    /**
     * Language of the source strings (`src/main/res/values/strings.xml`). Defaults to: `en`
     * (English)
     */
    val sourceLanguage = objects.property<String>().convention("en-US")

    /**
     * Language ISO-Codes (from `values-{iso-code}` folder names) to translate. By defaults detects
     * all available langauges from the `src/main/res` folder.
     */
    val targetLanguages = objects.setProperty<String>().convention(emptySet())

    /**
     * Languages to exclude when targetLanguages is not set. Useful to skip some autodetected
     * languages.
     */
    val excludeLanguages = objects.setProperty<String>().convention(emptySet())

    /** Provide Strings.xml translation configuration. */
    val translateStringsXml: Property<StringsXmlTranslationConfig> =
        objects.property(StringsXmlTranslationConfig::class.java)

    /** Provide Fastlane translation configuration. */
    val translateFastlane: Property<FastlaneTranslationConfig> =
        objects.property(FastlaneTranslationConfig::class.java)

    /** Specify which translation provider to use and set it's options. */
    val provider: Property<ProviderConfig> = objects.property(ProviderConfig::class.java)

    /** Create a [DeepLConfig] to use DeepL's translation api. */
    fun deepL(action: DeepLConfig.() -> Unit): DeepLConfig {
        val cfg = objects.newInstance(DeepLConfig::class.java)
        cfg.action()
        return cfg
    }

    /** Create a [AzureConfig] to use Azure's translation api. */
    fun azure(action: AzureConfig.() -> Unit): AzureConfig {
        val cfg = objects.newInstance(AzureConfig::class.java)
        cfg.action()
        return cfg
    }

    /** Create a [GoogleConfig] to use Google's translation api. */
    fun google(action: GoogleConfig.() -> Unit): GoogleConfig {
        val cfg = objects.newInstance(GoogleConfig::class.java)
        cfg.action()
        return cfg
    }

    /** Create a [LibreTranslateConfig] to use LibreTranslate api. */
    fun libreTranslate(action: LibreTranslateConfig.() -> Unit): LibreTranslateConfig {
        val cfg = objects.newInstance(LibreTranslateConfig::class.java)
        cfg.action()
        return cfg
    }

    /** Create an [OpenAIConfig] to use OpenAI's translation api. */
    fun openAI(action: OpenAIConfig.() -> Unit): OpenAIConfig {
        val cfg = objects.newInstance(OpenAIConfig::class.java)
        cfg.action()
        return cfg
    }

    /** Create a [StringsXmlTranslationConfig] to configure strings.xml translation. */
    fun translateStringsXml(
        action: StringsXmlTranslationConfig.() -> Unit
    ): StringsXmlTranslationConfig {
        val cfg = StringsXmlTranslationConfig.default(project)
        cfg.action()
        this.translateStringsXml.set(cfg)
        return cfg
    }

    /** Create a [FastlaneTranslationConfig] to configure Fastlane metadata translation. */
    fun translateFastlane(action: FastlaneTranslationConfig.() -> Unit): FastlaneTranslationConfig {
        val cfg =
            FastlaneTranslationConfig.default(project, sourceLanguage.get(), targetLanguages.get())
        cfg.action()
        this.translateFastlane.set(cfg)
        return cfg
    }
}
