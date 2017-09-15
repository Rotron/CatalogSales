/*
 * Copyright (C) 2017 Vladimir Markovic. All rights reserved.
 */

package com.humaneapps.catalogsales.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.v7.preference.PreferenceManager;

import com.humaneapps.catalogsales.CatalogSales;
import com.humaneapps.catalogsales.R;
import com.humaneapps.catalogsales.Util;

/**
 * Creates and updates database, products and customer tables.
 */
public class DbHelper extends SQLiteOpenHelper {


    // Application level class containing common data needed across the application.
    private final CatalogSales mApplication;


    public DbHelper(Context context) {
        super(context, DbContract.DB_NAME, null, PreferenceManager
                .getDefaultSharedPreferences(context).getInt(DbContract.DB_VERSION_KEY, 1));
        mApplication = (CatalogSales) context.getApplicationContext();
    }


    @Override
    public void onCreate(SQLiteDatabase sqLiteDb) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mApplication);

        // Get relevant column indices.
        int columnProductPriceIndex = mApplication.getProductColumnPriceIndex();
        int columnProductPackSizeIndex = mApplication.getProductColumnPackSizeIndex();
        int columnProductTaxIndex = mApplication.getProductColumnTaxIndex();
        int productIdentifierIndex = mApplication.getProductColumnIdentifierIndex();

        // Get product columns.
        String strProductColumns = sp.getString(DbContract.PREF_PRODUCT_COLUMNS_KEY, "");
        if (strProductColumns.length() == 0) { return; }
        String[] arProductColumns  = strProductColumns.split(",");
        int productColumnCount = arProductColumns.length;

        // Populate types and defaults for product columns
        String[] productTypes = new String[productColumnCount];
        String[] productDefaults = new String[productColumnCount];
        for (int i = 0; i < productColumnCount; i++) {
            if (i == columnProductPriceIndex) {
                productTypes[i] = "REAL";
                productDefaults[i] = "0";
            } else if (i == columnProductPackSizeIndex || i == columnProductTaxIndex) {
                productTypes[i] = "INTEGER";
                productDefaults[i] = "0";
            } else {
                productTypes[i] = "TEXT";
                productDefaults[i] = "''";
            }
        }

        // Create table 'products'.

        StringBuilder sqlCreateTableProducts = new StringBuilder();
        sqlCreateTableProducts.append("CREATE TABLE ");
        sqlCreateTableProducts.append(DbContract.TableProducts.TABLE_NAME);
        sqlCreateTableProducts.append(" (");
        sqlCreateTableProducts.append(DbContract.TableProducts._ID);
        sqlCreateTableProducts.append(" INTEGER PRIMARY KEY AUTOINCREMENT");
        for (int i = 1; i < productColumnCount; i++) {
            sqlCreateTableProducts.append(", ");
            sqlCreateTableProducts.append(arProductColumns[i]);
            sqlCreateTableProducts.append(" ");
            sqlCreateTableProducts.append(productTypes[i]);
            if (productIdentifierIndex == i) {
                sqlCreateTableProducts.append(" NOT NULL UNIQUE ON CONFLICT REPLACE");
            } else {
                if (!"".equals(productDefaults[i])) {
                    sqlCreateTableProducts.append(" DEFAULT ");
                    sqlCreateTableProducts.append(productDefaults[i]);
                }
            }
        }
        sqlCreateTableProducts.append(", ");
        sqlCreateTableProducts.append(mApplication.getString(R.string.column_name_favourite));
        sqlCreateTableProducts.append(" TEXT DEFAULT '");
        sqlCreateTableProducts.append(Util.FAVOURITE_NEGATIVE);
        sqlCreateTableProducts.append("');");

        sqLiteDb.execSQL(sqlCreateTableProducts.toString());

        // Get customer columns.
        int customerIdentifierIndex = mApplication.getCustomerColumnIdentifierIndex();
        String strCustomerColumns = sp.getString(DbContract.PREF_CUSTOMER_COLUMNS_KEY, "");
        if (strCustomerColumns.length() == 0) { return; }
        String[] arCustomerColumns  = strCustomerColumns.split(",");
        int customerColumnCount = arCustomerColumns.length;

        // Create table 'customers'.

        StringBuilder sqlCreateTableCustomers = new StringBuilder();
        sqlCreateTableCustomers.append("CREATE TABLE ");
        sqlCreateTableCustomers.append(DbContract.TableCustomers.TABLE_NAME);
        sqlCreateTableCustomers.append(" (");
        sqlCreateTableCustomers.append(DbContract.TableCustomers._ID);
        sqlCreateTableCustomers.append(" INTEGER PRIMARY KEY AUTOINCREMENT");
        for (int i = 1; i < customerColumnCount; i++) {
            sqlCreateTableCustomers.append(", [");
            sqlCreateTableCustomers.append(arCustomerColumns[i].trim());
            sqlCreateTableCustomers.append("] TEXT");
            if (customerIdentifierIndex == i) {
                sqlCreateTableCustomers.append(" NOT NULL UNIQUE ON CONFLICT REPLACE");
            } else {
                sqlCreateTableCustomers.append(" DEFAULT ''");
            }
        }
        String columnLatitude = mApplication.getString(R.string.column_name_latitude);
        String columnLongitude = mApplication.getString(R.string.column_name_longitude);
        sqlCreateTableCustomers.append(", ");
        sqlCreateTableCustomers.append(columnLatitude);
        sqlCreateTableCustomers.append(" REAL DEFAULT 0");
        sqlCreateTableCustomers.append(", ");
        sqlCreateTableCustomers.append(columnLongitude);
        sqlCreateTableCustomers.append(" REAL DEFAULT 0");
        sqlCreateTableCustomers.append(", ");
        sqlCreateTableCustomers.append(mApplication.getString(R.string.column_name_address));
        sqlCreateTableCustomers.append(" TEXT DEFAULT ''");
        sqlCreateTableCustomers.append(");");

        sqLiteDb.execSQL(sqlCreateTableCustomers.toString());
    }


    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDb, int oldVersion, int newVersion) {
        // If any of the columns changed, drop and recreate tables as all of the data is repopulated
        // on each update and favourites are copied.
        sqLiteDb.execSQL("DROP TABLE IF EXISTS " + DbContract.TableProducts.TABLE_NAME);
        sqLiteDb.execSQL("DROP TABLE IF EXISTS " + DbContract.TableCustomers.TABLE_NAME);
        onCreate(sqLiteDb);
    }


} // End DbHelper class.