/*
 * Copyright (C) 2017 Vladimir Markovic. All rights reserved.
 */

package com.humaneapps.catalogsales;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;

import static android.app.Activity.RESULT_OK;


/**
 * OrderFragment is used both to display existing orders and to display the selected order or
 * order being taken/edited.
 * When displaying an order it can be displayed from the main screen (then it is the first
 * fragment) or from he 'existing orders screen' (then it is the second fragment) in
 * OrderActivity.
 * Order can be shown when displaying or editing existing orders or when taking a new order.
 */
public class OrderFragment extends Fragment {

    private final String STATE_ORDERS_SELECT_ALL = "STATE_ORDERS_SELECT_ALL";
    private final String STATE_ORDERS_SELECTED = "STATE_ORDERS_SELECTED";
    private final String STATE_ORDERS_SPINNER_CUSTOMER = "STATE_ORDERS_SPINNER_CUSTOMER";
    private final String STATE_ORDERS_SPINNER_DAYS = "STATE_ORDERS_SPINNER_DAYS";

    private OrderActivity mOrderActivity;
    private CatalogSales mApplication;

    // Views for displaying the order details.
    @BindView(R.id.txvOrderTitle)
    TextView mTxvOrderTitle;
    @BindView(R.id.lvOrder)
    ListView mLvOrder;
    @BindView(R.id.txvTotalPrice)
    TextView mTxvTotalPrice;
    @BindView(R.id.llOrderSpinners)
    LinearLayout mLlOrderSpinners;
    @BindView(R.id.spnOrderCustomers)
    Spinner mSpnCustomers;
    @BindView(R.id.spnOrderNumberOfDays)
    Spinner mSpnNumberOfDays;

    private OrderAdapter mOrderAdapter;
    private Menu mMenu;
    private final ArrayList<String> mAllOrderFileNames = new ArrayList<>();

    private boolean mShowExistingOrders = false;
    private int mSpinnerCustomerPosition = 0;
    private int mSpinnerDaysPosition = 0;


    public OrderFragment() {}


    public static OrderFragment newInstance() { return new OrderFragment(); }


    public static OrderFragment newInstance(boolean listExistingOrders) {
        OrderFragment orderFragment = new OrderFragment();
        Bundle bundle = new Bundle();
        bundle.putBoolean(Util.ARG_EXISTING_ORDERS, listExistingOrders);
        orderFragment.setArguments(bundle);
        return orderFragment;
    }


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mOrderActivity = (OrderActivity) context;
        mApplication = (CatalogSales) context.getApplicationContext();
    }


    // Enable custom menu for adding 'delete', 'email' and 'select all items'.
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            Bundle args = getArguments();
            if (args != null && args.getBoolean(Util.ARG_EXISTING_ORDERS, false)) {
                mShowExistingOrders = true;
            }
        } else {
            mShowExistingOrders = savedInstanceState.getBoolean(Util
                    .STATE_SHOWING_EXISTING_ORDERS, false);
        }
        setHasOptionsMenu(true);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle
            savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_order, container, false);
        ButterKnife.bind(this, rootView);
        setMainTitle();
        return rootView;
    } // End onCreateView method


    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mOrderActivity = (OrderActivity) getActivity();
        mOrderAdapter = new OrderAdapter(mOrderActivity);
        mLvOrder.setAdapter(mOrderAdapter);

        if (mShowExistingOrders) {
            // Hide unused views.
            mTxvTotalPrice.setVisibility(View.GONE);
            mTxvOrderTitle.setVisibility(View.GONE);
            mLlOrderSpinners.setVisibility(View.VISIBLE);
            populateSpinners();
            populateAllOrderFileNames();
            // Restore spinner selection states.
            if (savedInstanceState != null) {
                mSpinnerCustomerPosition = savedInstanceState.getInt(
                        STATE_ORDERS_SPINNER_CUSTOMER, 0);
                mSpinnerDaysPosition = savedInstanceState.getInt(STATE_ORDERS_SPINNER_DAYS, 0);
            }
            setSpinnerListeners();
            showExistingOrders();
            mLvOrder.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    showSelectedOrder(view);
                }
            });
            // Restore checkbox states.
            if (savedInstanceState != null) {
                if (savedInstanceState.getBoolean(STATE_ORDERS_SELECT_ALL, false)) {
                    mLvOrder.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mMenu != null && mMenu.findItem(R.id.action_select_all) != null) {
                                mMenu.findItem(R.id.action_select_all).setIcon(android.R.drawable
                                        .checkbox_on_background);
                            }
                        }
                    });
                }
                mOrderAdapter.setSelected(savedInstanceState.getBooleanArray(
                        STATE_ORDERS_SELECTED));
                refreshListView();
            }
        } else {
            // If displaying certain order.
            // Order file name is not set until the order is saved, thus when displaying the order
            // being taken set title to customer. Otherwise set title to order file name. When
            // editing order add asterisk in-front of the order file name.
            String orderTitle = mApplication.orderFileName;
            if (orderTitle == null
                    || (mApplication.isTakingOrder && !mApplication.isEditingOrder())) {
                orderTitle = mApplication.order.getCustomer();
            } else {
                if (mApplication.isEditingOrder()) { orderTitle = "* " + orderTitle; }
            }
            mTxvOrderTitle.setText(orderTitle);
            String strTotal = mApplication.order.getTotalString(mApplication);
            if (!"".equals(strTotal)) {
                mTxvTotalPrice.setText(strTotal);
            } else {mTxvTotalPrice.setVisibility(View.GONE); }

            mOrderAdapter.swapData(mApplication.order);
        }

    }


    // Set appropriate menu for each screen ('show existing orders' and 'display order').
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        if (mShowExistingOrders) {
            inflater.inflate(R.menu.menu_existing_orders, menu);
        } else {
            if (mApplication.isTakingOrder) {
                inflater.inflate(R.menu.menu_show_taking_order, menu);
            } else {
                inflater.inflate(R.menu.menu_show_existing_order, menu);
            }
        }
        mMenu = menu;
    }


    @Override
    public boolean onOptionsItemSelected(final MenuItem menuItem) {

        int id = menuItem.getItemId();

        if (mApplication.isTakingOrder) {
            switch (id) {
                case R.id.action_save_order: {
                    Util.showDialogConfirm(getActivity(), getString(R.string
                            .dialog_save_order_title));
                    return true;
                }
                case R.id.action_save_and_send: {
                    Util.showDialogConfirm(getActivity(), getString(R.string
                            .dialog_save_and_send_order_title));
                    return true;
                }
                case R.id.action_cancel_order: {
                    Util.showDialogConfirm(getActivity(), getString(R.string
                            .dialog_cancel_taking_order_title));
                    return true;
                }
            }
        } else {
            if (mShowExistingOrders) {
                switch (id) {
                    case R.id.action_email_orders: {
                        Util.emailOrders(getCheckedOrderFileNames(), getActivity());
                        return true;
                    }
                    case R.id.action_select_all: {
                        selectAllOrders(menuItem);
                        return true;
                    }
                    case R.id.action_delete_selected: {
                        deleteSelectedOrders();
                        return true;
                    }
                }
            } else {
                switch (id) {
                    case R.id.action_edit_order: {
                        editOrder();
                        return true;
                    }
                    case R.id.action_email_order: {
                        ArrayList<String> orderFileNames = new ArrayList<>();
                        orderFileNames.add(mApplication.orderFileName);
                        Util.emailOrders(orderFileNames, getActivity());
                        return true;
                    }
                    case R.id.action_delete_order: {
                        Util.showDialogConfirm(getActivity(), getString(R.string
                                .dialog_delete_order_title));
                        return true;
                    }
                }
            }
        }

        return super.onOptionsItemSelected(menuItem);
    }


    void resetSelectAllIcon() {
        if (mMenu != null) {
            MenuItem selectAllMenuItem = mMenu.findItem(R.id.action_select_all);
            if (selectAllMenuItem != null) {
                if (mOrderAdapter.getSelectAll()) {
                    selectAllMenuItem.setIcon(android.R.drawable.checkbox_on_background);
                } else {
                    selectAllMenuItem.setIcon(android.R.drawable.checkbox_off_background);
                }
            }
        }
    }


    private void populateSpinners() {
        Util.populateSpinnerSimpleMediumLightText(mSpnCustomers, Util.getCustomers(mApplication)
                , mApplication);
        Util.populateAndCustomizeSpinner(mSpnNumberOfDays, Util.makeDaySelectionList(), mApplication
                , android.R.layout.simple_spinner_item, R.layout.day_number_spinner_item
                , R.dimen.text_size_s, R.color.colorTextLight, 0, 0);
    }


    private void setSpinnerListeners() {
        AdapterView.OnItemSelectedListener selectedListener = new AdapterView
                .OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mSpinnerCustomerPosition = mSpnCustomers.getSelectedItemPosition();
                mSpinnerDaysPosition = mSpnNumberOfDays.getSelectedItemPosition();
                showExistingOrders();
            }


            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        };
        // To prevent onItemSelected from firing when setting listeners
        mSpnCustomers.setSelection(mSpinnerCustomerPosition, false);
        mSpnNumberOfDays.setSelection(mSpinnerDaysPosition, false);
        mSpnCustomers.setOnItemSelectedListener(selectedListener);
        mSpnNumberOfDays.setOnItemSelectedListener(selectedListener);
    }


    // Show orders per customer spinner and day spinner selections. Each order file name is
    // constructed as customer-date-time.csv with date format yyMMdd. Go through all file names
    // and extract customer name and creation date and compare to spinner selections. Display all
    // orders which match the selections.
    private void showExistingOrders() {

        if (mAllOrderFileNames.size() == 0) {
            mOrderAdapter.showExistingOrders(new String[0]);
            return;
        }

        StringBuilder error = new StringBuilder();

        ArrayList<String> ordersToDisplay = new ArrayList<>();
        final StringBuilder date = new StringBuilder();
        final StringBuilder customer = new StringBuilder();

        Calendar cal = Calendar.getInstance();
        int year, month, day, dashIndex, daysSinceToday;

        // get customer and days spinner selections
        int setDays = Integer.parseInt(mSpnNumberOfDays.getItemAtPosition(mSpinnerDaysPosition)
                .toString());
        String setCustomer = mSpnCustomers.getItemAtPosition(mSpinnerCustomerPosition).toString();

        for (String orderFileName : mAllOrderFileNames) {

            year = -1; month = -1; day = -1;
            // Extract customer name, year, month and date strings.
            dashIndex = StringUtils.lastOrdinalIndexOf(orderFileName, "-", 2);
            customer.append(orderFileName.substring(0, dashIndex));
            String strYear = orderFileName.substring(dashIndex + 1, dashIndex + 3);
            String strMonth = orderFileName.substring(dashIndex + 3, dashIndex + 5);
            String strDay = orderFileName.substring(dashIndex + 5, dashIndex + 7);

            // Form year mont and day from extracted strings. If cannot add error into error message

            if (TextUtils.isDigitsOnly(strYear)) {
                year = Integer.parseInt(strYear) + 2000;
            } else {
                error.append(getString(R.string.wrong_year));
                error.append(" '");
                error.append(strYear);
                error.append("' ");
            }

            if (TextUtils.isDigitsOnly(strYear)) {
                month = Integer.parseInt(strMonth) - 1;
            } else {
                error.append(getString(R.string.wrong_month));
                error.append(" '");
                error.append(strMonth);
                error.append("' ");
            }

            if (TextUtils.isDigitsOnly(strYear)) {
                day = Integer.parseInt(strDay);
            } else {
                error.append(getString(R.string.wrong_day));
                error.append(" '");
                error.append(strDay);
                error.append("' ");
            }
            // If error happened, finalize error message.
            if (year < 0 || month < 0 || day < 0) {
                error.append(getString(R.string.in_order_name));
                error.append(" '");
                error.append(orderFileName);
                error.append("'\n\n");
                ordersToDisplay.add("*" + orderFileName);
            } else {
                // If no error happened create date for comparing
                cal.set(year, month, day);
                daysSinceToday = (int) TimeUnit.MILLISECONDS.toDays(Calendar.getInstance()
                        .getTimeInMillis() - cal.getTimeInMillis());
                // If date falls into number of days selected and customer name matches selected
                // customer or "ALL" is selected add that order name to display it.
                if (daysSinceToday < setDays && (setCustomer.equalsIgnoreCase("ALL")
                        || setCustomer.equalsIgnoreCase(customer.toString()))) {
                    ordersToDisplay.add(orderFileName);
                }
            }
            // Reset date and customer for next order file name.
            date.setLength(0);
            customer.setLength(0);
        }

        // Show all orders added which matched the criteria.
        mOrderAdapter.showExistingOrders(
                ordersToDisplay.toArray(new String[ordersToDisplay.size()]));

        // If error happened while processing any of the order file names, display error message.
        if (error.length() > 0) {
            Util.snackOrToast(mApplication, getView(),
                    error.toString(), Snackbar.LENGTH_INDEFINITE);
        }
    }


    private void showSelectedOrder(View view) {

        TextView txvProduct = view.findViewById(R.id.txvProductOrOrderFile);
        if (txvProduct == null) {
            Toast.makeText(getActivity(), getString(R.string.load_order_fail), Toast
                    .LENGTH_SHORT).show();
            return;
        }
        String orderFileName = txvProduct.getText().toString();

        if (orderFileName.indexOf("-") < 1) {
            Toast.makeText(getActivity(), getString(R.string.load_order_fail), Toast
                    .LENGTH_SHORT).show();
            return;
        }

        Util.loadOrder(orderFileName, mApplication);

        if (getActivity() instanceof AppCompatActivity) {
            Util.showFragment((AppCompatActivity) getActivity(), OrderFragment.newInstance(),
                    getString(R.string.title_order), true, null);
        }
    }


    // Save state on rotation.
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(Util.STATE_SHOWING_EXISTING_ORDERS, mShowExistingOrders);
        if (mShowExistingOrders) {
            outState.putBoolean(STATE_ORDERS_SELECT_ALL, mOrderAdapter.getSelectAll());
            outState.putBooleanArray(STATE_ORDERS_SELECTED, mOrderAdapter.getSelected());
            outState.putInt(STATE_ORDERS_SPINNER_CUSTOMER, mSpinnerCustomerPosition);
            outState.putInt(STATE_ORDERS_SPINNER_DAYS, mSpinnerDaysPosition);
        }
    }


    private void selectAllOrders(final MenuItem menuItem) {
        mOrderAdapter.setAllSelected(!mOrderAdapter.getSelectAll());
        refreshListView();
        mLvOrder.post(new Runnable() {
            @Override
            public void run() {
                if (mOrderAdapter.getSelectAll()) {
                    menuItem.setIcon(android.R.drawable.checkbox_on_background);
                } else {
                    menuItem.setIcon(android.R.drawable.checkbox_off_background);
                }
            }
        });
    }


    private void editOrder() {
        mApplication.setEditingOrder(true);
        getActivity().onBackPressed();
    }


    // Called from confirmation dialog.
    void cancelEditingOrder() {
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        mTxvOrderTitle.setText(mApplication.orderFileName);
        mApplication.setEditingOrder(false);
        activity.setTitle(getString(R.string.title_order));
        mMenu.clear();
        activity.getMenuInflater().inflate(R.menu.menu_show_existing_order, mMenu);
    }


    // Called from confirmation dialog.
    String saveOrder() {
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        String orderFileName = Util.saveOrder(activity, mApplication);
        mTxvOrderTitle.setText(orderFileName);
        mApplication.setEditingOrder(false);
        activity.setTitle(getString(R.string.title_order));
        mMenu.clear();
        activity.getMenuInflater().inflate(R.menu.menu_show_existing_order, mMenu);
        activity.setResult(RESULT_OK);
        return orderFileName;
    }


    // Remove all views to force RecyclerView redraw. Also destroy drawing cache and refresh
    // drawable state.
    private void refreshListView() {
        mLvOrder.invalidate();
        mOrderAdapter.notifyDataSetChanged();
    }


    // Fetch and store all csv files names in the order dir.
    private void populateAllOrderFileNames() {
        mAllOrderFileNames.clear();
        File orderDir = new File(Util.getOrderDirPath(mApplication));
        File[] orderFiles = orderDir.listFiles();
        // If order dir is empty, notify and return.
        if (orderFiles == null || orderFiles.length == 0) {
            mTxvOrderTitle.setText(getString(R.string.no_existing_orders));
            return;
        }
        String orderFileName;
        for (File orderFile : orderFiles) {
            orderFileName = orderFile.getName();
            if (".csv".equalsIgnoreCase(orderFileName.substring(orderFileName.length() - 4))) {
                mAllOrderFileNames.add(orderFileName);
            }
        }
    }


    private ArrayList<String> getCheckedOrderFileNames() {
        final ArrayList<String> orderFileNames = new ArrayList<>();
        View view;
        CheckBox cbxEmail;
        TextView txvOrderFile;
        for (int i = 0; i < mLvOrder.getCount(); i++) {
            view = mLvOrder.getAdapter().getView(i, null, null);
            cbxEmail = view.findViewById(R.id.cbxEmail);
            txvOrderFile = view.findViewById(R.id.txvProductOrOrderFile);
            if (cbxEmail != null && txvOrderFile != null && cbxEmail.isChecked()) {
                orderFileNames.add(txvOrderFile.getText().toString());
            }
        }
        return orderFileNames;
    }


    // On delete (selected orders) button click, show confirmation dialog.
    private void deleteSelectedOrders() {
        if (mShowExistingOrders && getCheckedOrderFileNames().size() == 0) {
            Toast.makeText(mApplication, getString(R.string.select_first), Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        Util.showDialogConfirm(getActivity(), getString(R.string
                .dialog_delete_selected_orders_title));
    }


    // On confirmation dialog 'Delete' click, delete selected orders.
    void deleteOrders() { deleteOrders(getCheckedOrderFileNames()); }


    // Delete passed list of orders.
    private void deleteOrders(ArrayList<String> orderFileNames) {

        int selectedCount = orderFileNames.size();
        if (selectedCount == 0) {
            Toast.makeText(getActivity(), getString(R.string.select_first), Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        String success = getString(R.string.success);
        String failed = getString(R.string.failed);
        StringBuilder message = new StringBuilder(getString(R.string.delete_result));
        message.append("\n\n");
        String orderFileName;

        for (int i = 0; i < selectedCount; i++) {

            orderFileName = orderFileNames.get(i);
            File orderFile = new File(Util.getOrderDirPath(mApplication), orderFileName);
            if (orderFile.exists() && orderFile.delete()) {
                message.append(success);
                mAllOrderFileNames.remove(orderFileName);
            } else {
                message.append(failed);
            }
            message.append(": ");
            message.append(orderFileName);
            message.append("\n");
        }
        mLvOrder.post(new Runnable() {
            @Override
            public void run() {
                showExistingOrders();
            }
        });
        Toast.makeText(getActivity(), message.toString(), Toast.LENGTH_LONG).show();
    }


    // Delete displayed order (from confirmation dialog).
    void deleteOrder() {
        ArrayList<String> orderFileNames = new ArrayList<>();
        orderFileNames.add(mApplication.orderFileName);
        deleteOrders(orderFileNames);
    }


    private void setMainTitle() {
        if (mApplication.isTakingOrder) {
            if (mApplication.isEditingOrder()) {
                getActivity().setTitle(getString(R.string.title_editing_order));
            } else {
                getActivity().setTitle(getString(R.string.title_taking_order));
            }
        } else {
            if (mShowExistingOrders) {
                getActivity().setTitle(getString(R.string.title_existing_orders));
            } else {
                getActivity().setTitle(getString(R.string.title_order));
            }
        }
    }


} // End OrderFragment class
