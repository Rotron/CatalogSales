/*
 * Copyright (C) 2017 Vladimir Markovic. All rights reserved.
 */

package com.humaneapps.catalogsales;

import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.view.MenuItem;

import com.humaneapps.catalogsales.helper.AppCompatPreferenceActivity;

import java.util.ArrayList;
import java.util.List;


/**
 * Settings activity uses preference headers. Passes the results to MainActivity.
 */
public class SettingsActivity extends AppCompatPreferenceActivity {


    private final ArrayList<PreferenceActivity.Header> mHeaders = new ArrayList<>();
    // For determining whether to set content view from resource. Set to false in onAttachFragment.
    private boolean mFragmentNotAttached;
    // For finding fragment to pass it onRequestPermissionsResult.
    private int mFragId;
    // Activity results
    private Boolean mFinishMain, mResetGroupingColumns, mOtherColumnTitle, mRecreateMainActivity;
    private String mLoadImagesLocation;
    private int mGridColumnWidth = 0;

    private boolean mInitialized = true;


    public void setFragId(int id) { mFragId = id; }


    @Override
    public void onBuildHeaders(List<PreferenceActivity.Header> target) {
        loadHeadersFromResource(R.xml.preference_headers, target);
        // Set init flag
        mInitialized = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(getString(R.string.pref_initialized), false);
        // If not initialized, app starts from settings screen, 'Load' header - remove all
        // headers and show only 'Load'.
        if (!mInitialized) {
            PreferenceActivity.Header headerLoad = target.get(target.size() - 2);
            target.clear();
            target.add(headerLoad);
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mFragmentNotAttached = true;
        super.onCreate(savedInstanceState);
        // Set init flag
        mInitialized = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(getString(R.string.pref_initialized), false);
        // Preserve state for activity results for propagating changes to MainActivity.
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(Util.RESULT_FINISH_MAIN)) {
                mFinishMain = savedInstanceState.getBoolean(Util.RESULT_FINISH_MAIN);
                Util.setSettingsResultBln(this, Util.RESULT_FINISH_MAIN, mFinishMain);
            }
            if (savedInstanceState.containsKey(Util.RESULT_RECREATE_MAIN_ACTIVITY)) {
                mRecreateMainActivity =
                        savedInstanceState.getBoolean(Util.RESULT_RECREATE_MAIN_ACTIVITY);
                Util.setSettingsResultBln(this, Util.RESULT_RECREATE_MAIN_ACTIVITY,
                        mRecreateMainActivity);
            }
            if (savedInstanceState.containsKey(Util.RESULT_GRID_COLUMN_WIDTH)) {
                mGridColumnWidth = savedInstanceState.getInt(Util.RESULT_GRID_COLUMN_WIDTH);
                Util.setSettingsResultInt(this, Util.RESULT_GRID_COLUMN_WIDTH, mGridColumnWidth);
            }
            if (savedInstanceState.containsKey(Util.RESULT_LOAD_IMAGES_LOCATION)) {
                mLoadImagesLocation = savedInstanceState.getString(Util
                        .RESULT_LOAD_IMAGES_LOCATION);
                Util.setSettingsResultStr(this, Util.RESULT_LOAD_IMAGES_LOCATION,
                        mLoadImagesLocation);
            }
            if (savedInstanceState.containsKey(Util.RESULT_RESET_GROUPING_COLUMNS)) {
                mResetGroupingColumns = savedInstanceState.getBoolean(Util
                        .RESULT_RESET_GROUPING_COLUMNS);
                Util.setSettingsResultBln(this, Util.RESULT_RESET_GROUPING_COLUMNS,
                        mResetGroupingColumns);
            }
            if (savedInstanceState.containsKey(Util.RESULT_OTHER_COLUMN_TITLE)) {
                mOtherColumnTitle = savedInstanceState.getBoolean(Util.RESULT_OTHER_COLUMN_TITLE);
                Util.setSettingsResultBln(this, Util.RESULT_OTHER_COLUMN_TITLE, mOtherColumnTitle);
            }
        }
    }


    // Save state for activity results for propagating changes to MainActivity.
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mGridColumnWidth > 0) {
            outState.putInt(Util.RESULT_GRID_COLUMN_WIDTH, mGridColumnWidth);
        }
        if (mFinishMain != null) { outState.putBoolean(Util.RESULT_FINISH_MAIN, mFinishMain); }
        if (mRecreateMainActivity != null) {
            outState.putBoolean(Util.RESULT_RECREATE_MAIN_ACTIVITY, mRecreateMainActivity);
        }
        if (mLoadImagesLocation != null) {
            outState.putString(Util.RESULT_LOAD_IMAGES_LOCATION, mLoadImagesLocation);
        }
        if (mResetGroupingColumns != null) {
            outState.putBoolean(Util.RESULT_RESET_GROUPING_COLUMNS, mResetGroupingColumns);
        }
        if (mOtherColumnTitle != null) {
            outState.putBoolean(Util.RESULT_OTHER_COLUMN_TITLE, mOtherColumnTitle);
        }
    }


    // If fragment is not attached via header, set content view from layout resource.
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (mFragmentNotAttached) { setContentView(R.layout.activity_settings); }
    }


    // Set flag for fragment attachment.
    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);
        mFragmentNotAttached = false;
    }


    // Security requirement.
    @Override
    protected boolean isValidFragment(String fragmentName) {
        if (mHeaders.size() == 0) {
            loadHeadersFromResource(R.xml.preference_headers, mHeaders);
        }
        for (PreferenceActivity.Header header : mHeaders) {
            if (fragmentName.equals(header.fragment)) { return true; }
        }
        return false;
    }


    // Back-press on back arrow click.
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { onBackPressed(); }
        return super.onOptionsItemSelected(item);
    }


    // Override to propagate result.
    @Override
    public void startActivity(Intent intent) {
        super.startActivityForResult(intent, Util.SETTINGS_ACTIVITY_RESULT_CODE);
    }


    // Propagate result to MainActivity and populate fields for saving state.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        setResult(resultCode, data);
        if (data != null) {
            if (data.hasExtra(Util.RESULT_FINISH_MAIN)) {
                mFinishMain = data.getBooleanExtra(Util.RESULT_FINISH_MAIN, false);
                finish();
            }
            if (data.hasExtra(Util.RESULT_RECREATE_MAIN_ACTIVITY)) {
                mRecreateMainActivity =
                        data.getBooleanExtra(Util.RESULT_RECREATE_MAIN_ACTIVITY, false);
            }
            if (data.hasExtra(Util.RESULT_GRID_COLUMN_WIDTH)) {
                mGridColumnWidth = data.getIntExtra(Util.RESULT_GRID_COLUMN_WIDTH, 0);
            }
            if (data.hasExtra(Util.RESULT_LOAD_IMAGES_LOCATION)) {
                mLoadImagesLocation = data.getStringExtra(Util.RESULT_LOAD_IMAGES_LOCATION);
            }
            if (data.hasExtra(Util.RESULT_RESET_GROUPING_COLUMNS)) {
                mResetGroupingColumns = data.getBooleanExtra(Util.RESULT_RESET_GROUPING_COLUMNS,
                        false);
            }
            if (data.hasExtra(Util.RESULT_OTHER_COLUMN_TITLE)) {
                mOtherColumnTitle = data.getBooleanExtra(Util.RESULT_OTHER_COLUMN_TITLE, false);
            }
        }
    }


    // Call fragments onRequestPermissionsResult.
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        SettingsFragment fragment = (SettingsFragment) getFragmentManager()
                .findFragmentById(mFragId);
        fragment.onRequestPermissionsResult(requestCode, permissions, grantResults);
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
            if (mInitialized) {
                Util.processUpdateResult(intent, (CatalogSales) getApplication(),
                        findViewById(android.R.id.content));
            }
        }
    };


} // End SettingsActivity class

