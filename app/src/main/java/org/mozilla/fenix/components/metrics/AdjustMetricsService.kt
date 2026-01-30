/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.metrics

import android.app.Application
import androidx.annotation.VisibleForTesting
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustConfig
import com.adjust.sdk.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.support.base.log.logger.Logger
import org.mozilla.fenix.BuildConfig
import org.mozilla.fenix.Config
import org.mozilla.fenix.GleanMetrics.AdjustAttribution
import org.mozilla.fenix.GleanMetrics.Pings
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.utils.Settings

class AdjustMetricsService(private val application: Application) : MetricsService {
    override val type = MetricServiceType.Marketing
    private val logger = Logger("AdjustMetricsService")

    override fun start() {}

    override fun stop() {
        Adjust.disable()
        Adjust.gdprForgetMe(application.applicationContext)
    }

    // We're not currently sending events directly to Adjust
    override fun track(event: Event) { /* noop */ }
    override fun shouldTrack(event: Event): Boolean = false

    companion object {
        @VisibleForTesting
        internal fun alreadyKnown(settings: Settings): Boolean {
            return settings.adjustCampaignId.isNotEmpty() || settings.adjustNetwork.isNotEmpty() ||
                settings.adjustCreative.isNotEmpty() || settings.adjustAdGroup.isNotEmpty()
        }

        private fun triggerPing() {
            CoroutineScope(Dispatchers.IO).launch {
                Pings.adjustAttribution.submit()
            }
        }
    }
}
