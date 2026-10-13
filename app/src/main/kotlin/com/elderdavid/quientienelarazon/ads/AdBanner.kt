package com.elderdavid.quientienelarazon.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Banner pequeño de tamaño fijo (320 × 50), centrado. Para el menú principal: ocupa poco y no
 * cambia el diseño. No ocupa espacio si los anuncios están desactivados o sin consentimiento.
 */
@Composable
fun SmallAdBanner(ads: AdsController, modifier: Modifier = Modifier) {
    val enabled by ads.bannersEnabled.collectAsStateWithLifecycle()
    if (!enabled) return
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.width(AdSize.BANNER.width.dp).height(AdSize.BANNER.height.dp),
            factory = { ctx ->
                AdView(ctx).apply {
                    adUnitId = ads.bannerUnitId
                    setAdSize(AdSize.BANNER)
                    loadAd(AdRequest.Builder().build())
                }
            },
            onRelease = { it.destroy() },
        )
    }
}

/** Banner adaptable anclado. No ocupa espacio si los anuncios están desactivados o sin consentimiento. */
@Composable
fun AdBanner(ads: AdsController, modifier: Modifier = Modifier) {
    val enabled by ads.bannersEnabled.collectAsStateWithLifecycle()
    if (!enabled) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthDp = maxWidth.value.toInt()
        val context = LocalContext.current
        val size = remember(widthDp) { AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp) }
        key(widthDp) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(size.height.dp),
                factory = { ctx ->
                    AdView(ctx).apply {
                        adUnitId = ads.bannerUnitId
                        setAdSize(size)
                        loadAd(AdRequest.Builder().build())
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }
}
