package net.cosmoway.smalo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MyBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            context.startService(Intent(context, MyService::class.java))
        }
    }
}
