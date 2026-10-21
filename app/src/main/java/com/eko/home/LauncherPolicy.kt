package com.eko.home

/** Pure policy shared by UI and unit tests. No device assumptions here. */
object LauncherPolicy {
    private val favorites = listOf("com.netflix.ninja", "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus", "com.stan.and", "au.com.kayosports", "au.com.binge", "com.apple.atve.androidtv.appletv")
    fun rank(packageName: String): Int = favorites.indexOf(packageName).let { if (it < 0) Int.MAX_VALUE else it }
    fun resolveInput(savedId: String?, availableIds: List<String>): String? = savedId?.takeIf { it in availableIds }
}
