/*
 * Copyright (C) 2017 Vladimir Markovic. All rights reserved.
 */

package com.humaneapps.catalogsales;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;


/**
 * Activity holding order fragment/s. When displaying existing orders that is the first fragment
 * and showing of specific order is second fragment. If showing certain order from the main screen
 * than that is the first fragment in the stack.
 */
public class OrderActivity extends AppCompatActivity {


    private CatalogSales mApplication;
    private boolean mShowExistingOrders;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_details);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mApplication = (CatalogSales) getApplication();
        // Show fragment: showing existing orders or certain order, depending on the passed in arg.
        Intent intent = getIntent();
        mShowExistingOrders = intent.getBooleanExtra(Util.STATE_SHOWING_EXISTING_ORDERS, false);
        String title = getString(R.string.title_order);
        if (mShowExistingOrders) { title = getString(R.string.title_existing_orders); }
        Util.showFragment(this, OrderFragment.newInstance(mShowExistingOrders), title, false, null);

    } // End onCreate


    @Override
    public void onBackPressed() {
        // Get back stack count before back press.
        int backStackCount = getSupportFragmentManager().getBackStackEntryCount();
        if (backStackCount > 0 && mShowExistingOrders) {
            // If order was shown in 'order display screen' from the 'existing orders screen':
            if (mApplication.isEditingOrder()) {
                // On click edit button, finish 'existing orders screen' to show 'main screen'
                // and set result flag to set layout to 'order editing main screen'.
                setResult(RESULT_OK);
                finish();
            } else {
                // If back button was pressed, just reset the title.
                setTitle(getString(R.string.title_existing_orders));
            }
        } else {
            // If order was shown in 'order display screen' from the 'main screen' (when it was
            // being taken or edited) and was then saved or canceled from 'order display screen',
            // reflect the changes in the main screen.
            if (!mApplication.isTakingOrder) {
                Util.endOrder(mApplication);
                setResult(RESULT_OK);
            }
        }

        super.onBackPressed();

    } // End onBackPressed


    // Passed on to fragment.
    void resetSelectAllIcon() {
        OrderFragment orderFragment = (OrderFragment) getSupportFragmentManager()
                .findFragmentByTag(getString(R.string.title_existing_orders));
        if (orderFragment != null) { orderFragment.resetSelectAllIcon(); }
    }




    @Override
    public void onResume() {
        super.onResume();
        // For receiving result from service update.
        LocalBroadcastManager.getInstance(this).registerReceiver(mLocalServiceUpdateReceiver,
                new IntentFilter(Util.ARG_UPDATE_SERVICE_BROADCAST));
    }


    @Override
    public void onPause() {
        super.onPause();
        // For receiving result from service update.
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocalServiceUpdateReceiver);
    }


    // Show update (products/customers) message if views order/s when starting the app.
    private final BroadcastReceiver mLocalServiceUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, final Intent intent) {
            Util.processUpdateResult(intent, (CatalogSales) getApplication(),
                    findViewById(android.R.id.content));
        }
    };


} // End OrderActivity class

