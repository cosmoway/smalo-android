package net.cosmoway.smalo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MyBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            val packageName = "net.cosmoway.smalo";
            val className = "net.cosmoway.smalo.MyService"
            intent.setClassName(packageName, className);
            context.startService(intent)
        }
    }
}
