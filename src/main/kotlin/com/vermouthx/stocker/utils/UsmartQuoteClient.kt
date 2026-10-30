package com.vermouthx.stocker.utils

import com.intellij.openapi.diagnostic.Logger
import com.vermouthx.stocker.entities.StockerQuote
import com.vermouthx.stocker.enums.StockerMarketType
import com.vermouthx.stocker.settings.StockerSetting
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.util.EntityUtils
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import kotlin.math.roundToInt

/**
 * uSMART quote client.
 *
 * Login signs the JSON body with MD5withRSA. Quote calls sign
 * Authorization, channel, language, request id, time, and body in that order.
 * Phone and password are encrypted with the separate privacy public key.
 */
internal object UsmartQuoteClient {

    private const val LOGIN_URL = "https://open-jy.yxzq.com/user-server/open-api/login"
    private const val QUOTE_URL = "https://open-hz.yxzq.com:8443/quotes-openservice/api/v1/realtime"
    private val log = Logger.getInstance(javaClass)
    private val http = StockerHttpClientPool(log)
    private val requestSeq = AtomicInteger()
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val sourceTime = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    private val lock = Any()
    private var token: String = ""
    private var tokenUntil: Long = 0
    private var lastWarn: String = ""

    fun invalidate() {
        synchronized(lock) {
            token = ""
            tokenUntil = 0
            lastWarn = ""
        }
    }

    fun quotes(marketType: StockerMarketType, codes: List<String>): List<StockerQuote> {
        if (codes.isEmpty() || marketType == StockerMarketType.Crypto) {
            return emptyList()
        }
        val requested = LinkedHashMap<String, String>()
        codes.forEach { code ->
            requested[secuId(marketType, code).lowercase()] = code
        }
        val body = buildSecuBody(requested.keys)
        val payload = post(QUOTE_URL, body, authorize = true) ?: return emptyList()
        return parseQuotes(payload, requested)
    }

    fun reachable(marketType: StockerMarketType, code: String): Boolean {
        return quotes(marketType, listOf(code)).isNotEmpty()
    }

    private fun secuId(marketType: StockerMarketType, code: String): String {
        val upper = code.trim().uppercase()
        return when (marketType) {
            StockerMarketType.AShare -> when {
                upper.startsWith("SH") -> "sh${upper.removePrefix("SH")}"
                upper.startsWith("SZ") -> "sz${upper.removePrefix("SZ")}"
                upper.startsWith("BJ") -> "bj${upper.removePrefix("BJ")}"
                else -> "sh$upper"
            }
            StockerMarketType.HKStocks -> "hk$upper"
            StockerMarketType.USStocks -> "us$upper"
            StockerMarketType.Crypto -> upper
        }
    }

    private fun buildSecuBody(ids: Collection<String>): String {
        val items = ids.joinToString(",") { id -> "\"${jsonEscape(id)}\"" }
        return """{"secuIds":[$items]}"""
    }

    private fun post(url: String, body: String, authorize: Boolean): Map<String, Any?>? {
        val setting = StockerSetting.instance
        if (setting.usmartChannel.isBlank() || setting.usmartPrivateKey.isBlank()) {
            warn("uSMART channel or signing key is missing")
            return null
        }
        val authorization = if (authorize) {
            login(setting) ?: return null
        } else {
            ""
        }
        return execute(url, body, setting.usmartChannel, setting.usmartPrivateKey, authorization)
    }

    private fun login(setting: StockerSetting): String? {
        synchronized(lock) {
            if (token.isNotBlank() && System.currentTimeMillis() < tokenUntil) {
                return token
            }
            if (setting.usmartAccount.isBlank() || setting.usmartPassword.isBlank() || setting.usmartPublicKey.isBlank()) {
                warn("uSMART account, password, or privacy public key is missing")
                return null
            }
            val phone = rsaEncrypt(setting.usmartAccount, setting.usmartPublicKey) ?: return null
            val password = rsaEncrypt(setting.usmartPassword, setting.usmartPublicKey) ?: return null
            val body = if (setting.usmartAccount.contains("@")) {
                """{"password":"$password","phoneNumber":"$phone"}"""
            } else {
                """{"areaCode":${setting.usmartAreaCode},"password":"$password","phoneNumber":"$phone"}"""
            }
            val payload = execute(LOGIN_URL, body, setting.usmartChannel, setting.usmartPrivateKey, "") ?: return null
            val data = payload["data"] as? Map<*, *>
            val issued = stringField(payload, "token").ifBlank { stringField(data, "token") }
            if (issued.isBlank()) {
                warn("uSMART login did not return a token: ${stringField(payload, "msg")}")
                return null
            }
            token = issued
            tokenUntil = expiry(payload, data)
            lastWarn = ""
            return token
        }
    }

    private fun execute(
        url: String,
        body: String,
        channel: String,
        privateKeyPem: String,
        authorization: String,
    ): Map<String, Any?>? {
        val lang = "1"
        val requestId = nextRequestId()
        val time = (System.currentTimeMillis() / 1000).toString()
        val row = if (authorization.isBlank()) {
            body
        } else {
            authorization + channel + lang + requestId + time + body
        }
        val sign = sign(row, privateKeyPem) ?: return null
        val request = HttpPost(url)
        request.setHeader("Content-Type", "application/json; charset=utf-8")
        request.setHeader("X-Lang", lang)
        request.setHeader("X-Request-Id", requestId)
        request.setHeader("X-Channel", channel)
        request.setHeader("X-Time", time)
        request.setHeader("X-Sign", sign)
        if (authorization.isNotBlank()) {
            request.setHeader("Authorization", authorization)
        }
        request.entity = StringEntity(body, Charsets.UTF_8)
        return try {
            http.client().execute(request).use { response ->
                val text = EntityUtils.toString(response.entity, Charsets.UTF_8)
                val payload = StockerJson.root(text)
                val code = numberField(payload, "code")
                if (code != null && code.toLong() != 0L) {
                    if (code.toLong() == 806109L) {
                        invalidate()
                    }
                    warn("uSMART ${response.statusLine.statusCode} code=$code ${stringField(payload, "msg")}")
                    return null
                }
                payload
            }
        } catch (e: Exception) {
            warn("uSMART request failed: ${e.message}")
            null
        }
    }

    private fun parseQuotes(payload: Map<String, Any?>, requested: Map<String, String>): List<StockerQuote> {
        val data = payload["data"] as? Map<*, *> ?: payload
        val list = data["list"] as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val row = item as? Map<*, *> ?: return@mapNotNull null
            val market = stringField(row, "market")
            val symbol = stringField(row, "symbol")
            val code = requested["$market$symbol".lowercase()] ?: symbol.uppercase()
            val current = numberField(row, "latestPrice") ?: return@mapNotNull null
            val opening = numberField(row, "open") ?: current
            val previous = numberField(row, "preClose") ?: numberField(row, "close") ?: current
            val high = numberField(row, "high") ?: current
            val low = numberField(row, "low") ?: current
            val change = ((current - previous) * 100.0).roundToInt() / 100.0
            val percentage = if (previous == 0.0) 0.0 else ((current - previous) / previous * 10000.0).roundToInt() / 100.0
            StockerQuote(
                code = code,
                name = "",
                current = current,
                opening = opening,
                close = previous,
                low = low,
                high = high,
                change = change,
                percentage = percentage,
                updateAt = formatTime(stringField(row, "latestTime")),
            )
        }
    }

    private fun formatTime(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        if (digits.length < 14) {
            return raw
        }
        return try {
            LocalDateTime.parse(digits.substring(0, 14), sourceTime).format(timeFormatter)
        } catch (_: Exception) {
            raw
        }
    }

    private fun expiry(payload: Map<String, Any?>, data: Map<*, *>?): Long {
        val raw = numberField(payload, "expiration") ?: numberField(data, "expiration") ?: 0.0
        val millis = when {
            raw <= 0.0 -> System.currentTimeMillis() + 6 * 60 * 60 * 1000
            raw < 10_000_000_000.0 -> raw.toLong() * 1000
            else -> raw.toLong()
        }
        return millis.coerceAtMost(System.currentTimeMillis() + 12 * 60 * 60 * 1000)
    }

    private fun sign(row: String, pem: String): String? {
        return try {
            val signature = Signature.getInstance("MD5withRSA")
            signature.initSign(privateKey(pem))
            signature.update(row.toByteArray(Charsets.UTF_8))
            Base64.getUrlEncoder().encodeToString(signature.sign())
        } catch (e: Exception) {
            warn("uSMART signing failed: ${e.message}")
            null
        }
    }

    private fun rsaEncrypt(plain: String, pem: String): String? {
        return try {
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, publicKey(pem))
            Base64.getUrlEncoder().encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
        } catch (e: Exception) {
            warn("uSMART field encryption failed: ${e.message}")
            null
        }
    }

    private fun privateKey(pem: String): PrivateKey {
        val pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY")
        val bytes = decodePem(pem)
        val spec = if (pkcs1) PKCS8EncodedKeySpec(pkcs1ToPkcs8(bytes)) else PKCS8EncodedKeySpec(bytes)
        return KeyFactory.getInstance("RSA").generatePrivate(spec)
    }

    private fun publicKey(pem: String): PublicKey {
        return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(decodePem(pem)))
    }

    private fun decodePem(pem: String): ByteArray {
        val body = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        return Base64.getDecoder().decode(body)
    }

    private fun pkcs1ToPkcs8(pkcs1: ByteArray): ByteArray {
        val version = byteArrayOf(0x02, 0x01, 0x00)
        val algorithm = byteArrayOf(
            0x30, 0x0d, 0x06, 0x09, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(),
            0xf7.toByte(), 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00,
        )
        val inner = version + algorithm + der(0x04, pkcs1)
        return der(0x30, inner)
    }

    private fun der(tag: Int, content: ByteArray): ByteArray {
        val length = content.size
        val len = when {
            length < 128 -> byteArrayOf(length.toByte())
            length < 256 -> byteArrayOf(0x81.toByte(), length.toByte())
            else -> byteArrayOf(0x82.toByte(), (length shr 8).toByte(), length.toByte())
        }
        return byteArrayOf(tag.toByte()) + len + content
    }

    private fun nextRequestId(): String {
        val suffix = String.format("%06d", requestSeq.updateAndGet { (it + 1) % 1_000_000 })
        return System.currentTimeMillis().toString() + suffix
    }

    private fun stringField(source: Map<*, *>?, key: String): String {
        return source?.get(key)?.toString().orEmpty()
    }

    private fun numberField(source: Map<*, *>?, key: String): Double? {
        val value = source?.get(key) ?: return null
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }

    private fun jsonEscape(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private fun warn(message: String) {
        if (message == lastWarn) {
            return
        }
        lastWarn = message
        log.warn(message)
    }
}
