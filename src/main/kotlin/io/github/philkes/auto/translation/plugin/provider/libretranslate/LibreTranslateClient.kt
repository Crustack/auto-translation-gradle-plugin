package io.github.philkes.auto.translation.plugin.provider.libretranslate

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients

data class TranslateRequest(
    val q: String,
    val source: String,
    val target: String,
    val format: String?,
    @JsonProperty("api_key") val apiKey: String? = null,
)

data class TranslateResponse(val translatedText: String?, val error: String?)

data class LanguageResponse(val code: String?, val name: String?, val targets: List<String>?)

/** See [LibreTranslate API Usage](https://docs.libretranslate.com/guides/api_usage/) */
class LibreTranslateClient(private val baseUrl: String, private val apiKey: String?) {
    private val httpClient: CloseableHttpClient = HttpClients.createDefault()
    private val mapper = jacksonObjectMapper()

    fun translate(text: String, source: String, target: String): TranslateResponse {
        val url = "${baseUrl.removeSuffix("/")}/translate"
        val requestBody = TranslateRequest(text, source, target, "html", apiKey)
        val json = mapper.writeValueAsString(requestBody)

        val request =
            HttpPost(url).apply { entity = StringEntity(json, ContentType.APPLICATION_JSON) }

        return httpClient.execute(request).use { response ->
            val body = response.entity.content.bufferedReader().use { it.readText() }
            mapper.readValue(body, TranslateResponse::class.java)
        }
    }

    fun getSupportedLanguages(): List<LanguageResponse> {
        val url = "${baseUrl.removeSuffix("/")}/languages"
        val request = HttpGet(url)
        return httpClient.execute(request).use { response ->
            val body = response.entity.content.bufferedReader().use { it.readText() }
            mapper.readValue(body)
        }
    }
}
