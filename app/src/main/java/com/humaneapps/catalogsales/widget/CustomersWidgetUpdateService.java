package com.humaneapps.catalogsales.widget;

import android.app.IntentService;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.app.TaskStackBuilder;
import android.widget.RemoteViews;

import com.humaneapps.catalogsales.MapActivity;
import com.humaneapps.catalogsales.R;

/**
 * IntentService which handles updating all Today widgets with the latest data
 */
public class CustomersWidgetUpdateService extends IntentService {


    public CustomersWidgetUpdateService() {
        super("CustomersWidgetUpdateService");
    }


    @Override
    protected void onHandleIntent(Intent intent) {
        // Retrieve all of the widget ids: these are the widgets we need to update
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(this,
                CustomersWidgetProvider.class));

        // Perform this loop procedure for each widget.
        for (int appWidgetId : appWidgetIds) {

            int layoutId = R.layout.customers_widget;

            RemoteViews views = new RemoteViews(getPackageName(), layoutId);

            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
            String title = sp.getString(getString(R.string.pref_company_name), "") + " "
                    + getString(R.string.customers_widget_title);
            views.setTextViewText(R.id.widget_customers_title, title);

            // Create an Intent to launch MainActivity on click.
            Intent launchIntent = new Intent(this, MapActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, launchIntent, 0);
            views.setOnClickPendingIntent(R.id.widget_customers_title, pendingIntent);

            views.setRemoteAdapter(R.id.widget_customers_list, new Intent(this,
                    CustomersWidgetItemService.class));

            Intent clickIntentTemplate = new Intent(this, MapActivity.class);
            PendingIntent clickPendingIntentTemplate = TaskStackBuilder.create(this)
                    .addNextIntentWithParentStack(clickIntentTemplate)
                    .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

            views.setPendingIntentTemplate(R.id.widget_customers_list, clickPendingIntentTemplate);

            // Tell the AppWidgetManager to perform an update on the current app widget
            appWidgetManager.updateAppWidget(appWidgetId, views);

        } // End for each widget.

    } // End onHandleIntent method.


} // End CustomersWidgetUpdateService class.