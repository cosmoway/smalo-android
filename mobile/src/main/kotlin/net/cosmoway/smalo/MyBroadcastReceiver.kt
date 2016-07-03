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

        val state = intent.getStringExtra(MyService.EXTRA_STATE)

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
        // FIXME: static になっているので、複数の箇所でこの Receiver を利用したい場合にバグが発生しそう
        var sHandler: Handler? = null
    }
}