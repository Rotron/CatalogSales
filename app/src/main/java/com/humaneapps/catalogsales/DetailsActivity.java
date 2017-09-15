/*
 * Copyright (C) 2017 Vladimir Markovic. All rights reserved.
 */

package com.humaneapps.catalogsales;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

/**
 * Activity for details screen - for holding DetailsFragment for one pane layout (in two pane
 * layout DetailsFragment is shown from the MainActivity).
 */
public class DetailsActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_details);

        // Application level class containing common data needed across the application.
        CatalogSales application = (CatalogSales) getApplication();
        // Get and store orientation.
        boolean isLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        // Set mode for detecting split screen (in tablet landscape if settings is on)
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        boolean twoPaneOn = sp.getBoolean(getString(R.string.pref_two_pane), false);
        boolean twoPane = twoPaneOn && isLandscape;

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Show details fragment passing selected product data to it.
        if (savedInstanceState == null) {
            Intent intent = getIntent();
            Bundle bundle = new Bundle();
            // Product
            bundle.putParcelable(Util.ARG_PRODUCT_PARCEL,
                    intent.getParcelableExtra(Util.ARG_PRODUCT_PARCEL));
            // Toolbar height - for determining image height.
            bundle.putInt(Util.ARG_TOOLBAR_HEIGHT,
                    intent.getIntExtra(Util.ARG_TOOLBAR_HEIGHT, 100));
            Util.showFragment(this, DetailsFragment.newInstance(),
                    getString(R.string.title_details), false, bundle);
            if (twoPaneOn) { application.setShownProductDetails(bundle); }
        } else {
            // In tablets, if two pane mode is on, if details screen was shown in portrait before
            // rotation (from DetailsActivity), it needs to be closed as in two-pane landscape
            // details fragment will be shown from MainActivity.
            if (twoPane) { finish(); }
        }

    } // End onCreate


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


} // End DetailsActivity class

