package com.humaneapps.catalogsales.widget;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.AdapterView;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.humaneapps.catalogsales.R;
import com.humaneapps.catalogsales.Util;
import com.humaneapps.catalogsales.data.DbContract;


/**
 * RemoteViewsService controlling the data being shown in the scrollable weather detail widget
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class CustomersWidgetItemService extends RemoteViewsService {


    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {

        return new RemoteViewsFactory() {

            private String[] mCustomerNames = null;
            private int mCustomerCount;


            @Override
            public void onCreate() { /* Nothing to do */ }


            @Override
            public void onDataSetChanged() {
                // Clear and restore the calling identity so the ContentProvider can access the data.
                final long identityToken = Binder.clearCallingIdentity();
                Cursor customerData = getContentResolver().query(DbContract.getCustomersTableUri(),
                        null, null, null, null);
                if (customerData != null) {
                    int columnCustomerNameIndex = -1;
                    String[] columns = customerData.getColumnNames();
                    SharedPreferences sp =
                            PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                    String columnCustomerName =
                            sp.getString(DbContract.COLUMN_CUSTOMER_NAME_KEY, "");
                    if (!"".equals(columnCustomerName)) {
                        for (int i = 0; i < columns.length; i++) {
                            if (columnCustomerName.equals(columns[i])) {
                                columnCustomerNameIndex = i; break;
                            }
                        }
                    }
                    if (columnCustomerNameIndex > -1) {
                        int counter = 0;
                        mCustomerCount = customerData.getCount();
                        mCustomerNames = new String[mCustomerCount];
                        while (customerData.moveToNext()) {
                            mCustomerNames[counter] = customerData.getString(columnCustomerNameIndex);
                            counter++;
                        }
                    }

                    customerData.close();
                }
                Binder.restoreCallingIdentity(identityToken);
            }


            @Override
            public void onDestroy() {
                if (mCustomerNames != null) { mCustomerNames = null; }
            }


            @Override
            public int getCount() { return mCustomerNames == null ? 0 : mCustomerCount; }


            @Override
            public RemoteViews getViewAt(int position) {
                if (position == AdapterView.INVALID_POSITION || mCustomerNames == null
                        || position >= mCustomerCount) {
                    return null;
                }
                RemoteViews views = new RemoteViews(getPackageName(), R.layout.customers_widget_item);
                views.setTextViewText(R.id.widget_customer_name, mCustomerNames[position]);
                views.setContentDescription(R.id.widget_customer_name, mCustomerNames[position]);

                Bundle extras = new Bundle();
                extras.putInt(Util.ARG_CUSTOMER_INDEX, position);
                Intent fillInIntent = new Intent();
                fillInIntent.putExtras(extras);
                views.setOnClickFillInIntent(R.id.widget_customer_name, fillInIntent);
                return views;
            }


            @Override
            public RemoteViews getLoadingView() {
                return new RemoteViews(getPackageName(), R.layout.customers_widget_item);
            }


            @Override
            public int getViewTypeCount() {
                return 1;
            }


            @Override
            public long getItemId(int position) {
                return position;
            }


            @Override
            public boolean hasStableIds() {
                return true;
            }
        };
    }

}