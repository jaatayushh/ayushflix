package com.lagradost.cloudstream3.utils

import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey

object ProviderRolloverManager {
    private const val TAG = "ProviderRollover"
    private const val KEY_NEXT_ROLLOVER_INDEX = "ayushflix_next_rollover_index"
    private const val KEY_ACTIVE_ROLLOVER_INDEX = "ayushflix_active_rollover_index"

    enum class ProviderFamily {
        NETFLIX,
        PRIME_VIDEO,
        CASTLE_TV
    }

    private val NETFLIX_NAMES = listOf("Netflix", "Netflix Mirror", "NetflixM", "Netflixm")
    private val PRIME_NAMES = listOf("Prime Video", "Prime Video Mirror", "PrimeVideoM", "Primevideom")
    private val CASTLE_NAMES = listOf("Castle TV", "Castle TV (Use VLC)", "CastleTvProvider", "Castletvprovider", "Castle")

    fun getFamily(apiName: String?): ProviderFamily? {
        if (apiName == null) return null
        return when {
            NETFLIX_NAMES.any { it.equals(apiName, ignoreCase = true) } || apiName.contains("netflix", ignoreCase = true) -> ProviderFamily.NETFLIX
            PRIME_NAMES.any { it.equals(apiName, ignoreCase = true) } || apiName.contains("prime", ignoreCase = true) -> ProviderFamily.PRIME_VIDEO
            CASTLE_NAMES.any { it.equals(apiName, ignoreCase = true) } || apiName.contains("castle", ignoreCase = true) -> ProviderFamily.CASTLE_TV
            else -> null
        }
    }

    fun getApiForFamily(family: ProviderFamily): MainAPI? {
        val candidates = when (family) {
            ProviderFamily.NETFLIX -> NETFLIX_NAMES
            ProviderFamily.PRIME_VIDEO -> PRIME_NAMES
            ProviderFamily.CASTLE_TV -> CASTLE_NAMES
        }
        return APIHolder.allProviders.firstOrNull { p ->
            candidates.any { it.equals(p.name, ignoreCase = true) }
        } ?: APIHolder.allProviders.firstOrNull { p ->
            when (family) {
                ProviderFamily.NETFLIX -> p.name.contains("netflix", ignoreCase = true)
                ProviderFamily.PRIME_VIDEO -> p.name.contains("prime", ignoreCase = true)
                ProviderFamily.CASTLE_TV -> p.name.contains("castle", ignoreCase = true)
            }
        }
    }

    /**
     * Determines family sequence for a given cycle index:
     * 0 -> [NETFLIX, PRIME_VIDEO, CASTLE_TV]
     * 1 -> [PRIME_VIDEO, CASTLE_TV, NETFLIX]
     * 2 -> [CASTLE_TV, NETFLIX, PRIME_VIDEO]
     */
    fun getFamilyOrderForIndex(index: Int): List<ProviderFamily> {
        val normalized = Math.floorMod(index, 3)
        return when (normalized) {
            0 -> listOf(ProviderFamily.NETFLIX, ProviderFamily.PRIME_VIDEO, ProviderFamily.CASTLE_TV)
            1 -> listOf(ProviderFamily.PRIME_VIDEO, ProviderFamily.CASTLE_TV, ProviderFamily.NETFLIX)
            else -> listOf(ProviderFamily.CASTLE_TV, ProviderFamily.NETFLIX, ProviderFamily.PRIME_VIDEO)
        }
    }

    @Synchronized
    fun advanceLaunchIndex(): Int {
        val nextIndex = getKey<Int>(KEY_NEXT_ROLLOVER_INDEX) ?: 0
        setKey(KEY_ACTIVE_ROLLOVER_INDEX, nextIndex)
        val followingIndex = (nextIndex + 1) % 3
        setKey(KEY_NEXT_ROLLOVER_INDEX, followingIndex)
        Log.i(TAG, "Advanced launch index: active=$nextIndex, next will be=$followingIndex")
        return nextIndex
    }

    fun getActiveFamilyOrder(): List<ProviderFamily> {
        val activeIndex = getKey<Int>(KEY_ACTIVE_ROLLOVER_INDEX) ?: 0
        return getFamilyOrderForIndex(activeIndex)
    }

    fun getLaunchApi(): MainAPI? {
        val order = getActiveFamilyOrder()
        for (family in order) {
            val api = getApiForFamily(family)
            if (api != null) {
                Log.i(TAG, "Found launch provider: ${api.name} for family $family")
                return api
            }
        }
        return null
    }

    fun getFailoverApi(failedApiName: String?): MainAPI? {
        if (failedApiName.isNullOrBlank()) return null
        val failedFamily = getFamily(failedApiName) ?: return null

        val familyOrder = getActiveFamilyOrder()
        val failedPos = familyOrder.indexOf(failedFamily)
        val remaining = if (failedPos != -1) familyOrder.drop(failedPos + 1) else familyOrder.filter { it != failedFamily }

        for (fallbackFamily in remaining) {
            val candidate = getApiForFamily(fallbackFamily)
            if (candidate != null) {
                Log.w(TAG, "Failover: '$failedApiName' ($failedFamily) failed -> Next candidate: '${candidate.name}' ($fallbackFamily)")
                return candidate
            }
        }
        return null
    }
}
