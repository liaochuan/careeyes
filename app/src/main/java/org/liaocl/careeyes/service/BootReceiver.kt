package org.liaocl.careeyes.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.liaocl.careeyes.appContainer
import org.liaocl.careeyes.data.shouldRestoreAfterBoot

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = context.appContainer.settingsRepository.load()
                if (shouldRestoreAfterBoot(settings)) {
                    runCatching { EyeCareService.start(context, EyeCareService.ACTION_ENSURE) }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
