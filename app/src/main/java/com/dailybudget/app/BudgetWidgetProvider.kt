package com.dailybudget.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import java.util.Locale

class BudgetWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == ACTION_REFRESH ||
            action == Intent.ACTION_DATE_CHANGED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED) {
            updateAllWidgets(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.dailybudget.app.ACTION_WIDGET_REFRESH"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, BudgetWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (widgetId in allWidgetIds) {
                updateWidget(context, appWidgetManager, widgetId)
            }
        }

        private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val db = DatabaseHelper.getInstance(context)
            val res = db.calculateCurrentBudget()

            val views = RemoteViews(context.packageName, R.layout.widget_budget)

            val roundedDaily = res.roundedDailyLimit
            val formattedDaily = String.format(Locale.getDefault(), "%,d ₽", roundedDaily)
            views.setTextViewText(R.id.tv_widget_daily_amount, formattedDaily)

            if (roundedDaily < 0) {
                views.setTextColor(R.id.tv_widget_daily_amount, Color.parseColor("#C62828"))
            } else {
                views.setTextColor(R.id.tv_widget_daily_amount, Color.parseColor("#2E7D32"))
            }

            views.setTextViewText(
                R.id.tv_widget_month_rem,
                String.format(Locale.getDefault(), "Остаток: %,.0f ₽", res.remainingBudgetRubles)
            )

            val appIntent = Intent(context, MainActivity::class.java)
            val appPendingIntent = PendingIntent.getActivity(
                context, 0, appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, appPendingIntent)

            val refreshIntent = Intent(context, BudgetWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context, widgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_refresh, refreshPendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
