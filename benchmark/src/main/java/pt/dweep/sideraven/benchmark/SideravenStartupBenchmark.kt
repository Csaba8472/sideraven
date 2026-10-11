/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Sideraven's cold start, without and with its baseline profile (spec 0009). Reports time to
 * first frame and to fully drawn, as medians over the iterations.
 *
 * ```
 * ./gradlew :benchmark:connectedForkReleaseAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=pt.dweep.sideraven.benchmark.SideravenStartupBenchmark
 * ```
 */
@RunWith(AndroidJUnit4::class)
class SideravenStartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    private fun coldStart(compilation: CompilationMode) =
        rule.measureRepeated(
            packageName = SIDERAVEN_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilation,
            startupMode = StartupMode.COLD,
            iterations = 10,
        ) { sideravenStartupJourney() }

    @Test
    fun coldStartWithoutProfile() = coldStart(CompilationMode.None())

    @Test
    fun coldStartWithProfile() = coldStart(CompilationMode.Partial(BaselineProfileMode.Require))
}
