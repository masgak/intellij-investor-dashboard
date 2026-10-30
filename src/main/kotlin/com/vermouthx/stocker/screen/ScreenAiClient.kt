package com.vermouthx.stocker.screen

import com.vermouthx.stocker.utils.StockerJson
import com.vermouthx.stocker.utils.asJsonList
import com.vermouthx.stocker.utils.asJsonMap
import com.vermouthx.stocker.utils.asText
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import com.vermouthx.stocker.utils.StockerHttpClientPool
import org.apache.http.util.EntityUtils
import java.nio.charset.StandardCharsets

internal object ScreenAiClient {

    fun writeReason(
        baseUrl: String,
        apiKey: String,
        model: String,
        thinking: String,
        prompt: String,
    ): String {
        if (apiKey.isBlank() || model.isBlank()) {
            error("AI is not configured")
        }
        val endpoint = baseUrl.trim().trimEnd('/') + "/chat/completions"
        val withThinking = thinking != "off"
        return try {
            complete(endpoint, apiKey, model, thinking, prompt, withThinking)
        } catch (e: AiRequestException) {
            if (withThinking && e.status == 400) {
                complete(endpoint, apiKey, model, thinking, prompt, false)
            } else {
                throw e
            }
        }
    }

    private fun complete(
        endpoint: String,
        apiKey: String,
        model: String,
        thinking: String,
        prompt: String,
        sendThinking: Boolean,
    ): String {
        val timeout = when (thinking) {
            "high" -> 180_000
            "medium" -> 90_000
            "low" -> 45_000
            else -> 40_000
        }
        val config = RequestConfig.custom()
            .setConnectTimeout(10_000)
            .setConnectionRequestTimeout(10_000)
            .setSocketTimeout(timeout)
            .build()
        val body = requestBody(model, prompt, thinking, sendThinking)
        StockerHttpClientPool.directClient().setDefaultRequestConfig(config).build().use { client ->
            val post = HttpPost(endpoint)
            post.setHeader("Authorization", "Bearer $apiKey")
            post.setHeader("Content-Type", "application/json")
            post.entity = StringEntity(body, StandardCharsets.UTF_8)
            client.execute(post).use { response ->
                val text = EntityUtils.toString(response.entity, StandardCharsets.UTF_8)
                val status = response.statusLine.statusCode
                if (status !in 200..299) {
                    throw AiRequestException(status, text.take(280))
                }
                return extractContent(text)
            }
        }
    }

    private fun requestBody(model: String, prompt: String, thinking: String, sendThinking: Boolean): String {
        val thinkingFields = if (sendThinking && thinking != "off") {
            ""","reasoning_effort":${thinking.json()},"enable_thinking":true"""
        } else {
            ""","enable_thinking":false"""
        }
        return buildString {
            append("""{"model":${model.json()},"temperature":0.3,"messages":[""")
            append("""{"role":"system","content":${SYSTEM.json()}},""")
            append("""{"role":"user","content":${prompt.json()}}""")
            append("]")
            append(thinkingFields)
            append("}")
        }
    }

    private fun extractContent(body: String): String {
        val message = StockerJson.root(body)["choices"].asJsonList()
            .firstOrNull()
            .asJsonMap()
            ?.get("message")
            .asJsonMap()
        val content = message?.get("content").asText().orEmpty().trim()
        if (content.isNotEmpty()) {
            return content
        }
        val reasoning = message?.get("reasoning_content").asText().orEmpty().trim()
        if (reasoning.isNotEmpty()) {
            return reasoning
        }
        error("The model returned an empty answer")
    }

    private fun String.json(): String {
        val out = StringBuilder(length + 8)
        out.append('"')
        for (c in this) {
            when (c) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (c.code < 0x20) out.append("\\u%04x".format(c.code)) else out.append(c)
            }
        }
        out.append('"')
        return out.toString()
    }

    private const val SYSTEM = "你是A股研究助手。只根据用户给出的筛选数字、策略规则和公司资料写买卖理由。不要编造财报、股价或人物经历。先给结论，再写依据和主要风险。用简体中文，分成短段落。"
}

internal class AiRequestException(val status: Int, detail: String) : IllegalStateException("HTTP $status $detail")
