package net.cosmoway.smalo

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

class MyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // サービスの起動
        val i: Intent = Intent(context, MyService::class.java)
        context.startService(i)
    }
}
