// Copyright (C) 2017 Vladimir Markovic. All rights reserved.

package com.humaneapps.catalogsales;

import android.app.Application;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.preference.PreferenceManager;

import com.humaneapps.catalogsales.data.DbContract;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Application level class containing common data needed across the application:
 * - locale,
 * - flag for blocking details screen showing when taking orders,
 * - shown products details bundle
 * - order object and order file name,
 * - flags for taking and editing order,
 * - setting of products and customer columns and grouping selection lists,
 * - getters for product and customer columns and related indices and of grouping selection list
 * and navigation selection (for populating navigation spinner and corresponding lists)
 */

public class CatalogSales extends Application {

    // Set locale to easily get it from one place and not to have to get configuration resources
    // inside RecyclerView.Adapter onBindViewHolder.

    private Locale mLocale;


    @SuppressWarnings("deprecation")
    public Locale getLocale() {
        if (mLocale == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mLocale = getResources().getConfiguration().getLocales().get(0);
            } else {
                mLocale = getResources().getConfiguration().locale;
            }
        }
        return mLocale;
    }


    // Block Details Switch - set in MainActivity, used in ImageAdapter.
    boolean blockDetails = false;


    // For preserving shown product (in details) - used in two pane mode only.

    private Bundle mShownProductDetails;


    public void setShownProductDetails(Bundle bundle) { mShownProductDetails = bundle; }


    public Bundle getShownProductDetails() { return mShownProductDetails; }


    // Order and order file name:
    final Order order = new Order();
    String orderFileName = null;


    // Flags for when taking and editing order in progress:

    public boolean isTakingOrder;

    private boolean isEditingOrder;


    public void setEditingOrder(boolean editing) {
        isEditingOrder = editing; isTakingOrder = editing;
    }


    public boolean isEditingOrder() { return isEditingOrder; }


    // Setters and getters for product table related data:

    // Set (main and other to main) product columns:
    private String[] mAllProductColumns, mOtherProductColumns, mOtherProductColumnsTitles;
    private int[] mOtherProductColumnsIndices;
    private int mProductColumnIdentifierIndex, mProductColumnImageIndex, mProductColumnTitleIndex,
            mProductColumnPriceIndex, mProductColumnTaxIndex, mProductColumnPackSizeIndex,
            mProductColumnActiveIndex;
    private int mAllProductColumnCount, mOtherProductColumnCount;


    void setMainAndOtherProductColumns(@Nullable SharedPreferences sp) {
        if (sp == null) { sp = PreferenceManager.getDefaultSharedPreferences(this); }
        String productColumns = sp.getString(DbContract.PREF_PRODUCT_COLUMNS_KEY, "");
        if ("".equals(productColumns)) { return; }
        mAllProductColumns = productColumns.split(", ");
        mAllProductColumnCount = mAllProductColumns.length;

        final ArrayList<String> alAllProductColumns = new ArrayList<>(mAllProductColumnCount);
        final ArrayList<Integer> alMainProductColumnsIndices = new ArrayList<>();

        alAllProductColumns.addAll(Arrays.asList(mAllProductColumns));
        mProductColumnIdentifierIndex = findProductColumnIndex(sp, alAllProductColumns, DbContract
                .COLUMN_PRODUCT_IDENTIFIER_KEY);
        mProductColumnTitleIndex = findProductColumnIndex(sp, alAllProductColumns, DbContract
                .COLUMN_PRODUCT_TITLE_KEY);
        mProductColumnImageIndex = findProductColumnIndex(sp, alAllProductColumns, DbContract
                .COLUMN_PRODUCT_IMAGE_KEY);
        mProductColumnPriceIndex = findProductColumnIndex(sp, alAllProductColumns, DbContract
                .COLUMN_PRODUCT_PRICE_KEY);
        mProductColumnTaxIndex = findProductColumnIndex(sp, alAllProductColumns, DbContract
                .COLUMN_PRODUCT_TAX_KEY);
        mProductColumnPackSizeIndex = findProductColumnIndex(sp, alAllProductColumns, DbContract
                .COLUMN_PRODUCT_PACK_SIZE_KEY);
        mProductColumnActiveIndex = findProductColumnIndex(sp, alAllProductColumns, DbContract
                .COLUMN_PRODUCT_ACTIVE_KEY);
        alMainProductColumnsIndices.add(0);
        if (mProductColumnImageIndex > -1) {
            alMainProductColumnsIndices.add(mProductColumnImageIndex);
        }
        if (mProductColumnTitleIndex > -1) {
            alMainProductColumnsIndices.add(mProductColumnTitleIndex);
        }
        if (mProductColumnPriceIndex > -1) {
            alMainProductColumnsIndices.add(mProductColumnPriceIndex);
        }
        if (mProductColumnTaxIndex > -1) {
            alMainProductColumnsIndices.add(mProductColumnTaxIndex);
        }
        if (mProductColumnPackSizeIndex > -1) {
            alMainProductColumnsIndices.add(mProductColumnPackSizeIndex);
        }
        if (mProductColumnActiveIndex > -1) {
            alMainProductColumnsIndices.add(mProductColumnActiveIndex);
        }

        mOtherProductColumnCount = mAllProductColumnCount - alMainProductColumnsIndices.size();
        mOtherProductColumnsTitles = new String[mOtherProductColumnCount];
        mOtherProductColumns = new String[mOtherProductColumnCount];
        mOtherProductColumnsIndices = new int[mOtherProductColumnCount];
        Map<String, Integer> otherProductColumnsMap = new HashMap<>();
        int otherProductColumnIndex = 0;
        for (int i = 0; i < mAllProductColumnCount; i++) {
            if (!alMainProductColumnsIndices.contains(i)) {
                otherProductColumnsMap.put(mAllProductColumns[i], i);
                mOtherProductColumns[otherProductColumnIndex] = mAllProductColumns[i];
                otherProductColumnIndex++;
            }
        }
        Arrays.sort(mOtherProductColumns);
        StringBuilder otherProductColumnsTitles = new StringBuilder();
        for (int i = 0; i < mOtherProductColumnCount; i++) {
            mOtherProductColumnsIndices[i] = otherProductColumnsMap.get(mOtherProductColumns[i]);
            otherProductColumnsTitles.setLength(0);
            otherProductColumnsTitles.append(sp.getString(
                    DbContract.OTHER_PRODUCT_COLUMNS_TITLE_PREF_PREFIX + mOtherProductColumns[i],
                    ""));
            if ("".equals(otherProductColumnsTitles.toString())) {
                otherProductColumnsTitles.append(mOtherProductColumns[i]);
            }
            mOtherProductColumnsTitles[i] = otherProductColumnsTitles.toString();
        }

    } // End setMainAndOtherProductColumns method.


    // Helper method for matching a column to its index as ordered in the products table in db.
    private int findProductColumnIndex(SharedPreferences sp, ArrayList<String> alColumns, String
            key) {
        String column = sp.getString(key, "");
        return alColumns.indexOf(column);
    }


    // Set other details product columns:

    private int[] mOtherDetailsProductColumnsIndices;


    private void setOtherDetailsProductColumns(@Nullable SharedPreferences sp) {
        if (sp == null) { sp = PreferenceManager.getDefaultSharedPreferences(this); }

        String strOtherProductColumns = sp.getString(DbContract.PRODUCT_COLUMNS_OTHER_KEY, "");
        if ("".equals(strOtherProductColumns)) { return; }
        String[] allProductColumnsArray = strOtherProductColumns.split(", ");
        ArrayList<String> alOtherProductColumns = new ArrayList<>();
        alOtherProductColumns.addAll(Arrays.asList(allProductColumnsArray));

        Set<String> otherDetailsProductColumns = sp.getStringSet(DbContract
                .PRODUCT_COLUMNS_OTHER_DETAILS_KEY, null);
        if (otherDetailsProductColumns == null) {
            otherDetailsProductColumns = new HashSet<>(alOtherProductColumns);
            SharedPreferences.Editor editor = sp.edit();
            editor.putStringSet(DbContract.PRODUCT_COLUMNS_OTHER_DETAILS_KEY,
                    otherDetailsProductColumns);
            editor.apply();
        }
        String[] arOtherDetailsProductColumn = otherDetailsProductColumns.toArray(new
                String[otherDetailsProductColumns.size()]);
        // Make grouping columns titles always appear in same order in the drawer navigation
        // spinner.
        Arrays.sort(arOtherDetailsProductColumn);
        Collections.sort(alOtherProductColumns);
        int otherDetailsProductColumnCount = arOtherDetailsProductColumn.length;
        mOtherDetailsProductColumnsIndices = new int[otherDetailsProductColumnCount];
        for (int i = 0; i < otherDetailsProductColumnCount; i++) {
            mOtherDetailsProductColumnsIndices[i] = alOtherProductColumns.indexOf
                    (arOtherDetailsProductColumn[i]);
        }
    }


    // Set grouping product columns:

    private ArrayList<String>[] mGroupingSelectionEntries;
    private int[] mGroupingProductColumnsIndices;
    private int mGroupingProductColumnsCount;


    @SuppressWarnings("unchecked")
    private void setGroupingProductColumns(@Nullable SharedPreferences sp) {
        if (sp == null) { sp = PreferenceManager.getDefaultSharedPreferences(this); }

        String strOtherProductColumns = sp.getString(DbContract.PRODUCT_COLUMNS_OTHER_KEY, "");
        if ("".equals(strOtherProductColumns)) { return; }
        String[] arOtherProductColumns = strOtherProductColumns.split(", ");
        ArrayList<String> alOtherProductColumns = new ArrayList<>();
        alOtherProductColumns.addAll(Arrays.asList(arOtherProductColumns));

        Set<String> groupingProductColumns = sp.getStringSet(DbContract
                .PRODUCT_COLUMNS_GROUPING_KEY, null);
        if (groupingProductColumns == null) {
            groupingProductColumns = new HashSet<>(alOtherProductColumns);
            SharedPreferences.Editor editor = sp.edit();
            editor.putStringSet(DbContract.PRODUCT_COLUMNS_GROUPING_KEY, groupingProductColumns);
            editor.apply();
        }
        String[] groupingProductColumnsArray = groupingProductColumns.toArray(new
                String[groupingProductColumns.size()]);
        // Make grouping columns titles always appear in same order in the drawer navigation
        // spinner.
        Arrays.sort(groupingProductColumnsArray);
        Collections.sort(alOtherProductColumns);
        mGroupingProductColumnsCount = groupingProductColumnsArray.length;
        mGroupingProductColumnsIndices = new int[mGroupingProductColumnsCount];
        for (int i = 0; i < mGroupingProductColumnsCount; i++) {
            mGroupingProductColumnsIndices[i] = alOtherProductColumns.indexOf
                    (groupingProductColumnsArray[i]);
        }
        mGroupingSelectionEntries = new ArrayList[mGroupingProductColumnsCount];
        for (int i = 0; i < mGroupingProductColumnsCount; i++) {
            mGroupingSelectionEntries[i] =
                    makeNavigationSelectionList(groupingProductColumnsArray[i]);
        }
    }


    // Helper method for populating mGroupingSelectionEntries.
    private ArrayList<String> makeNavigationSelectionList(String selectionProductColumn) {
        String[] projection, selectionArgs = null;
        String selection = null;
        if (mProductColumnActiveIndex == -1) {
            projection = new String[]{"DISTINCT [" + selectionProductColumn + "]"};
        } else {
            projection = new String[]{"DISTINCT [" + selectionProductColumn + "]",
                    "[" + getProductColumnActive() + "]"};
            selection = getProductColumnActive() + " = ?";
            selectionArgs = new String[]{"Y"};
        }
        Cursor cursor = getContentResolver().query(DbContract.getProductsTableUri(), projection
                , selection, selectionArgs, null);
        final ArrayList<String> entries = new ArrayList<>();
        entries.add(getString(R.string.all));
        if (cursor != null) {
            while (cursor.moveToNext()) {
                entries.add(cursor.getString(0));
            }
            cursor.close();
        }
        return entries;
    }


    // For populating navigation drawer spinner.
    String[] getNavigationSelection() {
        int count = mGroupingProductColumnsCount + 2;
        if (isTakingOrder) { count++; }
        String[] navSelection = new String[count];
        navSelection[0] = getString(R.string.all) + " / " + getString(R.string.active) + " / " +
                getString(R.string.inactive);
        System.arraycopy(getGroupingProductColumnsTitles(), 0, navSelection, 1,
                mGroupingProductColumnsCount);
        if (isTakingOrder) {
            navSelection[count - 2] = getString(R.string.custom);
            navSelection[count - 1] = getString(R.string.title_order);
        } else {
            navSelection[count - 1] = getString(R.string.custom);
        }
        return navSelection;
    }


    // For populating navigation drawer list view depending on the spinner selection.
    public ArrayList<String>[] getGroupingSelectionEntries() { return mGroupingSelectionEntries; }


    // Set product column active boolean format.

    private String mProductActiveColumnBoolFormat, mProductActiveColumnPositive,
            mProductActiveColumnNegative;


    public void setActiveProductColumnBoolFormat(String format) {
        if (format == null || "".equals(format)) { return; }
        mProductActiveColumnBoolFormat = format;
        String[] formats = format.split(" - ");
        mProductActiveColumnPositive = formats[0];
        mProductActiveColumnNegative = formats[1];
    }

    // Product column active boolean format related getters.


    public String getProductActiveColumnBoolFormat() { return mProductActiveColumnBoolFormat; }


    public String getProductActiveColumnPositive() { return mProductActiveColumnPositive; }


    public String getProductActiveColumnNegative() { return mProductActiveColumnNegative; }


    // Main product columns related getters.

    public int getProductColumnCount() { return mAllProductColumnCount; }


    public String getProductColumnIdentifier() {
        if (mProductColumnIdentifierIndex > -1) {
            return mAllProductColumns[mProductColumnIdentifierIndex];
        } else {
            return mAllProductColumns[mProductColumnTitleIndex];
        }
    }


    public String getProductColumnActive() {
        if (mProductColumnActiveIndex > -1) {
            return mAllProductColumns[mProductColumnActiveIndex];
        } else {
            return null;
        }
    }


    public int getProductColumnIdentifierIndex() {
        if (mProductColumnIdentifierIndex > -1) {
            return mProductColumnIdentifierIndex;
        } else {
            return mProductColumnTitleIndex;
        }
    }


    public int getProductColumnImageIndex() { return mProductColumnImageIndex; }


    public int getProductColumnTitleIndex() { return mProductColumnTitleIndex; }


    public int getProductColumnPriceIndex() { return mProductColumnPriceIndex; }


    public int getProductColumnTaxIndex() { return mProductColumnTaxIndex; }


    public int getProductColumnPackSizeIndex() { return mProductColumnPackSizeIndex; }


    public int getProductColumnActiveIndex() { return mProductColumnActiveIndex; }


    // Other product columns related getters.

    public int getOtherProductColumnCount() { return mOtherProductColumnCount; }


    public int[] getOtherProductColumnsIndices() { return mOtherProductColumnsIndices; }


    // Getters for product other details and grouping columns related data.

    public int[] getOtherDetailsProductColumnsIndices() {
        return mOtherDetailsProductColumnsIndices;
    }


    public String[] getOtherDetailsProductColumnsTitles() {
        return getOtherProductColumnsOrTitles(mOtherDetailsProductColumnsIndices,
                mOtherProductColumnsTitles);
    }


    public int getGroupingProductColumnCount() { return mGroupingProductColumnsCount; }


    public String[] getGroupingProductColumns() {
        return getOtherProductColumnsOrTitles(mGroupingProductColumnsIndices, mOtherProductColumns);
    }


    private String[] getGroupingProductColumnsTitles() {
        return getOtherProductColumnsOrTitles(mGroupingProductColumnsIndices,
                mOtherProductColumnsTitles);
    }


    // Helper method for getting other product columns descriptive titles set in preferences.
    private String[] getOtherProductColumnsOrTitles(int[] indices, String[] baseArray) {
        String[] result = new String[indices.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = baseArray[indices[i]];
        }
        return result;
    }



    // Set customer table related data:

    private String[] mAllCustomerColumns;
    private int mCustomerColumnIdentifierIndex;
    private int mCustomerColumnLatIndex, mCustomerColumnLngIndex, mCustomerColumnAddressIndex;
    private int[] mCustomerAddressColumnsIndices, mCustomerPhoneColumnsIndices,
            mCustomerOtherDetailsColumnsIndices;


    void setCustomerColumns(SharedPreferences sp) {

        if (sp == null) { sp = PreferenceManager.getDefaultSharedPreferences(this); }
        // Get all customer columns from shared preferences
        String customerColumns = sp.getString(DbContract.PREF_CUSTOMER_COLUMNS_KEY, "");
        if ("".equals(customerColumns)) { return; }
        mAllCustomerColumns = customerColumns.split(", ");
        int allCustomerColumnCount = mAllCustomerColumns.length;
        final ArrayList<String> alAllCustomerColumns = new ArrayList<>(allCustomerColumnCount);

        alAllCustomerColumns.addAll(Arrays.asList(mAllCustomerColumns));
        // Set customer identifier index.
        mCustomerColumnIdentifierIndex = findProductColumnIndex(sp, alAllCustomerColumns, DbContract
                .COLUMN_CUSTOMER_IDENTIFIER_KEY);

        // Set customer address columns indices.
        Set<String> customerAddressColumnsSet =
                sp.getStringSet(DbContract.PREF_CUSTOMER_ADDRESS_COLUMNS_KEY, null);
        if (customerAddressColumnsSet != null) {
            int customerAddressColumnsCount = customerAddressColumnsSet.size();
            mCustomerAddressColumnsIndices = new int[customerAddressColumnsCount];
            String[] customerAddressColumns =
                    customerAddressColumnsSet.toArray(new String[customerAddressColumnsCount]);
            for (int i = 0; i < customerAddressColumnsCount; i++) {
                mCustomerAddressColumnsIndices[i] =
                        alAllCustomerColumns.indexOf(customerAddressColumns[i]);
            }
        }

        // Set customer phone columns indices.
        Set<String> customerPhoneColumnsSet =
                sp.getStringSet(DbContract.PREF_CUSTOMER_PHONES_COLUMNS_KEY, null);
        if (customerPhoneColumnsSet != null) {
            int customerPhoneColumnsCount = customerPhoneColumnsSet.size();
            mCustomerPhoneColumnsIndices = new int[customerPhoneColumnsCount];
            String[] customerPhoneColumns =
                    customerPhoneColumnsSet.toArray(new String[customerPhoneColumnsCount]);
            for (int i = 0; i < customerPhoneColumnsCount; i++) {
                mCustomerPhoneColumnsIndices[i] =
                        alAllCustomerColumns.indexOf(customerPhoneColumns[i]);
            }
        }

        // Set customer other details columns indices.
        Set<String> customerOtherDetailsColumnsSet =
                sp.getStringSet(DbContract.PREF_CUSTOMER_OTHER_DETAILS_COLUMNS_KEY, null);
        if (customerOtherDetailsColumnsSet != null) {
            int customerOtherDetailsCount = customerOtherDetailsColumnsSet.size();
            mCustomerOtherDetailsColumnsIndices = new int[customerOtherDetailsCount];
            String[] customerOtherDetailsColumns =
                    customerOtherDetailsColumnsSet.toArray(new String[customerOtherDetailsCount]);
            for (int i = 0; i < customerOtherDetailsCount; i++) {
                mCustomerOtherDetailsColumnsIndices[i] =
                        alAllCustomerColumns.indexOf(customerOtherDetailsColumns[i]);
            }
        }

        mCustomerColumnLatIndex = allCustomerColumnCount;
        mCustomerColumnLngIndex = allCustomerColumnCount + 1;
        mCustomerColumnAddressIndex = allCustomerColumnCount + 2;
    }


    public String getCustomerColumnIdentifier() {
        return mAllCustomerColumns[mCustomerColumnIdentifierIndex];
    }


    public int getCustomerColumnIdentifierIndex() { return mCustomerColumnIdentifierIndex; }


    public int getCustomerColumnLatIndex() { return mCustomerColumnLatIndex; }


    public int getCustomerColumnLngIndex() { return mCustomerColumnLngIndex; }


    public int getCustomerColumnAddressIndex() { return mCustomerColumnAddressIndex; }


    public int[] getCustomerAddressColumnsIndices() { return mCustomerAddressColumnsIndices; }


    public int[] getCustomerPhoneColumnsIndices() { return mCustomerPhoneColumnsIndices; }


    public int[] getCustomerOtherDetailsColumnsIndices() {
        return mCustomerOtherDetailsColumnsIndices;
    }


    private String[] mCustomers;
    private int mCustomerCount;


    private void setCustomers(SharedPreferences sp) {
        String columnCustomerName = "[" + sp.getString(DbContract.COLUMN_CUSTOMER_NAME_KEY, "") +
                "]";
        Cursor cursor = getContentResolver().query(DbContract.getCustomersTableUri(), new String[]
                {columnCustomerName}, null, null, columnCustomerName + " ASC");
        if (cursor != null) {
            mCustomerCount = cursor.getCount();
            mCustomers = new String[mCustomerCount];
            while (cursor.moveToNext()) {
                mCustomers[cursor.getPosition()] = cursor.getString(0);
            }
            cursor.close();
        }
    }


    // Get customer table related data:

    public int getCustomerCount() { return mCustomerCount; }


    public String[] getCustomers() { return mCustomers; }


    // Method amalgamating all setting methods for product and customer tables related data.
    void setProductColumnsAndCustomers(@Nullable SharedPreferences sp) {
        if (sp == null) { sp = PreferenceManager.getDefaultSharedPreferences(this); }
        setProductColumns(sp);
        setCustomerColumnsAndCustomers(sp);
    }


    // Method amalgamating all setting methods for product tables related data.
    void setProductColumns(@Nullable SharedPreferences sp) {
        if (sp == null) { sp = PreferenceManager.getDefaultSharedPreferences(this); }
        setMainAndOtherProductColumns(sp);
        setOtherDetailsProductColumns(sp);
        setActiveProductColumnBoolFormat(sp.getString(DbContract.COLUMN_PRODUCT_ACTIVE_BOOL_KEY,
                ""));
        setGroupingProductColumns(sp);
    }


    // Method amalgamating all setting methods for customer tables related data.
    // here, MA
    void setCustomerColumnsAndCustomers(@Nullable SharedPreferences sp) {
        if (sp == null) { sp = PreferenceManager.getDefaultSharedPreferences(this); }
        setCustomerColumns(sp);
        setCustomers(sp);
    }


} // End CatalogSales class
