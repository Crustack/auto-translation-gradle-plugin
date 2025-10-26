package io.github.philkes.auto.translation.plugin.config

import com.openai.models.ChatModel
import io.github.philkes.auto.translation.plugin.provider.openai.OpenAIOkHttpClientBuilder
import io.github.philkes.auto.translation.plugin.provider.openai.OpenAITranslationService
import javax.inject.Inject
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional

/** Configuration for OpenAI translation provider. */
abstract class OpenAIConfig @Inject constructor(objects: ObjectFactory) : ProviderConfig {

    /**
     * OpenAI specific options for the Translation completions.
     *
     * At least apiKey has to be set on [OpenAIOkHttpClientBuilder].
     */
    @get:Input
    val options: Property<OpenAIOkHttpClientBuilder> =
        objects.property(OpenAIOkHttpClientBuilder::class.java)

    /**
     * Optional overwrite model to use.
     *
     * Defaults: `gpt-4o-mini` ([ChatModel.GPT_4O_MINI])
     */
    @get:Input @get:Optional val model: Property<String> = objects.property(String::class.java)

    /**
     * Optional overwrite the used system message.
     *
     * Defaults to: [OpenAITranslationService.DEFAULT_TRANSLATION_SYSTEM_MESSAGE]
     */
    @get:Input
    @get:Optional
    val systemMessage: Property<String> = objects.property(String::class.java)

    init {
        model.convention(ChatModel.GPT_4O_MINI.toString())
        systemMessage.convention(OpenAITranslationService.DEFAULT_TRANSLATION_SYSTEM_MESSAGE)
    }

    @Internal
    override fun isValid(): Boolean {
        return options.isPresent
    }

    @Internal
    override fun getConstraints(): String {
        return "'options' must be set"
    }

    override fun toLogString(): String {
        return "OpenAIConfig(options=${options.orNull?.toString()}, model=${model.get()}, systemMessage=${systemMessage.get()})"
    }
}
