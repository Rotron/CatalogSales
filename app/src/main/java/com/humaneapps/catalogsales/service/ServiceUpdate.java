/*
 * Copyright (C) 2017 Vladimir Markovic. All rights reserved.
 */

package com.humaneapps.catalogsales.service;

import android.app.IntentService;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.dropbox.core.DbxDownloader;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.humaneapps.catalogsales.BuildConfig;
import com.humaneapps.catalogsales.CatalogSales;
import com.humaneapps.catalogsales.R;
import com.humaneapps.catalogsales.Util;
import com.humaneapps.catalogsales.data.DbContract;
import com.humaneapps.catalogsales.data.DbHelper;
import com.humaneapps.catalogsales.helper.AdvancedCrypto;
import com.humaneapps.catalogsales.widget.CustomersWidgetUpdateService;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.ArrayUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Fetches customers and products data using from specified location (url or local file),
 * extracts data (can be from JSON or csv format) and writes it to db customers and products tables.
 * Also only retrieves customers and/or products columns on demand.
 */
public class ServiceUpdate extends IntentService {


    // Flag for indicating if columns changed to drop and recreate tables.
    private boolean mIncrementDbVersion;
    // For not allowing another start until running one finishes.
    private boolean mFirstRun = true;
    // For preparing ContentValues for inserting data into the db.
    private final LinkedList<ContentValues> mLlInsertValues = new LinkedList<>();
    private ArrayList<ContentProviderOperation> mLlUpdateOperations = null;
    // Action flags. First three are passed in. mRedoProducts is true when customer columns
    // change (while products columns have not changed and both need to be done).
    private boolean mGetColumns, mDoProducts, mDoCustomers, mRedoProducts = false;
    // E.g. 'Y - N', 'Yes - No', '1 - 0' or similar.
    private String mProductColumnActiveBoolFormat;
    // Holds error message if error occurred.
    private String mErrorMessage;


    public ServiceUpdate() { super("ServiceUpdate"); }


    @Override
    protected void onHandleIntent(Intent intent) {

        // only allow service to run the code once
        if (mFirstRun) {
            // disable consecutive runs.
            mFirstRun = false;
            // If true, just extract columns and return.
            mGetColumns = intent.getBooleanExtra(Util.ARG_GET_COLUMNS, false);
            // If true, do products (extract columns or write data to db depending on above flag).
            mDoProducts = intent.getBooleanExtra(Util.ARG_DO_PRODUCTS, false);
            // If true, do customers (extract columns or write data to db depending on above flag).
            mDoCustomers = intent.getBooleanExtra(Util.ARG_DO_CUSTOMERS, false);

            // Intent for broadcasting the end result.
            Intent broadcastIntent = new Intent(Util.ARG_UPDATE_SERVICE_BROADCAST);
            // Intent for holding intermediate result - added to end intent.
            Intent tempIntent;
            // Do products if so flagged.
            if (mDoProducts) {
                tempIntent = updateData(DbContract.PREF_PRODUCT_COLUMNS_KEY);
                broadcastIntent.putExtras(tempIntent);
            }
            // Do customers if so flagged.
            if (mDoCustomers) {
                tempIntent = updateData(DbContract.PREF_CUSTOMER_COLUMNS_KEY);
                broadcastIntent.putExtras(tempIntent);
            }
            // Redo products if so flagged - when some of the customer columns changed, which caused
            // both tables to be dropped and recreated.
            if (mRedoProducts) {
                tempIntent = updateData(DbContract.PREF_PRODUCT_COLUMNS_KEY);
                broadcastIntent.putExtras(tempIntent);
            }
            // Broadcast the result - with prepared success or error message.
            broadcast(broadcastIntent);

        } // End if first run.

    } // End onHandleIntent.


    /**
     * Extract (customers or products) columns and/or write data to db if no errors occurred.
     *
     * @param prefKey - key for customers or product columns preference. Used to store
     *                corresponding columns when extracted and determine if customers or products
     *                need to be done.
     * @return intent containing the result message.
     */
    private Intent updateData(String prefKey) {

        final String PRODUCTS_LOCAL_COPY_FILE_NAME = "products.csv";
        final String CUSTOMERS_LOCAL_COPY_FILE_NAME = "customers.csv";
        // For storing table uri - customers or products.
        Uri uri;
        // For storing key for data location preference (for customers or products)
        String dataLocationKey, dataLocation, localFileName;
        // Intent for broadcasting the result, returned by 'extractData...' methods.
        Intent returnIntent = new Intent();
        // Intent for holding intermediate result - added to the return intent.
        Intent tempIntent;
        // 'Favourites' (last) extra column in Products table or 'Latitude', 'Longitude' and
        // 'Address' (last) three) extra column in Customers table.
        int extraColumns;

        // Set key of the location pref.
        if (prefKey.equals(DbContract.PREF_PRODUCT_COLUMNS_KEY)) {
            uri = DbContract.getProductsTableUri();
            dataLocationKey = getString(R.string.pref_products_data_location);
            localFileName = PRODUCTS_LOCAL_COPY_FILE_NAME;
            extraColumns = 1;
        } else {
            uri = DbContract.getCustomersTableUri();
            dataLocationKey = getString(R.string.pref_customers_data_location);
            localFileName = CUSTOMERS_LOCAL_COPY_FILE_NAME;
            extraColumns = 3;
        }
        // Get old columns to match to new; to determine if changed, to update db (version).
        String[] arOldColumns = null;
        File dbFile = getDatabasePath(DbContract.DB_NAME);
        if (dbFile.exists()) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                arOldColumns = Arrays.copyOf(cursor.getColumnNames(), cursor.getColumnCount() -
                        extraColumns);
                cursor.close();
            }
        }
        // Get data location specified in the preference.
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        String dataLocationInput = sp.getString(dataLocationKey, "").trim();
        // If data location is not specified in the preference, return with error message.
        if ("".equals(dataLocationInput)) {
            mErrorMessage = getString(R.string.warning_data_location_not_set);
            return returnIntent;
        }
        // Set flag for determining whether loading will be from local file or from online url.
        boolean fromLocalFile = (new File(dataLocationInput)).exists();

        if (fromLocalFile) {
            dataLocation = dataLocationInput;
        } else {
            // If fetching from online url, check for connection. Return with error if offline.
            if (!Util.isOnline(this)) {
                mErrorMessage = getString(R.string.warning_cannot_update_offline);
                return returnIntent;
            }
            // If dropbox is used, location needs to start with 'dropbox'.
            // E.g. 'dropbox/dir/file.csv'
            if (dataLocationInput.indexOf("dropbox") == 0) {
                fromLocalFile = true;
                String error = getFileFromDropBox(dataLocationInput, localFileName, sp);
                if (error == null) {
                    dataLocation = getFilesDir() + "/" + localFileName;
                } else {
                    mErrorMessage = error;
                    return returnIntent;
                }
            } else {
                // Add http:// if omitted.
                dataLocation = Util.addProtocol(dataLocationInput);
                if (dataLocation == null) {
                    mErrorMessage = getString(R.string.error_load_data_location) + ": " + dataLocationInput;
                    return returnIntent;
                }
            }
        }

        String strInput = Util.fetchInput(dataLocation, fromLocalFile);
        if (strInput == null) {
            mErrorMessage = getString(R.string.error_load_data_location) + ": " + dataLocationInput;
            return returnIntent;
        }
        boolean formatIsJson = true;
        JSONArray tableJsonArray = null;
        try {
            // Get the JSON array representing all loaded data from provided source table.
            tableJsonArray = new JSONArray(strInput);// If fetch data in json format
        } catch (JSONException e) {
            formatIsJson = false;
        }

        // Extract data for appropriate format from specified location, returning intent containing
        // result to broadcast.
        if (formatIsJson) {
            tempIntent = extractDataFromJson(sp, tableJsonArray, arOldColumns, prefKey);
        } else {
            tempIntent = extractDataFromCsv(sp, dataLocation, fromLocalFile, arOldColumns, prefKey);
        }

        returnIntent.putExtras(tempIntent);
        return returnIntent;
    }


    // If using dropbox to store csv customers and products files, when specifying location,
    // it needs to start with "dropbox". E.g. "dropbox/dir/file.csv". App needs to be registered
    // on dropbox and token needs to be generated and specified in settings.
    // Returns null if all ok, or error message if error occurred.
    private String getFileFromDropBox(String location, String localFileName, SharedPreferences sp) {
        location = location.substring("dropbox".length());
        // Create DropBox client for the app.
        @SuppressWarnings("deprecation")
        DbxRequestConfig config = new DbxRequestConfig(getString(R.string.app_name), "en_US");
        // Decrypt the token (encrypted when input in shared preferences)
        String token;
        String encryptedToken = sp.getString(getString(R.string.pref_dropbox_token), "");
        if ("".equals(encryptedToken)) { return null; } // error
        try {
            token = AdvancedCrypto.decrypt(AdvancedCrypto.getSecretKey(
                    BuildConfig.DROPBOX_SALT_PASS, BuildConfig.DROPBOX_SALT), encryptedToken);
        } catch (Exception e) {
            return getString(R.string.warning_wrong_dropbox_token);
        }
        if (token == null) { return getString(R.string.warning_wrong_dropbox_token); }
        // Use the token to connect to dropbox client
        DbxClientV2 client = new DbxClientV2(config, token);
        try {
            // Dropbox file will be saved locally. Create the holding file.
            File file = new File(getFilesDir() + "/" + localFileName);
            if (!file.exists() && !file.createNewFile()) {
                return getString(R.string.warning_could_not_save_dropbox_file);
            }
            // Get the specified file from the drop box and save it locally.
            FileOutputStream outputStream = new FileOutputStream(file);
            DbxDownloader<FileMetadata> downloader = client.files().download(location);
            downloader.download(outputStream);
            client.files().downloadBuilder("");
        } catch (IOException | DbxException e) { return e.toString(); }

        return null;
    }


    /**
     * Extract (customers or products) data from JSON and write it to db if no errors occurred.
     *
     * @param prefKey        - key for customers or product columns preference. Used to store
     *                       corresponding columns when extracted and determine if customers or
     *                       products
     *                       need to be done.
     * @param sp             - shared preferences
     * @param tableJsonArray - JSON array representing the table of all data.
     * @param oldColumns     - current columns as per db table - to compare to new.
     * @return intent containing the result message.
     */
    private Intent extractDataFromJson(SharedPreferences sp, JSONArray tableJsonArray
            , String[] oldColumns, String prefKey) {

        // For storing error if occurs in insert values methods.
        String error;
        // Intent for broadcasting the result, returned by 'extractData...' methods.
        Intent returnIntent = new Intent();
        // Intent for holding intermediate result - added to the return intent.
        Intent tempIntent;

        try {
            // Get the JSON object representing header row containing column names.
            JSONArray columnsJsonArray = tableJsonArray.getJSONObject(0).names();
            // Get columns string array (with _id as first column)
            String[] arColumnsWithId = new String[columnsJsonArray.length() + 1];
            arColumnsWithId[0] = DbContract.TableProducts._ID;
            for (int i = 1; i < arColumnsWithId.length; i++) {
                arColumnsWithId[i] = (String) columnsJsonArray.get(i - 1);
            }

            // Extract (customer or product) columns and write them into shared preference,
            // returning intent containing result to broadcast.
            tempIntent = putColumnsString(sp, arColumnsWithId, prefKey);
            returnIntent.putExtras(tempIntent);

            if (!mGetColumns) {
                // Check if columns changed to flag to increment db version which will drop and
                // recreate the tables.
                if (oldColumns != null) { checkColumns(oldColumns, arColumnsWithId); }

                // Populate content values array list filed and detect errors in data.
                mLlInsertValues.clear();
                if (prefKey.equals(DbContract.PREF_PRODUCT_COLUMNS_KEY)) {
                    error = insertProductValues(arColumnsWithId, tableJsonArray, null);
                } else {
                    error = insertCustomerValues(arColumnsWithId, tableJsonArray, null);
                }
                // Write populated content values to db is no error occurred.
                writeToDbIfNoError(sp, error, prefKey);
            }

        } catch (JSONException e) {
            putErrorPrefLocation(sp, prefKey);
        }

        return returnIntent;

    } // End method extractDataFromJson.


    /**
     * Extract (customers or products) columns from csv and write data to db if no errors occurred.
     *
     * @param prefKey       - key for customers or product columns preference. Used to store
     *                      corresponding columns when extracted and determine if customers or
     *                      products need to be done.
     * @param sp            - shared preferences
     * @param dataLocation  - location to customers or products data (url or local file)
     * @param fromLocalFile - flag signaling whether data is located online or locally.
     * @param oldColumns    - current columns as per db table - to compare to new.
     * @return intent containing the result message.
     */
    private Intent extractDataFromCsv(SharedPreferences sp, String dataLocation, boolean
            fromLocalFile, String[] oldColumns, String prefKey) {

        // For parsing input csv file.
        CSVParser csvParser = null;
        Reader reader = null;
        // For storing error if occurs in insert values methods.
        String error;
        // Intent for broadcasting the result, returned by 'extractData...' methods.
        Intent returnIntent = new Intent();
        // Intent for holding intermediate result - added to the return intent.
        Intent tempIntent;

        try {
            if (fromLocalFile) {
                // If fetch data from local file use FileReader.
                reader = new FileReader(dataLocation);
            } else {
                // If fetch data from url use InputStreamReader.
                URL url = new URL(dataLocation);
                reader = new InputStreamReader(new BOMInputStream(url.openStream()), "UTF-8");
            }

            // Extract columns from csv header line: map -> array -> string comma separated.
            csvParser = new CSVParser(reader, CSVFormat.EXCEL.withHeader());
            // Get columns map from scv parser header line.
            Map<String, Integer> headerMap = csvParser.getHeaderMap();
            // Get columns string array from map (with _id as first column)
            String[] arColumnsWithId = ArrayUtils.addAll(new String[]{DbContract.TableProducts._ID},
                    headerMap.keySet().toArray(new String[headerMap.size()]));

            // Extract (customer or product) columns and write them into shared preference,
            // returning intent containing result to broadcast.
            tempIntent = putColumnsString(sp, arColumnsWithId, prefKey);
            returnIntent.putExtras(tempIntent);

            if (!mGetColumns) {
                // Increment db version if columns changed, which will drop and recreate the table.
                if (oldColumns != null) { checkColumns(oldColumns, arColumnsWithId); }

                // Populate content values array list filed and detect errors in data.
                mLlInsertValues.clear();
                if (prefKey.equals(DbContract.PREF_PRODUCT_COLUMNS_KEY)) {
                    error = insertProductValues(arColumnsWithId, null, csvParser.getRecords());
                } else {
                    error = insertCustomerValues(arColumnsWithId, null, csvParser.getRecords());
                }
                // Write populated content values to db is no error occurred.
                writeToDbIfNoError(sp, error, prefKey);
            }

        } catch (IOException e) {
            putErrorPrefLocation(sp, prefKey);
        } finally {
            try {
                if (csvParser != null) { csvParser.close(); }
                if (reader != null) { reader.close(); }
            } catch (IOException e) { /* Ignore */ }
        }

        return returnIntent;

    } // End method extractDataFromCsv.


    // If there was errors from populating content values, then populate error to broadcast;
    // otherwise write populated content values to db.
    private void writeToDbIfNoError(SharedPreferences sp, String error, String prefKey) {
        if (mLlInsertValues.size() > 0 && error == null) {
            writeToDb(prefKey);
        } else {
            if (error != null) {
                mErrorMessage = error;
            } else {
                putErrorPrefLocation(sp, prefKey);
            }
        }
    }


    // Populate error message to broadcast with 'data location error message'.
    private void putErrorPrefLocation(SharedPreferences sp, String prefKey) {
        String prefLocation;
        if (prefKey.equals(DbContract.PREF_PRODUCT_COLUMNS_KEY)) {
            prefLocation = sp.getString(getString(R.string.pref_products_data_location), "");
        } else {
            prefLocation = sp.getString(getString(R.string.pref_customers_data_location), "");
        }
        mErrorMessage = getString(R.string.error_load_data_location) + ": " + prefLocation;
    }


    /**
     * Put passed columns into shared preferences for given key.
     *
     * @param sp              - shared preferences.
     * @param arColumnsWithId - all extracted (customer or products) columns + _id
     * @param prefKey         - columns shared pref key.
     * @return - intent containing columns to broadcast as string of comma separated values.
     */
    private Intent putColumnsString(SharedPreferences sp, String[] arColumnsWithId, String
            prefKey) {
        int columnCount = arColumnsWithId.length;
        StringBuilder sbColumnsWithId = new StringBuilder();
        StringBuilder sbColumnsWithoutId = new StringBuilder();
        for (int i = 0; i < columnCount; i++) {
            sbColumnsWithId.append(arColumnsWithId[i].trim());
            if (i < columnCount - 1) { sbColumnsWithId.append(", "); }
            if (i > 0) {
                sbColumnsWithoutId.append(arColumnsWithId[i].trim());
                if (i < columnCount - 1) { sbColumnsWithoutId.append(", "); }
            }
        }
        Intent intent = new Intent(Util.ARG_UPDATE_SERVICE_BROADCAST);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(prefKey, sbColumnsWithId.toString().trim());
        editor.apply();
        intent.putExtra(prefKey, sbColumnsWithoutId.toString());
        return intent;
    }


    // Check if new columns are different to old columns (current in db). If yes increment db
    // version -> update table to new columns (tables data is completely erased and rewritten on
    // update anyways).
    private void checkColumns(String[] arOldColumns, String[] arNewColumns) {
        String strNewColumns = TextUtils.join(", ", arNewColumns);
        if (arOldColumns.length != arNewColumns.length) {
            mIncrementDbVersion = true;
        } else {
            for (String oldColumn : arOldColumns) {
                if (!strNewColumns.contains(oldColumn.trim())) {
                    mIncrementDbVersion = true;
                    return;
                }
            }
            String strOldColumns = TextUtils.join(", ", arOldColumns);
            for (String newColumn : arNewColumns) {
                if (!strOldColumns.contains(newColumn.trim())) {
                    mIncrementDbVersion = true;
                    break;
                }
            }
        }
    }


    // Increment db version (because table columns changed) and update table to new columns.
    private void incrementDbVersion(String prefKey) {
        // If both products and customers need to be done, products are done first. onUpgrade db
        // both tables are dropped. If product columns were the same (did not start onUpgrade) and
        // customer columns changed (started onUpgrade after products were already done) - products
        // need to be redone. Flag this situation.
        if (prefKey.equals(DbContract.PREF_CUSTOMER_COLUMNS_KEY)) {
            mRedoProducts = true;
        }

        // Prepare update operations to preserve favourites:
        mLlUpdateOperations = new ArrayList<>();
        CatalogSales application = (CatalogSales) getApplication();
        Uri uri = DbContract.getProductsTableUri();
        // Get column 'favourite' and 'product id' names.
        String columnFavourite = getString(R.string.column_name_favourite);
        String columnProductCode = "[" + application.getProductColumnIdentifier() + "]";
        // Traverse through all products in the products table and store favourite column value to
        // copy it applying batch update operations.
        Cursor cursor = getContentResolver().query(DbContract.getProductsTableUri(),
                new String[]{columnProductCode, columnFavourite}, null, null, null);
        String selection = columnProductCode + " = ?";
        if (cursor != null) {
            while (cursor.moveToNext()) {
                mLlUpdateOperations.add(ContentProviderOperation.newUpdate(uri)
                                .withSelection(selection, new String[]{cursor.getString(0)})
                                .withValue(columnFavourite, cursor.getString(1))
                                .build());
            }
            cursor.close();
        }

        // Increment db version
        SQLiteDatabase db = SQLiteDatabase.openDatabase(
                getDatabasePath(DbContract.DB_NAME).getAbsolutePath(), null, 0);
        int dbVersion = db.getVersion();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sp.edit();
        editor.putInt(DbContract.DB_VERSION_KEY, ++dbVersion);
        editor.apply();
        // Start onUpgrade to update tables to new columns.
        (new DbHelper(this)).getWritableDatabase();
    }


    /**
     * Same method is used both for extracting from JSON and from csv. Usage is such that the
     * other is passed as null. So if tableJsonArray is null, then csvRecords is not so extract
     * from csv. Vice versa extract data from JSON.
     *
     * @param columns        - product column names for the passed data.
     * @param tableJsonArray - JSONArray of product data if extracting from JSON. Null if not.
     * @param csvRecords     - List of apache commons CSVRecord containing product data if
     *                       extracting from csv. Null if not.
     * @return - error message if error occurred or null if not.
     */
    private String insertProductValues(String[] columns, JSONArray tableJsonArray, List<CSVRecord>
            csvRecords) {

        // Words 'integer' and 'decimal' used in error message.
        final String INTEGER = getString(R.string.integer);
        final String DECIMAL = getString(R.string.decimal);

        int productCount;
        JSONObject productJsonObject = null;
        String currentColumn;

        // Application level class containing common data needed across the application.
        CatalogSales application = (CatalogSales) getApplicationContext();
        // Relevant product columns indices used to test product data for valid format.
        int columnPackSizeIndex = application.getProductColumnPackSizeIndex();
        int columnPriceIndex = application.getProductColumnPriceIndex();
        int columnActiveIndex = application.getProductColumnActiveIndex();
        int columnTaxIndex = application.getProductColumnTaxIndex();
        int columnIdentifierIndex = application.getProductColumnIdentifierIndex();
        String[] boolFormatActive = new String[2];
        // Flags for indicating error in data in corresponding column.
        boolean activeNoErrorYet = true, taxNoErrorYet = true, packSizeNoErrorYet = true,
                priceNoErrorYet = true;
        // Error message holder.
        StringBuilder errorMessage = new StringBuilder();

        // Get product count
        if (tableJsonArray != null) {
            productCount = tableJsonArray.length();
        } else if (csvRecords != null) {
            productCount = csvRecords.size();
        } else { return getString(R.string.error_writing_to_db); }

        // Traverse all product records in product data
        for (int productIndex = 0; productIndex < productCount; productIndex++) {
            // Extract current row of product data
            if (tableJsonArray != null) {
                try {
                    productJsonObject = tableJsonArray.getJSONObject(productIndex);
                } catch (JSONException e) { continue; }
            }
            ContentValues insertValues = new ContentValues();

            // Traverse all columns for current row of product data.
            for (int columnIndex = 1; columnIndex < columns.length; columnIndex++) {
                // Get value for current row and column of product data.
                String value = "";
                if (tableJsonArray != null) {
                    try {
                        value = productJsonObject.getString(columns[columnIndex]).trim();
                    } catch (JSONException e) { continue; }
                } else if (csvRecords != null) {
                    value = csvRecords.get(productIndex).get(columns[columnIndex]).trim();
                }

                currentColumn = "[" + columns[columnIndex].trim() + "]";

                if (columnIndex == columnTaxIndex || columnIndex == columnPackSizeIndex) {
                    // Test columns 'tax' and 'pack size' for valid integer value and put into
                    // content values. If invalid populate error message.
                    if (!"".equals(value) && TextUtils.isDigitsOnly(value)) {
                        insertValues.put(currentColumn, Integer.parseInt(value));
                    } else {
                        if (columnIndex == columnTaxIndex) {
                            if (taxNoErrorYet) {
                                errorMessage.append(String.format(application.getLocale(),
                                        getString(R.string.incorrect_number_format),
                                        getString(R.string.column_tax_default_name), INTEGER));
                                errorMessage.append("\n\n");
                            }
                            taxNoErrorYet = false;
                        } else if (columnIndex == columnPackSizeIndex) {
                            if (packSizeNoErrorYet) {
                                errorMessage.append(String.format(application.getLocale(),
                                        getString(R.string.incorrect_number_format),
                                        getString(R.string.column_pack_size_default_name),
                                        INTEGER));
                                errorMessage.append("\n\n");
                            }
                            packSizeNoErrorYet = false;
                        }
                        insertValues.put(currentColumn, value);
                    }
                } else if (columnIndex == columnPriceIndex) {
                    // Test price for valid double value and put into content values. If
                    // invalid populate error message.
                    try {
                        double price = Double.parseDouble(value);
                        insertValues.put(currentColumn, price);
                    } catch (NumberFormatException e) {
                        if (priceNoErrorYet) {
                            errorMessage.append(String.format(application.getLocale(),
                                    getString(R.string.incorrect_number_format),
                                    getString(R.string.column_price_default_name), DECIMAL));
                            errorMessage.append("\n\n");
                        }
                        insertValues.put(currentColumn, value);
                        priceNoErrorYet = false;
                    }
                } else if (columnIndex == columnActiveIndex) {
                    // Test column 'active' for valid boolean - only two distinct values and put
                    // into content values. If invalid populate error message.
                    if (productIndex == 0) { boolFormatActive[0] = value; }
                    if (!boolFormatActive[0].equals(value)) {
                        // If first value is set:
                        if (boolFormatActive[1] == null) {
                            // If second value is not set, set it.
                            boolFormatActive[1] = value;
                        } else if (!boolFormatActive[1].equals(value)) {
                            // If second value is set and the new value does not equal to first
                            // or second value
                            if (activeNoErrorYet) {
                                errorMessage.append(String.format(application.getLocale(),
                                        getString(R.string.bool_columns_error),
                                        getString(R.string.bool_column_active),
                                        boolFormatActive[0], boolFormatActive[1], value));
                                activeNoErrorYet = false;
                            }
                        }
                    }
                    insertValues.put(currentColumn, value);
                } else {
                    // Text columns don't need testing. Just put into content values.
                    insertValues.put(currentColumn, value);
                }
            }

            // Preserve favourites:
            // Get column 'favourite' and 'product id' names.
            String columnFavourite = getString(R.string.column_name_favourite);
            String identifier = "";
            if (tableJsonArray != null) {
                try {
                    identifier = productJsonObject.getString(columns[columnIdentifierIndex]);
                } catch (JSONException e) { continue; }
            } else if (csvRecords != null) {
                identifier = csvRecords.get(productIndex).get(columns[columnIdentifierIndex]);
            }
            // Get db record for current product code and read column favourite. Put what was
            // read into content values to rewrite.
            Cursor cursor = getContentResolver().query(DbContract.getProductsTableUri(),
                    new String[]{columnFavourite},
                    application.getProductColumnIdentifier() + " = ?",
                    new String[]{identifier}, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    insertValues.put(columnFavourite, cursor.getString(0));
                }
                cursor.close();
            }
            // Insert prepared content values into the list of content values for inserting into db.
            mLlInsertValues.add(insertValues);
        }

        if (errorMessage.length() > 0) {
            // If error occurred, return it.
            return errorMessage.toString();
        } else {
            // If no error occurred, populate column active boolean param field
            if (boolFormatActive[0] != null) {
                mProductColumnActiveBoolFormat = TextUtils.join(" - ", boolFormatActive);
            }
            return null;
        }

    }


    /**
     * Same method is used both for extracting from JSON and from csv. Usage is such that the
     * other is passed as null. So if tableJsonArray is null, then csvRecords is not so extract
     * from csv. Vice versa extract data from JSON.
     *
     * @param columns        - customer column names for the passed data.
     * @param tableJsonArray - JSONArray of product data if extracting from JSON. Null if not.
     * @param csvRecords     - List of apache commons CSVRecord containing product data if
     *                       extracting from csv. Null if not.
     * @return - error message if error occurred or null if not.
     */
    private String insertCustomerValues(String[] columns, JSONArray tableJsonArray, List<CSVRecord>
            csvRecords) {

        // Application level class containing common data needed across the application.
        CatalogSales application = (CatalogSales) getApplicationContext();
        int customerCount;
        JSONObject jsonObject = null;
        StringBuilder sbAddress = new StringBuilder();
        String columnLatitude = getString(R.string.column_name_latitude);
        String columnLongitude = getString(R.string.column_name_longitude);
        String columnAddress = getString(R.string.column_name_address);

        Geocoder geocoder = new Geocoder(this, application.getLocale());
        ArrayList<Integer> alAddressColumnsIndices = new ArrayList<>();
        alAddressColumnsIndices.addAll(
                Arrays.asList(ArrayUtils.toObject(application.getCustomerAddressColumnsIndices())));

        // Get customer count.
        if (tableJsonArray != null) {
            customerCount = tableJsonArray.length();
        } else if (csvRecords != null) {
            customerCount = csvRecords.size();
        } else { return getString(R.string.error_writing_to_db); }

        // Traverse all customer records in customer data
        for (int customerIndex = 0; customerIndex < customerCount; customerIndex++) {
            sbAddress.setLength(0);
            if (tableJsonArray != null) {
                try {
                    jsonObject = tableJsonArray.getJSONObject(customerIndex);
                } catch (JSONException e) { continue; }
            }
            ContentValues insertValues = new ContentValues();

            // Traverse all columns for current row of customer data.
            for (int columnIndex = 1; columnIndex < columns.length; columnIndex++) {
                // Get value for current row & column of product data and put it into content values
                String value;
                if (tableJsonArray != null) {
                    try {
                        value = jsonObject.getString(columns[columnIndex]).trim();
                    } catch (JSONException e) { continue; }
                } else { // if (csvRecords != null) {
                    value = csvRecords.get(customerIndex).get(columns[columnIndex]).trim();
                }
                insertValues.put("[" + columns[columnIndex].trim() + "]", value);
                if (alAddressColumnsIndices.contains(columnIndex)) {
                    sbAddress.append(value.trim()); sbAddress.append(" ");
                }
            }

            try {
                List<Address> addresses =
                        geocoder.getFromLocationName(sbAddress.toString().trim(), 1);
                if (addresses.size() > 0) {
                    Address address = addresses.get(0);
                    insertValues.put(columnLatitude, address.getLatitude());
                    insertValues.put(columnLongitude, address.getLongitude());
                    insertValues.put(columnAddress, address.getAddressLine(0));
                }
            } catch (IOException | IllegalArgumentException e) {
                Log.e(" ADDRESS", e.toString());
            }

            // Insert prepared content values into the list of content values for inserting into db.
            mLlInsertValues.add(insertValues);
        }

        startService(new Intent(this, CustomersWidgetUpdateService.class));

        return null;

    }


    private void writeToDb(String prefKey) {
        Uri uri;
        String tableName;

        if (mIncrementDbVersion) { incrementDbVersion(prefKey); mIncrementDbVersion = false; }

            // Get uri and table name for appropriate table.
        if (prefKey.equals(DbContract.PREF_CUSTOMER_COLUMNS_KEY)) {
            uri = DbContract.getCustomersTableUri();
            tableName = DbContract.TableCustomers.TABLE_NAME;
        } else {
            uri = DbContract.getProductsTableUri();
            tableName = DbContract.TableProducts.TABLE_NAME;
        }
        File dbFile = getDatabasePath(DbContract.DB_NAME);
        if (dbFile.exists()) {
            // if db and tables were created before, delete all data in tables
            try {
                // Delete all rows from the table to remove no more used products / customers
                getContentResolver().delete(uri, null, null);
                // Delete a row from sqlite_sequence table for table so it will start _id
                // form 0 again.
                getContentResolver().delete(DbContract.SQLITE_SEQUENCE_URI,
                        DbContract.SQLITE_SEQUENCE_TABLE_COLUMN + " = ?", new String[]{tableName});
            } catch (SQLException e) { /* ignore */ }
        } else {
            // If db and tables have not been created, get dbHelper into motion to get it done.
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) { cursor.close(); }
        }

        // Insert data into db.
        ContentValues[] insertValues = new ContentValues[mLlInsertValues.size()];
        mLlInsertValues.toArray(insertValues);
        try {
            getContentResolver().bulkInsert(uri, insertValues);
        } catch (IllegalArgumentException | IllegalStateException | SQLiteException e) {
            mErrorMessage = getString(R.string.error_writing_to_db) + "\n" + e.toString();
        }

        // Restore favourites if columns changed (database was upgraded to new version).
        if (mLlUpdateOperations != null && prefKey.equals(DbContract.PREF_PRODUCT_COLUMNS_KEY)) {
            try {
                getContentResolver().applyBatch(DbContract.CONTENT_AUTHORITY, mLlUpdateOperations);
            } catch (RemoteException | OperationApplicationException e) {
                Toast.makeText(this, getString(R.string.warning_could_not_preserve_favourites),
                        Toast.LENGTH_SHORT).show();
            }
        }

    }


    // Send result. Picked up by SettingsFragment for setting columns or MainActivity for update
    // result.
    private void broadcast(Intent intent) {
        intent.putExtra(Util.ARG_GET_COLUMNS, mGetColumns);
        if (mDoProducts) { intent.putExtra(Util.ARG_DO_PRODUCTS, true); }
        if (mDoCustomers) { intent.putExtra(Util.ARG_DO_CUSTOMERS, true); }
        if (mErrorMessage != null) {
            intent.putExtra(Util.ARG_SERVICE_UPDATE_ERROR, mErrorMessage);
        }
        if (mProductColumnActiveBoolFormat != null) {
            intent.putExtra(DbContract.COLUMN_PRODUCT_ACTIVE_BOOL_KEY,
                    mProductColumnActiveBoolFormat);
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }


} // End class ServiceUpdate.
