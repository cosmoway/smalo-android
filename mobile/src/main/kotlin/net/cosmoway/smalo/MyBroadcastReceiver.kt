package net.cosmoway.smalo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Message

/* Receiver内*/
class MyBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        val state = intent.getStringExtra("state")

        if (sHandler != null) {
            val msg = Message()
            val data = Bundle()
            data.putString("state", state)
            msg.data = data
            sHandler!!.sendMessage(msg)
        }
    }

    /**
     * メイン画面の表示を更新
     */
    fun registerHandler(handler: Handler) {
        sHandler = handler
    }

    companion object {
        var sHandler: Handler? = null
    }
}