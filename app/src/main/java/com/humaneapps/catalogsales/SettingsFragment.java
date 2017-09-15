/*
 * Copyright (C) 2017 Vladimir Markovic. All rights reserved.
 */

package com.humaneapps.catalogsales;


import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.humaneapps.catalogsales.data.DbContract;
import com.humaneapps.catalogsales.helper.AdvancedCrypto;
import com.humaneapps.catalogsales.service.ServiceUpdate;
import com.humaneapps.catalogsales.widget.CustomersWidgetUpdateService;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static android.preference.PreferenceActivity.EXTRA_SHOW_FRAGMENT;

/**
 * PreferenceFragment for displaying and handling preferences. It also works as initialization
 * screen for initializing app on install - handles user input for data and images location
 * and structure of the data for customers and products tables.
 */
public class SettingsFragment extends PreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    // Keys for saving state
    private final String STATE_SHOW_CONTINUE = "STATE_SHOW_CONTINUE";
    private final String STATE_SHOWN_PC = "STATE_SHOWN_PREF_CATEGORY";
    private final String STATE_WRITE_DB_DONE = "STATE_WRITE_DB_DONE";
    private final String STATE_INIT_MESSAGE = "STATE_INIT_MESSAGE";
    private final String STATE_PREF_INDEX = "STATE_PREF_INDEX";
    private final String STATE_PROGRESS_DIALOG = "STATE_PROGRESS_DIALOG";
    // Indices of relevant preferences as ordered in 'Load' preference screen.
    private final int PREF_DROPBOX_TOKEN_INDEX = 1;
    private final int PREF_PRODUCTS_LOCATION_INDEX = 3;
    // Indices of relevant preferences as ordered in 'Other columns' preference screen.
    @SuppressWarnings("FieldCanBeLocal")
    private final int PREF_ACTIVE_BOOL_PARAM_INDEX = 6;

    // Application level class containing common data needed across the mApplication.
    private CatalogSales mApplication;

    // Fields used with initialization:

    // For storing state to distinguish if initialization is completed or not.
    private boolean mInitialized;
    // For keeping track of what columns to display to user to choose from (don't display already
    // assigned ones). At the end of assigning all of the main columns, this list will contain
    // 'other' columns (other to main).
    private ArrayList<String> mUnassignedProductColumns;
    // Used in initialization for holding preferences from preference screen to display them one
    // by one. It is needed because preference screen gets cleared.
    private Preference[] mInitPreferences;
    // For setting activity results when related preference is changed - used to propagate change
    // back to MainActivity (via SettingsActivity).
    private Boolean mFinishMain, mResetGroupingProductColumns, mOtherProductColumnTitle,
            mRecreateMainActivity;
    private String mLoadImagesLocation;
    private int mGridColumnWidth = 0;
    // For displaying progress spinner while data is being fetched and processed.
    @SuppressWarnings("deprecation")
    private ProgressDialog mProgressDialog;
    private String mProgressDialogMessage;
    // For keeping track/saving state if Continue 'button' preference is/was shown.
    private boolean mShowContinue;
    // For keeping track/saving state of which preference category is/was shown/processed.
    private String mCategoryKey;
    // For keeping track/saving state of which preference is/was shown/processed within a category.
    private int mPrefIndex;
    // For saving state of a message if it was shown.
    private String mShownMessage;
    // For determining if data for customers and products into corresponding tables is done.
    private boolean mWriteDbDone;


    // Constructor
    public SettingsFragment() {}


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        // Init mApplication object containing common data needed across the mApplication components.
        mApplication = (CatalogSales) context.getApplicationContext();
    }


    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Init mApplication object containing common data needed across the mApplication components.
        mApplication = (CatalogSales) getActivity().getApplicationContext();
    }


    // Instantiate preferences, set their summary and listeners.
    @Override
    @SuppressWarnings("deprecation")
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle
            savedInstanceState) {
        // Inflate view.
        View view = super.onCreateView(inflater, container, savedInstanceState);
        // Init mApplication object containing common data needed across the mApplication components.
        mApplication = (CatalogSales) getActivity().getApplicationContext();
        // Save fragment id in activity to be able to call fragment's onRequestPermissionsResult
        // from activity's onRequestPermissionsResult.
        ((SettingsActivity) getActivity()).setFragId(getId());

        // Retrieve saved instance state.
        if (savedInstanceState != null) {
            // Retrieve state for activity results if existent.
            if (savedInstanceState.containsKey(Util.RESULT_FINISH_MAIN)) {
                mFinishMain = savedInstanceState.getBoolean(Util.RESULT_FINISH_MAIN);
                Util.setSettingsResultBln(getActivity(), Util.RESULT_FINISH_MAIN, mFinishMain);
            }
            if (savedInstanceState.containsKey(Util.RESULT_RECREATE_MAIN_ACTIVITY)) {
                mRecreateMainActivity =
                        savedInstanceState.getBoolean(Util.RESULT_RECREATE_MAIN_ACTIVITY);
                Util.setSettingsResultBln(getActivity(), Util.RESULT_RECREATE_MAIN_ACTIVITY,
                        mRecreateMainActivity);
            }
            if (savedInstanceState.containsKey(Util.RESULT_GRID_COLUMN_WIDTH)) {
                mGridColumnWidth = savedInstanceState.getInt(Util.RESULT_GRID_COLUMN_WIDTH);
                Util.setSettingsResultInt(getActivity(), Util.RESULT_GRID_COLUMN_WIDTH,
                        mGridColumnWidth);
            }
            if (savedInstanceState.containsKey(Util.RESULT_LOAD_IMAGES_LOCATION)) {
                mLoadImagesLocation = savedInstanceState.getString(Util
                        .RESULT_LOAD_IMAGES_LOCATION);
                Util.setSettingsResultStr(getActivity(), Util.RESULT_LOAD_IMAGES_LOCATION,
                        mLoadImagesLocation);
            }
            if (savedInstanceState.containsKey(Util.RESULT_RESET_GROUPING_COLUMNS)) {
                mResetGroupingProductColumns = savedInstanceState.getBoolean(Util
                        .RESULT_RESET_GROUPING_COLUMNS);
            }
            if (savedInstanceState.containsKey(Util.RESULT_OTHER_COLUMN_TITLE)) {
                mOtherProductColumnTitle = savedInstanceState.getBoolean(
                        Util.RESULT_OTHER_COLUMN_TITLE);
            }
            if (savedInstanceState.containsKey(DbContract.COLUMN_PRODUCT_ACTIVE_BOOL_KEY)) {
                mApplication.setActiveProductColumnBoolFormat(savedInstanceState
                        .getString(DbContract.COLUMN_PRODUCT_ACTIVE_BOOL_KEY));
            }
            // Retrieve state when initializing.
            if (!mInitialized) {
                mShowContinue = savedInstanceState.getBoolean(STATE_SHOW_CONTINUE);
                mWriteDbDone = savedInstanceState.getBoolean(STATE_WRITE_DB_DONE);
                mPrefIndex = savedInstanceState.getInt(STATE_PREF_INDEX);
                mCategoryKey = savedInstanceState.getString(STATE_SHOWN_PC);
                mShownMessage = savedInstanceState.getString(STATE_INIT_MESSAGE);
            }
            // Retrieve state of progressbar if it was showing.
            if (savedInstanceState.containsKey(STATE_PROGRESS_DIALOG)) {
                mProgressDialogMessage = savedInstanceState.getString(STATE_PROGRESS_DIALOG);
                mProgressDialog = ProgressDialog.show(getActivity(), "", mProgressDialogMessage,
                        true, true);
            }
        }

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());

        // Check if initialized.
        mInitialized = sp.getBoolean(getString(R.string.pref_initialized), false);
        if (mInitialized) {
            // If initialized, load static (defined in xml) preferences, set their summary and
            // onPreferenceChange listener.
            setPreferences(sp);
        } else {
            // If not initialized, initialize.
            if (savedInstanceState == null) {
                startInit(sp);
            } else {
                // On configuration change preserve shown screen.
                showRestoredState(sp, mPrefIndex);
            }
        }
        return view;
    }


    // If initialized, load static (defined in xml) preferences, set their summary and
    // onPreferenceChange listener.
    private void setPreferences(final SharedPreferences sp) {
       if (getArguments() != null) {
            String prefScreenName = getArguments().getString(getString(R.string.pref_screen));
            if (prefScreenName != null) {
                PreferenceManager pm = getPreferenceManager();
                if (prefScreenName.equals(getString(R.string.pref_category_general))) {
                    // General preferences
                    addPreferencesFromResource(R.xml.preference_general);
                    // Preference for setting image size.
                    setPreference(pm, getString(R.string.pref_column_size));
                    // Preference for setting what to show in main-order screen when coming from
                    // 'edit order': all products, ordered products or previous selection.
                    setPreference(pm, getString(R.string.pref_on_edit_order));
                    // Preference for determining whether to display order sum tax inclusive or not.
                    Preference prefOrderSumTaxInc = pm.findPreference(getString(R.string
                            .pref_sum_tax_inclusive));
                    prefOrderSumTaxInc.setOnPreferenceChangeListener(this);
                    PreferenceCategory pc = (PreferenceCategory) getPreferenceScreen()
                            .getPreference(0);
                   // Preference for showing two pane view in landscape for tablets.
                    boolean isTablet = getResources().getBoolean(R.bool.tablet);
                    if (isTablet) {
                        Preference prefShowTwoPane = new SwitchPreference(getActivity());
                        prefShowTwoPane.setKey(getString(R.string.pref_two_pane));
                        prefShowTwoPane.setTitle(getString(R.string.pref_two_pane));
                        prefShowTwoPane.setDefaultValue(false);
                        pc.addPreference(prefShowTwoPane);
                        setPreference(pm, getString(R.string.pref_two_pane));
                    }
                    addSpace(pc);
                    // Preference which destroys app cache on click so images can be reloaded.
                    Preference prefUpdateImages = new Preference(getActivity());
                    prefUpdateImages.setKey(getString(R.string.pref_update_images));
                    prefUpdateImages.setTitle(getString(R.string.pref_update_images));
                    prefUpdateImages.setPersistent(false);
                    prefUpdateImages.setOnPreferenceClickListener(mUpdateImagesListener);
                    pc.addPreference(prefUpdateImages);
                } else if (prefScreenName.equals(getString(R.string.pref_category_customize))) {
                    // Customization preferences
                    addPreferencesFromResource(R.xml.preference_customize);
                    // Preference for specifying company name.
                    setPreference(pm, getString(R.string.pref_company_name));
                    // Preference for specifying sales person - used in order details.
                    setPreference(pm, getString(R.string.pref_sales_person_first_name));
                    setPreference(pm, getString(R.string.pref_sales_person_last_name));
                    // Preferences for email defaults (when emailing orders).
                    setPreference(pm, getString(R.string.pref_recipient_email));
                    setPreference(pm, getString(R.string.pref_cc_email));
                    setPreference(pm, getString(R.string.pref_bcc_email));
                    setPreference(pm, getString(R.string.pref_email_subject));
                    setPreference(pm, getString(R.string.pref_email_text));
                } else if (prefScreenName.equals(getString(R.string.pref_category_load))) {
                    // Load preferences
                    addPreferencesFromResource(R.xml.preference_load);
                    // Drop box token if drop box is used to store data.
                    Preference prefDropBoxToken =
                            findPreference(getString(R.string.pref_dropbox_token));
                    setPreferenceSummary(prefDropBoxToken, "");
                    prefDropBoxToken.setOnPreferenceChangeListener(this);
                    // Preference for specifying full path to actual image location - url or dir.
                    setPreference(pm, getString(R.string.pref_images_location));
                    // Preference for specifying full path to customers data location - url or dir.
                    setPreference(pm, getString(R.string.pref_customers_data_location));
                    // Preference for specifying full path to products data location - url or dir.
                    setPreference(pm, getString(R.string.pref_products_data_location));
                } else if (prefScreenName.equals(getString(R.string
                        .pref_category_main_columns))) {
                    // Preferences for specifying main columns (list) - dynamically added.
                    addPreferencesFromResource(R.xml.preference_main_columns);
                    // First preference in the screen is a pref. category - get reference to it.
                    final PreferenceCategory pc = (PreferenceCategory) getPreferenceScreen()
                            .getPreference(0);
                    // Create one list preference for each main column(containing list of all
                    // loaded columns to choose from).
                    createMainColumnsPreferences(sp, pc);
                    // Disable this category as changing these preference is allowed only one by
                    // one, since they all have to be set at once.
                    pc.setEnabled(false);
                    // Hold reference to list of preferences in this category to be able to show
                    // them one by one.
                    populateInitPreferences(pc);
                    // Create preference that acts like a button, which when pressed starts
                    // altering procedure which doesn't allow app to resume until finished -
                    // until all main preferences are set.
                    Preference prefAlter = new Preference(getActivity());
                    prefAlter.setKey(getString(R.string.pref_alter));
                    prefAlter.setTitle(getString(R.string.pref_alter));
                    prefAlter.setPersistent(false);
                    // On click to Alter, remove all preferences for main columns as this allows
                    // them to be re-set, and restart to show them one by one for setting.
                    prefAlter.setOnPreferenceClickListener(new Preference
                            .OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(Preference preference) {
                            // If need to fetch data from online url, only allow this if online.
                            boolean cleared = clearMainColumnsPreferences(sp);
                            if (cleared) { restartSettingsScreen(); }
                            return true;
                        }
                    });
                    getPreferenceScreen().addPreference(prefAlter);
                } else if (prefScreenName.equals(getString(R.string
                        .pref_category_other_product_columns))) {
                    // Preferences for specifying other columns - dynamically added (not in xml).
                    addPreferencesFromResource(R.xml.preference_other_product_columns);
                    // First preference in the screen is a pref. category - get reference to it.
                    PreferenceCategory pc = (PreferenceCategory) getPreferenceScreen()
                            .getPreference(0);
                    // Create one multi-select preference for selecting grouping columns and one
                    // for selecting other columns to display as details (multi-select) and one
                    // edit-text pref. for each for 'other' columns to set its descriptive title.
                    createOtherProductColumnsPreferences(sp, pc);
                }
            }
        }
    }


    // Helper method for setting preference summary and onPreferenceChangeListener to passed pref.
    private void setPreference(PreferenceManager pm, String key) {
        Preference preference = pm.findPreference(key);
        setPreferenceSummary(preference);
        preference.setOnPreferenceChangeListener(this);
    }


    @Override
    public void onResume() {
        super.onResume();
        // Set main title.
        getActivity().setTitle(getString(R.string.title_settings));
        // For receiving result from service update.
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver
                (mLocalServiceUpdateReceiver,
                        new IntentFilter(Util.ARG_UPDATE_SERVICE_BROADCAST));
    }


    @Override
    public void onPause() {
        super.onPause();
        // For receiving result from service update.
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver
                (mLocalServiceUpdateReceiver);
    }


    // Overloaded to simplify method call for passing only preference, since value we can get. Still
    // setPreferenceSummary method is needed - with both preference and value as arguments, as both
    // arguments are returned by the onPreferenceChange method in which setPreferenceSummary is
    // called.
    private void setPreferenceSummary(Preference preference) {
        setPreferenceSummary(preference, getPreferenceValueAsString(preference));
    }


    // Get preference value for the specified preference - used in overloaded setPreferenceSummary.
    private String getPreferenceValueAsString(Preference preference) {
        // On error - if passed preference is null, return without setting.
        if (preference == null) { return ""; }
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(preference
                .getContext());

        String strPrefValue;

        if (preference instanceof SwitchPreference) {
            strPrefValue = sharedPrefs.getBoolean(preference.getKey(), true) + "";
        } else if (preference instanceof MultiSelectListPreference) {
            Set<String> selectionSet = sharedPrefs.getStringSet(preference.getKey(), null);
            if (selectionSet == null) { return ""; }
            strPrefValue = selectionSet.toString();
        } else {
            strPrefValue = sharedPrefs.getString(preference.getKey(), "");
        }

        return strPrefValue;

    } // End getPreferenceValueAsString


    // Set preference summary to specified value.
    private void setPreferenceSummary(Preference preference, Object value) {

        // On error - if passed preference is null, return without setting
        if (preference == null) { return; }
        // Don't ever set summary to dropbox token preference.
        if (getString(R.string.pref_dropbox_token).equals(preference.getKey())) { return; }

        String strValue = value.toString();

        // Set summary for specified preference.
        if (preference instanceof ListPreference) {
            // For list preferences, look up the correct display value in
            // the preference's 'entries' list (since they have separate entries/values).
            ListPreference listPreference = (ListPreference) preference;
            int prefIndex = listPreference.findIndexOfValue(strValue);
            if (prefIndex >= 0) {
                strValue = listPreference.getEntries()[prefIndex].toString();
            }
        } else if (preference instanceof MultiSelectListPreference) {
            // For multi-select preferences with nothing selected.
            if (strValue.length() > 1) {
                String[] values = Util.removeBrackets(strValue).split(", ");
                Arrays.sort(values);
                strValue = Util.addBrackets(TextUtils.join(", ", values));
                if ("[]".equals(strValue)) {
                    strValue = "";
                }
            }
        } else if (preference instanceof SwitchPreference) {
            // Do not set summary for switch preferences.
            return;
        }

        // For other preferences, set the summary to the value's simple string representation.
        preference.setSummary(strValue);

    } // End setPreferenceSummary method.


    /**
     * Deal with preference changes and update preference summary to new value after user changes
     * it.
     *
     * @param preference - preference that changed.
     * @param value      - updated preference value.
     * @return true if all went well.
     */
    @Override
    @SuppressWarnings("unchecked")
    public boolean onPreferenceChange(Preference preference, Object value) {

        String key = preference.getKey();
        String strValue = value.toString();
        boolean updateToNewValue = true;

        if (key.equals(getString(R.string.pref_column_size))) {
            // Poster size preference :
            // Set RecyclerView (column No) to new poster size and force it to redraw.
            mGridColumnWidth = Integer.parseInt(strValue);
            Util.setSettingsResultInt(getActivity(), Util.RESULT_GRID_COLUMN_WIDTH,
                    mGridColumnWidth);
        } else if (key.equals(getString(R.string.pref_images_location))) {
            // Propagate change to MainActivity to set to new image size.
            mLoadImagesLocation = strValue;
            Util.setSettingsResultStr(getActivity(), Util.RESULT_LOAD_IMAGES_LOCATION,
                    mLoadImagesLocation);
        } else if (key.equals(getString(R.string.pref_two_pane))) {
            // Propagate change to MainActivity to process it.
            if (!(boolean)value) { mApplication.setShownProductDetails(null); }
            mRecreateMainActivity = true;
            Util.setSettingsResultBln(getActivity(), Util.RESULT_RECREATE_MAIN_ACTIVITY,
                    mRecreateMainActivity);
        } else if (key.equals(getString(R.string.pref_company_name))) {
            // If company name changed, update widgets.
            mApplication.startService(new Intent(mApplication, CustomersWidgetUpdateService.class));
        } else if (key.equals(DbContract.COLUMN_PRODUCT_ACTIVE_KEY)) {
            // If column active changed, remove boolean format so it can be re-set.
            PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
                    .remove(DbContract.COLUMN_PRODUCT_ACTIVE_BOOL_KEY).apply();
        } else if (key.equals(DbContract.COLUMN_PRODUCT_ACTIVE_BOOL_KEY)) {
            // If boolean format for column active changed, reflect in mApplication class.
            mApplication.setActiveProductColumnBoolFormat(strValue);
            // Refresh recycler view.
            getActivity().setResult(Activity.RESULT_OK, null);
        } else if (key.equals(DbContract.PRODUCT_COLUMNS_GROUPING_KEY)) {
            // Propagate change to MainActivity to process it.
            mResetGroupingProductColumns = true;
            Util.setSettingsResultBln(getActivity(), Util.RESULT_RESET_GROUPING_COLUMNS,
                    mResetGroupingProductColumns);
        } else if (key.indexOf(DbContract.OTHER_PRODUCT_COLUMNS_TITLE_PREF_PREFIX) == 0
                || key.equals(DbContract.PRODUCT_COLUMNS_OTHER_DETAILS_KEY)) {
            // Propagate change to MainActivity to process it.
            mOtherProductColumnTitle = true;
            Util.setSettingsResultBln(getActivity(), Util.RESULT_OTHER_COLUMN_TITLE,
                    mOtherProductColumnTitle);
        } else if (key.equals(DbContract.PREF_CUSTOMER_OTHER_DETAILS_COLUMNS_KEY)) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
            SharedPreferences.Editor editor = sp.edit();
            editor.putStringSet(key, (Set<String>)value);
            editor.apply();
            mApplication.setCustomerColumns(sp);
            updateToNewValue = false;
        } else if (key.equals(getString(R.string.pref_dropbox_token))) {
            String message;
            try {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
                String encryptedToken = AdvancedCrypto.encrypt(AdvancedCrypto.getSecretKey(
                        BuildConfig.DROPBOX_SALT_PASS, BuildConfig.DROPBOX_SALT), strValue);
                SharedPreferences.Editor editor = sp.edit();
                editor.putString(key, encryptedToken);
                editor.apply();
                message = getString(R.string.message_dropbox_token_set);
            } catch (Exception e) {
                message = getString(R.string.warning_wrong_dropbox_token);
            }
            Util.snackOrToast(mApplication, getView(), message, Snackbar.LENGTH_LONG);
            // Prevent overriding of the preference to the input token.
            updateToNewValue = false;
            // Prevent setting summary to drop box token preference.
            value = "";
        } // End if drop box token preference.

        // If not initialized, continue initializing sequence.
        if (!mInitialized && !"".equals(strValue)) {
            showSinglePreference(null, preference, true);
        }
        // Set summary for changed preference to reflect the change.
        setPreferenceSummary(preference, value);

        return updateToNewValue;
    } // End method onPreferenceChange(Preference preference, Object value).


    // Used in initialization to get 'storage' permission to be able to access and save files.
    // On first user click (on 'Continue' or 'Skip') the request permission dialog is shown and
    // only if permission is granted, continue/skip - from here.
    @Override
    @SuppressWarnings("deprecation")
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == getResources().getInteger(R.integer
                .permission_external_storage_id)) {
            // For when user pressed 'Continue' on first init screen.
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                doContinue(null, null);
            } else { notifyPermissionNeeded(); }
        } else if (requestCode == getResources().getInteger(R.integer
                .permission_external_storage_on_skip_id)) {
            // For when user pressed 'Skip' on first init screen.
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Util.skipInit(getActivity());
                mProgressDialogMessage = getString(R.string.loading) + "...";
                mProgressDialog = ProgressDialog.show(getActivity(), "", mProgressDialogMessage);
            } else { notifyPermissionNeeded(); }
        }
    }


    // Toast message to show if user does not grant 'storage' permission.
    private void notifyPermissionNeeded() {
        Toast.makeText(getActivity(), getString(R.string.permission_storage_needed),
                Toast.LENGTH_LONG).show();
    }


    // If not initialized this is the first init method called on app start (but not on rotation).
    private void startInit(SharedPreferences sp) {
        mCategoryKey = getString(R.string.pref_category_load);
        PreferenceCategory pc = readyCategory(sp);
        if (pc == null) { return; }
        if (!sp.contains(getString(R.string.pref_dropbox_token))) {
            showStartMessage(pc);
        } else {
            mPrefIndex = findNextPref(sp);
            showSinglePreference(null, mInitPreferences[mPrefIndex], false);
        }
    }


    // Find first unset preference in current preference category.
    private int findNextPrefInCategory(SharedPreferences sp) {
        String key;
        // Traverse all preferences in this category.
        for (int i = 0; i < mInitPreferences.length; i++) {
            key = mInitPreferences[i].getKey();
            if (sp.contains(key)) {
                // For all already set preferences, remove from unassigned columns to adjust it.
                if (mUnassignedProductColumns != null
                        && key.indexOf(DbContract.MAIN_PRODUCT_COLUMNS_PREF_PREFIX) == 0
                        && !"".equals(sp.getString(key, ""))) {
                    mUnassignedProductColumns.remove(sp.getString(key, ""));
                }
            } else {
                // For when altering main columns, to fetch columns first and give option to reset.
                if (DbContract.COLUMN_CUSTOMER_NAME_KEY.equals(key)) { getBothColumns(); }
                // If found pref. which is not set, return its index in category.
                return i;
            }
        }

        return -1;

    } // End findNextPref method.


    // Find next unset preference (any preference category - if all set in first category, go to
    // next until you wind the first unset). If all are set, finish initialization.
    private int findNextPref(SharedPreferences sp) {
        int foundPref = findNextPrefInCategory(sp);
        if (foundPref > -1) {
            return foundPref;
        } else {
            PreferenceCategory pc = nextCategory(sp);
            // pc is null when it comes to the end after other product columns category is done.
            // setInitialized method is called;
            if (pc == null) { return 0; }
            return findNextPref(sp);
        }
    } // End findNextPref method.


    // For when altering main columns, to fetch columns first and give option to reset.
    @SuppressWarnings("deprecation")
    private void getBothColumns() {
        Intent intent = new Intent(getActivity(), ServiceUpdate.class);
        intent.putExtra(Util.ARG_GET_COLUMNS, true);
        intent.putExtra(Util.ARG_DO_CUSTOMERS, true);
        intent.putExtra(Util.ARG_DO_PRODUCTS, true);
        getActivity().startService(intent);
        mProgressDialogMessage = getString(R.string.loading) + "...";
        mProgressDialog = ProgressDialog.show(getActivity(), "", mProgressDialogMessage);
    }


    // Init next preference category
    private PreferenceCategory nextCategory(@Nullable SharedPreferences sp) {

        if (mCategoryKey.equals(getString(R.string.pref_category_load))) {
            // From load to main columns pref. category
            mCategoryKey = getString(R.string.pref_category_main_columns);
        } else if (mCategoryKey.equals(getString(R.string.pref_category_main_columns))) {
            // From main columns to other columns pref. category
            mCategoryKey = getString(R.string.pref_category_other_product_columns);
        } else if (mCategoryKey.equals(getString(R.string.pref_category_other_product_columns))) {
            // From other columns (last) pref. category to finish initialization.
            setInitialized(sp);
            return null;
        }

        PreferenceCategory pc = readyCategory(sp);
        if (pc == null) { return null; }
        pc.removeAll();
        return pc;
    }


    // Ready current preference category (determined by category key).
    private PreferenceCategory readyCategory(@Nullable SharedPreferences sp) {
        PreferenceCategory pc = null;
        if (mCategoryKey.equals(getString(R.string.pref_category_load))) {
            pc = readyCategoryLoad();
        } else if (mCategoryKey.equals(getString(R.string.pref_category_main_columns))) {
            pc = readyCategoryMainColumns(sp);
        } else if (mCategoryKey.equals(getString(R.string.pref_category_other_product_columns))) {
            pc = readyCategoryOtherProductColumns(sp);
            if (pc == null) {
                setInitialized(sp);
                return null;
            }
        }
        return pc;
    }


    // Add 'Load' preferences from xml (images, customers and products locations).
    private PreferenceCategory readyCategoryLoad() {
        PreferenceScreen ps = getPreferenceScreen();
        if (ps != null) { ps.removeAll(); }
        addPreferencesFromResource(R.xml.preference_load);
        PreferenceCategory pc = (PreferenceCategory) getPreferenceScreen().getPreference(0);
        mCategoryKey = pc.getKey();
        populateInitPreferences(pc);
        return pc;
    }


    // Create 'main columns' preferences - for specifying which column from loaded corresponds to
    // columns: 'customer name', 'customer id', 'product name', 'image name', 'price', 'tax',
    // 'pack size', 'active' and 'product id'.
    private PreferenceCategory readyCategoryMainColumns(@Nullable SharedPreferences sp) {
        PreferenceScreen ps = getPreferenceScreen();
        if (ps != null) { ps.removeAll(); }
        addPreferencesFromResource(R.xml.preference_main_columns);
        PreferenceCategory pc = (PreferenceCategory) getPreferenceScreen().getPreference(0);
        mCategoryKey = pc.getKey();
        createMainColumnsPreferences(sp, pc);
        populateInitPreferences(pc);
        return pc;
    }

    // Create 'other columns' preferences - multi select preferences for other details columns
    // and grouping columns, as well as edit-text prefs for descriptive title for other columns.
    private PreferenceCategory readyCategoryOtherProductColumns(@Nullable SharedPreferences sp) {
        PreferenceScreen ps = getPreferenceScreen();
        if (ps != null) { ps.removeAll(); }
        addPreferencesFromResource(R.xml.preference_other_product_columns);
        PreferenceCategory pc = (PreferenceCategory) getPreferenceScreen().getPreference(0);
        mCategoryKey = pc.getKey();
        createOtherProductColumnsPreferences(null, pc);
        // preserve last pref index as it is reset in populateInitPreferences method.
        int index = mPrefIndex;
        populateInitPreferences(pc);
        mPrefIndex = index;
        // reset other product column preferences only if changed. Don't do this when showing last
        // preference for active bool param, to allow saving state on rotation when it's set.
        if (index != PREF_ACTIVE_BOOL_PARAM_INDEX) {
            boolean noChanges = reSetOtherProductColumnsPreferences(sp);
            if (noChanges || mPrefIndex == -2) {
                return null;
            } else {
                // Recreate to reflect adjusted other product columns
                pc.removeAll();
                createOtherProductColumnsPreferences(null, pc);
                index = mPrefIndex;
                populateInitPreferences(pc);
                mPrefIndex = index;
            }
        }
        return pc;
    }


    // Called first when initializing on configuration change.
    private void showRestoredState(SharedPreferences sp, int prefIndex) {
        PreferenceCategory pc = readyCategory(sp);
        if (pc == null) { return; }
        // Bridge resetting of mPrefIndex (in readyCategory(sp) via populateInitPreferences(pc))
        // so it reflects what was in savedInstanceState.
        mPrefIndex = prefIndex;
        resetUnassignedProductColumns(sp);
        if (mShownMessage != null) {
            // If message was shown, reshow it.
            reshowMessage(pc, mShownMessage);
        } else {
            // If preference was shown, reshow it.
            showSinglePreference(pc, mInitPreferences[mPrefIndex], mShowContinue);
        }
    }


    // Called on click of "Continue" 'button preference'.
    @SuppressWarnings("deprecation")
    private void doContinue(@Nullable SharedPreferences sp, @Nullable PreferenceCategory pc) {

        if (sp == null) { sp = PreferenceManager.getDefaultSharedPreferences(getActivity()); }
        if (pc == null) { pc = (PreferenceCategory) getPreferenceScreen().getPreference(0); }

        // Immediately after 'Continue' is clicked, continue is never shown.
        mShowContinue = false;

        if (getString(R.string.pref_category_other_product_columns).equals(pc.getKey())) {
            // For other columns pref. category do not traverse preferences one by one, since
            // When altering main columns, some might already be set, so just go through the not
            // set ones, by always finding the next unset pref.
            mPrefIndex = findNextPrefInCategory(sp);
        } else {
            // If a preference was shown before the current one (if it's not the first or message
            // was not shown before,
            if (mPrefIndex > -1 && mShownMessage == null) {
                String previousPrefKey = mInitPreferences[mPrefIndex].getKey();
                if (getString(R.string.pref_customers_data_location).equals(previousPrefKey)) {
                    // If just set customer data location, start service update to get customer
                    // columns
                    Intent intent = new Intent(getActivity(), ServiceUpdate.class);
                    intent.putExtra(Util.ARG_GET_COLUMNS, true);
                    intent.putExtra(Util.ARG_DO_CUSTOMERS, true);
                    getActivity().startService(intent);
                    mProgressDialogMessage = getString(R.string.loading) + "...";
                    mProgressDialog = ProgressDialog.show(getActivity(), "", mProgressDialogMessage);
                    return;
                } else if (getString(R.string.pref_products_data_location).equals
                        (previousPrefKey)) {
                    // If just set product data location, start service update to get product
                    // columns
                    Intent intent = new Intent(getActivity(), ServiceUpdate.class);
                    intent.putExtra(Util.ARG_GET_COLUMNS, true);
                    intent.putExtra(Util.ARG_DO_PRODUCTS, true);
                    getActivity().startService(intent);
                    mProgressDialogMessage = getString(R.string.loading) + "...";
                    mProgressDialog = ProgressDialog.show(getActivity(), "", mProgressDialogMessage);
                    return;
                } else if (DbContract.COLUMN_PRODUCT_IDENTIFIER_KEY.equals(previousPrefKey)) {
                    // If finished all main columns prefs, start service update to input data
                    // into db.
                    mApplication.setMainAndOtherProductColumns(sp);
                    mApplication.setCustomerColumns(sp);
                    Intent intent = new Intent(getActivity(), ServiceUpdate.class);
                    intent.putExtra(Util.ARG_DO_PRODUCTS, true);
                    intent.putExtra(Util.ARG_DO_CUSTOMERS, true);
                    getActivity().startService(intent);
                    pc.removeAll();
                    mProgressDialogMessage = getString(R.string.fetching_data) + "...";
                    mProgressDialog = ProgressDialog.show(getActivity(), "", mProgressDialogMessage);
                    return;
                } else if (previousPrefKey.indexOf(DbContract.MAIN_PRODUCT_COLUMNS_PREF_PREFIX)
                        == 0) {
                    // If doing main product columns, adjust unassigned columns.
                    String value;
                    if (!"".equals(value = sp.getString(previousPrefKey, ""))) {
                        mUnassignedProductColumns.remove(value);
                    }
                }
            }
            // For 'Load' and 'Main columns' categories go through prefs one by one.
            mPrefIndex++;
        }

        if (mPrefIndex > -1 && mPrefIndex < mInitPreferences.length) {
            // while index is within the count of preferences within current preference category,
            // show preference at given index.
            showSinglePreference(pc, mInitPreferences[mPrefIndex], false);
        } else {
            // When index goes out of boundary, show next pref. category.
            pc = nextCategory(sp);
            if (pc != null) {
                String key = pc.getKey();
                if (getString(R.string.pref_category_main_columns).equals(key)) {
                    // In the case of 'Main Columns' category, there is now extra message to show,
                    // so go to show the first preference.
                    showSinglePreference(pc, mInitPreferences[mPrefIndex = 0], false);
                } else if (getString(R.string.other_columns_category_message).equals(key)) {
                    // For 'Other Columns' category, show relevant message on start.
                    showOtherColumnsMessage(pc);
                }
            }
        }
    }


    // Used on configuration change when message was shown.
    private void reshowMessage(PreferenceCategory pc, String message) {
        if (mCategoryKey.equals(getString(R.string.pref_category_load))) {
            // Load category has 3 messages - Start message, customer columns message and product
            // columns message
            if (mPrefIndex == -1) {
                showStartMessage(pc);
            } else {
                String prefKey = mInitPreferences[mPrefIndex].getKey();
                if (getString(R.string.pref_customers_data_location).equals(prefKey)) {
                    // Show option to restart specifying customers data location.
                    showRestartLoadCustomers(message, mShowContinue);
                } else if (getString(R.string.pref_products_data_location).equals(prefKey)) {
                    // Show option to restart specifying products data location.
                    showRestartLoadProducts(message, mShowContinue);
                }
            }
        } else if (mCategoryKey.equals(getString(R.string.pref_category_main_columns))) {
            // 'Main Columns' pref. category shows message at start and at end.
            if (mPrefIndex == -1) {
                // At start, re-fetch customer and product columns to show the message
                getBothColumns();
            } else {
                // At end, show option to restart main columns setting or to continue to fetch
                // data into db.
                String prefKey = mInitPreferences[mPrefIndex].getKey();
                if (DbContract.COLUMN_PRODUCT_IDENTIFIER_KEY.equals(prefKey)) {
                    showRestartMainColumns(message, mShowContinue);
                } else { getBothColumns(); }
            }
        } else if (mCategoryKey.equals(getString(R.string.pref_category_other_product_columns))) {
            // 'Other Columns' pref. category shows message only at start.
            showOtherColumnsMessage(pc);
        }
    }


    // Show given message in a given pref. category
    private void showMessage(@Nullable PreferenceCategory pc, String message) {
        if (pc == null) { pc = (PreferenceCategory) getPreferenceScreen().getPreference(0); }
        mShownMessage = message;
        pc.removeAll();
        pc.setTitle(message);
        addContinue(pc, null);
    }


    // Show app description and data populating instructions.
    private void showStartMessage(PreferenceCategory pc) {
        mShownMessage = getString(R.string.app_description) + "\n" +
                getString(R.string.load_data_description);
        showMessage(pc, mShownMessage);
        addSkipInitialize(pc);
    }


    // Show option to restart specifying customers data location.
    private void showRestartLoadCustomers(String restartMessage, boolean addContinue) {
        mShownMessage = restartMessage;
        PreferenceCategory pc = (PreferenceCategory) getPreferenceScreen().getPreference(0);
        Preference prefRestart = readyRestartPref(pc, restartMessage);
        prefRestart.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                mShownMessage = null;
                mShowContinue = false;
                clearLoadLocationPreferences();
                PreferenceCategory pc = readyCategoryLoad();
                mPrefIndex = PREF_DROPBOX_TOKEN_INDEX;
                showSinglePreference(pc, mInitPreferences[mPrefIndex], false);
                return true;
            }
        });
        if (addContinue) { addContinue(pc, null); }
        addSpace(pc);
        pc.addPreference(prefRestart);
    }


    // Show option to restart specifying products data location.
    private void showRestartLoadProducts(String restartMessage, boolean addContinue) {
        mShownMessage = restartMessage;
        PreferenceCategory pc = (PreferenceCategory) getPreferenceScreen().getPreference(0);
        Preference prefRestart = readyRestartPref(pc, restartMessage);
        prefRestart.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                mShownMessage = null;
                mShowContinue = false;
                clearLoadProductLocationPreferences();
                PreferenceCategory pc = readyCategoryLoad();
                mPrefIndex = PREF_PRODUCTS_LOCATION_INDEX;
                showSinglePreference(pc, mInitPreferences[mPrefIndex], false);
                return true;
            }
        });
        if (addContinue) { addContinue(pc, null); }
        addSpace(pc);
        pc.addPreference(prefRestart);
    }


    // Show option to restart specifying main columns.
    private void showRestartMainColumns(String restartMessage, boolean addContinue) {
        mShownMessage = restartMessage;
        PreferenceCategory pc = (PreferenceCategory) getPreferenceScreen().getPreference(0);
        Preference prefRestart = readyRestartPref(pc, restartMessage);
        prefRestart.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                mShownMessage = null;
                mShowContinue = false;
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
                clearMainColumnsPreferences(sp);
                PreferenceCategory pc = readyCategoryMainColumns(sp);
                mPrefIndex = 0;
                showSinglePreference(pc, mInitPreferences[mPrefIndex], false);
                return true;
            }
        });
        if (addContinue) { addContinue(pc, null); }
        addSpace(pc);
        pc.addPreference(prefRestart);
    }


    // Helper method to prepare Restart 'button' preference.
    private Preference readyRestartPref(PreferenceCategory pc, String restartMessage) {
        mCategoryKey = pc.getKey();
        pc.setTitle(restartMessage);
        pc.removeAll();
        Preference prefRestart = new Preference(getActivity());
        prefRestart.setKey(getString(R.string.pref_restart));
        prefRestart.setTitle(getString(R.string.pref_restart));
        prefRestart.setPersistent(false);
        return prefRestart;
    }


    // Show message explaining other columns with option to skip - usually after fetching data into
    // db. When skipping, other and grouping columns will be set to all 'other' columns, and
    // descriptive titles for 'other columns' will be set to column names.
    private void showOtherColumnsMessage(@Nullable PreferenceCategory pc) {
        if (pc == null) { pc = (PreferenceCategory) getPreferenceScreen().getPreference(0); }
        if (!mCategoryKey.equals(getString(R.string.pref_category_other_product_columns))) {
            pc = readyCategoryOtherProductColumns(null);
        }
        if (pc == null) { setInitialized(null); return; }
        showMessage(pc, getString(R.string.other_columns_category_message));
        addSkipOtherProductColumnsPrefs(pc);
    }


    // Create preferences for specifying which column from loaded corresponds to
    // columns: 'customer name', 'customer id', 'product name', 'image name', 'price', 'tax',
    // 'pack size', 'active' and 'product id'.
    private void createMainColumnsPreferences(@Nullable SharedPreferences sp,
                                              PreferenceCategory pc) {

        if (sp == null) { sp = PreferenceManager.getDefaultSharedPreferences(getActivity()); }

        // Get customer columns
        String prefCustomerColumns = sp.getString(DbContract.PREF_CUSTOMER_COLUMNS_KEY, "");
        String[] customerColumns = prefCustomerColumns.split(", ");

        // Customer columns without _id column - for displaying to user to choose from.
        int customerColumnsToDisplayCount = customerColumns.length - 1;
        String[] customerColumnsToDisplay = new String[customerColumnsToDisplayCount];
        System.arraycopy(customerColumns, 1, customerColumnsToDisplay, 0,
                customerColumnsToDisplayCount);

        // Customer name column pref.
        ListPreference customerNamePref = new ListPreference(getActivity());
        customerNamePref.setKey(DbContract.COLUMN_CUSTOMER_NAME_KEY);
        customerNamePref.setEntryValues(customerColumnsToDisplay);
        customerNamePref.setEntries(customerColumnsToDisplay);
        customerNamePref.setTitle(getString(R.string.pref_column_customer_name_title));
        customerNamePref.getExtras().putString(DbContract.COLUMN_CUSTOMER_NAME_KEY,
                getString(R.string.pref_column_customer_name_message));
        customerNamePref.setOnPreferenceChangeListener(this);
        pc.addPreference(customerNamePref);
        setPreferenceSummary(customerNamePref);

        // Customer id column pref.
        ListPreference customerIdentifierPref = new ListPreference(getActivity());
        customerIdentifierPref.setKey(DbContract.COLUMN_CUSTOMER_IDENTIFIER_KEY);
        customerIdentifierPref.setEntryValues(customerColumnsToDisplay);
        customerIdentifierPref.setEntries(customerColumnsToDisplay);
        customerIdentifierPref.setTitle(getString(R.string.pref_column_customer_id_title));
        customerIdentifierPref.getExtras().putString(DbContract.COLUMN_CUSTOMER_IDENTIFIER_KEY,
                getString(R.string.pref_column_customer_id_message));
        customerIdentifierPref.setOnPreferenceChangeListener(this);
        pc.addPreference(customerIdentifierPref);
        setPreferenceSummary(customerIdentifierPref);

        // Customer address columns preference.
        MultiSelectListPreference addressPref = new MultiSelectListPreference(getActivity());
        addressPref.setKey(DbContract.PREF_CUSTOMER_ADDRESS_COLUMNS_KEY);
        addressPref.setEntryValues(customerColumnsToDisplay);
        addressPref.setEntries(customerColumnsToDisplay);
        addressPref.setTitle(getString(R.string.pref_customer_address_columns_title));
        addressPref.getExtras().putString(DbContract.PREF_CUSTOMER_ADDRESS_COLUMNS_KEY,
                getString(R.string.pref_customer_address_message));
        addressPref.setOnPreferenceChangeListener(this);
        pc.addPreference(addressPref);
        setPreferenceSummary(addressPref);

        // Customer phones columns preference.
        MultiSelectListPreference phonesPref = new MultiSelectListPreference(getActivity());
        phonesPref.setKey(DbContract.PREF_CUSTOMER_PHONES_COLUMNS_KEY);
        phonesPref.setEntryValues(customerColumnsToDisplay);
        phonesPref.setEntries(customerColumnsToDisplay);
        phonesPref.setTitle(getString(R.string.pref_customer_phones_columns_title));
        phonesPref.getExtras().putString(DbContract.PREF_CUSTOMER_PHONES_COLUMNS_KEY,
                getString(R.string.pref_customer_phones_message));
        phonesPref.setOnPreferenceChangeListener(this);
        pc.addPreference(phonesPref);
        setPreferenceSummary(phonesPref);


        // Main product columns prefs:

        // Get product columns
        String prefProductColumns = sp.getString(DbContract.PREF_PRODUCT_COLUMNS_KEY, "");
        String[] productColumns = prefProductColumns.split(", ");

        // Customer product without _id column - for displaying to user to choose from.
        int productColumnsToDisplayCount = productColumns.length - 1;
        String[] productColumnsToDisplay = new String[productColumnsToDisplayCount];
        System.arraycopy(productColumns, 1, productColumnsToDisplay, 0,
                productColumnsToDisplayCount);

        // Set initially unassigned columns to all product columns (minus _id).
        mUnassignedProductColumns = new ArrayList<>(Arrays.asList(productColumnsToDisplay));

        // Load titles and messages for main product columns from resources.
        String[] titles = getResources().getStringArray(R.array.pref_columns_titles);
        String[] messages = getResources().getStringArray(R.array.pref_columns_messages);

        // 'product name', 'image name', 'price', 'tax', 'pack size' and 'active' column prefs.
         for (int i = 1; i < DbContract.MAIN_PRODUCT_COLUMNS_COUNT; i++) {
            String key = DbContract.PRODUCT_COLUMNS_MAIN_KEYS[i];
            ListPreference listPref = new ListPreference(getActivity());
            listPref.setKey(key);
            listPref.setEntryValues(productColumnsToDisplay);
            listPref.setEntries(productColumnsToDisplay);
            listPref.setTitle(titles[i]);
            listPref.getExtras().putString(key, messages[i]);
            listPref.setOnPreferenceChangeListener(this);
            setPreferenceSummary(listPref);
            pc.addPreference(listPref);
        }

        // Product id column pref.
        ListPreference productIdentifierPref = new ListPreference(getActivity());
        productIdentifierPref.setKey(DbContract.COLUMN_PRODUCT_IDENTIFIER_KEY);
        productIdentifierPref.setEntryValues(productColumnsToDisplay);
        productIdentifierPref.setEntries(productColumnsToDisplay);
        productIdentifierPref.setTitle(getString(R.string.pref_column_product_id_title));
        productIdentifierPref.getExtras().putString(DbContract.COLUMN_PRODUCT_IDENTIFIER_KEY,
                getString(R.string.pref_column_product_id_message));
        productIdentifierPref.setOnPreferenceChangeListener(this);
        pc.addPreference(productIdentifierPref);
        setPreferenceSummary(productIdentifierPref);


    } // End createMainColumnsPreferences method.


    // Create 'other' product columns preferences - multi select preferences for other details
    // columns and grouping columns, as well as edit-text prefs for descriptive titles for other
    // product columns.
    private void createOtherProductColumnsPreferences(@Nullable SharedPreferences sp,
                                                      PreferenceCategory pc) {

        if (sp == null) { sp = PreferenceManager.getDefaultSharedPreferences(getActivity()); }

        // Get all 'other' product columns
        String[] otherProductColumns =
                sp.getString(DbContract.PRODUCT_COLUMNS_OTHER_KEY, "").split(", ");
        Arrays.sort(otherProductColumns);

        // Get all 'other' product columns
        String[] customerColumns = sp.getString(DbContract.PREF_CUSTOMER_COLUMNS_KEY, "").trim()
                .substring("_id, ".length()).split(", ");

        // Other product details columns preference.
        MultiSelectListPreference detailsSelectPref = new MultiSelectListPreference(getActivity());
        detailsSelectPref.setKey(DbContract.PRODUCT_COLUMNS_OTHER_DETAILS_KEY);
        detailsSelectPref.setEntryValues(otherProductColumns);
        detailsSelectPref.setEntries(otherProductColumns);
        detailsSelectPref.setTitle(getString(R.string.pref_other_details_product_columns_title));
        detailsSelectPref.getExtras().putString(DbContract.PRODUCT_COLUMNS_OTHER_DETAILS_KEY,
                getString(R.string.pref_other_details_product_columns_message));
        detailsSelectPref.setOnPreferenceChangeListener(this);
        pc.addPreference(detailsSelectPref);
        setPreferenceSummary(detailsSelectPref);

        // Other customer details columns preference, displayed with map pin snippets.
        MultiSelectListPreference phonesPref = new MultiSelectListPreference(getActivity());
        phonesPref.setKey(DbContract.PREF_CUSTOMER_OTHER_DETAILS_COLUMNS_KEY);
        phonesPref.setEntryValues(customerColumns);
        phonesPref.setEntries(customerColumns);
        phonesPref.setTitle(getString(R.string.pref_customer_other_details_columns_title));
        phonesPref.getExtras().putString(DbContract.PREF_CUSTOMER_OTHER_DETAILS_COLUMNS_KEY,
                getString(R.string.pref_customer_other_details_message));
        phonesPref.setOnPreferenceChangeListener(this);
        pc.addPreference(phonesPref);
        setPreferenceSummary(phonesPref);

        // Other product details columns descriptive titles preferences.
        StringBuilder key = new StringBuilder();
        for (String otherProductColumn : otherProductColumns) {
            key.setLength(0);
            key.append(DbContract.OTHER_PRODUCT_COLUMNS_TITLE_PREF_PREFIX);
            key.append(otherProductColumn);
            EditTextPreference describePref = new EditTextPreference(getActivity());
            describePref.setKey(key.toString());
            describePref.setTitle(String.format(getString(R.string.pref_other_column_title),
                    otherProductColumn));
            describePref.getExtras().putString(key.toString(), String.format(getString(
                    R.string.pref_other_column_title_message), otherProductColumn));
            describePref.setOnPreferenceChangeListener(this);
            pc.addPreference(describePref);
            setPreferenceSummary(describePref);
        }

        // Grouping product columns preference.
        MultiSelectListPreference groupingSelectPref = new MultiSelectListPreference(getActivity());
        groupingSelectPref.setKey(DbContract.PRODUCT_COLUMNS_GROUPING_KEY);
        groupingSelectPref.setEntryValues(otherProductColumns);
        groupingSelectPref.setEntries(otherProductColumns);
        groupingSelectPref.setTitle(getString(R.string.pref_grouping_product_columns_title));
        groupingSelectPref.getExtras().putString(DbContract.PRODUCT_COLUMNS_GROUPING_KEY,
                getString(R.string.pref_grouping_product_columns_message));
        groupingSelectPref.setOnPreferenceChangeListener(this);
        pc.addPreference(groupingSelectPref);
        setPreferenceSummary(groupingSelectPref);

        // Add column 'active' boolean format preference, if format is set.
        addBoolPref(mApplication.getProductActiveColumnBoolFormat(),
                DbContract.COLUMN_PRODUCT_ACTIVE_BOOL_KEY,
                getString(R.string.pref_column_active_bool_title),
                getString(R.string.pref_column_active_bool_message), pc);

    }


    // For setting column 'active' boolean format preference.
    private void addBoolPref(String boolFormat, String key, String title, String message,
                             PreferenceCategory pc) {
        if (boolFormat == null || StringUtils.countMatches(boolFormat, " - ") != 1) { return; }
        String[] boolValues = boolFormat.split(" - ");
        String boolFormatReverse = boolValues[1] + " - " + boolValues[0];
        String[] entries = new String[]{boolFormat, boolFormatReverse};
        ListPreference boolPref = new ListPreference(getActivity());
        boolPref.setKey(key);
        boolPref.setEntryValues(entries);
        boolPref.setEntries(entries);
        boolPref.setTitle(title);
        boolPref.getExtras().putString(key, message);
        boolPref.setOnPreferenceChangeListener(this);
        pc.addPreference(boolPref);
        setPreferenceSummary(boolPref);
    }


    // Set reference to list of preferences in this category to be able to show them one by one.
    private void populateInitPreferences(PreferenceCategory pc) {
        mInitPreferences = new Preference[pc.getPreferenceCount()];
        for (int i = 0; i < pc.getPreferenceCount(); i++) {
            mInitPreferences[i] = pc.getPreference(i);
        }
        mPrefIndex = -1;
    }


    // Remove all, and add only this preference to screen while setting the appropriate message.
    private void showSinglePreference(@Nullable PreferenceCategory pc, Preference pref,
                                      boolean addContinue) {
        if (pc == null) { pc = (PreferenceCategory) getPreferenceScreen().getPreference(0); }
        if (pref == null) { return; }
        String prefKey = pref.getKey();
        mCategoryKey = pc.getKey();
        mShownMessage = null;
        // Set message.
        if (pref.peekExtras() != null) {
            pc.setTitle(pref.peekExtras().getString(prefKey));
        }

        // Set listener and summary.
        setPreferenceSummary(pref);
        pref.setOnPreferenceChangeListener(this);
        // Apply unassigned columns as list to choose from (used for main columns preferences).
        applyUnassignedProductColumns(prefKey, pref);
        // Remove all, and add only this preference to screen
        pc.removeAll();
        pc.addPreference(pref);
        // Add Continue if so specified
        if (addContinue) {
            addContinue(pc, prefKey);
            if (prefKey.equals(getString(R.string.pref_dropbox_token))) {
                pc.setTitle(getString(R.string.message_dropbox_token_set));
            }
        } else {
            // If not adding Continue, add Skip where needed.
            if (prefKey.equals(getString(R.string.pref_dropbox_token))
                    || prefKey.equals(getString(R.string.pref_images_location))
                    || prefKey.equals(DbContract.COLUMN_PRODUCT_IMAGE_KEY)
                    || prefKey.equals(DbContract.COLUMN_PRODUCT_PRICE_KEY)
                    || prefKey.equals(DbContract.COLUMN_PRODUCT_TAX_KEY)
                    || prefKey.equals(DbContract.COLUMN_PRODUCT_PACK_SIZE_KEY)
                    || prefKey.equals(DbContract.COLUMN_PRODUCT_ACTIVE_KEY)
                    || prefKey.contains(DbContract
                    .OTHER_PRODUCT_COLUMNS_TITLE_PREF_PREFIX)
                    ) {
                addSkipPref(pc, prefKey);
            }
        }
        // When showing pref for product columns location, show option to restart customer columns.
        if (getString(R.string.pref_products_data_location).equals(prefKey)) {
            Preference prefRestart = new Preference(getActivity());
            prefRestart.setKey(getString(R.string.pref_restart_customer_columns));
            prefRestart.setTitle(getString(R.string.pref_restart_customer_columns));
            prefRestart.setPersistent(false);
            prefRestart.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    mShownMessage = null;
                    mShowContinue = false;
                    clearLoadLocationPreferences();
                    PreferenceCategory pc = readyCategoryLoad();
                    mPrefIndex = PREF_DROPBOX_TOKEN_INDEX;
                    showSinglePreference(pc, mInitPreferences[mPrefIndex], false);
                    return true;
                }
            });
            addSpace(pc);
            pc.addPreference(prefRestart);
        }
    }


    // Apply unassigned columns as list to choose from for ListPreference.
    private void applyUnassignedProductColumns(String key, Preference pref) {
        if (mUnassignedProductColumns != null && key.indexOf(DbContract
                .MAIN_PRODUCT_COLUMNS_PREF_PREFIX)
                == 0) {
            String[] productColumnsToDisplay = mUnassignedProductColumns.toArray(new
                    String[mUnassignedProductColumns
                    .size()]);
            ((ListPreference) pref).setEntries(productColumnsToDisplay);
            ((ListPreference) pref).setEntryValues(productColumnsToDisplay);
        }
    }


    // Add preference titled 'Continue' to continue init sequence when clicked.
    @SuppressWarnings("unused")
    private void addContinue(final PreferenceCategory pc, final String key) {
        mShowContinue = true;
        Preference prefContinue = new Preference(getActivity());
        prefContinue.setKey(getString(R.string.pref_continue));
        prefContinue.setTitle(getString(R.string.pref_continue));
        prefContinue.setPersistent(false);
        prefContinue.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (Util.requestPermission(getActivity(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        getResources().getInteger(R.integer.permission_external_storage_id))) {
                    // On click to 'Continue', continue init sequence showing next unset
                    // preference in category.
                    doContinue(null, pc);
                }
                return true;
            }
        });
        pc.addPreference(prefContinue);
    }


    // Add preference 'Skip' start screen to set all preferences to pre-loaded.
    @SuppressWarnings("deprecation")
    private void addSkipInitialize(final PreferenceCategory pc) {
        mShowContinue = true;
        Preference prefSkip = new Preference(getActivity());
        prefSkip.setKey(getString(R.string.pref_skip));
        prefSkip.setTitle(getString(R.string.pref_skip));
        prefSkip.setPersistent(false);
        prefSkip.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (Util.requestPermission(getActivity(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE, getResources()
                                .getInteger(R.integer.permission_external_storage_on_skip_id))) {
                    Util.skipInit(getActivity());
                    mProgressDialogMessage = getString(R.string.loading) + "...";
                    mProgressDialog = ProgressDialog.show(getActivity(), "", mProgressDialogMessage);
                }
                return true;
            }
        });
        addSpace(pc);
        pc.addPreference(prefSkip);
    }


    // Add preference 'Skip' to set to empty string for main columns and column name for
    // other product columns.
    private void addSkipPref(final PreferenceCategory pc, final String key) {
        Preference prefSkip = new Preference(getActivity());
        prefSkip.setKey(getString(R.string.pref_skip));
        prefSkip.setTitle(getString(R.string.pref_skip));
        prefSkip.setPersistent(false);
        prefSkip.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
                SharedPreferences.Editor editor = sp.edit();
                if (key != null) {
                    if (key.equals(getString(R.string.pref_images_location))) {
                        editor.putString(getString(R.string.pref_images_location),
                                getString(R.string.warning_images_location_not_set));
                    } else if (key.equals(getString(R.string.pref_dropbox_token))
                            || key.equals(DbContract.COLUMN_PRODUCT_IMAGE_KEY)
                            || key.equals(DbContract.COLUMN_PRODUCT_PRICE_KEY)
                            || key.equals(DbContract.COLUMN_PRODUCT_TAX_KEY)
                            || key.equals(DbContract.COLUMN_PRODUCT_PACK_SIZE_KEY)
                            || key.equals(DbContract.COLUMN_PRODUCT_ACTIVE_KEY)
                            ) {
                        // Preserve previous value or put empty string.
                        editor.putString(key, sp.getString(key, ""));
                    } else if (key.contains(DbContract.OTHER_PRODUCT_COLUMNS_TITLE_PREF_PREFIX)
                            && "".equals(sp.getString(key, ""))) {
                        // If title was not set before or not specified (skipped), save column name
                        // as title
                        editor.putString(key, key.substring(DbContract
                                .OTHER_PRODUCT_COLUMNS_TITLE_PREF_PREFIX.length()));
                    }
                    editor.apply();
                }
                doContinue(sp, pc);
                return true;
            }
        });
        addSpace(pc);
        pc.addPreference(prefSkip);
    }


    // Add preference 'Skip' to other product columns message to skip all in category, by setting
    // all other columns as grouping and other product details columns, and column names as other
    // columns descriptive titles.
    private void addSkipOtherProductColumnsPrefs(final PreferenceCategory pc) {
        mShowContinue = true;
        Preference prefSkip = new Preference(getActivity());
        prefSkip.setKey(getString(R.string.pref_skip));
        prefSkip.setTitle(getString(R.string.pref_skip));
        prefSkip.setPersistent(false);
        prefSkip.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                mShowContinue = false;
                mShownMessage = null;
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
                SharedPreferences.Editor editor = sp.edit();
                // Get all other product columns.
                String[] otherProductColumns = sp.getString(DbContract
                        .PRODUCT_COLUMNS_OTHER_KEY, "")
                        .split(", ");
                Arrays.sort(otherProductColumns);
                Set<String> otherProductColumnsSet = new HashSet<>(otherProductColumns.length);
                for (String productColumn : otherProductColumns) {
                    editor.putString(DbContract.OTHER_PRODUCT_COLUMNS_TITLE_PREF_PREFIX +
                                    productColumn,
                            productColumn);
                    otherProductColumnsSet.add(productColumn);
                }
                editor.putStringSet(DbContract.PRODUCT_COLUMNS_GROUPING_KEY,
                        otherProductColumnsSet);
                editor.putStringSet(DbContract.PRODUCT_COLUMNS_OTHER_DETAILS_KEY,
                        otherProductColumnsSet);
                editor.apply();
                // find and show active bool format pref if exists.
                mPrefIndex = findNextPrefInCategory(sp);
                if (mPrefIndex > -1) {
                    showSinglePreference(pc, mInitPreferences[mPrefIndex], mShowContinue);
                }
                return true;
            }
        });
        addSpace(pc);
        pc.addPreference(prefSkip);
    }


    // Add space (usually before Skip or Restart preference) - in form of empty preference.
    private void addSpace(PreferenceCategory pc) {
        Preference prefSpace = new Preference(getActivity());
        prefSpace.setPersistent(false);
        prefSpace.setEnabled(false);
        pc.addPreference(prefSpace);
    }


    // Clear preference for specifying product data location, when its restart is clicked.
    private void clearLoadProductLocationPreferences() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());

        String productsLocationKey = getString(R.string.pref_products_data_location);
        String productsLocation = sp.getString(productsLocationKey, "").trim();
        boolean fromLocalFile = (new File(productsLocation)).exists();
        if (!fromLocalFile && !Util.isOnline(mApplication)) {
            Toast.makeText(mApplication, getString(R.string.warning_cannot_update_offline),
                    Toast.LENGTH_LONG).show();
            return;
        }

        SharedPreferences.Editor editor = sp.edit();
        editor.remove(getString(R.string.pref_products_data_location));
        editor.remove(DbContract.PREF_PRODUCT_COLUMNS_KEY);
        editor.apply();
    }


    // Clear preferences for specifying both product snd customer data locations, when its restart
    // is clicked.
    private void clearLoadLocationPreferences() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());

        // If fetching data from online url, check for connection before removing prefs.
        String customersLocationKey = getString(R.string.pref_customers_data_location);
        String customersLocation = sp.getString(customersLocationKey, "").trim();
        boolean customersFromLocalFile = (new File(customersLocation)).exists();
        String productsLocationKey = getString(R.string.pref_products_data_location);
        String productsLocation = sp.getString(productsLocationKey, "").trim();
        boolean productsFromLocalFile = (new File(productsLocation)).exists();
        if ((!customersFromLocalFile || !productsFromLocalFile) && !Util.isOnline(mApplication)) {
            Toast.makeText(mApplication, getString(R.string.warning_cannot_update_offline),
                    Toast.LENGTH_LONG).show();
            return;
        }

        SharedPreferences.Editor editor = sp.edit();
        editor.remove(getString(R.string.pref_customers_data_location));
        editor.remove(DbContract.PREF_CUSTOMER_COLUMNS_KEY);
        editor.remove(getString(R.string.pref_products_data_location));
        editor.remove(DbContract.PREF_PRODUCT_COLUMNS_KEY);
        editor.remove(getString(R.string.pref_dropbox_token));
        editor.apply();
    }


    // Clear preferences for main columns, when its restart is clicked.
    private boolean clearMainColumnsPreferences(@Nullable SharedPreferences sp) {
        if (sp == null) { sp = PreferenceManager.getDefaultSharedPreferences(getActivity()); }

        String productsLocationKey = getString(R.string.pref_products_data_location);
        String productsLocation = sp.getString(productsLocationKey, "").trim();
        boolean fromLocalFile = (new File(productsLocation)).exists();
        if (!fromLocalFile && !Util.isOnline(mApplication)) {
            Toast.makeText(mApplication, getString(R.string.warning_cannot_update_offline),
                    Toast.LENGTH_LONG).show();
            return false;
        }

        SharedPreferences.Editor editor = sp.edit();
        // Remove initialized flag
        editor.remove(getString(R.string.pref_initialized));
        // Remove main columns preferences
        for (int i = 1; i < DbContract.MAIN_PRODUCT_COLUMNS_COUNT; i++) {
            editor.remove(DbContract.PRODUCT_COLUMNS_MAIN_KEYS[i]);
        }
        // Remove unique id index preference.
        editor.remove(DbContract.COLUMN_CUSTOMER_NAME_KEY);
        editor.remove(DbContract.COLUMN_CUSTOMER_IDENTIFIER_KEY);
        editor.remove(DbContract.COLUMN_PRODUCT_IDENTIFIER_KEY);
        editor.apply();
        return true;
    }


    // Reset unassigned columns by setting them to all main product columns and then removing
    // the ones that are set.
    private void resetUnassignedProductColumns(@Nullable SharedPreferences sp) {
        if (sp == null) { sp = PreferenceManager.getDefaultSharedPreferences(getActivity()); }
        // In case the app was restarted, recreate unassigned columns
        String[] allProductColumns = sp.getString(DbContract.PREF_PRODUCT_COLUMNS_KEY, "")
                .split(", ");
        mUnassignedProductColumns = new ArrayList<>(Arrays.asList(allProductColumns));
        mUnassignedProductColumns.remove(DbContract.TableProducts._ID);
        String value;
        if (!"".equals(value = sp.getString(DbContract.COLUMN_PRODUCT_TITLE_KEY, ""))) {
            mUnassignedProductColumns.remove(value);
        }
        if (!"".equals(value = sp.getString(DbContract.COLUMN_PRODUCT_IMAGE_KEY, ""))) {
            mUnassignedProductColumns.remove(value);
        }
        if (!"".equals(value = sp.getString(DbContract.COLUMN_PRODUCT_PRICE_KEY, ""))) {
            mUnassignedProductColumns.remove(value);
        }
        if (!"".equals(value = sp.getString(DbContract.COLUMN_PRODUCT_TAX_KEY, ""))) {
            mUnassignedProductColumns.remove(value);
        }
        if (!"".equals(value = sp.getString(DbContract.COLUMN_PRODUCT_PACK_SIZE_KEY, ""))) {
            mUnassignedProductColumns.remove(value);
        }
        if (!"".equals(value = sp.getString(DbContract.COLUMN_PRODUCT_ACTIVE_KEY, ""))) {
            mUnassignedProductColumns.remove(value);
        }
    }


    // Called inside readyCategoryOtherProductColumns method to reset other product column
    // preferences only if changed.
    // re-set unassigned columns if null; put other columns pref; if columns (other) changed,
    // remove other, other details and grouping columns prefs; garbage collect old no more used
    // other columns titles prefs. Old used will not be removed so they will stay the same and not
    // show asking to set. New other columns titles prefs will be shown to be set.
    private boolean reSetOtherProductColumnsPreferences(@Nullable SharedPreferences sp) {
        if (sp == null) { sp = PreferenceManager.getDefaultSharedPreferences(getActivity()); }
        // In case the app was restarted, recreate unassigned columns
        resetUnassignedProductColumns(sp);
        SharedPreferences.Editor editor = sp.edit();
        // Columns other then main ones from previously specified mains.
        String oldOtherProductColumns = sp.getString(DbContract.PRODUCT_COLUMNS_OTHER_KEY, "");
        // If other columns preference has never been set, set it and return.
        if ("".equals(oldOtherProductColumns)) {
            editor.putString(DbContract.PRODUCT_COLUMNS_OTHER_KEY,
                    TextUtils.join(", ", mUnassignedProductColumns)).apply();
            return false;
        }

        // If other columns were set before, check if they changed and apply detected changes if
        // yes.

        boolean noChanges = true;
        // 'Garbage collection' list for descriptive columns titles of columns previously
        // specified as 'other columns' (usually by mistake), but not now.
        // Prepopulate removed columns with all old columns - to remove from the retained ones.
        ArrayList<String> removedOtherProductColumns = new ArrayList<>(Arrays.asList
                (oldOtherProductColumns.split(", ")));
        int oldOtherProductColumnsCount = removedOtherProductColumns.size();
        // Go through all new other columns and detect changes comparing to list of old other
        // columns.
        for (int i = 0; i < mUnassignedProductColumns.size(); i++) {
            if (!oldOtherProductColumns.contains(mUnassignedProductColumns.get(i))) {
                if (noChanges) {
                    // If other columns changed reset preferences for other columns and
                    editor.putString(DbContract.PRODUCT_COLUMNS_OTHER_KEY,
                            TextUtils.join(", ", mUnassignedProductColumns));
                    // remove preferences for grouping columns and other details columns to be
                    // re-set.
                    editor.remove(DbContract.PRODUCT_COLUMNS_GROUPING_KEY);
                    editor.remove(DbContract.PRODUCT_COLUMNS_OTHER_DETAILS_KEY);
                    noChanges = false;
                }
            } else {
                // If 'other' column is still present remove it from the 'garbage collection' list.
                removedOtherProductColumns.remove(mUnassignedProductColumns.get(i));
            }
        }

        // If all old other columns are contained in new, check if new have some that old don't.
        if (noChanges && mUnassignedProductColumns.size() != oldOtherProductColumnsCount) {
            // reset preferences for other columns and
            editor.putString(DbContract.PRODUCT_COLUMNS_OTHER_KEY,
                    TextUtils.join(", ", mUnassignedProductColumns));
            // remove preferences for grouping columns and other details columns to be re-set.
            editor.remove(DbContract.PRODUCT_COLUMNS_GROUPING_KEY);
            editor.remove(DbContract.PRODUCT_COLUMNS_OTHER_DETAILS_KEY);
            noChanges = false;
        }


        // 'Garbage collect' titles for old other columns which are no more in use as such.
        for (String productColumn : removedOtherProductColumns) {
            editor.remove(DbContract.OTHER_PRODUCT_COLUMNS_TITLE_PREF_PREFIX + productColumn);
        }
        // Apply all the changes determined relevant if any.
        editor.apply();

        // If column active is set and column active boolean param is not set, or if column active
        // is not set, set noChanges to false to prevent finishing initialization and show
        // first not set pref in other product columns category. If all set, finish initialization.
        String columnActive = sp.getString(DbContract.COLUMN_PRODUCT_ACTIVE_KEY, "");
        if ((!"".equals(columnActive) && !sp.contains(DbContract.COLUMN_PRODUCT_ACTIVE_BOOL_KEY))
                || "".equals(columnActive)) {
            noChanges = false;
            // Find index of first unset preference in category other product columns
            // (when altering it should be pref active bool param if column active is set)
            mPrefIndex = findNextPrefInCategory(sp);
            // Go one back, since mPrefIndex is again incremented by 1 in doContinue.
            mPrefIndex--;
        }

        return noChanges;
    }


    // Called after all init preferences are set to restart app to go to main screen.
    private void setInitialized(@Nullable SharedPreferences sp) {
        if (sp == null) { sp = PreferenceManager.getDefaultSharedPreferences(mApplication); }
        mInitialized = true;
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean(getString(R.string.pref_initialized), true);
        editor.apply();
        restartApp();
    }


    // For receiving result from service update.
    private final BroadcastReceiver mLocalServiceUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, final Intent intent) {
            // Dismiss progress dialog
            if (mProgressDialog != null) {
                mProgressDialog.dismiss(); mProgressDialog = null; mProgressDialogMessage = null;
            }
            if (!mInitialized) {
                boolean gotColumnsOnly = intent.getBooleanExtra(Util.ARG_GET_COLUMNS, false);
                boolean didProducts = intent.getBooleanExtra(Util.ARG_DO_PRODUCTS, false);
                boolean didCustomers = intent.getBooleanExtra(Util.ARG_DO_CUSTOMERS, false);
                String error = intent.getStringExtra(Util.ARG_SERVICE_UPDATE_ERROR);
                String productColumns =
                        intent.getStringExtra(DbContract.PREF_PRODUCT_COLUMNS_KEY);
                String customerColumns =
                        intent.getStringExtra(DbContract.PREF_CUSTOMER_COLUMNS_KEY);
                if (error != null) {
                    // On error show message and restart option.
                    if (gotColumnsOnly) {
                        if (didCustomers) {
                            showRestartLoadCustomers(error, false);
                        } else if (didProducts) {
                            showRestartLoadProducts(error, false);
                        }
                    } else {
                        showRestartMainColumns(error, false);
                    }
                } else {
                    if (gotColumnsOnly) {
                        // After fetching columns show columns and options to restart and continue.
                        if (didProducts && didCustomers) {
                            // After fetching both product and customer columns when altering main
                            // columns.
                            String message = String.format(mApplication.getLocale(),
                                    mApplication.getString(R.string.extracted_customer_columns),
                                    customerColumns)
                                    + "\n\n" + String.format(mApplication.getLocale(),
                                    mApplication.getString(R.string.extracted_product_columns),
                                    productColumns);
                            error = testProductColumns();
                            readyCategoryLoad();
                            mPrefIndex = PREF_PRODUCTS_LOCATION_INDEX;
                            if (error == null) {
                                message += "\n\n" + getString(R.string.continue_if_correct);
                                showRestartLoadProducts(message, true);
                            } else {
                                message += "\n\n" + error + "\n\n" +
                                        mApplication.getString(R.string.restart_to_reset_columns);
                                showRestartLoadProducts(message, false);
                            }
                        } else {
                            if (didProducts) {
                                // After fetching product columns
                                showRestartLoadProducts(String.format(mApplication.getLocale(),
                                        mApplication.getString(R.string.extracted_product_columns),
                                        productColumns) + "\n\n" +
                                        mApplication.getString(R.string.continue_if_correct), true);
                            } else if (didCustomers) {
                                // After fetching customer columns
                                showRestartLoadCustomers(String.format(mApplication.getLocale(),
                                        mApplication.getString(R.string.extracted_customer_columns),
                                        customerColumns) + "\n\n" +
                                        mApplication.getString(R.string.continue_if_correct), true);
                            }
                        }
                    } else {
                        // After writing to db.
                        // Used when altering main columns only.
                        mApplication.setActiveProductColumnBoolFormat(intent.getStringExtra
                                (DbContract.COLUMN_PRODUCT_ACTIVE_BOOL_KEY));
                        mApplication.setMainAndOtherProductColumns(null);
                        mWriteDbDone = true;
                        if (isAdded() && !isDetached()) {
                            String successMessage = getString(R.string.message_updated_data);
                            Util.snackOrToast(mApplication, getView(), successMessage,
                                    Snackbar.LENGTH_LONG);
                            // Used when skipping init to force into other columns category. This
                            // will finalize help initialization when skipping it (instead of
                            // directly calling readyCategoryOtherProductColumns method to get pc.
                            showOtherColumnsMessage(null);
                        }
                    }
                }
            }
        }
    };


    // Used when resetting main columns to test without ServiceUpdate.
    // Set main and other columns in mApplication class;
    // Test if price column is double (real), pack size and tax columns are integer and active
    // column is boolean (has only two distinct values). If all satisfy, return null (error =
    // null), if any don't satisfy set error message to corresponding message to return; If any
    // of these columns is not specified, that test will be skipped.
    // If active column satisfies, set active column boolean format in mApplication class.
    @SuppressWarnings("deprecation")
    private String testProductColumns() {
        // Words 'integer' and 'decimal' used in error message.
        final String INTEGER = getString(R.string.integer);
        final String DECIMAL = getString(R.string.decimal);
        // For specifying if error occurred to not retest that test.
        boolean activeError = false, taxError = false, packError = false, priceError = false;
        StringBuilder errorMessage = new StringBuilder();
        String boolFormatActive = null;
        // Set main and other columns in mApplication class;
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mApplication.setMainAndOtherProductColumns(sp);
        mApplication.setCustomerColumns(sp);
        // Start progress dialog.
        mProgressDialogMessage = getString(R.string.testing_data) + "...";
        mProgressDialog = ProgressDialog.show(getActivity(), "", mProgressDialogMessage);
        // Get all products data to test it.
        Cursor cursor = getActivity().getContentResolver().query(DbContract.getProductsTableUri(),
                null, null, null, null, null);
       if (cursor != null && cursor.moveToFirst()) {
            int productColumnActiveIndex = mApplication.getProductColumnActiveIndex();
            int productColumnTaxIndex = mApplication.getProductColumnTaxIndex();
            int productColumnPackSizeIndex = mApplication.getProductColumnPackSizeIndex();
            int productColumnPriceIndex = mApplication.getProductColumnPriceIndex();
            if (productColumnActiveIndex > -1) {
                boolFormatActive = cursor.getString(productColumnActiveIndex);
            }

            if (productColumnTaxIndex > -1 && !TextUtils.isDigitsOnly(cursor.getString
                    (productColumnTaxIndex))) {
                errorMessage.append(String.format(mApplication.getLocale(),
                        getString(R.string.incorrect_number_format),
                        getString(R.string.column_tax_default_name), INTEGER));
                errorMessage.append("\n\n");
                taxError = true;
            }
            if (productColumnPackSizeIndex > -1 &&
                    !TextUtils.isDigitsOnly(cursor.getString(productColumnPackSizeIndex))) {
                errorMessage.append(String.format(mApplication.getLocale(),
                        getString(R.string.incorrect_number_format),
                        getString(R.string.column_pack_size_default_name), INTEGER));
                errorMessage.append("\n\n");
                packError = true;
            }
            if (productColumnPriceIndex > -1) {
                if (!Util.isNumber(cursor.getString(productColumnPriceIndex))) {
                    errorMessage.append(String.format(mApplication.getLocale(),
                            getString(R.string.incorrect_number_format),
                            getString(R.string.column_price_default_name), DECIMAL));
                    errorMessage.append("\n\n");
                    priceError = true;
                }
            }
            // If at least one column is set
            if (productColumnActiveIndex > -1 || productColumnTaxIndex > -1 ||
                    productColumnPackSizeIndex > -1
                    || productColumnPriceIndex > -1) {
                while (cursor.moveToNext()) {
                    if (!taxError && productColumnTaxIndex > -1) {
                        if (!TextUtils.isDigitsOnly(cursor.getString(productColumnTaxIndex))) {
                            errorMessage.append(String.format(mApplication.getLocale(),
                                    getString(R.string.incorrect_number_format),
                                    getString(R.string.column_tax_default_name), INTEGER));
                            errorMessage.append("\n\n");
                            taxError = true;
                        }
                    }
                    if (!packError && productColumnPackSizeIndex > -1) {
                        if (!TextUtils.isDigitsOnly(cursor.getString(productColumnPackSizeIndex))) {
                            errorMessage.append(String.format(mApplication.getLocale(),
                                    getString(R.string.incorrect_number_format),
                                    getString(R.string.column_pack_size_default_name), INTEGER));
                            errorMessage.append("\n\n");
                            packError = true;
                        }
                    }
                    if (!priceError && productColumnPriceIndex > -1) {
                        if (!Util.isNumber(cursor.getString(productColumnPriceIndex))) {
                            errorMessage.append(String.format(mApplication.getLocale(),
                                    getString(R.string.incorrect_number_format),
                                    getString(R.string.column_price_default_name), DECIMAL));
                            errorMessage.append("\n\n");
                            priceError = true;
                        }
                    }
                    if (!activeError && boolFormatActive != null && !boolFormatActive.contains(
                            cursor.getString(productColumnActiveIndex))) {
                        boolFormatActive += " - " + cursor.getString(productColumnActiveIndex);
                        if (StringUtils.countMatches(boolFormatActive, " - ") > 1) {
                            errorMessage.append(String.format(mApplication.getLocale(),
                                    getString(R.string.bool_columns_error), getString(R.string
                                            .bool_column_active),
                                    boolFormatActive, "", ""));
                            activeError = true;
                        }
                    }
                }
            }
            cursor.close();

        }
        if (!activeError) { mApplication.setActiveProductColumnBoolFormat(boolFormatActive); }
        if (mProgressDialog != null) {
            mProgressDialog.dismiss(); mProgressDialog = null; mProgressDialogMessage = null;
        }
        if (taxError || packError || priceError || activeError) {
            return errorMessage.toString();
        } else { return null; }
    }


    // Called when alter main columns is clicked.
    private void restartSettingsScreen() {
        // Set flag and result so main activity is destroyed as well.
        mFinishMain = true;
        Util.setSettingsResultBln(getActivity(), Util.RESULT_FINISH_MAIN, mFinishMain);
        Intent intent = new Intent(getActivity(), SettingsActivity.class);
        intent.putExtra(EXTRA_SHOW_FRAGMENT, SettingsFragment.class.getName());
        getActivity().finish();
        startActivity(intent);
    }


    // Called on initialization finish.
    private void restartApp() {
        getActivity().finish();
        startActivity(new Intent(getActivity(), MainActivity.class));
    }


    // Save instance state
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (!mInitialized) {
            outState.putBoolean(STATE_SHOW_CONTINUE, mShowContinue);
            outState.putBoolean(STATE_WRITE_DB_DONE, mWriteDbDone);
            outState.putString(STATE_SHOWN_PC, mCategoryKey);
            outState.putInt(STATE_PREF_INDEX, mPrefIndex);
            outState.putString(STATE_INIT_MESSAGE, mShownMessage);
        }
        // Save state for activity results if present.
        if (mFinishMain != null) {
            outState.putBoolean(Util.RESULT_FINISH_MAIN, mFinishMain);
        }
        if (mRecreateMainActivity != null) {
            outState.putBoolean(Util.RESULT_RECREATE_MAIN_ACTIVITY, mRecreateMainActivity);
        }
        if (mGridColumnWidth > 0) {
            outState.putInt(Util.RESULT_GRID_COLUMN_WIDTH, mGridColumnWidth);
        }
        if (mLoadImagesLocation != null) {
            outState.putString(Util.RESULT_LOAD_IMAGES_LOCATION, mLoadImagesLocation);
        }
        if (mResetGroupingProductColumns != null) {
            outState.putBoolean(Util.RESULT_RESET_GROUPING_COLUMNS, mResetGroupingProductColumns);
        }
        if (mOtherProductColumnTitle != null) {
            outState.putBoolean(Util.RESULT_OTHER_COLUMN_TITLE, mOtherProductColumnTitle);
        }
        // Active bool format
        if (mApplication.getProductActiveColumnBoolFormat() != null) {
            outState.putString(DbContract.COLUMN_PRODUCT_ACTIVE_BOOL_KEY,
                    mApplication.getProductActiveColumnBoolFormat());
        }
        // Progress dialog
        if (mProgressDialog != null) {
            outState.putString(STATE_PROGRESS_DIALOG, mProgressDialogMessage);
        }
    }


    // Click listener for update images preference which destroys app cache.
    private final Preference.OnPreferenceClickListener mUpdateImagesListener = new Preference
            .OnPreferenceClickListener() {
        @Override
        public boolean onPreferenceClick(Preference pref) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mApplication);
            String message = getString(R.string.pref_update_images_click_message) + " " +
                    sp.getString(getString(R.string.pref_images_location), "");
            try {
                // Destroy app cache.
                FileUtils.deleteDirectory(mApplication.getCacheDir());
                // Reset counter for destroying app cache.
                SharedPreferences.Editor editor = sp.edit();
                editor.putLong(getString(R.string.pref_update_images), System.currentTimeMillis());
                editor.apply();
                // Refresh recycler view.
                getActivity().setResult(Activity.RESULT_OK, null);
            } catch (IOException e) {
                message = getString(R.string.pref_update_images_fail_message);
            }

            Util.snackOrToast(mApplication, getView(), message, Snackbar.LENGTH_LONG);

            return false;
        }
    };


} // End SettingsFragment