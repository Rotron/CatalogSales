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
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Switch;

import com.humaneapps.catalogsales.data.DbContract;
import com.humaneapps.catalogsales.service.ServiceUpdate;

import org.apache.commons.io.FileUtils;

import java.io.IOException;

import butterknife.BindView;
import butterknife.ButterKnife;

import static android.preference.PreferenceActivity.EXTRA_SHOW_FRAGMENT;

/**
 * Starting activity
 */
public class MainActivity extends AppCompatActivity implements LoaderManager
        .LoaderCallbacks<Cursor> {

    // Loader unique constant.
    private static final int IMAGE_LOADER_ID = 0;
    // Constants used as arguments for preserving state.
    private final String STATE_DRAWER_OPEN = "STATE_DRAWER_OPEN";
    private final String STATE_DRAWER_SELECTION = "STATE_DRAWER_SELECTION";
    private final String STATE_DRAWER_SPINNER_POSITION = "STATE_DRAWER_SPINNER_POSITION";
    private static final String STATE_DRAWER_LIST_SCROLL_Y = "STATE_DRAWER_LIST_SCROLL_Y";
    private static final String STATE_SHOW_ACTIVE_PRODUCTS = "STATE_SHOW_ACTIVE_PRODUCTS";
    private static final String STATE_RECYCLER_SCROLL_POSITION = "STATE_RECYCLER_SCROLL_POSITION";
    // Drawer related:
    @BindView(R.id.app_main_drawer_layout)
    DrawerLayout mDrawerLayout;
    @BindView(R.id.drawer_spinner)
    Spinner mDrawerSpinner;
    @BindView(R.id.drawer_list)
    ListView mDrawerList;
    private ActionBarDrawerToggle mDrawerToggle;
    private ArrayAdapter<String> mDrawerAdapter;
    // Position/selection holders; temp - for temporary holding previous position/selection.
    private int mDrawerSpinnerPosition = 1, mTempDrawerSpinnerPosition;
    private int mDrawerSelection = 0, mTempDrawerSelection = -1;
    // For preserving state:
    private int mDrawerScrollPosition = 0, mRecyclerScrollPosition = 0;
    private boolean mWasDrawerOpen;
    private boolean mUpdateCustomers;
    // RecyclerView.Adapter
    ImageAdapter imageAdapter;
    // Application level class containing common data needed across the application.
    private CatalogSales mApplication;
    // true -> show active products; false -> show inactive products; null -> show all products.
    private Boolean mShowActiveProducts = true;
    private boolean mTwoPane;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set application level class containing common data needed across the application.
        mApplication = (CatalogSales) getApplication();

        // Get shared preferences
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplication());
        // Get orientation.
        boolean isLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        // Set mode for detecting split screen (only in tablet landscape when setting is on).
        boolean twoPaneOn = sp.getBoolean(getString(R.string.pref_two_pane), false);
        mTwoPane = twoPaneOn && isLandscape;
        if (mTwoPane) {
            setContentView(R.layout.activity_main_two_pane);
        } else {
            setContentView(R.layout.activity_main);
        }
        ButterKnife.bind(this);
        // Set action bar.
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Set preference defaults.
        PreferenceManager.setDefaultValues(this, R.xml.preference_general, true);
        PreferenceManager.setDefaultValues(this, R.xml.preference_customize, true);
        PreferenceManager.setDefaultValues(this, R.xml.preference_load, true);
        PreferenceManager.setDefaultValues(this, R.xml.preference_main_columns, true);
        PreferenceManager.setDefaultValues(this, R.xml.preference_other_product_columns, true);
        // If not initialized, initialize.
        if (!sp.getBoolean(getString(R.string.pref_initialized), false)) {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.putExtra(EXTRA_SHOW_FRAGMENT, SettingsFragment.class.getName());
            finish();
            startActivity(intent);
            return;
        }
    // Set product and customer columns, related indices, navigation selections and
        // related lists and customer list. These can then be ready accessed from application class.
        mApplication.setProductColumnsAndCustomers(sp);
        // RecyclerView.Adapter for displaying product images.
        imageAdapter = new ImageAdapter(this, mTwoPane);
        // Add drawer listener.
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, toolbar,
                R.string.drawer_open_content_desc, R.string.drawer_close_content_desc) {
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                // If spinner selection is changed but no new selection was made from the new list,
                // revert spinner selection and list to reflect currently displayed products.
                mDrawerSpinnerPosition = mTempDrawerSpinnerPosition;
                drawerSpinnerSelect(mDrawerSpinnerPosition);
            }
        };
        mDrawerLayout.addDrawerListener(mDrawerToggle);
        // Set drawer adapter
        mDrawerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        mDrawerAdapter.addAll(mApplication.getGroupingSelectionEntries()[0]);
        mDrawerList.setAdapter(mDrawerAdapter);

        if (savedInstanceState == null) {
            // Update images after 90 days - remove cash
            long lastUpdatedImages = sp.getLong(getString(R.string.pref_update_images), 0);
            long nowMinus90Days = System.currentTimeMillis() - (90L * 86400000L);
            if (lastUpdatedImages < nowMinus90Days) {
                try {
                    FileUtils.deleteDirectory(getCacheDir());
                    SharedPreferences.Editor editor = sp.edit();
                    editor.putLong(getString(R.string.pref_update_images), System
                            .currentTimeMillis());
                    editor.apply();
                } catch (IOException e) { /* ignore */ }
            }

            // Update data (products, which will update customers on receiving result).
            Intent intentUpdate = new Intent(MainActivity.this, ServiceUpdate.class);
            intentUpdate.putExtra(Util.ARG_DO_PRODUCTS, true);
            startService(intentUpdate);
            mUpdateCustomers = true;
            // Show main fragment
            if (findViewById(R.id.fragmentContainer) != null) {
                populateDrawerSpinner(mApplication.getNavigationSelection());
                Util.showFragment(this, MainFragment.newInstance(), getString(R.string.app_name),
                        false, null);
            }
            // If app was killed while order taking was in place, order was saved. Reload it.
            if (sp.contains(Util.KEY_ORDER_FILE_NAME)) {
                Util.loadOrder(sp.getString(Util.KEY_ORDER_FILE_NAME, ""), mApplication);
                if (sp.contains(Util.KEY_EDITING_ORDER)) {
                    mApplication.setEditingOrder(sp.getBoolean(Util.KEY_EDITING_ORDER, false));
                }
                mApplication.isTakingOrder = true;
                mDrawerLayout.post(new Runnable() {
                    @Override
                    public void run() {
                        setLayout(false);
                    }
                });
            }
        } else {
            // Restore saved state values.
            mDrawerScrollPosition = savedInstanceState.getInt(STATE_DRAWER_LIST_SCROLL_Y);
            mWasDrawerOpen = savedInstanceState.getBoolean(STATE_DRAWER_OPEN);
            mDrawerSelection = savedInstanceState.getInt(STATE_DRAWER_SELECTION, 0);
            mDrawerSpinnerPosition = savedInstanceState.getInt(STATE_DRAWER_SPINNER_POSITION, 0);
            mRecyclerScrollPosition = savedInstanceState.getInt(STATE_RECYCLER_SCROLL_POSITION, 0);
            // If it wasn't put, it will set it to null -> show all, both active & inactive.
            mShowActiveProducts = savedInstanceState.getBoolean(STATE_SHOW_ACTIVE_PRODUCTS);
            mDrawerList.post(new Runnable() {
                @Override
                public void run() {
                    if (mApplication.isTakingOrder) {
                        // Preserve order state if was taking one:
                        setLayout(true);
                    } else {
                        // Preserve drawer selection state when not taking order:
                        populateDrawerSpinner(mApplication.getNavigationSelection());
                    }
                }
            });
        }


        mDrawerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Hold previous drawer spinner position before it changed
                mTempDrawerSpinnerPosition = mDrawerSpinnerPosition;
                mDrawerSpinnerPosition = position;
                if (position == 0) {
                    // All / Active / Inactive
                    String all = getString(R.string.all);
                    String active = getString(R.string.active);
                    String inactive = getString(R.string.inactive);
                    // Add asterisk prefix to the currently selected one.
                    if (mShowActiveProducts == null) {
                        all = "* " + all;
                    } else {
                        if (mShowActiveProducts) {
                            active = "* " + active;
                        } else {
                            inactive = "* " + inactive;
                        }
                    }
                    mDrawerAdapter.clear();
                    mDrawerAdapter.add(all);
                    mDrawerAdapter.add(active);
                    mDrawerAdapter.add(inactive);
                } else if (position <= mApplication.getGroupingProductColumnCount()) {
                    // Grouping columns as set by user.
                    mDrawerAdapter.clear();
                    mDrawerAdapter.addAll(mApplication.getGroupingSelectionEntries()[position - 1]);
                    if (mTempDrawerSelection > -1) { showDrawerItem(mTempDrawerSelection); }
                } else if (position == mApplication.getGroupingProductColumnCount() + 1) {
                    // Custom favourite
                    mDrawerAdapter.clear();
                    mDrawerAdapter.add(getString(R.string.display_custom_items));
                    showDrawerItem(0);
                } else if (position == mApplication.getGroupingProductColumnCount() + 2) {
                    // Order
                    mDrawerAdapter.clear();
                    mDrawerAdapter.add(getString(R.string.display_order_items));
                    showDrawerItem(0);
                }

                mTempDrawerSelection = -1;
                mDrawerAdapter.notifyDataSetChanged();
            }


            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        mDrawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
                if (mDrawerSpinnerPosition == 0) {
                    // All / Active / Inactive
                    switch (position) {
                        case 0: { mShowActiveProducts = null; break; }  // All (both)
                        case 1: { mShowActiveProducts = true; break; }  // Active
                        case 2: { mShowActiveProducts = false; break; } // Inactive
                    }
                    // Show previous selection adding active/inactive filter as per above flag.
                    mDrawerSpinnerPosition = mTempDrawerSpinnerPosition;
                    mTempDrawerSelection = mDrawerSelection;
                    drawerSpinnerSelect(mDrawerSpinnerPosition);
                } else {
                    // preserve spinner position for use in All / Active / Inactive
                    mTempDrawerSpinnerPosition = mDrawerSpinnerPosition;
                    // Set selection
                    mDrawerSelection = position;
                    // Reload products per set selection resetting recycler view adapter data.
                    getSupportLoaderManager().restartLoader(IMAGE_LOADER_ID, null, MainActivity
                            .this);
                }
                if (!mWasDrawerOpen) { mDrawerLayout.closeDrawers(); }
                setMainTitle();
            }
        });

    } // End onCreate


    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {

        // For all selection
        String selectionArgs[] = null;
        String selection = null;
        if (mShowActiveProducts != null && mApplication.getProductColumnActiveIndex() != -1) {
            if (mShowActiveProducts) {
                selection = "[" + mApplication.getProductColumnActive() + "] = '"
                        + mApplication.getProductActiveColumnPositive() + "'";
            } else {
                selection = mApplication.getProductColumnActive() + " = '"
                        + mApplication.getProductActiveColumnNegative() + "'";
            }
        }

        // If not displaying all products
        if (mDrawerSpinnerPosition > 0) {
            if (mDrawerSpinnerPosition <= mApplication.getGroupingProductColumnCount()) {
                // For Grouping categories.
                if (mDrawerSelection > 0) {
                    // If not all from grouping
                    selection = selection == null ? "" : selection + " AND ";
                    selection += "[" + mApplication.getGroupingProductColumns()
                            [mDrawerSpinnerPosition - 1] + "] = ? ";
                    selectionArgs = new String[]{(String) mDrawerList.getItemAtPosition
                            (mDrawerSelection)};
                }
            } else if (mDrawerSpinnerPosition == mApplication.getGroupingProductColumnCount() + 1) {
                // Custom - favourite
                selection = selection == null ? "" : selection + " AND ";
                selection += getString(R.string.column_name_favourite) + " = ? ";
                selectionArgs = new String[]{Util.FAVOURITE_POSITIVE};
            } else if (mDrawerSpinnerPosition == mApplication.getGroupingProductColumnCount() + 2) {
                // Order
                selection = "[" + mApplication.getProductColumnIdentifier() + "] IN (? ";
                selectionArgs = mApplication.order.getIdentifiersAsArray();
                for (int i = 1; i < selectionArgs.length; i++) { selection += ",?"; }
                selection += ")";
            }
        } // End if not "ALL"

        return new CursorLoader(mApplication, DbContract.getProductsTableUri(), null, selection,
                selectionArgs, null);
    }


    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {

        if (cursor != null) {

            // Update recycler view adapter data to show products as per latest selection.
            int count = imageAdapter.swapCursor(cursor);

            // If there are no products to display
            if (count == 0) {
                if (mDrawerSpinnerPosition == mApplication.getGroupingProductColumnCount() + 1) {
                    // Favourite products
                    Util.snackOrToast(mApplication, mDrawerLayout, getString(R.string
                                    .message_empty_favourites), Snackbar.LENGTH_LONG);
                } else if (mDrawerSpinnerPosition == mApplication.getGroupingProductColumnCount()
                        + 2) {
                    // Ordered products
                    Util.snackOrToast(mApplication, mDrawerLayout, getString(R.string
                            .message_empty_order), Snackbar.LENGTH_LONG);
                } else {
                    // All other grouping selection options
                    Util.snackOrToast(mApplication, mDrawerLayout, getString(R.string
                            .message_empty_selection), Snackbar.LENGTH_LONG);
                }
            } else { restoreRecyclerScrollPosition(); }

        } else { mDrawerSelection = 0; }

    }


    @Override
    public void onLoaderReset(Loader<Cursor> loader) { imageAdapter.swapCursor(null); }


    // Inflate the menu and add items to the action bar if it is present.
    @Override
    @SuppressWarnings("deprecation")
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_activity_main, menu);
        mMenu = menu;
        final MenuItem detailsSwitch = menu.findItem(R.id.details_switch);
        if (detailsSwitch != null) {
            Switch actionView = (Switch) MenuItemCompat.getActionView(detailsSwitch);
            if (actionView != null) {
                actionView.setChecked(mApplication.blockDetails);
                actionView.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        mApplication.blockDetails = isChecked;
                    }
                });
            }
        }
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Get action id.
        int id = item.getItemId();

        switch (id) {
            // Start order. It changes to save order if already started
            case R.id.action_start_or_save_order: {
                startOrSaveOrder();
                return true;
            }
            // Save and email order
            case R.id.action_save_and_send: {
                // Start dialog to confirm saving order and to send it.
                Util.showDialogConfirm(this, getString(R.string.dialog_save_and_send_order_title));
                return true;
            }
            // Cancel order without saving
            case R.id.action_cancel_order: {
                Util.showDialogConfirm(this, getString(R.string.dialog_cancel_taking_order_title));
                return true;
            }
            // Display current order
            case R.id.action_display_order: {
                startActivityForResult(new Intent(mApplication, OrderActivity.class),
                        Util.ORDER_ACTIVITY_RESULT_CODE);
                return true;
            }
            // Show existing orders with options to delete, email and select/display.
            case R.id.action_show_existing_orders: {
                Intent intent = new Intent(mApplication, OrderActivity.class);
                intent.putExtra(Util.STATE_SHOWING_EXISTING_ORDERS, true);
                startActivityForResult(intent, Util.ORDER_ACTIVITY_RESULT_CODE);
                return true;
            }
            // Delete current order
            case R.id.action_delete_order: {
                Util.showDialogConfirm(this, getString(R.string.dialog_delete_order_title));
                return true;
            }
            // Show customers in a map
            case R.id.action_map: {
                startActivity(new Intent(mApplication, MapActivity.class));
                return true;
            }
            // Show settings screen
            case R.id.action_settings: {
                // Show SettingsActivity
                startActivityForResult(new Intent(mApplication, SettingsActivity.class), Util
                        .SETTINGS_ACTIVITY_RESULT_CODE);
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }


    private void startOrSaveOrder() {
        if (mApplication.isTakingOrder) {
            // Start dialog to confirm saving order.
            Util.showDialogConfirm(this, getString(R.string.dialog_save_order_title));
        } else {
            // Start dialog to set customer and start new order.
            Util.showDialogConfirm(this, getString(R.string.dialog_start_order_title));
        }
    }


    void startNewOrder(String customer) {
        mApplication.order.startNewOrder(customer);
        mApplication.isTakingOrder = true;
        setLayout(false);
    }


    // When no order is being taken, just show product images on white background. When taking
    // order square images with gray borders and add order taking views (+,-,quantity,price).
    // Set title to customer name. Set menu appropriately.
    @SuppressWarnings("deprecation")
    void setLayout(boolean configurationChanged) {
        if (!configurationChanged) {
            final MenuItem detailsSwitch = mMenu.findItem(R.id.details_switch);
            if (detailsSwitch != null) {
                Switch actionView = (Switch) MenuItemCompat.getActionView(detailsSwitch);
                mApplication.blockDetails = mApplication.isTakingOrder;
                actionView.setChecked(mApplication.blockDetails);
            }
        }
        int rvBgdColor;
        if (mApplication.isTakingOrder) {
            rvBgdColor = ContextCompat.getColor(mApplication, R.color.colorLightGray);
        } else {
            rvBgdColor = Color.WHITE;
        }
        imageAdapter.recyclerView.setBackgroundColor(rvBgdColor);
        imageAdapter.refreshRecyclerView();
        setMenu();
        setMainTitle();
        populateDrawerSpinner(mApplication.getNavigationSelection());

        // On cancel order if showing order products, show all products
        if (!mApplication.isTakingOrder) {
            if (mDrawerSpinnerPosition == mApplication.getGroupingProductColumnCount() + 2) {
                showAllProducts();
            }
        }

        if (mTwoPane) {
            DetailsFragment detailsFragment = (DetailsFragment) getSupportFragmentManager()
                    .findFragmentByTag(getString(R.string.title_details));
            if (detailsFragment != null) {
                detailsFragment.setImageLayout();
            }
        }

    }


    // Set main activity menu appropriately to reflect taking/editing order or no order.

    private Menu mMenu;

    private boolean mBlnGroupStart;
    private boolean mBlnGroupOrder;
    private boolean mBlnGroupDelete;
    private boolean mBlnGroupShowExisting;


    private void setMenu() {

        if (mMenu != null) {

            mBlnGroupStart = true;
            mBlnGroupOrder = mApplication.isTakingOrder;
            mBlnGroupDelete = mApplication.isEditingOrder();
            mBlnGroupShowExisting = !mApplication.isTakingOrder;

            mDrawerLayout.post(new Runnable() {
                @Override
                public void run() {
                    if (mBlnGroupOrder) {
                        mMenu.findItem(R.id.action_start_or_save_order).setIcon(ContextCompat.
                                getDrawable(mApplication, android.R.drawable.ic_menu_save))
                                .setTitle(R.string.menu_save_order);
                    } else {
                        mMenu.findItem(R.id.action_start_or_save_order).setIcon(ContextCompat.
                                getDrawable(mApplication, android.R.drawable.ic_menu_add))
                                .setTitle(R.string.menu_start_order);
                    }
                    mMenu.setGroupVisible(R.id.groupStart, mBlnGroupStart);
                    mMenu.setGroupVisible(R.id.groupOrder, mBlnGroupOrder);
                    mMenu.setGroupVisible(R.id.groupDelete, mBlnGroupDelete);
                    mMenu.setGroupVisible(R.id.groupShowExisting, mBlnGroupShowExisting);
                    MenuItem cancelItem = mMenu.findItem(R.id.action_cancel_order);
                    if (cancelItem != null) {
                        if (mBlnGroupDelete) {
                            cancelItem.setTitle(R.string.menu_cancel_changes);
                        } else {
                            cancelItem.setTitle(R.string.menu_cancel_order);
                        }
                    }
                }
            });

        }
    }


    // App name when no order is being taken.
    // Customer name when taking order.
    // Asterisk in front of customer name when editing order.
    private void setMainTitle() {
        String title = getString(R.string.app_name);
        if (mApplication.isTakingOrder) {
            title = mApplication.order.getCustomer();
            if (mApplication.isEditingOrder()) { title = "* " + title; }
        } else {
            if (mDrawerSelection > 0 && mDrawerAdapter.getCount() > mDrawerSelection) {
                title = mDrawerAdapter.getItem(mDrawerSelection);
            }
        }
        final String TITLE = title;
        mDrawerLayout.post(new Runnable() {
            @Override
            public void run() {
                setTitle(TITLE);
            }
        });
    }


    // Simulate spinner select item - prepare drawer list.
    private void drawerSpinnerSelect(final int position) {
        mDrawerSpinner.post(new Runnable() {
            @Override
            public void run() {
                mDrawerSpinner.setSelection(position);
            }
        });
    }


    // Simulate drawer list item click.
    private void showDrawerItem(final int position) {
        mDrawerList.post(new Runnable() {
            @Override
            public void run() {
                mDrawerList.performItemClick(mDrawerList, position, mDrawerList
                        .getItemIdAtPosition(position));
            }
        });
    }


    // Prepare list and click on item - spinner select and drawer list click together.
    private void showSelection() {
        drawerSpinnerSelect(mDrawerSpinnerPosition);
        showDrawerItem(mDrawerSelection);
    }


    // Simulate click 'All' (first item) in first product 'grouping' category.
    private void showAllProducts() {
        mDrawerSpinnerPosition = 1;
        mDrawerSelection = 0;
        showSelection();
    }


    // Simulate select last - Order option is always last if added.
    private void showOrderProducts() { drawerSpinnerSelect(mDrawerSpinner.getCount() - 1); }


    // Populate selection spinner in navigation with prepared list in application class.
    private void populateDrawerSpinner(String[] array) {
        Util.populateSpinnerSimpleMediumLightText(mDrawerSpinner, array, this);
        // Prevent IndexOutOfBoundsException - when order is removed from the list position will
        // equal length.
        if (mDrawerSpinnerPosition < array.length) {
            // Selection will reset for some options - preserve previous selection.
            mDrawerSpinner.post(new Runnable() {
                @Override
                public void run() {
                    mDrawerSpinner.setSelection(mDrawerSpinnerPosition, true);
                    preserveDrawerSelection();
                }
            });
        }
    }


    // Preserve drawer selection state (both on start/end order and on orientation change
    // when taking order):
    private void preserveDrawerSelection() {
        if (mDrawerSpinnerPosition > 0 || mDrawerSpinnerPosition <= mApplication
                .getGroupingProductColumnCount()) {
            mDrawerList.post(new Runnable() {
                @Override
                public void run() {
                    if (mDrawerAdapter.getCount() > mDrawerSelection) {
                        mDrawerList.performItemClick(mDrawerList, mDrawerSelection,
                                mDrawerList.getItemIdAtPosition(mDrawerSelection));
                    }
                    mDrawerList.setSelection(mDrawerScrollPosition);
                }
            });
        }
    }


    // Reset screen titles and menu.
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        // Get back stack count after back press.
        int backStackCount = getSupportFragmentManager().getBackStackEntryCount();
        if (backStackCount == 0) {
            // Reset title and menu when no fragment showing on top.
            setMainTitle();
            setMenu();
        }
    } // End onBackPressed


    private int saveRecyclerScrollPosition() {
        mRecyclerScrollPosition = ((GridLayoutManager) imageAdapter.recyclerView
                .getLayoutManager()).findFirstCompletelyVisibleItemPosition();
        return mRecyclerScrollPosition;
    }


    private void restoreRecyclerScrollPosition() {
        if (mRecyclerScrollPosition > 0) {
            imageAdapter.recyclerView.scrollToPosition(mRecyclerScrollPosition);
        }
    }


    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Pass any configuration change to the drawer toggle
        mDrawerToggle.onConfigurationChanged(newConfig);
    }


    // For receiving result from service update when it updates data.
    private final BroadcastReceiver mLocalServiceUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, final Intent intent) {

            String error = intent.getStringExtra(Util.ARG_SERVICE_UPDATE_ERROR);
            boolean getColumns = intent.getBooleanExtra(Util.ARG_GET_COLUMNS, false);
            boolean didProducts = intent.getBooleanExtra(Util.ARG_DO_PRODUCTS, false);
            boolean didCustomers = intent.getBooleanExtra(Util.ARG_DO_CUSTOMERS, false);

            // Only interested in broadcast when writing db is done; not interested in
            // broadcast after extracting columns.
            if (!getColumns) {
                if (error == null) {
                    // Ready message and set columns depending on what was done (products/customers)
                    String successMessage = getString(R.string.message_updated_data);
                    if (didProducts && didCustomers) {
                        mApplication.setProductColumnsAndCustomers(null);
                    } else {
                        if (didProducts && mUpdateCustomers) {
                            // Update customers after products when starting the app. No need to
                            // preserve state for the flag, customers are less often changed than
                            // products - updating them on next start is sufficient.
                            mUpdateCustomers = false;
                            mApplication.setProductColumns(null);
                            successMessage = getString(R.string.message_updated_products);
                            // Update customers
                            Intent intentUpdate = new Intent(MainActivity.this, ServiceUpdate
                                    .class);
                            intentUpdate.putExtra(Util.ARG_DO_CUSTOMERS, true);
                            startService(intentUpdate);
                        } else if (didCustomers) {
                            mApplication.setCustomerColumnsAndCustomers(null);
                            successMessage = getString(R.string.message_updated_customers);
                        }
                    }

                    saveRecyclerScrollPosition();
                    getSupportLoaderManager().restartLoader(IMAGE_LOADER_ID, null, MainActivity
                            .this);

                    Util.snackOrToast(mApplication, mDrawerLayout, successMessage,
                            Snackbar.LENGTH_LONG);
                } else {
                    // Display error if it happened.
                    int duration = Snackbar.LENGTH_INDEFINITE;
                    if (getString(R.string.warning_cannot_update_offline).equals(error)) {
                        duration = Snackbar.LENGTH_LONG;
                    }
                    Util.snackOrToast(mApplication, mDrawerLayout, error, duration);
                }
            }
        }
    };


    // Get result from Setting and Order activities. SettingsActivity passes changes to some
    // preferences to be able to reflect them in MainActivity. When displaying order (in
    // OrderActivity -> OrderFragment), if edit order was pressed, it leads to MainActivity in
    // order to allow for order editing. Then layout, menu and title need to be set to 'take/edit'
    // order layout, menu and title and depending on user settings preference, desired list of
    // products is shown - all, ordered or previous selection.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case Util.SETTINGS_ACTIVITY_RESULT_CODE: {
                if (resultCode == RESULT_OK) {
                    if (data != null) {
                        if (data.hasExtra(Util.RESULT_FINISH_MAIN)) {
                            // If restarting settings screen, close main activity as well as it
                            // will be restarted.
                            if (data.getBooleanExtra(Util.RESULT_FINISH_MAIN, true)) {
                                finish(); return;
                            }
                        }
                        if (data.hasExtra(Util.RESULT_RECREATE_MAIN_ACTIVITY)) {
                            // When restarting app from settings screen which gets closed,
                            // recreate main activity.
                            boolean isLandscape = getResources().getConfiguration().orientation
                                    == Configuration.ORIENTATION_LANDSCAPE;
                            if (data.getBooleanExtra(Util.RESULT_RECREATE_MAIN_ACTIVITY, true)
                                    && isLandscape) {
                                recreate();
                            }
                        }
                        if (data.hasExtra(Util.RESULT_GRID_COLUMN_WIDTH)) {
                            // After resetting grid column width (image size)
                            saveRecyclerScrollPosition();
                            int gridColumnWidth = data.getIntExtra(Util.RESULT_GRID_COLUMN_WIDTH,
                                    Util.DEFAULT_GRID_COLUMN_WIDTH);
                            imageAdapter.setGridColumns(gridColumnWidth);
                            imageAdapter.recyclerView
                                    .setLayoutManager(imageAdapter.makeGridLayoutManager());
                        }
                        if (data.hasExtra(Util.RESULT_LOAD_IMAGES_LOCATION)) {
                            // After resetting location where images are stored
                            imageAdapter.setImagesSource(data.getStringExtra(Util
                                    .RESULT_LOAD_IMAGES_LOCATION));
                        }
                        if (data.hasExtra(Util.RESULT_RESET_GROUPING_COLUMNS)) {
                            // After resetting which columns to use in navigation selection.
                            saveRecyclerScrollPosition();
                            mApplication.setProductColumnsAndCustomers(null);
                            populateDrawerSpinner(mApplication.getNavigationSelection());
                            // Select first 'grouping' column & populate navigation list to reflect
                            drawerSpinnerSelect(1);
                        }
                        if (data.hasExtra(Util.RESULT_OTHER_COLUMN_TITLE)) {
                            // After resetting titles for other columns - shown in navigation
                            // selection and details screen if selected.
                            saveRecyclerScrollPosition();
                            mApplication.setProductColumnsAndCustomers(null);
                            populateDrawerSpinner(mApplication.getNavigationSelection());
                        }
                    }
                    // Reload data and refresh recycler view to reflect the changes.
                    getSupportLoaderManager().restartLoader(IMAGE_LOADER_ID, null, this);
                    imageAdapter.refreshRecyclerView();
                }
                break;
            }
            case Util.ORDER_ACTIVITY_RESULT_CODE: {
                if (resultCode == RESULT_OK) {
                    // Set 'edit order' layout, menu and title.
                    setLayout(false);
                    if (mApplication.isEditingOrder()) {
                        // Show preferred selection of products on start edit order.
                        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences
                                (mApplication);
                        String show = sp.getString(getString(R.string.pref_on_edit_order),
                                getString(R.string.show_all_products));
                        if (show.equalsIgnoreCase(getString(R.string.show_all_products))) {
                            showAllProducts();   // All
                        } else if (show.equalsIgnoreCase(getString(R.string
                                .show_ordered_products))) {

                            showOrderProducts(); // Ordered
                        }                        // Previous selection (else) - no changes by
                        // default.
                    }
                }
                break;
            }
        }

    } // End onActivityResult method.


    @Override
    public void onPause() {
        super.onPause();
        // Unregister receivers.
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocalServiceUpdateReceiver);
        // If order is being taken, save it in order to reload it if app is killed.
        if (mApplication.isTakingOrder) {
            mApplication.orderFileName = mApplication.order.saveOrder(mApplication, false);
            SharedPreferences.Editor editor =
                    PreferenceManager.getDefaultSharedPreferences(mApplication).edit();
            editor.putString(Util.KEY_ORDER_FILE_NAME, mApplication.orderFileName);
            if (mApplication.isEditingOrder()) {
                editor.putBoolean(Util.KEY_EDITING_ORDER, mApplication.isEditingOrder());
            }
            editor.apply();
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        setMainTitle();
        // Register receivers.
        LocalBroadcastManager.getInstance(this).registerReceiver(mLocalServiceUpdateReceiver,
                new IntentFilter(Util.ARG_UPDATE_SERVICE_BROADCAST));
    }


    // Save state on rotation.
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_DRAWER_OPEN, mDrawerLayout.isDrawerOpen(GravityCompat.START));
        outState.putInt(STATE_DRAWER_LIST_SCROLL_Y, mDrawerList.getFirstVisiblePosition());
        outState.putInt(STATE_DRAWER_SELECTION, mDrawerSelection);
        outState.putInt(STATE_DRAWER_SPINNER_POSITION, mTempDrawerSpinnerPosition);
        outState.putInt(STATE_RECYCLER_SCROLL_POSITION, saveRecyclerScrollPosition());
        if (mShowActiveProducts != null) {
            outState.putBoolean(STATE_SHOW_ACTIVE_PRODUCTS, mShowActiveProducts);
        }
    }


} // End MainActivity class

