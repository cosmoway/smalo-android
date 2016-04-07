package net.cosmoway.smalo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.widget.RemoteViews

class WidgetIntentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        val bundle = intent.extras
        val message = bundle.getString("message")

        if (sHandler != null) {
            val msg = Message()
            val data = Bundle()
            data.putString("message", message)
            msg.data = data
            sHandler!!.sendMessage(msg)
        }
    }

    /**
     * メイン画面の表示を更新
     */
    fun registerHandler(locationUpdateHandler: Handler) {
        sHandler = locationUpdateHandler
    }

    companion object {
        var sHandler: Handler? = null
    }
}
