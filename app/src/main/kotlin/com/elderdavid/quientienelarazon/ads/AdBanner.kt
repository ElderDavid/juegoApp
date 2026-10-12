package com.elderdavid.quientienelarazon.ads

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

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
