package com.humaneapps.catalogsales.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.TaskStackBuilder;
import android.widget.RemoteViews;

import com.humaneapps.catalogsales.MapActivity;
import com.humaneapps.catalogsales.R;
import com.humaneapps.catalogsales.Util;

/**
 *
 */
public class CustomersWidgetProvider extends AppWidgetProvider {

    private static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
                                int appWidgetId) {

        // Construct the RemoteViews object
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.customers_widget);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String title = sp.getString(context.getString(R.string.pref_company_name), "") + " "
                + context.getString(R.string.customers_widget_title);
        views.setTextViewText(R.id.widget_customers_title, title);

        // Create an Intent to launch MainActivity on click.
        Intent intent = new Intent(context, MapActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
        views.setOnClickPendingIntent(R.id.widget_customers_title, pendingIntent);

        views.setRemoteAdapter(R.id.widget_customers_list, new Intent(context,
                CustomersWidgetItemService.class));

        Intent clickIntentTemplate = new Intent(context, MapActivity.class);
        PendingIntent clickPendingIntentTemplate = TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(clickIntentTemplate)
                .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        views.setPendingIntentTemplate(R.id.widget_customers_list, clickPendingIntentTemplate);

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }


    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // There may be multiple widgets active, so update all of them
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
        context.startService(new Intent(context, CustomersWidgetUpdateService.class));
    }


    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        super.onReceive(context, intent);

        if (Util.ACTION_WIDGET_CUSTOMERS_UPDATED.equals(intent.getAction())) {

            context.startService(new Intent(context, CustomersWidgetUpdateService.class));

            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                    new ComponentName(context, getClass()));
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds,
                    R.id.widget_customers_list);
        }
    }


} // End CustomersWidgetProvider class.