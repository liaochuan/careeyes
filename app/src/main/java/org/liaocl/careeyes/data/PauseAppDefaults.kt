package org.liaocl.careeyes.data

/**
 * Video apps paused by default when a break starts. The settings page lets the user turn any of
 * them off and add other installed apps, so this list is only the initial value.
 */
val DEFAULT_PAUSE_APP_PACKAGES: Set<String> = setOf(
    "com.ss.android.ugc.aweme", // 抖音
    "com.ss.android.ugc.aweme.lite", // 抖音极速版
    "tv.danmaku.bili", // 哔哩哔哩
    "tv.danmaku.bilibilihd", // 哔哩哔哩 HD
    "com.smile.gifmaker", // 快手
    "com.kuaishou.nebula", // 快手极速版
    "com.ss.android.article.video", // 西瓜视频
    "com.tencent.qqlive", // 腾讯视频
    "com.qiyi.video", // 爱奇艺
    "com.youku.phone", // 优酷
    "com.hunantv.imgo.activity", // 芒果TV
    "com.google.android.youtube", // YouTube
)
