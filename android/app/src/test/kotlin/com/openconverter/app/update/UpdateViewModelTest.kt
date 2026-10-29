package com.openconverter.app.update

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {
    @Test fun permission_return_keeps_download_and_consumes_install_once() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var downloads = 0
            val asset = ReleaseAsset("1.4.5", "https://example.test/update.apk")
            val file = File("synthetic-update.apk")
            val model = UpdateViewModel("1.4.4", { asset }, { downloads++; file })
            model.update()
            advanceUntilIdle()
            assertEquals(file, model.state.value.pendingInstall)
            // Permission navigation and screen recreation use this same Activity ViewModel.
            model.update()
            advanceUntilIdle()
            assertEquals(1, downloads)
            model.installLaunched()
            assertEquals(null, model.state.value.pendingInstall)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun failed_download_can_be_retried() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var attempts = 0
            val model = UpdateViewModel("1.4.4", { ReleaseAsset("1.4.5", "https://example.test/update.apk") }, {
                attempts++
                if (attempts == 1) throw java.io.IOException("connection lost")
                File("synthetic-update.apk")
            })
            model.update()
            advanceUntilIdle()
            assertEquals(UpdatePhase.Failed, model.state.value.phase)
            model.update()
            advanceUntilIdle()
            assertEquals(UpdatePhase.Ready, model.state.value.phase)
            assertEquals(2, attempts)
        } finally { Dispatchers.resetMain() }
    }
}
