/*
 * Copyright (C) 2017 Vladimir Markovic. All rights reserved.
 */

package com.humaneapps.catalogsales.data;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.net.Uri;
import android.provider.BaseColumns;

/**
 * Table constants, names, uris and methods.
 */
public class DbContract {


    private static final String CONTENT_SCHEME = "content";
    public static final String CONTENT_AUTHORITY = "com.humaneapps.catalogsales";
    public static final String DB_NAME = "catalog_sales.db";

    // db related preference keys

    public static final String DB_VERSION_KEY = "DB_VERSION_KEY";

    // Keys for product columns related preference:

    // All product columns key.
    public static final String PREF_PRODUCT_COLUMNS_KEY = "PREF_PRODUCT_COLUMNS_KEY";
    // Main product columns keys.
    public static final String MAIN_PRODUCT_COLUMNS_PREF_PREFIX = "MAIN_COLUMN_";
    public static final String COLUMN_PRODUCT_TITLE_KEY = MAIN_PRODUCT_COLUMNS_PREF_PREFIX +
            "TITLE_KEY";
    public static final String COLUMN_PRODUCT_IMAGE_KEY = MAIN_PRODUCT_COLUMNS_PREF_PREFIX +
            "IMAGE_KEY";
    public static final String COLUMN_PRODUCT_PRICE_KEY = MAIN_PRODUCT_COLUMNS_PREF_PREFIX +
            "PRICE_KEY";
    public static final String COLUMN_PRODUCT_TAX_KEY = MAIN_PRODUCT_COLUMNS_PREF_PREFIX +
            "TAX_KEY";
    public static final String COLUMN_PRODUCT_PACK_SIZE_KEY = MAIN_PRODUCT_COLUMNS_PREF_PREFIX
            + "PACK_SIZE_KEY";
    public static final String COLUMN_PRODUCT_ACTIVE_KEY = MAIN_PRODUCT_COLUMNS_PREF_PREFIX +
            "ACTIVE_KEY";
    public static final String COLUMN_PRODUCT_ACTIVE_BOOL_KEY =
            "COLUMN_PRODUCT_ACTIVE_BOOL_KEY";
    public static final String COLUMN_PRODUCT_IDENTIFIER_KEY = "COLUMN_PRODUCT_IDENTIFIER_KEY";
    // Array of main product columns keys.
    public static final String[] PRODUCT_COLUMNS_MAIN_KEYS = new String[]{
            TableProducts._ID, COLUMN_PRODUCT_TITLE_KEY,
            COLUMN_PRODUCT_IMAGE_KEY, COLUMN_PRODUCT_PRICE_KEY, COLUMN_PRODUCT_TAX_KEY,
            COLUMN_PRODUCT_PACK_SIZE_KEY, COLUMN_PRODUCT_ACTIVE_KEY};
    public static final int MAIN_PRODUCT_COLUMNS_COUNT = 7;
    // Other product columns keys.
    public static final String PRODUCT_COLUMNS_OTHER_KEY = "PRODUCT_COLUMNS_OTHER_KEY";
    public static final String PRODUCT_COLUMNS_OTHER_DETAILS_KEY =
            "PRODUCT_COLUMNS_OTHER_DETAILS_KEY";
    public static final String PRODUCT_COLUMNS_GROUPING_KEY = "PRODUCT_COLUMNS_GROUPING_KEY";
    public static final String OTHER_PRODUCT_COLUMNS_TITLE_PREF_PREFIX = "OTHER_COLUMN_";

    // Keys for customer columns related preference.
    public static final String PREF_CUSTOMER_COLUMNS_KEY = "PREF_CUSTOMER_COLUMNS_KEY";
    public static final String COLUMN_CUSTOMER_NAME_KEY = "COLUMN_CUSTOMER_NAME_KEY";
    public static final String COLUMN_CUSTOMER_IDENTIFIER_KEY = "COLUMN_CUSTOMER_IDENTIFIER_KEY";
    public static final String PREF_CUSTOMER_ADDRESS_COLUMNS_KEY =
            "PREF_CUSTOMER_ADDRESS_COLUMNS_KEY";
    public static final String PREF_CUSTOMER_PHONES_COLUMNS_KEY =
            "PREF_CUSTOMER_PHONES_COLUMNS_KEY";
    public static final String PREF_CUSTOMER_OTHER_DETAILS_COLUMNS_KEY =
            "PREF_CUSTOMER_OTHER_DETAILS_COLUMNS_KEY";

    // SQLite sequence table info for deleting sequence for products and customers data, so
    // _id auto numbering can reset.
    private static final String SQLITE_SEQUENCE_TABLE_NAME = "sqlite_sequence";
    public static final Uri SQLITE_SEQUENCE_URI = new Uri.Builder()
            .scheme(CONTENT_SCHEME)
            .authority(CONTENT_AUTHORITY)
            .appendPath(SQLITE_SEQUENCE_TABLE_NAME)
            .build();
    public static final String SQLITE_SEQUENCE_TABLE_COLUMN = "name";


    // Tables:

    public static final class TableProducts implements BaseColumns {
        public static final String TABLE_NAME = "products";
    }


    public static final class TableCustomers implements BaseColumns {
        public static final String TABLE_NAME = "customers";
    }


    // Content provider used methods:

    static String getTypeItem(String tableName) {
        return ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + tableName;
    }


    static String getTypeDir(String tableName) {
        return ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + tableName;
    }


    static Uri buildUriWithId(long id, String tableName) {
        return ContentUris.withAppendedId(getTableUri(tableName), id);
    }


    // For getting uri for querying:

    private static Uri getTableUri(String tableName) {
        return new Uri.Builder()
                .scheme(CONTENT_SCHEME)
                .authority(CONTENT_AUTHORITY)
                .appendPath(tableName)
                .build();
    }


    public static Uri getProductsTableUri() {
        return getTableUri(TableProducts.TABLE_NAME);
    }


    public static Uri getCustomersTableUri() {
        return getTableUri(TableCustomers.TABLE_NAME);
    }


} // End DbContract class
