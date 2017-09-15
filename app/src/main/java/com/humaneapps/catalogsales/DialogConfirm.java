/*
 * Copyright (C) 2017 Vladimir Markovic. All rights reserved.
 */

package com.humaneapps.catalogsales;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.File;

import static android.app.Activity.RESULT_OK;

/*
 * Dialog used for selecting a customer when starting new order and for confirming different user
 * actions: save order, cancel order or delete order/s.
 */


public class DialogConfirm extends DialogFragment {


    private Spinner mSpnCustomers;


    public static DialogConfirm newInstance() { return new DialogConfirm(); }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        @SuppressLint("InflateParams")
        View customView = LayoutInflater.from(getActivity()).inflate(R.layout.new_order_dialog,
                null);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), android.R.style
                .Theme_InputMethod);
        builder.setView(customView);

        CatalogSales application = (CatalogSales) getActivity().getApplicationContext();

        String title = "", message = "", positiveButtonText = "", negativeButtonText = "";
        final String DIALOG_TAG = getTag();

        String orderFileName = application.orderFileName;
        if (orderFileName == null) { orderFileName = ""; }

        // Setup dialog in correspondence to the passed in tag.

        if (DIALOG_TAG.equalsIgnoreCase(getString(R.string.dialog_start_order_title))) {
            // Start order
            mSpnCustomers = customView.findViewById(R.id.spnCustomers);
            mSpnCustomers.setVisibility(View.VISIBLE);
            Util.populateSpinnerSimpleMediumLightText(mSpnCustomers, application.getCustomers(),
                    getActivity());
            title = getString(R.string.dialog_start_order_title);
            message = getString(R.string.dialog_start_order_message);
            positiveButtonText = getString(R.string.menu_start_order);
            negativeButtonText = getString(R.string.cancel);
        } else if (DIALOG_TAG.equalsIgnoreCase(getString(R.string.dialog_save_order_title))) {
            // Save order being taken or edited
            title = getString(R.string.dialog_save_order_title);
            if (application.isEditingOrder()) {
                message = String.format(getString(R.string.dialog_save_editing_order_message),
                        orderFileName);
            } else {
                message = String.format(getString(R.string.dialog_save_taking_order_message),
                        orderFileName);
            }
            positiveButtonText = getString(R.string.save);
            negativeButtonText = getString(R.string.cancel);
        } else if (DIALOG_TAG.equalsIgnoreCase(getString(R.string
                .dialog_save_and_send_order_title))) {
            // Save and send order being taken or edited
            title = getString(R.string.dialog_save_and_send_order_title);
            if (application.isEditingOrder()) {
                message = String.format(getString(R.string.dialog_send_editing_order_message),
                        orderFileName);
            } else {
                message = String.format(getString(R.string.dialog_send_taking_order_message),
                        orderFileName);
            }
            positiveButtonText = getString(R.string.dialog_save_and_send_order_title);
            negativeButtonText = getString(R.string.cancel);
        } else if (DIALOG_TAG.equalsIgnoreCase(getString(R.string.dialog_cancel_taking_order_title))) {
            // Cancel order
            if (application.isEditingOrder()) {
                title = getString(R.string.dialog_cancel_editing_order_title);
                message = getString(R.string.dialog_cancel_editing_order_message) + " " +
                        orderFileName;
            } else {
                title = getString(R.string.dialog_cancel_taking_order_title);
                message = getString(R.string.dialog_cancel_taking_order_message) + " " +
                        orderFileName;
            }

            positiveButtonText = getString(R.string.yes);
            negativeButtonText = getString(R.string.no);
        } else if (DIALOG_TAG.equalsIgnoreCase(getString(R.string.dialog_delete_order_title))) {
            // Delete order
            title = getString(R.string.dialog_delete_order_title);
            message = getString(R.string.dialog_delete_order_message) + ":\n\n" + orderFileName;
            positiveButtonText = getString(R.string.delete);
            negativeButtonText = getString(R.string.cancel);
        } else if (DIALOG_TAG.equalsIgnoreCase(getString(R.string
                .dialog_delete_selected_orders_title))) {
            // Delete selected orders
            title = getString(R.string.dialog_delete_selected_orders_title);
            message = getString(R.string.dialog_delete_selected_orders_message);
            positiveButtonText = getString(R.string.delete);
            negativeButtonText = getString(R.string.cancel);
        }

        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(positiveButtonText,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        startOrDeleteOrder(DIALOG_TAG);
                    }
                });
        builder.setNegativeButton(negativeButtonText,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dismissAllowingStateLoss();
                    }
                });

        return builder.create();
    }


    // Positive button action.
    private void startOrDeleteOrder(final String DIALOG_TAG) {
        Activity activity = getActivity();
        CatalogSales application = (CatalogSales) activity.getApplication();
        if (activity instanceof MainActivity) {
            // When called from MainActivity
            MainActivity mainActivity = (MainActivity) activity;
            if (DIALOG_TAG.equalsIgnoreCase(getString(R.string.dialog_start_order_title))) {
                // Start order
                mainActivity.startNewOrder(mSpnCustomers.getSelectedItem().toString());
            } else if (DIALOG_TAG.equalsIgnoreCase(getString(R.string.dialog_save_order_title))) {
                // Save order being taken or edited
                Util.saveAndEndOrder(activity);
                mainActivity.setLayout(false);
            } else if (DIALOG_TAG.equalsIgnoreCase(getString(R.string
                    .dialog_save_and_send_order_title))) {
                // Save and send order being taken or edited
                String orderFileName = Util.saveAndEndOrder(activity);
                mainActivity.setLayout(false);
                if (orderFileName != null) { Util.emailOrder(activity, orderFileName); }
            } else if (DIALOG_TAG.equalsIgnoreCase(getString(R.string.dialog_delete_order_title))) {
                // Delete order
                deleteOrder(application, true);
                Util.endOrder(application);
                mainActivity.setLayout(false);
            } else if (DIALOG_TAG.equalsIgnoreCase(getString(R.string.dialog_cancel_taking_order_title))) {
                // Cancel order
                if (!application.isEditingOrder() && application.orderFileName != null) {
                    // If taking (not editing order), if order was saved (in onPause), delete it.
                    deleteOrder(application, false);
                }
                Util.endOrder(application);
                mainActivity.setLayout(false);
            }
        } else if (activity instanceof OrderActivity) {
            // When called from OrderActivity
            OrderActivity orderActivity = (OrderActivity) activity;
            if (DIALOG_TAG.equalsIgnoreCase(getString(R.string.dialog_save_order_title))) {
                // Save order being taken or edited
                OrderFragment orderFragment = (OrderFragment) orderActivity
                        .getSupportFragmentManager().findFragmentByTag(
                                getString(R.string.title_order));
                if (orderFragment != null) { orderFragment.saveOrder(); }
            } else if (DIALOG_TAG.equalsIgnoreCase(getString(R.string
                    .dialog_save_and_send_order_title))) {
                // Save and send order being taken or edited
                OrderFragment orderFragment = (OrderFragment) orderActivity
                        .getSupportFragmentManager().findFragmentByTag(
                                getString(R.string.title_order));
                if (orderFragment != null) {
                    String orderFileName = orderFragment.saveOrder();
                    if (orderFileName != null) { Util.emailOrder(activity, orderFileName); }
                }
            } else if (DIALOG_TAG.equalsIgnoreCase(getString(R.string.dialog_cancel_taking_order_title))) {
                // Cancel order
                if (application.isTakingOrder) {
                    if (application.isEditingOrder()) {
                        OrderFragment orderFragment = (OrderFragment) orderActivity
                                .getSupportFragmentManager().findFragmentByTag(
                                        getString(R.string.title_order));
                        if (orderFragment != null) { orderFragment.cancelEditingOrder(); }
                        activity.setResult(RESULT_OK);
                    } else {
                        if (application.orderFileName != null) { deleteOrder(application, false); }
                        Util.endOrder(application);
                        // Go back to show main no order screen.
                        activity.onBackPressed();
                    }
                }
            } else if (DIALOG_TAG.equalsIgnoreCase(getString(R.string.dialog_delete_order_title))) {
                // Delete order
                OrderFragment orderFragmentExistingOrders = (OrderFragment) orderActivity
                        .getSupportFragmentManager().findFragmentByTag(getString(R.string
                                .title_existing_orders));
                if (orderFragmentExistingOrders != null) {
                    orderFragmentExistingOrders.deleteOrder();
                }
                // Go back to show existing orders screen.
                activity.onBackPressed();
            } else if (DIALOG_TAG.equalsIgnoreCase(getString(R.string
                    .dialog_delete_selected_orders_title))) {
                // Delete selected orders
                OrderFragment orderFragmentExistingOrders = (OrderFragment) orderActivity
                        .getSupportFragmentManager().findFragmentByTag(getString(R.string
                                .title_existing_orders));
                if (orderFragmentExistingOrders != null) {
                    orderFragmentExistingOrders.deleteOrders();
                }
            }
        }
        dismissAllowingStateLoss();
    }


    private void deleteOrder(CatalogSales application, boolean showMessage) {
        File orderFile = Util.getFile(Util.getOrderDirPath(application), application.orderFileName);
        boolean deleted = false;
        if (orderFile != null) { deleted = orderFile.delete(); }
        if (showMessage) {
            String message = getString(R.string.failed);
            if (deleted) { message = getString(R.string.success); }
            message += " " + getString(R.string.deleting_order) + " " + application.orderFileName;
            Toast.makeText(application, message, Toast.LENGTH_SHORT).show();
        }
    }


} // End DialogConfirm class.