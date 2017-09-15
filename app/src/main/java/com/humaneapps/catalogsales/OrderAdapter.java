/*
 * Copyright (C) 2017 Vladimir Markovic. All rights reserved.
 */
package com.humaneapps.catalogsales;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * For list view displaying:
 * product order as row of ordered products - name, price, quantity, pack price, number of packs
 * - when showing a specific order, and
 * list of order names when showing existing orders.
 */

class OrderAdapter extends ArrayAdapter<String> {

    private final CatalogSales mApplication;
    private final OrderActivity mOrderActivity;
    private final Order mOrder = new Order();
    private boolean mShowExistingOrders = false;
    private boolean mSelectAll = false;
    private boolean[] mSelected;
    private String[] mOrderFileNames;
    private int mItemCount;
    @SuppressWarnings("FieldCanBeLocal")
    private int mQuantity;
    @SuppressWarnings("FieldCanBeLocal")
    private double mPrice, mSum;
    private int mSelectedCount = 0;


    OrderAdapter(OrderActivity orderActivity) {
        super(orderActivity.getApplicationContext(), R.layout.order_item);
        mOrderActivity = orderActivity;
        mApplication = (CatalogSales) orderActivity.getApplication();
    }


    @Override
    public int getCount() {
        return mItemCount;
    }


    @Override
    public void clear() {
        mOrder.clear();
        mItemCount = 0;
    }


    void swapData(Order order) {
        mOrder.swapOrder(order);
        mShowExistingOrders = false;
        mItemCount = mOrder.getCount();
        notifyDataSetChanged();
    }


    void showExistingOrders(String[] orderFileNames) {
        // First call to notifyDataSetChanged is for resetting mSelectedCount to zero, via
        // onCheckChanged, since it has to be done before the number of items change. When the
        // spinner selection (for customer or number of days) change, if the number of displaying
        // order changes and the one of the previously shown is showing no more and it was checked,
        // it will not trigger onCheckChanged and it will not adjust mSelectedCount.
        notifyDataSetChanged();
        mOrderFileNames = orderFileNames;
        mShowExistingOrders = true;
        mItemCount = mOrderFileNames.length;
        mSelected = new boolean[mItemCount];
        mSelectAll = false;
        notifyDataSetChanged();
    }


    @Override
    @NonNull
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        // If old view cannot be reused, create new view to use.
        if (null == convertView) {
            convertView = LayoutInflater.from(mApplication).inflate(R.layout.order_item, parent,
                    false);
        }

        OrderViewHolder holder = new OrderViewHolder(convertView);
        if (mShowExistingOrders) {
            // Show existing orders.
            holder.mTxvProductOrOrderFile.setText(mOrderFileNames[position]);
            holder.mCbxEmail.setTag(position);
            holder.mCbxEmail.setChecked(mSelected[position]);
        } else {
            // Show specific order details.
            mPrice = mOrder.getPrice(position);
            mQuantity = mOrder.getQuantity(position);
            mSum = mPrice * mQuantity;
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mApplication);
            boolean includeTax = sp.getBoolean(mApplication.getString(R.string
                    .pref_sum_tax_inclusive), false);
            int taxPercent = mOrder.getTaxPercent(position);
            if (includeTax && taxPercent > 0) { mSum *= (100 + taxPercent) / 100d; }
            holder.mTxvProductOrOrderFile.setText(mOrder.getTitle(position));
            if (mPrice > 0) {
                holder.mTxvPrice.setText(String.format(Locale.getDefault(), "$%1$.2f", mPrice));
            }
            holder.mTxvQuantity.setText(String.valueOf(mQuantity));
            if (mSum > 0) {
                holder.mTxvSum.setText(String.format(Locale.getDefault(), "$%1$.2f", mSum));
            }
        }
        return convertView;
    }


    boolean getSelectAll() { return mSelectAll; }


    // For persisting state before configuration change.
    boolean[] getSelected() { return mSelected; }


    void setAllSelected(boolean selectAll) {
        mSelectAll = selectAll;
        for (int i = 0; i < mSelected.length; i++) { mSelected[i] = selectAll; }
    }


    // For retaining state on configuration change.
    void setSelected(boolean[] selected) {
        if (selected != null) { mSelected = selected; }
    }


    class OrderViewHolder {

        @BindView(R.id.llOrderItem)
        LinearLayout mLlOrderItem;
        @BindView(R.id.txvProductOrOrderFile)
        TextView mTxvProductOrOrderFile;
        @BindView(R.id.txvPrice)
        TextView mTxvPrice;
        @BindView(R.id.txvQuantity)
        TextView mTxvQuantity;
        @BindView(R.id.txvSum)
        TextView mTxvSum;
        @BindView(R.id.cbxEmail)
        CheckBox mCbxEmail;


        OrderViewHolder(View view) {

            ButterKnife.bind(this, view);

            if (mShowExistingOrders) {
                // When showing existing orders
                setVisibilityToViews(View.GONE);
                // Keep count of checked orders. When all are checked - check 'all' checkbox, and
                // un-check it if one order gets unchecked.
                mCbxEmail.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        mSelected[Integer.parseInt(buttonView.getTag().toString())] = isChecked;
                        if (isChecked) {
                            mSelectedCount++;
                        } else {
                            mSelectedCount--;
                        }
                        if (mSelectAll) {
                            mSelectAll = mSelectedCount == mItemCount;
                            if (!mSelectAll) { mOrderActivity.resetSelectAllIcon(); }
                        } else {
                            mSelectAll = mSelectedCount == mItemCount;
                            if (mSelectAll) { mOrderActivity.resetSelectAllIcon(); }
                        }
                    }
                });

            } else {
                // When showing specific order
                setVisibilityToViews(View.VISIBLE);
                int padding = mApplication.getResources().getDimensionPixelSize(R.dimen.gap_m);
                mLlOrderItem.setPadding(padding, padding, padding, padding);
            }
        }


        // Remove/add unused/used views as needed for 'show existing orders' and 'display order'.
        private void setVisibilityToViews(int visibility) {
            int oppositeVisibility = visibility == View.GONE ? View.VISIBLE : View.GONE;
            // Shown in 'display order' but not in 'show existing orders'.
            mTxvPrice.setVisibility(visibility);
            mTxvQuantity.setVisibility(visibility);
            mTxvSum.setVisibility(visibility);
            // Shown in 'show existing orders' but not in 'display order'.
            mCbxEmail.setVisibility(oppositeVisibility);
        }


    } // End inner class OrderViewHolder.


} // End class OrderAdapter.
