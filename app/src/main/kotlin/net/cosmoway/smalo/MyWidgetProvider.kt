package net.cosmoway.smalo

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
import android.os.Handler
import android.os.Message
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.RemoteViews

class MyWidgetProvider : AppWidgetProvider() {

    private var remoteViews: RemoteViews? = null
    private var mReceiver: WidgetIntentReceiver? = null
    private var mIntentFilter: IntentFilter? = null

    // サービスから値を受け取ったら動かしたい内容を書く
    private val updateHandler = object : Handler() {
        override fun handleMessage(msg: Message) {

            val bundle = msg.data
            val message: String = bundle.getString("message")
            // テキストフィールドに文字表示
            remoteViews?.setTextViewText(R.id.title, message)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // ウィジェットレイアウトの初期化
        remoteViews = RemoteViews(context.packageName, R.layout.widget_layout)

        mReceiver = WidgetIntentReceiver()
        mIntentFilter = IntentFilter()
        (mIntentFilter as IntentFilter).addAction("UPDATE_ACTION")
        context.registerReceiver(mReceiver, mIntentFilter)

        (mReceiver as WidgetIntentReceiver).registerHandler(updateHandler)
    }
}
