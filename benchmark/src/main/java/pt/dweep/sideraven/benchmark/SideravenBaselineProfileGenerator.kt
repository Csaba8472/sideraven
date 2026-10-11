/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.benchmark

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.benchmark.utils.HtmlAsset
import org.mozilla.fenix.benchmark.utils.MockWebServerRule
import org.mozilla.fenix.benchmark.utils.uri

/**
 * Generates Sideraven's baseline profile (spec 0009): the code run while starting, opening tabs,
 * switching tabs and scrolling the Tab List gets compiled ahead of time.
 *
 * With the tablet connected and the release-style build installable:
 * ```
 * ./gradlew :benchmark:connectedForkReleaseAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=pt.dweep.sideraven.benchmark.SideravenBaselineProfileGenerator
 * ```
 * The profile lands under `benchmark/build/outputs/connected_android_test_additional_output/`;
 * copy its `*-baseline-prof.txt` to `app/src/main/baseline-prof.txt` and rebuild.
 */
@RequiresApi(Build.VERSION_CODES.P)
@RunWith(AndroidJUnit4::class)
class SideravenBaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @get:Rule
    val server = MockWebServerRule()

    @Test
    fun generate() {
        rule.collect(packageName = SIDERAVEN_PACKAGE, maxIterations = 5) {
            sideravenStartupJourney()
            openTabsJourney(List(4) { server.uri(HtmlAsset.SIMPLE) } + server.uri(HtmlAsset.LONG))
            switchTabsJourney()
            scrollTabListJourney()
        }
    }
}
