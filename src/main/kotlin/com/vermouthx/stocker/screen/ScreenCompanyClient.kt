package com.vermouthx.stocker.screen

import com.intellij.openapi.diagnostic.Logger
import com.vermouthx.stocker.utils.StockerHttpClientPool
import com.vermouthx.stocker.utils.StockerJson
import com.vermouthx.stocker.utils.asJsonList
import com.vermouthx.stocker.utils.asJsonMap
import com.vermouthx.stocker.utils.asText
import org.apache.http.client.methods.HttpGet
import org.apache.http.util.EntityUtils
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

internal object ScreenCompanyClient {

    private val log = Logger.getInstance(javaClass)
    private val pool = StockerHttpClientPool(log)

    fun load(pluginCode: String): CompanyProfile {
        val code = pluginCode.trim().uppercase()
        val profile = runCatching { loadProfile(code) }.getOrElse {
            log.warn("Company profile failed for $code", it)
            CompanyProfile(code)
        }
        val managers = runCatching { loadManagers(code) }.getOrElse {
            log.warn("Managers failed for $code", it)
            emptyList()
        }
        return profile.copy(managers = managers)
    }

    private fun loadProfile(code: String): CompanyProfile {
        val body = get("https://emweb.securities.eastmoney.com/PC_HSF10/CompanySurvey/CompanySurveyAjax?code=$code")
        val info = StockerJson.root(body)["jbzl"].asJsonMap() ?: return CompanyProfile(code)
        return CompanyProfile(
            code = code,
            fullName = info["gsmc"].asText().orEmpty(),
            englishName = info["ywmc"].asText().orEmpty(),
            industry = info["sshy"].asText().orEmpty(),
            chairman = info["dsz"].asText().orEmpty(),
            legalPerson = info["frdb"].asText().orEmpty(),
            generalManager = info["zjl"].asText().orEmpty(),
            employees = info["glryrs"].asText().orEmpty(),
            capital = info["zczb"].asText().orEmpty(),
            website = info["gswz"].asText().orEmpty(),
            address = info["zcdz"].asText().orEmpty(),
            intro = info["gsjj"].asText().orEmpty().trim(),
            business = info["jyfw"].asText().orEmpty().trim(),
        )
    }

    private fun loadManagers(code: String): List<CompanyManager> {
        val body = get("https://emweb.securities.eastmoney.com/PC_HSF10/CompanyManagement/CompanyManagementAjax?code=$code")
        return StockerJson.root(body)["RptManagerList"].asJsonList().mapNotNull { item ->
            val row = item.asJsonMap() ?: return@mapNotNull null
            val name = row["xm"].asText().orEmpty()
            if (name.isBlank()) {
                return@mapNotNull null
            }
            CompanyManager(
                name = name,
                gender = row["xb"].asText().orEmpty(),
                age = row["nl"].asText().orEmpty(),
                education = row["xl"].asText().orEmpty(),
                title = row["zw"].asText().orEmpty(),
                resume = row["jj"].asText().orEmpty().trim(),
            )
        }.distinctBy { it.name + it.title }.take(8)
    }

    private fun get(url: String): String {
        val httpGet = HttpGet(url)
        httpGet.setHeader("Referer", "https://emweb.securities.eastmoney.com/")
        httpGet.setHeader("User-Agent", "Mozilla/5.0")
        httpGet.setHeader("Accept-Encoding", "gzip")
        httpGet.setHeader("Connection", "close")
        return pool.client().execute(httpGet).use { response ->
            val bytes = EntityUtils.toByteArray(response.entity)
            if (response.statusLine.statusCode !in 200..299) {
                error("HTTP ${response.statusLine.statusCode}")
            }
            val payload = if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
                GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
            } else {
                bytes
            }
            String(payload, StandardCharsets.UTF_8)
        }
    }
}

internal data class CompanyProfile(
    val code: String,
    val fullName: String = "",
    val englishName: String = "",
    val industry: String = "",
    val chairman: String = "",
    val legalPerson: String = "",
    val generalManager: String = "",
    val employees: String = "",
    val capital: String = "",
    val website: String = "",
    val address: String = "",
    val intro: String = "",
    val business: String = "",
    val managers: List<CompanyManager> = emptyList(),
)

internal data class CompanyManager(
    val name: String,
    val gender: String,
    val age: String,
    val education: String,
    val title: String,
    val resume: String,
)
