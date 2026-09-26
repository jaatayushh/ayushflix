package com.lagradost.cloudstream3.desktop.builtin


import android.net.Uri
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.max
import java.security.SecureRandom
import kotlin.random.Random
class MovieBoxProvider : MainAPI() {
    companion object {
        var context: android.content.Context? = null
        const val RENDER_API_BASE = "https://jaatayushh.onrender.com/api"
        const val UPSTREAM_API_URL = "https://api3.aoneroom.com"

        val API_KEYS = listOf(
            "ayush_live_dev_7f8a9b1c2d3e4f506172",
            "ayush_live_7c36d4541d7c501fdd3967f4e18303c9",
            "ayush_live_3061430492d6b6d9d58345434f251ae5",
            "ayush_live_0adabf9608754f5fb117c90b3b6957d5"
        )
        private val keyIndex = java.util.concurrent.atomic.AtomicInteger(0)
        fun getApiKey(): String {
            val idx = Math.abs(keyIndex.getAndIncrement() % API_KEYS.size)
            return API_KEYS[idx]
        }
    }
    override var mainUrl = "https://api3.aoneroom.com"
    override var name = "MovieBox"
    override val hasMainPage = true
    override var lang = "hi"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val secretKeyDefault = base64Decode("NzZpUmwwN3MweFNOOWpxbUVXQXQ3OUVCSlp1bElRSXNWNjRGWnIyTw==")
    private val secretKeyAlt = base64Decode("WHFuMm5uTzQxL0w5Mm8xaXVYaFNMSFRiWHZZNFo1Wlo2Mm04bVNMQQ==")

        private fun md5(input: ByteArray): String {
        return MessageDigest.getInstance("MD5").digest(input)
            .joinToString("") { "%02x".format(it) }
    }

    private fun reverseString(input: String): String = input.reversed()

    private fun generateXClientToken(hardcodedTimestamp: Long? = null): String {
        val timestamp = (hardcodedTimestamp ?: System.currentTimeMillis()).toString()
        val reversed = reverseString(timestamp)
        val hash = md5(reversed.toByteArray())
        return "$timestamp,$hash"
    }

    private val random = SecureRandom()

    fun generateDeviceId(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    val deviceId = generateDeviceId()

    fun getUserRegionInfo(): Pair<String, String> {
        var region = "IN"
        var timezone = "Asia/Calcutta"
        try {
            val tz = java.util.TimeZone.getDefault()
            if (tz != null && !tz.id.isNullOrBlank()) {
                timezone = tz.id
            }
        } catch (_: Exception) {}

        if (timezone.contains("Calcutta", ignoreCase = true) ||
            timezone.contains("Kolkata", ignoreCase = true) ||
            timezone.contains("India", ignoreCase = true) ||
            timezone.contains("IST", ignoreCase = true)) {
            return Pair("IN", timezone)
        }

        try {
            val loc = java.util.Locale.getDefault()
            val country = loc.country?.trim()?.uppercase()
            if (!country.isNullOrBlank() && country.length == 2) {
                return Pair(country, timezone)
            }
        } catch (_: Exception) {}

        return Pair("IN", timezone)
    }

    private fun getUserAgent(): String {
        val (region, _) = getUserRegionInfo()
        val pkg = if (region == "IN") "com.community.mbox.in" else "com.community.oneroom"
        val locale = if (region == "IN") "en_IN" else "en_$region"
        return "$pkg/50020126 (Linux; U; Android 14; $locale; Pixel 8; Build/UD1A.230803.041; Cronet/145.0.7582.0)"
    }

    private fun getClientInfoJson(): String {
        val (region, timezone) = getUserRegionInfo()
        val pkg = if (region == "IN") "com.community.mbox.in" else "com.community.oneroom"
        return """{"package_name":"$pkg","version_name":"4.0.02.0831.03","version_code":50020126,"os":"android","os_version":"14","install_ch":"official","device_id":"$deviceId","install_store":"official","gaid":"1b2212c1-dadf-43c3-a0c8-bd6ce48ae22d","brand":"Google","model":"Pixel 8","system_language":"en","net":"NETWORK_WIFI","region":"$region","timezone":"$timezone","sp_code":"","X-Play-Mode":"1","X-Idle-Data":"1","X-Family-Mode":"1","X-Content-Mode":"1"}""".trimIndent()
    }

    private val adultPattern = Regex(
        """(?i)\b(porn|porno|xxx|erotic|erotica|hentai|nsfw|nudity|onlyfans|softcore|hardcore|fetish|ullu|kooku|primeplay|hotshots|besharams|voovi|moodx|jav|playboy|lust\s*stories|rabbit\s*movies|hunters\s*app|chikooflix|redprime|sexy\s*scenes|adult|18\+|sex)\b"""
    )

    private fun isAdultContent(title: String?, genre: String? = null, description: String? = null): Boolean {
        if (title != null && adultPattern.containsMatchIn(title)) return true
        if (genre != null && adultPattern.containsMatchIn(genre)) return true
        if (description != null && Regex("""(?i)\b(porn|porno|xxx|hentai|nsfw|onlyfans|ullu|kooku|primeplay|hotshots|besharams|voovi|moodx|jav|playboy)\b""").containsMatchIn(description)) return true
        return false
    }

    data class BrandModel(val brand: String, val model: String)

    private val brandModels = mapOf(
        "Samsung" to listOf("SM-S918B", "SM-A528B", "SM-M336B"),
        "Xiaomi" to listOf("2201117TI", "M2012K11AI", "Redmi Note 11"),
        "OnePlus" to listOf("LE2111", "CPH2449", "IN2023"),
        "Google" to listOf("Pixel 6", "Pixel 7", "Pixel 8"),
        "Realme" to listOf("RMX3085", "RMX3360", "RMX3551")
    )

    fun randomBrandModel(): BrandModel {
        val brand = brandModels.keys.random()
        val model = brandModels[brand]!!.random()
        return BrandModel(brand, model)
    }

    @Volatile
    private var cachedGuestToken: String? = null
    @Volatile
    private var tokenLastFetchMs: Long = 0L

    private suspend fun fetchAnonymousToken(forceRefresh: Boolean = false): String? {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedGuestToken != null && (now - tokenLastFetchMs) < 3600000L) {
            return cachedGuestToken
        }
        val tokenDomains = listOf(
            mainUrl,
            "https://apig.inmoviebox.com",
            "https://api.inmoviebox.com"
        )
        for (domain in tokenDomains) {
            try {
                val xClientToken = generateXClientToken(now)
                val rankingUrl = "$domain/wefeed-mobile-bff/tab/ranking-list?tabId=0&categoryType=4516404531735022304&page=1&perPage=1"
                val xTrSignature = generateXTrSignature(
                    method = "GET",
                    accept = "application/json",
                    contentType = "application/json",
                    url = rankingUrl,
                    body = null,
                    useAltKey = false,
                    hardcodedTimestamp = now
                )
                val headers = mapOf(
                    "user-agent" to getUserAgent(),
                    "accept" to "application/json",
                    "content-type" to "application/json",
                    "x-client-token" to xClientToken,
                    "x-tr-signature" to xTrSignature,
                    "x-client-info" to getClientInfoJson(),
                    "x-client-status" to "0"
                )
                val res = app.get(rankingUrl, headers = headers, timeout = 5)
                val xUserHeader = res.headers["x-user"]
                if (!xUserHeader.isNullOrBlank()) {
                    val mapper = jacksonObjectMapper()
                    val tok = mapper.readTree(xUserHeader)?.get("token")?.asText()
                    if (!tok.isNullOrBlank()) {
                        cachedGuestToken = tok
                        tokenLastFetchMs = now
                        return tok
                    }
                }
            } catch (_: Exception) {
                // Try next domain
            }
        }
        return cachedGuestToken
    }

    private fun buildCanonicalString(
        method: String,
        accept: String?,
        contentType: String?,
        url: String,
        body: String?,
        timestamp: Long
    ): String {
        val parsed = Uri.parse(url)
        val path = parsed.path ?: ""
        
        // Build query string with sorted parameters (if any)
        val query = if (parsed.queryParameterNames.isNotEmpty()) {
            parsed.queryParameterNames.sorted().joinToString("&") { key ->
                parsed.getQueryParameters(key).joinToString("&") { value ->
                    "$key=$value"  // Don't URL encode here - Python doesn't do it
                }
            }
        } else ""
        
        val canonicalUrl = if (query.isNotEmpty()) "$path?$query" else path

        val bodyBytes = body?.toByteArray(Charsets.UTF_8)
        val bodyHash = if (bodyBytes != null) {
            val trimmed = if (bodyBytes.size > 102400) bodyBytes.copyOfRange(0, 102400) else bodyBytes
            md5(trimmed)
        } else ""

        val bodyLength = bodyBytes?.size?.toString() ?: ""
        return "${method.uppercase()}\n" +
                "${accept ?: ""}\n" +
                "${contentType ?: ""}\n" +
                "$bodyLength\n" +
                "$timestamp\n" +
                "$bodyHash\n" +
                canonicalUrl
    }

    private fun generateXTrSignature(
        method: String,
        accept: String?,
        contentType: String?,
        url: String,
        body: String? = null,
        useAltKey: Boolean = false,
        hardcodedTimestamp: Long? = null
    ): String {
        val timestamp = hardcodedTimestamp ?: System.currentTimeMillis()
        val canonical = buildCanonicalString(method, accept, contentType, url, body, timestamp)
        val secret = if (useAltKey) secretKeyAlt else secretKeyDefault
        val secretBytes = base64DecodeArray(secret)

        val mac = Mac.getInstance("HmacMD5")
        mac.init(SecretKeySpec(secretBytes, "HmacMD5"))
        val signature = mac.doFinal(canonical.toByteArray(Charsets.UTF_8))
        val signatureB64 = base64Encode(signature)

        return "$timestamp|2|$signatureB64"
    }

    private fun extractPolicyResource(cookie: String): String? {
        return try {
            val match = Regex("""CloudFront-Policy=([^;]+)""").find(cookie) ?: return null
            val rawB64 = match.groupValues.getOrNull(1) ?: return null
            val rem = rawB64.length % 4
            val padded = if (rem > 0) rawB64 + "=".repeat(4 - rem) else rawB64
            val normalized = padded.replace('-', '+').replace('_', '/')
            val decodedBytes = base64DecodeArray(normalized)
            val json = String(decodedBytes, Charsets.UTF_8)
            val root = mapper.readTree(json)
            root["Statement"]?.get(0)?.get("Resource")?.asText()
        } catch (_: Exception) {
            null
        }
    }

    override val mainPage: List<MainPageData>
        get() = buildRegionalMainPage()

    private fun buildRegionalMainPage(): List<MainPageData> {
        val (region, _) = getUserRegionInfo()
        val list = mutableListOf<Pair<String, String>>()

        if (region == "IN") {
            list.add("4516404531735022304" to "Trending in India")
            list.add("414907768299210008" to "Bollywood")
            list.add("3859721901924910512" to "South Indian (Hindi Dub)")
            list.add("5692654647815587592" to "Trending in Cinema")
            list.add("4903182713986896328" to "Indian Drama")
            list.add("8019599703232971616" to "Hollywood")
            list.add("4741626294545400336" to "Top Series This Week")
            list.add("1|1;country=India" to "Indian (Movies)")
            list.add("1|2;country=India" to "Indian (Series)")
            list.add("1|1;classify=Hindi dub;country=United States" to "Hollywood in Hindi (Movies)")
            list.add("1|2;classify=Hindi dub;country=United States" to "Hollywood in Hindi (Series)")
            list.add("8434602210994128512" to "Anime")
            list.add("1|1;classify=Hindi dub;genre=Action" to "Action (Hindi Dub)")
            list.add("1|1;classify=Hindi dub;genre=Comedy" to "Comedy (Hindi Dub)")
            list.add("1|1;classify=Hindi dub;genre=Crime" to "Crime & Thriller")
            list.add("7878715743607948784" to "Korean Drama")
            list.add("8788126208987989488" to "Chinese Drama")
            list.add("3910636007619709856" to "Western TV")
            list.add("5177200225164885656" to "Turkish Drama")
            list.add("1|1" to "All Movies")
            list.add("1|2" to "All Series")
        } else {
            val regionDisplayName = try {
                val loc = java.util.Locale.Builder().setRegion(region).build()
                loc.displayCountry.ifBlank { "You" }
            } catch (_: Exception) { "You" }

            list.add("4516404531735022304" to "Trending in $regionDisplayName")
            list.add("5692654647815587592" to "Trending in Cinema")
            list.add("8019599703232971616" to "Hollywood & Global Hits")
            list.add("4741626294545400336" to "Top Series This Week")
            list.add("3910636007619709856" to "Western TV")
            list.add("1|1;country=United States" to "Popular US Movies")
            list.add("1|2;country=United States" to "Popular US Series")
            list.add("8434602210994128512" to "Anime")
            list.add("1|1;genre=Action" to "Action Movies")
            list.add("1|1;genre=Comedy" to "Comedy Movies")
            list.add("1|1;genre=Crime" to "Crime & Mystery")
            list.add("7878715743607948784" to "Korean Drama")
            list.add("8788126208987989488" to "Chinese Drama")
            list.add("414907768299210008" to "Bollywood Spotlight")
            list.add("1|1;country=Korea" to "South Korean Movies")
            list.add("1|1;country=Japan" to "Japanese Cinema")
            list.add("1|1;country=Philippines" to "Philippines")
            list.add("1|1;country=Nigeria" to "Nollywood")
            list.add("1|1" to "All Movies")
            list.add("1|2" to "All Series")
        }
        return list.map { (data, name) -> MainPageData(name = name, data = data) }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        try {
            val tab = if (request.name.contains("series", true) || request.name.contains("drama", true) || request.data.contains("|2")) "series" else "all"
            val apiKey = getApiKey()
            val rUrl = "$RENDER_API_BASE/home?tab=$tab&api_key=$apiKey"
            val rResp = app.get(rUrl, headers = mapOf("X-API-Key" to apiKey), timeout = 12L).text
            val rRoot = jacksonObjectMapper().readTree(rResp)
            val rows = rRoot.get("rows")
            val renderList = mutableListOf<SearchResponse>()
            if (rows != null && rows.isArray && rows.size() > 0) {
                val rowIndex = (request.name.hashCode().let { kotlin.math.abs(it) } % rows.size())
                val targetRow = rows.get(rowIndex) ?: rows.get(0)
                val items = targetRow?.get("items")
                if (items != null && items.isArray && items.size() > 0) {
                    for (item in items) {
                        val rTitle = item["title"]?.asText() ?: continue
                        val rId = item["id"]?.asText() ?: continue
                        val rPoster = item["poster"]?.asText()
                        val typeStr = item["type"]?.asText() ?: "movie"
                        val rType = if (typeStr.equals("series", true)) TvType.TvSeries else TvType.Movie
                        renderList.add(
                            newMovieSearchResponse(name = rTitle, url = rId, type = rType) {
                                this.posterUrl = rPoster
                                this.score = Score.from10(item["rating"]?.asText())
                            }
                        )
                    }
                    if (renderList.isNotEmpty()) {
                        return newHomePageResponse(listOf(HomePageList(request.name, renderList)))
                    }
                }
            }
        } catch (_: Exception) {}

        val perPage = 15
        val url = if (request.data.contains("|")) "$UPSTREAM_API_URL/wefeed-mobile-bff/subject-api/list" else "$UPSTREAM_API_URL/wefeed-mobile-bff/tab/ranking-list?tabId=0&categoryType=${request.data}&page=$page&perPage=$perPage"

        val data1 = request.data

        val mainParts = data1.substringBefore(";").split("|")
        val pg = mainParts.getOrNull(0)?.toIntOrNull() ?: 1
        val channelId = mainParts.getOrNull(1)

        val options = mutableMapOf<String, String>()
        data1.substringAfter(";", "")
            .split(";")
            .forEach {
                val (k, v) = it.split("=").let { p ->
                    p.getOrNull(0) to p.getOrNull(1)
                }
                if (!k.isNullOrBlank() && !v.isNullOrBlank()) {
                    options[k] = v
                }
            }

        val classify = options["classify"] ?: "All"
        val country  = options["country"] ?: "All"
        val year     = options["year"] ?: "All"
        val genre    = options["genre"] ?: "All"
        val sort     = options["sort"] ?: "ForYou"

        val jsonBody = """{"page":$pg,"perPage":$perPage,"channelId":"$channelId","classify":"$classify","country":"$country","year":"$year","genre":"$genre","sort":"$sort"}"""

        val token = fetchAnonymousToken()
        val xClientToken = generateXClientToken()
        val xTrSignature = generateXTrSignature("POST", "application/json", "application/json; charset=utf-8", url , jsonBody)

        val getxTrSignature = generateXTrSignature("GET", "application/json", "application/json", url)

        val headers = mutableMapOf(
            "user-agent" to getUserAgent(),
            "accept" to "application/json",
            "content-type" to "application/json",
            "connection" to "keep-alive",
            "x-client-token" to xClientToken,
            "x-tr-signature" to xTrSignature,
            "x-client-info" to getClientInfoJson(),
            "x-client-status" to "0",
            "x-play-mode" to "2" // Optional, if needed for specific API behavior
        )

        val getheaders = mutableMapOf(
            "user-agent" to getUserAgent(),
            "accept" to "application/json",
            "content-type" to "application/json",
            "connection" to "keep-alive",
            "x-client-token" to xClientToken,
            "x-tr-signature" to getxTrSignature,
            "x-client-info" to getClientInfoJson(),
            "x-client-status" to "0",
        )

        if (!token.isNullOrBlank()) {
            headers["Authorization"] = "Bearer $token"
            getheaders["Authorization"] = "Bearer $token"
        }

        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
        var response = if (request.data.contains("|")) app.post(url, headers = headers, requestBody = requestBody) else app.get(url, headers = getheaders)

        if (response.code == 441 || response.code == 401) {
            val refreshed = fetchAnonymousToken(forceRefresh = true)
            if (!refreshed.isNullOrBlank()) {
                headers["Authorization"] = "Bearer $refreshed"
                getheaders["Authorization"] = "Bearer $refreshed"
                response = if (request.data.contains("|")) app.post(url, headers = headers, requestBody = requestBody) else app.get(url, headers = getheaders)
            }
        }

        val xUserHeader = response.headers["x-user"]
        if (!xUserHeader.isNullOrBlank()) {
            try {
                val mapper = jacksonObjectMapper()
                val tok = mapper.readTree(xUserHeader)?.get("token")?.asText()
                if (!tok.isNullOrBlank()) {
                    cachedGuestToken = tok
                    tokenLastFetchMs = System.currentTimeMillis()
                }
            } catch (_: Exception) {}
        }

            val responseBody = response.body.string()
            // Use Jackson to parse the new API response structure
            val data = try {
                val mapper = jacksonObjectMapper()
                val root = mapper.readTree(responseBody)
                val items = root["data"]?.get("items") ?: root["data"]?.get("subjects") ?: return newHomePageResponse(emptyList())
                items.mapNotNull { item ->
                    val title = item["title"]?.asText()?.substringBefore("[") ?: return@mapNotNull null
                    val genre = item["genre"]?.asText()
                    val desc = item["description"]?.asText()
                    if (isAdultContent(title, genre, desc)) return@mapNotNull null
                    val id = item["subjectId"]?.asText() ?: return@mapNotNull null
                    val coverImg = item["cover"]?.get("url")?.asText()
                    val subjectType = item["subjectType"]?.asInt() ?: 1
                    val type = when (subjectType) {
                        1 -> TvType.Movie
                        2 -> TvType.TvSeries
                        else -> TvType.Movie
                    }
                    newMovieSearchResponse(
                        name = title,
                        url = id,
                        type = type
                    ) {
                        this.posterUrl = coverImg
                        this.score = Score.from10(item["imdbRatingValue"]?.asText())
                    }
                }
            } catch (_: Exception) {
                null
            } ?: emptyList()

            val finalData = if (data.isNotEmpty()) {
                data
            } else {
                try {
                    val apiKey = getApiKey()
                    val rUrl = "$RENDER_API_BASE/home?tab=all&api_key=$apiKey"
                    val rResp = app.get(rUrl, headers = mapOf("X-API-Key" to apiKey), timeout = 12L).text
                    val rRoot = jacksonObjectMapper().readTree(rResp)
                    val rows = rRoot.get("rows")
                    val fallbackList = mutableListOf<SearchResponse>()
                    if (rows != null && rows.isArray) {
                        for (row in rows) {
                            val items = row["items"] ?: continue
                            for (item in items) {
                                val rTitle = item["title"]?.asText() ?: continue
                                val rId = item["id"]?.asText() ?: continue
                                val rPoster = item["poster"]?.asText()
                                val typeStr = item["type"]?.asText() ?: "movie"
                                val rType = if (typeStr.equals("series", true) || typeStr.equals("tv", true)) TvType.TvSeries else TvType.Movie
                                fallbackList.add(
                                    newMovieSearchResponse(name = rTitle, url = rId, type = rType) {
                                        this.posterUrl = rPoster
                                        this.score = Score.from10(item["rating"]?.asText())
                                    }
                                )
                            }
                        }
                    }
                    fallbackList
                } catch (_: Exception) {
                    emptyList()
                }
            }

            return newHomePageResponse(
                listOf(
                    HomePageList(request.name, finalData)
                )
            )

    }

    override suspend fun search(query: String,page: Int): SearchResponseList {
        val searchList = mutableListOf<SearchResponse>()
        try {
            val apiKey = getApiKey()
            val renderUrl = "$RENDER_API_BASE/search?q=${URLEncoder.encode(query, "UTF-8")}&api_key=$apiKey"
            val rResp = app.get(renderUrl, headers = mapOf("X-API-Key" to apiKey), timeout = 12L).text
            val rRoot = jacksonObjectMapper().readTree(rResp)
            val rResults = rRoot.get("results")
            if (rResults != null && rResults.isArray && rResults.size() > 0) {
                for (item in rResults) {
                    val rTitle = item["title"]?.asText() ?: continue
                    val rId = item["id"]?.asText() ?: continue
                    val rPoster = item["poster"]?.asText()
                    val typeStr = item["type"]?.asText() ?: "movie"
                    val rType = if (typeStr.equals("series", true) || typeStr.equals("tv", true)) TvType.TvSeries else TvType.Movie
                    searchList.add(
                        newMovieSearchResponse(name = rTitle, url = rId, type = rType) {
                            this.posterUrl = rPoster
                            this.score = Score.from10(item["rating"]?.asText())
                        }
                    )
                }
                return searchList.toNewSearchResponseList()
            }
        } catch (_: Exception) {}

        val url = "$UPSTREAM_API_URL/wefeed-mobile-bff/subject-api/search/v2"
        val jsonBody = """{"page": $page, "perPage": 20, "keyword": "$query"}"""
        var token = fetchAnonymousToken()
        val xClientToken = generateXClientToken()
        val xTrSignature = generateXTrSignature("POST", "application/json", "application/json; charset=utf-8", url, jsonBody)
        val headers = mutableMapOf(
            "user-agent" to getUserAgent(),
            "accept" to "application/json",
            "content-type" to "application/json",
            "connection" to "keep-alive",
            "x-client-token" to xClientToken,
            "x-tr-signature" to xTrSignature,
            "x-client-info" to getClientInfoJson(),
            "x-client-status" to "0"
        )
        if (!token.isNullOrBlank()) {
            headers["Authorization"] = "Bearer $token"
        }
        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
        var response = app.post(
            url,
            headers = headers,
            requestBody = requestBody
        )

        val xUserResp = response.headers["x-user"]
        if (!xUserResp.isNullOrBlank()) {
            try {
                val tok = jacksonObjectMapper().readTree(xUserResp)?.get("token")?.asText()
                if (!tok.isNullOrBlank()) {
                    cachedGuestToken = tok
                    tokenLastFetchMs = System.currentTimeMillis()
                }
            } catch (_: Exception) {}
        }

        if (response.code == 441 || response.code == 401) {
            val refreshed = fetchAnonymousToken(forceRefresh = true)
            if (!refreshed.isNullOrBlank()) {
                token = refreshed
                headers["Authorization"] = "Bearer $token"
                headers["x-tr-signature"] = generateXTrSignature("POST", "application/json", "application/json; charset=utf-8", url, jsonBody)
                response = app.post(url, headers = headers, requestBody = requestBody)
            }
        }

        val responseBody = response.body.string()
        val mapper = jacksonObjectMapper()
        val root = mapper.readTree(responseBody)
        val results = root.get("data")?.get("results") ?: return newSearchResponseList(emptyList())
        for (result in results) {
            val subjects = result["subjects"] ?: continue
            for (subject in subjects) {
                val title = subject["title"]?.asText() ?: continue
                val genre = subject["genre"]?.asText()
                val desc = subject["description"]?.asText()
                if (isAdultContent(title, genre, desc)) continue
                val id = subject["subjectId"]?.asText() ?: continue
                val coverImg = subject["cover"]?.get("url")?.asText()
                val subjectType = subject["subjectType"]?.asInt() ?: 1
            val type = when (subjectType) {
                        1 -> TvType.Movie
                        2 -> TvType.TvSeries
                        else -> TvType.Movie
                }
            searchList.add(
                newMovieSearchResponse(
                name = title,
                url = id,
                type = type
                ) {
                    this.posterUrl = coverImg
                    this.score = Score.from10(subject["imdbRatingValue"]?.asText())
                }
            )
            }
        }
        return searchList.toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse {
        val id = Regex("""subjectId=([^&]+)""")
            .find(url)
            ?.groupValues?.get(1)
            ?: url.substringAfterLast('/')

        try {
            val apiKey = getApiKey()
            val rUrl = "$RENDER_API_BASE/details?id=$id&api_key=$apiKey"
            val rResp = app.get(rUrl, headers = mapOf("X-API-Key" to apiKey), timeout = 12L).text
            val rRoot = jacksonObjectMapper().readTree(rResp)
            val det = rRoot.get("details")
            if (det != null && det.isObject) {
                val rTitle = det["title"]?.asText() ?: "Movie"
                val rDesc = det["desc"]?.asText() ?: ""
                val rPoster = det["poster"]?.asText()
                val rBackdrop = det["backdrop"]?.asText() ?: rPoster
                val rYear = det["year"]?.asText()?.toIntOrNull()
                val isSeries = det["isSeries"]?.asBoolean() ?: false
                val seasons = det["seasons"]
                val rRating = det["rating"]?.asText()

                val actorsList = det["actors"]?.mapNotNull { a ->
                    val aname = a["name"]?.asText() ?: return@mapNotNull null
                    val achar = a["character"]?.asText()
                    ActorData(Actor(aname, null), roleString = achar)
                } ?: emptyList()

                val watchUrl = "$mainUrl/watch/$id"

                if (isSeries && seasons != null && seasons.isArray && seasons.size() > 0) {
                    val episodesList = mutableListOf<Episode>()
                    for (s in seasons) {
                        val sNum = s["season"]?.asInt() ?: 1
                        val eps = s["episodes"]
                        if (eps != null && eps.isArray) {
                            for (ep in eps) {
                                val epNum = ep.asInt()
                                episodesList.add(
                                    newEpisode("$id|$sNum|$epNum") {
                                        this.name = "Episode $epNum"
                                        this.season = sNum
                                        this.episode = epNum
                                    }
                                )
                            }
                        }
                    }
                    return newTvSeriesLoadResponse(rTitle, watchUrl, TvType.TvSeries, episodesList) {
                        this.posterUrl = rPoster
                        this.backgroundPosterUrl = rBackdrop
                        this.plot = rDesc
                        this.year = rYear
                        this.actors = actorsList
                        this.score = Score.from10(rRating)
                    }
                } else {
                    return newMovieLoadResponse(rTitle, watchUrl, TvType.Movie, "$id|0|0") {
                        this.posterUrl = rPoster
                        this.backgroundPosterUrl = rBackdrop
                        this.plot = rDesc
                        this.year = rYear
                        this.actors = actorsList
                        this.score = Score.from10(rRating)
                    }
                }
            }
        } catch (_: Exception) {}

        val finalUrl = "$UPSTREAM_API_URL/wefeed-mobile-bff/subject-api/get?subjectId=$id"
        var token = fetchAnonymousToken()
        val xClientToken = generateXClientToken()
        val xTrSignature = generateXTrSignature("GET", "application/json", "application/json", finalUrl)

        val headers = mutableMapOf(
            "user-agent" to getUserAgent(),
            "accept" to "application/json",
            "content-type" to "application/json",
            "connection" to "keep-alive",
            "x-client-token" to xClientToken,
            "x-tr-signature" to xTrSignature,
            "x-client-info" to getClientInfoJson(),
            "x-client-status" to "0",
            "x-play-mode" to "2"
        )
        if (!token.isNullOrBlank()) {
            headers["Authorization"] = "Bearer $token"
        }

        var response = app.get(finalUrl, headers = headers)

        val xUserResp = response.headers["x-user"]
        if (!xUserResp.isNullOrBlank()) {
            try {
                val tok = jacksonObjectMapper().readTree(xUserResp)?.get("token")?.asText()
                if (!tok.isNullOrBlank()) {
                    cachedGuestToken = tok
                    tokenLastFetchMs = System.currentTimeMillis()
                }
            } catch (_: Exception) {}
        }

        if (response.code == 441 || response.code == 401) {
            val refreshed = fetchAnonymousToken(forceRefresh = true)
            if (!refreshed.isNullOrBlank()) {
                token = refreshed
                headers["Authorization"] = "Bearer $token"
                headers["x-tr-signature"] = generateXTrSignature("GET", "application/json", "application/json", finalUrl)
                response = app.get(finalUrl, headers = headers)
            }
        }
        if (response.code != 200) {
            throw ErrorLoadingException("Failed to load data: ${response.body.string()}")
        }

        val body = response.body.string()
        val mapper = jacksonObjectMapper()
        val root = mapper.readTree(body)
        val data = root["data"] ?: throw ErrorLoadingException("No data")

        val title = data["title"]?.asText()?.substringBefore("[") ?: throw ErrorLoadingException("No title found")
        val description = data["description"]?.asText()
        val releaseDate = data["releaseDate"]?.asText()
        val duration = data["duration"]?.asText()
        val genre = data["genre"]?.asText()

        if (isAdultContent(title, genre, description)) {
            throw ErrorLoadingException("This title is blocked by Family Safe filter.")
        }
        val imdbRating = data["imdbRatingValue"]?.asText()?.toDoubleOrNull()?.times(10)?.toInt()
        val year = releaseDate?.substring(0, 4)?.toIntOrNull()

        val coverUrl = data["cover"]?.get("url")?.asText()
        val backgroundUrl = data["cover"]?.get("url")?.asText()

        val subjectType = data["subjectType"]?.asInt() ?: 1

        val actors = data["staffList"]
            ?.mapNotNull { staff ->
                val staffType = staff["staffType"]?.asInt()
                if (staffType == 1) {
                    val name = staff["name"]?.asText() ?: return@mapNotNull null
                    val character = staff["character"]?.asText()
                    val avatarUrl = staff["avatarUrl"]?.asText()
                    ActorData(
                        Actor(name, avatarUrl),
                        roleString = character
                    )
                } else null
            }
            ?.distinctBy { it.actor.name }
            ?: emptyList()


        val tags = genre?.split(",")?.map { it.trim() } ?: emptyList()

        val durationMinutes = duration?.let { dur ->
            val regex = """(\d+)h\s*(\d+)m""".toRegex()
            val m = regex.find(dur)
            if (m != null) {
                val h = m.groupValues[1].toIntOrNull() ?: 0
                val min = m.groupValues[2].toIntOrNull() ?: 0
                h * 60 + min
            } else dur.replace("m", "").toIntOrNull()
        }

        val type = when (subjectType) {
            1 -> TvType.Movie
            2 -> TvType.TvSeries
            7 -> TvType.TvSeries
            else -> TvType.Movie
        }

        val (tmdbId, imdbId) = try {
            identifyID(
                title = title.substringBefore("(").substringBefore("["),
                year = releaseDate?.take(4)?.toIntOrNull(),
                imdbRatingValue = imdbRating?.toDouble(),
            )
        } catch (_: Exception) {
            Pair(null, null)
        }

        val logoUrl = try {
            fetchTmdbLogoUrl(
                tmdbAPI = "https://api.themoviedb.org/3",
                apiKey = "98ae14df2b8d8f8f8136499daf79f0e0",
                type = type,
                tmdbId = tmdbId,
                appLangCode = "en"
            )
        } catch (_: Exception) {
            null
        }

        val meta = if (!imdbId.isNullOrBlank()) fetchMetaData(imdbId, type) else null
        val metaVideos = meta?.get("videos")?.toList() ?: emptyList()

        val Poster = meta?.get("poster")?.asText() ?: coverUrl
        val Background = meta?.get("background")?.asText() ?: backgroundUrl
        val Description = meta?.get("overview")?.asText() ?: description
        val IMDBRating = meta?.get("imdbRating")?.asText()

        if (type == TvType.TvSeries) {
            val allSubjectIds = mutableListOf<String>()
            allSubjectIds.add(id)
            data["dubs"]?.forEach {
                val sid = it["subjectId"]?.asText()
                if (!sid.isNullOrBlank() && sid !in allSubjectIds) {
                    allSubjectIds.add(sid)
                }
            }

            val episodeMap = mutableMapOf<Int, MutableSet<Int>>() // season -> episodes

            for (subjectId in allSubjectIds) {
                val seasonUrl = "$UPSTREAM_API_URL/wefeed-mobile-bff/subject-api/season-info?subjectId=$subjectId"
                val seasonSig = generateXTrSignature("GET", "application/json", "application/json", seasonUrl)

                val seasonHeaders = headers.toMutableMap().apply {
                    put("x-tr-signature", seasonSig)
                }

                val seasonResponse = app.get(seasonUrl, headers = seasonHeaders)
                if (seasonResponse.code != 200) continue

                val seasonRoot = mapper.readTree(seasonResponse.body.string())
                val seasons = seasonRoot["data"]?.get("seasons")

                if (seasons == null || !seasons.isArray || seasons.size() == 0) {
                    continue
                }

                seasons.forEach { season ->
                    val seasonNumber = season["se"]?.asInt() ?: 1
                    val maxEp = season["maxEp"]?.asInt() ?: 1

                    val epSet = episodeMap.getOrPut(seasonNumber) { mutableSetOf() }

                    for (ep in 1..maxEp) {
                        epSet.add(ep)
                    }
                }
            }

            val episodes = mutableListOf<Episode>()

            episodeMap.forEach { (seasonNumber, epSet) ->
                epSet.sorted().forEach { episodeNumber ->

                    val epMeta = metaVideos.firstOrNull {
                        it["season"]?.asInt() == seasonNumber &&
                                it["episode"]?.asInt() == episodeNumber
                    }

                    val epName = epMeta?.get("name")?.asText()
                        ?: epMeta?.get("title")?.asText()?.takeIf { it.isNotBlank() }
                        ?: "S${seasonNumber}E${episodeNumber}"

                    val epDesc = epMeta?.get("overview")?.asText()
                        ?: epMeta?.get("description")?.asText()
                        ?: "Season $seasonNumber Episode $episodeNumber"

                    val epThumb = epMeta?.get("thumbnail")?.asText()?.takeIf { it.isNotBlank() }
                        ?: coverUrl

                    val runtime = epMeta?.get("runtime")?.asText()
                        ?.filter { it.isDigit() }
                        ?.toIntOrNull()

                    val aired = epMeta?.get("released")?.asText()
                        ?.takeIf { it.isNotBlank() } ?: ""

                    episodes.add(
                        newEpisode("$id|$seasonNumber|$episodeNumber") {
                            this.name = epName
                            this.season = seasonNumber
                            this.episode = episodeNumber
                            this.posterUrl = epThumb
                            this.description = epDesc
                            this.runTime = runtime
                            addDate(aired)
                        }
                    )
                }
            }

            // fallback
            if (episodes.isEmpty()) {
                episodes.add(
                    newEpisode("$id|1|1") {
                        this.name = "Episode 1"
                        this.season = 1
                        this.episode = 1
                        this.posterUrl = coverUrl
                    }
                )
            }

            return newTvSeriesLoadResponse(title, finalUrl, type, episodes) {
                this.posterUrl = coverUrl ?: Poster
                this.backgroundPosterUrl = Background ?: backgroundUrl ?: Poster
                try { this.logoUrl = logoUrl } catch(_: Throwable) {}
                this.plot = Description ?: description
                this.year = year
                this.tags = tags
                this.actors = actors
                this.score = Score.from10(IMDBRating) ?: imdbRating?.let { Score.from10(it) }
                this.duration = durationMinutes
                addImdbId(imdbId)
                addTMDbId(tmdbId.toString())
            }
        }

        return newMovieLoadResponse(title, finalUrl, type, id) {
            this.posterUrl = coverUrl ?: Poster
            this.backgroundPosterUrl = Background ?: backgroundUrl
            try { this.logoUrl = logoUrl } catch(_:Throwable){}
            this.plot = Description ?: description
            this.year = year
            this.tags = tags
            this.actors = actors
            this.score = Score.from10(IMDBRating) ?:imdbRating?.let { Score.from10(it) }
            this.duration = durationMinutes
            addImdbId(imdbId)
            addTMDbId(tmdbId.toString())
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val (brand, model) = randomBrandModel()

        try {
            val parts = data.split("|")
            val originalSubjectId = when {
                parts[0].contains("get?subjectId") -> {
                    Regex("""subjectId=([^&]+)""")
                        .find(parts[0])
                        ?.groupValues?.get(1)
                        ?: parts[0].substringAfterLast('/')
                }
                parts[0].contains("/") -> {
                    parts[0].substringAfterLast('/')
                }
                else -> parts[0]
            }

            val season = if (parts.size > 1) parts[1].toIntOrNull() ?: 0 else 0
            val episode = if (parts.size > 2) parts[2].toIntOrNull() ?: 0 else 0

            val mapper = jacksonObjectMapper()
            try {
                val apiKey = getApiKey()
                val rUrl = "$RENDER_API_BASE/streams?id=$originalSubjectId&se=$season&ep=$episode&api_key=$apiKey"
                val rResp = app.get(rUrl, headers = mapOf("X-API-Key" to apiKey), timeout = 12L).text
                val rRoot = mapper.readTree(rResp)
                val rStreams = rRoot.get("streams")
                val rSubs = rRoot.get("subtitles")
                if (rSubs != null && rSubs.isArray) {
                    for (sub in rSubs) {
                        val subUrl = sub["url"]?.asText() ?: continue
                        val subLang = sub["language"]?.asText() ?: "English"
                        subtitleCallback.invoke(
                            newSubtitleFile(
                                url = subUrl,
                                lang = subLang
                            )
                        )
                    }
                }
                var renderStreamCount = 0
                if (rStreams != null && rStreams.isArray && rStreams.size() > 0) {
                    for (st in rStreams) {
                        val origUrl = st["originalUrl"]?.asText() ?: continue
                        val format = st["format"]?.asText() ?: "DASH"
                        val cookie = st["cookie"]?.asText()
                        val headersMap = mutableMapOf<String, String>()
                        if (!cookie.isNullOrEmpty()) {
                            headersMap["Cookie"] = cookie
                        }
                        callback(
                            newExtractorLink(
                                name = "Ayush Flix • ${st["language"]?.asText() ?: "Original Audio"}",
                                source = "Ayush API (Singapore)",
                                url = origUrl,
                                type = if (format == "DASH") ExtractorLinkType.DASH else ExtractorLinkType.VIDEO
                            ) {
                                this.headers = headersMap
                            }
                        )
                        renderStreamCount++
                    }
                    if (renderStreamCount > 0) {
                        return true
                    }
                }
            } catch (_: Exception) {}

            val subjectUrl = "$UPSTREAM_API_URL/wefeed-mobile-bff/subject-api/get?subjectId=$originalSubjectId"
            var token = fetchAnonymousToken()
            val subjectXClientToken = generateXClientToken()
            val subjectXTrSignature = generateXTrSignature("GET", "application/json", "application/json", subjectUrl)
            val subjectHeaders = mutableMapOf(
                "user-agent" to getUserAgent(),
                "accept" to "application/json",
                "content-type" to "application/json",
                "connection" to "keep-alive",
                "x-client-token" to subjectXClientToken,
                "x-tr-signature" to subjectXTrSignature,
                "x-client-info" to getClientInfoJson(),
                "x-client-status" to "0"
            )
            if (!token.isNullOrBlank()) {
                subjectHeaders["Authorization"] = "Bearer $token"
            }

            var subjectResponse = app.get(subjectUrl, headers = subjectHeaders)
            if (subjectResponse.code == 441 || subjectResponse.code == 401) {
                val refreshed = fetchAnonymousToken(forceRefresh = true)
                if (!refreshed.isNullOrBlank()) {
                    token = refreshed
                    subjectHeaders["Authorization"] = "Bearer $token"
                    subjectHeaders["x-tr-signature"] = generateXTrSignature("GET", "application/json", "application/json", subjectUrl)
                    subjectResponse = app.get(subjectUrl, headers = subjectHeaders)
                }
            }

            val subjectIds = mutableListOf<Pair<String, String>>() // Pair of (subjectId, language)
            var originalLanguageName = "Original"
            if (subjectResponse.code == 200) {
                val subjectResponseBody = subjectResponse.body.string()
                val subjectRoot = mapper.readTree(subjectResponseBody)
                val subjectData = subjectRoot["data"]
                val dubs = subjectData?.get("dubs")
                if (dubs != null && dubs.isArray) {
                    for (dub in dubs) {
                        val dubSubjectId = dub["subjectId"]?.asText()
                        val lanName = dub["lanName"]?.asText()
                        if (dubSubjectId != null && lanName != null) {
                            if (dubSubjectId == originalSubjectId) {
                                originalLanguageName = lanName
                            } else {
                                subjectIds.add(Pair(dubSubjectId, lanName))
                            }
                        }
                    }
                }
            }

            val xUserHeader = subjectResponse.headers["x-user"]
            if (!xUserHeader.isNullOrBlank()) {
                try {
                    val xUserJson = mapper.readTree(xUserHeader)
                    val tok = xUserJson["token"]?.asText()
                    if (!tok.isNullOrBlank()) {
                        token = tok
                        cachedGuestToken = tok
                        tokenLastFetchMs = System.currentTimeMillis()
                    }
                } catch (_: Exception) {}
            }

            // Add the original subject ID first as the base source
            subjectIds.add(0, Pair(originalSubjectId, originalLanguageName))

            // Sort so Hindi audio dubs are ALWAYS prioritized first (played by default when play is clicked)
            val sortedSubjectIds = subjectIds.sortedWith(
                compareByDescending<Pair<String, String>> {
                    it.second.contains("hindi", ignoreCase = true) || it.second.equals("hi", ignoreCase = true)
                }.thenBy {
                    if (it.first == originalSubjectId) 0 else 1
                }
            )

            // Process each subjectId (Hindi first, then original and others)
            for ((subjectId, language) in sortedSubjectIds) {
                try {
                    val url = "$UPSTREAM_API_URL/wefeed-mobile-bff/subject-api/play-info?subjectId=$subjectId&se=$season&ep=$episode"

                    val xClientToken = generateXClientToken()
                    val xTrSignature = generateXTrSignature("GET", "application/json", "application/json", url)
                    val headers = mutableMapOf(
                        "user-agent" to getUserAgent(),
                        "accept" to "application/json",
                        "content-type" to "application/json",
                        "connection" to "keep-alive",
                        "x-client-token" to xClientToken,
                        "x-tr-signature" to xTrSignature,
                        "x-client-info" to getClientInfoJson(),
                        "x-client-status" to "0"
                    )
                    if (!token.isNullOrBlank()) {
                        headers["Authorization"] = "Bearer $token"
                    }

                    var response = app.get(url, headers = headers)
                    if (response.code == 441 || response.code == 401) {
                        val refreshed = fetchAnonymousToken(forceRefresh = true)
                        if (!refreshed.isNullOrBlank()) {
                            token = refreshed
                            headers["Authorization"] = "Bearer $token"
                            headers["x-tr-signature"] = generateXTrSignature("GET", "application/json", "application/json", url)
                            response = app.get(url, headers = headers)
                        }
                    }
                    if (response.code == 200) {
                        val responseBody = response.body.string()
                        val root = mapper.readTree(responseBody)
                        val playData = root["data"]
                        var hasValidStream = false
                        // Handle the new API response format with streams
                        val streams = playData?.get("streams")
                        if (streams != null && streams.isArray) {
                            for (stream in streams) {
                                val streamUrl = stream["url"]?.asText() ?: continue
                                val format = stream["format"]?.asText() ?: ""
                                val resolutions = stream["resolutions"]?.asText() ?: ""
                                val signCookieRaw = stream["signCookie"]?.asText()
                                val signCookie = if (signCookieRaw.isNullOrEmpty()) null else signCookieRaw
                                val id = stream["id"]?.asText() ?: "$subjectId|$season|$episode"
                                val quality = getHighestQuality(resolutions)

                                val resolvedUrl = if (signCookie != null) {
                                    val policyResource = extractPolicyResource(signCookie)
                                    if (policyResource != null) {
                                        val trimmed = policyResource.trimEnd('*', '/')
                                        if (trimmed.endsWith(".mpd", ignoreCase = true)) {
                                            trimmed
                                        } else {
                                            "$trimmed/index.mpd"
                                        }
                                    } else {
                                        streamUrl
                                    }
                                } else {
                                    streamUrl
                                }

                                if (resolvedUrl.contains("b164fbfb4347792950bdfbfb563d39d9")) continue
                                if (resolvedUrl == streamUrl && resolvedUrl.contains("/other/2026/09/04/")) continue

                                callback.invoke(
                                    newExtractorLink(
                                        source = "Original Direct (Backup)",
                                        name = "Backup • ${language.replace("dub", "Audio").trim()}",
                                        url = resolvedUrl,
                                        type = when {
                                            resolvedUrl.startsWith("magnet:", ignoreCase = true) -> ExtractorLinkType.MAGNET
                                            resolvedUrl.contains(".mpd", ignoreCase = true) -> ExtractorLinkType.DASH
                                            resolvedUrl.substringAfterLast('.', "").equals("torrent", ignoreCase = true) -> ExtractorLinkType.TORRENT
                                            format.equals("HLS", ignoreCase = true) || resolvedUrl.substringAfterLast('.', "").equals("m3u8", ignoreCase = true) -> ExtractorLinkType.M3U8
                                            resolvedUrl.contains(".mp4", ignoreCase = true) || resolvedUrl.contains(".mkv", ignoreCase = true) -> ExtractorLinkType.VIDEO
                                            else -> INFER_TYPE
                                        }
                                    ) {
                                        this.headers = mapOf(
                                            "Referer" to mainUrl,
                                            "User-Agent" to getUserAgent()
                                        )
                                        if (quality != null) {
                                            this.quality = quality
                                        }
                                        if (signCookie != null) {
                                            this.headers += mapOf("Cookie" to signCookie)
                                        }
                                    }
                                )
                                hasValidStream = true

                                val subLink = "$UPSTREAM_API_URL/wefeed-mobile-bff/subject-api/get-stream-captions?subjectId=$subjectId&streamId=$id"
                                val xClientToken = generateXClientToken()
                                val xTrSignature = generateXTrSignature("GET", "", "", subLink)
                                val subHeaders = mutableMapOf(
                                    "user-agent" to getUserAgent(),
                                    "Accept" to "",
                                    "x-client-info" to getClientInfoJson(),
                                    "X-Client-Status" to "0",
                                    "Content-Type" to "",
                                    "X-Client-Token" to xClientToken,
                                    "x-tr-signature" to xTrSignature,
                                )
                                if (!token.isNullOrBlank()) {
                                    subHeaders["Authorization"] = "Bearer $token"
                                }
                                val subResponse = app.get(subLink, headers = subHeaders)
                                val subRoot = mapper.readTree(subResponse.toString())
                                val extCaptions = subRoot["data"]?.get("extCaptions")
                                if (extCaptions != null && extCaptions.isArray) {
                                    for (caption in extCaptions) {
                                        val captionUrl = caption["url"]?.asText() ?: continue
                                        val lang = caption["language"]?.asText()
                                            ?: caption["lanName"]?.asText()
                                            ?: caption["lan"]?.asText()
                                            ?: "Unknown"
                                        subtitleCallback.invoke(
                                            newSubtitleFile(
                                                url = captionUrl,
                                                lang = "$lang (${language.replace("dub","Audio")})"
                                            )
                                        )
                                    }
                                }

                                val subLink1 = "$UPSTREAM_API_URL/wefeed-mobile-bff/subject-api/get-ext-captions?subjectId=$subjectId&resourceId=$id&episode=0"
                                val xClientToken1 = generateXClientToken()
                                val xTrSignature1 = generateXTrSignature("GET", "", "", subLink1)
                                val subHeaders1 = mutableMapOf(
                                    "User-Agent" to getUserAgent(),
                                    "Accept" to "",
                                    "X-Client-Info" to getClientInfoJson(),
                                    "X-Client-Status" to "0",
                                    "Content-Type" to "",
                                    "X-Client-Token" to xClientToken1,
                                    "x-tr-signature" to xTrSignature1,
                                )
                                if (!token.isNullOrBlank()) {
                                    subHeaders1["Authorization"] = "Bearer $token"
                                }
                                val subResponse1 = app.get(subLink1, headers = subHeaders1)

                                val subRoot1 = mapper.readTree(subResponse1.toString())
                                val extCaptions1 = subRoot1["data"]?.get("extCaptions")
                                if (extCaptions1 != null && extCaptions1.isArray) {
                                    for (caption in extCaptions1) {
                                        val captionUrl = caption["url"]?.asText() ?: continue
                                        val lang = caption["lan"]?.asText()
                                            ?: caption["lanName"]?.asText()
                                            ?: caption["language"]?.asText()
                                            ?: "Unknown"
                                        subtitleCallback.invoke(
                                            newSubtitleFile(
                                                url = captionUrl,
                                                lang = "$lang (${language.replace("dub","Audio")})"
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Fallback if no valid streams were extracted
                        if (!hasValidStream) {
                            val fallbackUrl = "$UPSTREAM_API_URL/wefeed-mobile-bff/subject-api/get?subjectId=$subjectId"
                            val fallbackHeaders = headers.toMutableMap().apply {
                                put("x-tr-signature", generateXTrSignature(
                                    "GET",
                                    "application/json",
                                    "application/json",
                                    fallbackUrl
                                ))
                            }

                            val fallbackResponse = app.get(fallbackUrl, headers = fallbackHeaders)
                            if (fallbackResponse.code == 200) {
                                val fallbackRoot = mapper.readTree(fallbackResponse.body.string())
                                val detectors = fallbackRoot["data"]?.get("resourceDetectors")
                                detectors?.forEach { detector ->
                                    detector["resolutionList"]?.forEach { video ->
                                        val link = video["resourceLink"]?.asText() ?: return@forEach
                                        if (link.contains("b164fbfb4347792950bdfbfb563d39d9")) return@forEach
                                        if (link.contains("/other/2026/09/04/")) return@forEach
                                        val quality = video["resolution"]?.asInt() ?: 0
                                        val se = video["se"]?.asInt()
                                        val ep = video["ep"]?.asInt()
                                        if (season > 0 && se != null && se != season) return@forEach
                                        if (episode > 0 && ep != null && ep != episode) return@forEach

                                        callback.invoke(
                                            newExtractorLink(
                                                source = "$name ${language.replace("dub","Audio").trim()}",
                                                name = if (se != null && ep != null) "$name S${se}E${ep} ${quality}p (${language.replace("dub","Audio").trim()})" else "$name ${quality}p (${language.replace("dub","Audio").trim()})",
                                                url = link,
                                                type = ExtractorLinkType.VIDEO
                                            ) {
                                                this.headers = mapOf(
                                                    "Referer" to mainUrl,
                                                    "User-Agent" to getUserAgent()
                                                )
                                                this.quality = quality
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    continue
                }
            }

            try {
                val apiKey = getApiKey()
                val rUrl = "$RENDER_API_BASE/streams?id=$originalSubjectId&se=$season&ep=$episode&api_key=$apiKey"
                val rResp = app.get(rUrl, headers = mapOf("X-API-Key" to apiKey), timeout = 12L).text
                val rRoot = mapper.readTree(rResp)
                val rStreams = rRoot.get("streams")
                val rSubs = rRoot.get("subtitles")
                if (rSubs != null && rSubs.isArray) {
                    for (sub in rSubs) {
                        val subUrl = sub["url"]?.asText() ?: continue
                        val subLang = sub["language"]?.asText() ?: "English"
                        subtitleCallback.invoke(
                            newSubtitleFile(
                                url = subUrl,
                                lang = subLang
                            )
                        )
                    }
                }
                if (rStreams != null && rStreams.isArray) {
                    for (st in rStreams) {
                        val origUrl = st["originalUrl"]?.asText() ?: continue
                        val format = st["format"]?.asText() ?: "DASH"
                        val cookie = st["cookie"]?.asText()
                        val headersMap = mutableMapOf<String, String>()
                        if (!cookie.isNullOrEmpty()) {
                            headersMap["Cookie"] = cookie
                        }
                        callback(
                            newExtractorLink(
                                name = "Ayush Flix (${st["language"]?.asText() ?: "Server"})",
                                source = "Ayush Flix Cloud",
                                url = origUrl,
                                type = if (format == "DASH") ExtractorLinkType.DASH else ExtractorLinkType.VIDEO
                            ) {
                                this.headers = headersMap
                            }
                        )
                    }
                }
            } catch (_: Exception) {}
            
            return true
              
        } catch (_: Exception) {
            return false
        }
    }
}

fun getHighestQuality(input: String): Int? {
    val qualities = listOf(
        "2160" to Qualities.P2160.value,
        "1440" to Qualities.P1440.value,
        "1080" to Qualities.P1080.value,
        "720"  to Qualities.P720.value,
        "480"  to Qualities.P480.value,
        "360"  to Qualities.P360.value,
        "240"  to Qualities.P240.value
    )

    for ((label, mappedValue) in qualities) {
        if (input.contains(label, ignoreCase = true)) {
            return mappedValue
        }
    }
    return null
}


private fun cleanTitle(s: String): String {
    return s.lowercase()
        .replace("[^a-z0-9 ]".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()
}
private suspend fun identifyID(
    title: String,
    year: Int?,
    imdbRatingValue: Double?
): Pair<Int?, String?> {
    val normTitle = normalize(title)
    val res = searchAndPick(normTitle, year, imdbRatingValue)
    if (res.first != null) return res

    return Pair(null, null)
}

private suspend fun searchAndPick(
    normTitle: String,
    year: Int?,
    imdbRatingValue: Double?,
): Pair<Int?, String?> {

    suspend fun doSearch(endpoint: String, extraParams: String = ""): org.json.JSONArray? {
        return try {
            val url = buildString {
                append("https://api.themoviedb.org/3/").append(endpoint)
                append("?api_key=").append("1865f43a0549ca50d341dd9ab8b29f49")
                append(extraParams)
                append("&include_adult=false&page=1")
                append("&random=").append(Random.nextInt())
            }
            val text = app.get(url, timeout = 2).text
            JSONObject(text).optJSONArray("results")
        } catch (_: Exception) {
            null
        }
    }

    val multiResults = doSearch("search/multi", "&query=${URLEncoder.encode(normTitle, "UTF-8")}" + (if (year != null) "&year=$year" else ""))
    val searchQueues: List<Pair<String, org.json.JSONArray?>> = listOf(
        "multi" to multiResults,
        "tv" to doSearch("search/tv", "&query=${URLEncoder.encode(normTitle, "UTF-8")}" + (if (year != null) "&first_air_date_year=$year" else "")),
        "movie" to doSearch("search/movie", "&query=${URLEncoder.encode(normTitle, "UTF-8")}" + (if (year != null) "&year=$year" else ""))
    )

    var bestId: Int? = null
    var bestScore = -1.0
    var bestIsTv = false

    for ((sourceType, results) in searchQueues) {
        if (results == null) continue
        for (i in 0 until results.length()) {
            val o = results.getJSONObject(i)

            val mediaType = when (sourceType) {
                "multi" -> o.optString("media_type", "")
                "tv" -> "tv"
                else -> "movie"
            }

            val candidateId = o.optInt("id", -1)
            if (candidateId == -1) continue

            val titles = listOf(
                o.optString("title"),
                o.optString("name"),
                o.optString("original_title"),
                o.optString("original_name")
            ).filter { it.isNotBlank() }

            val candDate = when (mediaType) {
                "tv" -> o.optString("first_air_date", "")
                else -> o.optString("release_date", "")
            }
            val candYear = candDate.take(4).toIntOrNull()
            val candRating = o.optDouble("vote_average", Double.NaN)

            // scoring
            var score = 0.0
            val normClean = cleanTitle(normTitle)

            var titleScore = 0.0
            for (t in titles) {
                val candClean = cleanTitle(t)

                if (tokenEquals(candClean, normClean)) {
                    titleScore = 50.0
                    break
                }

                if (candClean.contains(normClean) || normClean.contains(candClean)) {
                    titleScore = maxOf(titleScore, 20.0)
                }
            }
            score += titleScore


            if (candYear != null && year != null && candYear == year) score += 35.0

            if (imdbRatingValue != null && !candRating.isNaN()) {
                val diff = kotlin.math.abs(candRating - imdbRatingValue)
                if (diff <= 0.5) score += 10.0 else if (diff <= 1.0) score += 5.0
            }

            if (o.has("popularity")) score += (o.optDouble("popularity", 0.0) / 100.0).coerceAtMost(5.0)

            if (score > bestScore) {
                bestScore = score
                bestId = candidateId
                bestIsTv = (mediaType == "tv")
            }
        }
    }

    if (bestId == null || bestScore < 40.0) return Pair(null, null)

    // fetch details for external_ids
    val detailKind = if (bestIsTv) "tv" else "movie"
    val detailUrl = "https://api.themoviedb.org/3/$detailKind/$bestId?api_key=1865f43a0549ca50d341dd9ab8b29f49&append_to_response=external_ids&random=${Random.nextInt()}"
    val detailJson = try {
        val detailText = app.get(detailUrl, timeout = 2).text
        JSONObject(detailText)
    } catch (_: Exception) {
        null
    }
    val imdbId = detailJson?.optJSONObject("external_ids")?.optString("imdb_id")

    return Pair(bestId, imdbId)
}

private fun tokenEquals(a: String, b: String): Boolean {
    val sa = a.split("\\s+".toRegex()).filter { it.isNotBlank() }.toSet()
    val sb = b.split("\\s+".toRegex()).filter { it.isNotBlank() }.toSet()
    if (sa.isEmpty() || sb.isEmpty()) return false
    val inter = sa.intersect(sb).size
    return inter >= max(1, minOf(sa.size, sb.size) * 3 / 4)
}

private fun normalize(s: String): String {
    val t = s.replace("\\[.*?]".toRegex(), " ")
        .replace("\\(.*?\\)".toRegex(), " ")
        .replace("(?i)\\b(dub|dubbed|hd|4k|hindi|tamil|telugu|dual audio)\\b".toRegex(), " ")
        .trim()
        .lowercase()
        .replace(":", " ")
        .replace("\\p{Punct}".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
    return t
}

private suspend fun fetchMetaData(imdbId: String?, type: TvType): JsonNode? {
    if (imdbId.isNullOrBlank()) return null

    val metaType = if (type == TvType.TvSeries) "series" else "movie"
    val url = "https://v3-cinemeta.strem.io/meta/$metaType/$imdbId.json"

    return try {
        val resp = app.get(url, timeout = 3).text
        mapper.readTree(resp)["meta"]
    } catch (_: Exception) {
        null
    }
}

suspend fun fetchTmdbLogoUrl(
    tmdbAPI: String,
    apiKey: String,
    type: TvType,
    tmdbId: Int?,
    appLangCode: String?
): String? {

    if (tmdbId == null) return null

    val url = if (type == TvType.Movie)
        "$tmdbAPI/movie/$tmdbId/images?api_key=$apiKey&random=${Random.nextInt()}"
    else
        "$tmdbAPI/tv/$tmdbId/images?api_key=$apiKey&random=${Random.nextInt()}"

    val json = runCatching { JSONObject(app.get(url, timeout = 2).text) }.getOrNull() ?: return null
    val logos = json.optJSONArray("logos") ?: return null
    if (logos.length() == 0) return null

    val lang = appLangCode?.trim()?.lowercase()

    fun path(o: JSONObject) = o.optString("file_path")
    fun isSvg(o: JSONObject) = path(o).endsWith(".svg", true)
    fun urlOf(o: JSONObject) = "https://image.tmdb.org/t/p/w500${path(o)}"

    // Language match
    var svgFallback: JSONObject? = null

    for (i in 0 until logos.length()) {
        val logo = logos.optJSONObject(i) ?: continue
        val p = path(logo)
        if (p.isBlank()) continue

        val l = logo.optString("iso_639_1").trim().lowercase()
        if (l == lang) {
            if (!isSvg(logo)) return urlOf(logo)
            if (svgFallback == null) svgFallback = logo
        }
    }
    svgFallback?.let { return urlOf(it) }

    // Highest voted fallback
    var best: JSONObject? = null
    var bestSvg: JSONObject? = null

    fun voted(o: JSONObject) = o.optDouble("vote_average", 0.0) > 0 && o.optInt("vote_count", 0) > 0

    fun better(a: JSONObject?, b: JSONObject): Boolean {
        if (a == null) return true
        val aAvg = a.optDouble("vote_average", 0.0)
        val aCnt = a.optInt("vote_count", 0)
        val bAvg = b.optDouble("vote_average", 0.0)
        val bCnt = b.optInt("vote_count", 0)
        return bAvg > aAvg || (bAvg == aAvg && bCnt > aCnt)
    }

    for (i in 0 until logos.length()) {
        val logo = logos.optJSONObject(i) ?: continue
        if (!voted(logo)) continue

        if (isSvg(logo)) {
            if (better(bestSvg, logo)) bestSvg = logo
        } else {
            if (better(best, logo)) best = logo
        }
    }

    best?.let { return urlOf(it) }
    bestSvg?.let { return urlOf(it) }

    // No language match & no voted logos
    return null


}
