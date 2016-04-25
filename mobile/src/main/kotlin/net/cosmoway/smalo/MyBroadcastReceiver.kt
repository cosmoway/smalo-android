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

        val bundle = intent.extras
        val list = bundle.getStringArray("valueList")
        val major = bundle.getString("major")
        val minor = bundle.getString("minor")

        if (sHandler != null) {
            val msg = Message()
            val data = Bundle()
            data.putString("major", major)
            data.putString("minor", minor)
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