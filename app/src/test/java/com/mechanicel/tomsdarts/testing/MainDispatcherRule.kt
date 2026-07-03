package com.mechanicel.tomsdarts.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit-Rule, die [Dispatchers.Main] fuer die Testdauer durch einen
 * Test-Dispatcher ersetzt und ihn nach dem Test wieder zuruecksetzt.
 *
 * Der gehaltene [testDispatcher] (und damit sein [TestDispatcher.scheduler])
 * muss an `runTest(...)` uebergeben werden, damit `runTest` denselben
 * Scheduler wie der `viewModelScope` (auf `Dispatchers.Main`) nutzt. Nur so
 * laeuft das `WhileSubscribed`-Sharing-Timeout deterministisch ab und aktive
 * Collectors/Subscriptions werden am Ende von `runTest` zuverlaessig
 * abgeraeumt, bevor z.B. `db.close()` in `@After` laeuft. Ohne diese
 * Kopplung wird die Suite flaky (sporadisch `UncaughtExceptionsBeforeTest`).
 *
 * Verwendung:
 * ```
 * @get:Rule
 * val mainDispatcherRule = MainDispatcherRule()
 *
 * @Test
 * fun foo() = runTest(mainDispatcherRule.testDispatcher.scheduler) { ... }
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
