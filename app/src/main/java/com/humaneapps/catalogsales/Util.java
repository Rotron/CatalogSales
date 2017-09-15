/*
 * Copyright (C) 2017 Vladimir Markovic. All rights reserved.
 */

package com.humaneapps.catalogsales;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.humaneapps.catalogsales.data.DbContract;
import com.humaneapps.catalogsales.service.ServiceUpdate;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


/**
 * Non-instantiable utility class containing commonly used constants and methods.
 */

final public class Util {

    // Suppress default constructor for non-instantiability
    private Util() { throw new AssertionError(); }


    public static final String ACTION_WIDGET_CUSTOMERS_UPDATED =
            "com.humaneapps.catalogsales.ACTION_WIDGET_CUSTOMERS_UPDATED";

    static final int DEFAULT_GRID_COLUMN_WIDTH = 300;

    static final int SETTINGS_ACTIVITY_RESULT_CODE = 1;
    static final int ORDER_ACTIVITY_RESULT_CODE = 2;

    public static final String ARG_CUSTOMER_INDEX = "ARG_CUSTOMER_INDEX";

    // Arguments for bundles and intents:
    public static final String ARG_GET_COLUMNS = "ARG_GET_COLUMNS";
    public static final String ARG_DO_PRODUCTS = "ARG_DO_PRODUCTS";
    public static final String ARG_DO_CUSTOMERS = "ARG_DO_CUSTOMERS";
    public static final String ARG_UPDATE_SERVICE_BROADCAST = "ARG_UPDATE_SERVICE_BROADCAST";
    public static final String ARG_SERVICE_UPDATE_ERROR = "ARG_SERVICE_UPDATE_ERROR";
    static final String ARG_PRODUCT_PARCEL = "ARG_PRODUCT_PARCEL";
    static final String ARG_TOOLBAR_HEIGHT = "ARG_TOOLBAR_HEIGHT";
    static final String ARG_DETAIL_SCROLL = "ARG_DETAIL_SCROLL";
    static final String ARG_EXISTING_ORDERS = "ARG_EXISTING_ORDERS";
    // Arguments for bundles and intents related to activity results
    static final String RESULT_GRID_COLUMN_WIDTH = "RESULT_GRID_COLUMN_WIDTH";
    static final String RESULT_LOAD_IMAGES_LOCATION = "RESULT_LOAD_IMAGES_LOCATION";
    static final String RESULT_FINISH_MAIN = "RESULT_FINISH_MAIN";
    static final String RESULT_RESET_GROUPING_COLUMNS = "RESULT_RESET_GROUPING_COLUMNS";
    static final String RESULT_OTHER_COLUMN_TITLE = "RESULT_OTHER_COLUMN_TITLE";
    static final String RESULT_RECREATE_MAIN_ACTIVITY = "RESULT_RECREATE_MAIN_ACTIVITY";
    // Arguments for bundles and intents related to saving state.
    static final String STATE_SHOWING_EXISTING_ORDERS = "STATE_SHOWING_EXISTING_ORDERS";
    // Keys for shared preferences
    static final String KEY_ORDER_FILE_NAME = "KEY_ORDER_FILE_NAME";
    static final String KEY_EDITING_ORDER = "KEY_EDITING_ORDER";

    static final String FAVOURITE_POSITIVE = "Y";
    public static final String FAVOURITE_NEGATIVE = "N";

    // Used in a provider as a flag whether to notify notifyChange or not.
    public static final String DO_NOT_NOTIFY_CHANGE = "do_not_notify_change";

    static final String[] ORDER_COLUMNS = {"Co./Last Name", "Date", "Customer PO",
            "Item Number", "Quantity", "Description", "Price", "Total", "Inc-Tax Total",
            "Salesperson Last Name", "Salesperson First Name", "Tax Code", "GST Amount"};

    // Order columns
    private static final int INDEX_CUSTOMER = 0;
    // static final int INDEX_DATE = 1;
    // static final int INDEX_NOTE = 2;
    private static final int INDEX_CODE = 3;
    private static final int INDEX_QUANTITY = 4;
    private static final int INDEX_DESC = 5;
    private static final int INDEX_PRICE = 6;
    // static final int INDEX_TOTAL = 7;
    // static final int INDEX_INC_TAX_TOTAL = 8;
    // static final int INDEX_LAST_NAME = 9;
    // static final int INDEX_FIRST_NAME = 10;
    // private static final int INDEX_TAX_CODE = 11;
    private static final int INDEX_TAX = 12;


    // Populate and customize spinner text size and color and padding.
    static void populateAndCustomizeSpinner(Spinner spinner, String[] array, Context context,
                                            int itemLayout, int dropDownItemLayout,
                                            int textSizeId, int textColorId,
                                            final int topBottom, final int leftRight) {
        final float textSize = context.getResources().getDimension(textSizeId);
        final int textColor = ContextCompat.getColor(context, textColorId);
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(context, itemLayout, array) {
            @Override
            @NonNull
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = view.findViewById(android.R.id.text1);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
                textView.setTextColor(textColor);
                textView.setPadding(leftRight, topBottom, leftRight, topBottom);
                textView.setSingleLine(false);
                return view;
            }
        };
        arrayAdapter.setDropDownViewResource(dropDownItemLayout);
        spinner.setAdapter(arrayAdapter);
    }


    // Populate spinner with medium text size and light text color.
    static void populateSpinnerSimpleMediumLightText(Spinner spinner, String[] array, Context
            context) {
        populateAndCustomizeSpinner(spinner, array, context, android.R.layout.simple_spinner_item,
                R.layout.customers_spinner_item,
                R.dimen.text_size_m, R.color.colorTextLight, 0, 0);
    }


    // Check for internet connection.
    public static boolean isOnline(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getApplicationContext().getSystemService(Context
                        .CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }


    /**
     * Read input from local or online file line by line and return appended String.
     *
     * @param fullPath      - path to the file - url or local.
     * @param fromLocalFile - flag signaling if reading local file or url stream.
     * @return - Appended String of read input lines. Null on error.
     */
    @Nullable
    public static String fetchInput(String fullPath, boolean fromLocalFile) {
        try {
            // Read all the text returned by the server
            BufferedReader in;
            if (fromLocalFile) {
                in = new BufferedReader(new InputStreamReader(new FileInputStream(fullPath)));
            } else {
                in = new BufferedReader(new InputStreamReader((new URL(fullPath)).openStream()));
            }
            String str;
            StringBuilder builder = new StringBuilder();
            builder.append("");

            while ((str = in.readLine()) != null) { builder.append(str); }

            in.close();
            return builder.toString();

        } catch (IOException | NullPointerException e) {
            return null;
        }
    }


    // Request passed in permission, if not already granted.
    static boolean requestPermission(Activity activity, String permission, int permissionId) {
        if (ContextCompat.checkSelfPermission(activity, permission) == PackageManager
                .PERMISSION_GRANTED) {
            return true;
        }
        ActivityCompat.requestPermissions(activity, new String[]{permission}, permissionId);
        return false;
    }


    // Orders will be stored in /storage/emulated/0/[app name]/Orders
    static String getOrderDirPath(Context context) {
        return getExternalFileDirPath(context) + "/" + context.getString(R.string.orders_dir);
    }


    // Get path to directory in external storage for this app (sd card/app name)
    // If not available, return internal.
    private static String getExternalFileDirPath(Context context) {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            return Environment.getExternalStorageDirectory().getAbsolutePath() + "/"
                    + context.getString(R.string.app_name);
        } else {
            return context.getFilesDir().toString();
        }
    }


    static String getFilePath(String path) {
        return "file://" + path.replaceAll("\\s+", "%20");
    }


    // Add front and end slash if not present.
    @NonNull
    static String addSlashes(String path) {
        StringBuilder output = new StringBuilder();
        path = path.trim();
        if (path.indexOf("/") != 0) { output.append("/"); }
        output.append(path);
        if (path.lastIndexOf("/") != path.length() - 1) { output.append("/"); }
        return output.toString();
    }


    // Try to get the passed file (name) in passed dir. Used for getting order file if it exists.
    // Return null if it doesn't.
    static File getFile(String dir, String fileName) {
        if (dir == null || fileName == null) { return null; }
        File file;
        try {
            file = new File(dir, fileName);
        } catch (NullPointerException | IllegalArgumentException e) {
            file = null;
        }
        if (file != null && file.exists()) {
            return file;
        } else {
            return null;
        }
    }


    // Get file for the passed file name in the passed dir. Create both dir and file if any
    // doesn't exist. Return opened or created file or null on error.
    static File getOrCreateFile(String dirPath, String fileName) {
        if (dirPath == null || fileName == null) { return null; }
        // Create access to directory for saving files or make it if it doesn't exist.
        File fileDir = new File(dirPath);
        if (!fileDir.exists() && !fileDir.mkdirs()) {
            return null;
        }
        // Create file.
        File file;
        try {
            file = new File(fileDir, fileName);
        } catch (NullPointerException | IllegalArgumentException e) {
            Log.e(" File creation", " failed: " + e);
            file = null;
        }
        return file;
    }


    // Add http:// in front and slash at end of url, if not present. Return null on error.
    static String formUrlDir(String url) {
        // If trailing slash is not present, add it.
        if (url.lastIndexOf("/") != url.length() - 1) { url += "/"; }
        // If http:// was not specified, add it.
        url = addProtocol(url);
        return url;
    }


    // Add http:// in front of url, if not present. Return null on error.
    public static String addProtocol(String url) {
        if (url == null || url.length() < 4) { return null; }
        final String SCHEME = "http";
        if (!SCHEME.equals(url.substring(0, 4))) {
            url = SCHEME + "://" + url;
        }
        return url;
    }


    // Return array of customer names with 'All' as the first entry.
    static String[] getCustomers(CatalogSales application) {
        int customerCount = application.getCustomerCount();
        String[] allCustomers = new String[customerCount + 1];
        allCustomers[0] = application.getString(R.string.all);
        System.arraycopy(application.getCustomers(), 0, allCustomers, 1, customerCount);
        return allCustomers;
    }


    // Number of days to go back to include orders from that date - for populating 'days' spinner.
    static String[] makeDaySelectionList() {
        return new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
                "20", "30", "60", "90", "120", "366", "732", "999"};
    }


    static void emailOrder(Activity activity, String orderFileName) {
        if (orderFileName == null) { return; }
        final ArrayList<String> orderFileNames = new ArrayList<>();
        orderFileNames.add(orderFileName);
        emailOrders(orderFileNames, activity);
    }


    static void emailOrders(ArrayList<String> orderFileNames, Activity activity) {
        final Intent emailIntent = getOrdersEmailIntent(orderFileNames, activity);
        try {
            activity.startActivity(emailIntent);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(activity, activity.getString(R.string.no_email_app), Toast
                    .LENGTH_LONG).show();
        }
    }


    // Prepare intent for email when attaching orders to be emailed.
    private static Intent getOrdersEmailIntent(ArrayList<String> orderFileNames, Context context) {

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String recipient = sp.getString(context.getString(R.string.pref_recipient_email), "");
        String subject = sp.getString(context.getString(R.string.pref_email_subject), "");
        String message = sp.getString(context.getString(R.string.pref_email_text), "");
        String cc = sp.getString(context.getString(R.string.pref_cc_email), "");
        String bcc = sp.getString(context.getString(R.string.pref_bcc_email), "");
        String couldNotAttach = context.getString(R.string.error_attach_file);

        //need to "send multiple" to get more than one attachment
        final Intent emailIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        emailIntent.setType("text/plain");
        if (recipient.length() != 0) {
            emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{recipient});
        }
        if (cc.length() != 0) {
            emailIntent.putExtra(Intent.EXTRA_CC, new String[]{cc});
        }
        if (bcc.length() != 0) {
            emailIntent.putExtra(Intent.EXTRA_BCC, new String[]{bcc});
        }
        if (subject.length() != 0) {
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        }
        if (message.length() != 0) {
            emailIntent.putExtra(Intent.EXTRA_TEXT, message);
        }
        // For extra stream
        ArrayList<Uri> uris = new ArrayList<>();
        // Get all selected order files and and pun in uris for attaching.
        for (String orderFileName : orderFileNames) {
            if (orderFileName == null) {
                message = couldNotAttach + " order file" + "\n" + message;
                continue;
            }
            File orderFile = new File(Util.getOrderDirPath(context.getApplicationContext()),
                    orderFileName);
            if (!orderFile.exists()) {
                message = couldNotAttach + " " + orderFileName + "\n" + message;
                continue;
            }
            Uri uri = Uri.fromFile(orderFile);
            uris.add(uri);
        }
        emailIntent.putExtra(Intent.EXTRA_TEXT, message);
        emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        return emailIntent;
    } // End method getOrdersEmailIntent.


    static String saveAndEndOrder(Activity activity) {
        CatalogSales application = (CatalogSales) activity.getApplication();
        String orderFileName = saveOrder(activity, application);
        endOrder(application);
        return orderFileName;
    }


    // Save order as csv file and return saved order file name or null on error.
    static String saveOrder(Activity activity, CatalogSales application) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            return application.order.saveOrder(application, true);
        } else {
            ActivityCompat.requestPermissions(activity, new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, application.getResources()
                    .getInteger(R.integer.permission_external_storage_id));
            return null;
        }
    }


    static void endOrder(CatalogSales application) {
        application.setEditingOrder(false);
        application.orderFileName = null;
        application.order.clear();
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences
                (application).edit();
        editor.remove(KEY_ORDER_FILE_NAME);
        editor.remove(KEY_EDITING_ORDER);
        editor.apply();

    }


    private static final float COLUMN_RATIO = 1.2f;


    static float getGridColumnRatio() { return COLUMN_RATIO; }


    // For passing activity result from SettingsFragment to MainActivity


    static void setSettingsResultInt(Activity activity, String param, int extra) {
        Intent intent = new Intent();
        intent.putExtra(param, extra);
        activity.setResult(Activity.RESULT_OK, intent);
    }


    static void setSettingsResultBln(Activity activity, String param, boolean extra) {
        Intent intent = new Intent();
        intent.putExtra(param, extra);
        activity.setResult(Activity.RESULT_OK, intent);
    }


    static void setSettingsResultStr(Activity activity, String param, String extra) {
        Intent intent = new Intent();
        intent.putExtra(param, extra);
        activity.setResult(Activity.RESULT_OK, intent);
    }


    // For processing MultiSelectListPreference value.
    static String removeBrackets(String input) {
        if (input.length() < 2) { return input; }
        StringBuilder output = new StringBuilder(input.trim());
        if (output.indexOf("[") == 0) {
            output.deleteCharAt(0);
        } else { return input; }
        if (output.lastIndexOf("]") == output.length() - 1) {
            output.deleteCharAt(output.length() - 1);
        } else { return input; }
        return output.toString();
    }


    // For processing MultiSelectListPreference value.
    static String addBrackets(String input) {
        StringBuilder output = new StringBuilder(input.trim());
        if (output.indexOf("[") != 0) { output.insert(0, "["); }
        if (output.lastIndexOf("]") != output.length()) { output.insert(output.length(), "]"); }
        return output.toString();
    }


    // Form number as currency.
    static String getFormattedPrice(Locale locale, double price) {
        String currencySymbol = Currency.getInstance(locale).getSymbol();
        return String.format(Locale.getDefault(), "%1s%2$.2f", currencySymbol, price);
    }


    // Check if string is decimal or integer.
    static boolean isNumber(String in) {
        String separator;
        int dotCount = StringUtils.countMatches(in, ".");
        int commaCount = StringUtils.countMatches(in, ",");
        if (dotCount == 0) {
            if (commaCount == 0) {
                separator = "";
            } else if (commaCount == 1) {
                separator = ",";
            } else { return false; }
        } else if (dotCount == 1) {
            if (commaCount == 0) {
                separator = ".";
            } else { return false; }
        } else { return false; }
        in = in.replace(separator, "");
        return TextUtils.isDigitsOnly(in);
    }


    // Load order from file: user saved to display it, or auto saved to continue taking/editing it.
    static void loadOrder(String orderFileName, CatalogSales application) {

        if (orderFileName.indexOf("-") < 1) {
            Toast.makeText(application, application.getString(R.string.load_order_fail),
                    Toast.LENGTH_LONG).show();
            return;
        }

        File orderFile = Util.getFile(Util.getOrderDirPath(application), orderFileName);

        if (orderFile == null) {
            Toast.makeText(application, application.getString(R.string.load_order_fail),
                    Toast.LENGTH_LONG).show();
            return;
        }

        try {
            Reader in = new FileReader(orderFile);
            Iterable<CSVRecord> records = CSVFormat.EXCEL.withFirstRecordAsHeader().parse(in);

            int counter = 0, quantity, taxPercent, packSize, packSizeIndex, spaceIndex;
            double price, taxAmount;
            String code, title, strPackSize;
            Order order = new Order();
            for (CSVRecord record : records) {
                // code, q, desc,
                Map map = record.toMap();
                // set customer once
                if (counter == 0) {
                    order.setCustomer((String) map.get(Util.ORDER_COLUMNS[Util.INDEX_CUSTOMER]));
                }
                title = (String) map.get(Util.ORDER_COLUMNS[Util.INDEX_DESC]);
                packSizeIndex = title.lastIndexOf(" x ") + 3;
                if (packSizeIndex > 10) {
                    strPackSize = title.substring(packSizeIndex).trim();
                    spaceIndex = strPackSize.indexOf(" ");
                    if (spaceIndex > 0) {
                        strPackSize = strPackSize.substring(0, spaceIndex).trim();
                    }
                } else {
                    if (title.lastIndexOf(" only of ") > 10) {
                        spaceIndex = title.lastIndexOf(" ");
                        if (spaceIndex > 10) {
                            strPackSize = title.substring(spaceIndex + 1).trim();
                        } else {
                            strPackSize = "0";
                        }
                    } else {
                        strPackSize = "0";
                    }
                }
                if (TextUtils.isDigitsOnly(strPackSize)) {
                    packSize = Integer.parseInt(strPackSize);
                } else {
                    packSize = 0;
                }
                title = title.replace("\\r\\n", "\r\n");
                code = (String) map.get(Util.ORDER_COLUMNS[Util.INDEX_CODE]);
                quantity = Integer.parseInt((String) map.get(Util.ORDER_COLUMNS[Util
                        .INDEX_QUANTITY]));
                price = Double.parseDouble((String) map.get(Util.ORDER_COLUMNS[Util.INDEX_PRICE]));
                taxAmount = Double.parseDouble((String) map.get(Util.ORDER_COLUMNS[Util
                        .INDEX_TAX]));
                taxPercent = (int) Math.round((price * quantity) / taxAmount);
                order.changeQuantity(code, quantity, title, price, taxPercent, packSize);
                counter++;
            }

            application.order.swapOrder(order);
            application.orderFileName = orderFileName;

        } catch (IOException | NumberFormatException | NullPointerException |
                ArrayIndexOutOfBoundsException e) {
            Toast.makeText(application, application.getString(R.string.load_order_fail),
                    Toast.LENGTH_LONG).show();
        }
    }


    // Show message in Snackbar if used view is not null. Otherwise show message as Toast.
    static void snackOrToast(Context context, View view, String message, int duration) {
        if (view != null) {
            Snackbar snackbar = Snackbar.make(view, message, duration);
            if (duration == Snackbar.LENGTH_INDEFINITE) {
                // For indefinite Snackbar duration, show dismiss option.
                ((TextView) snackbar.getView().findViewById(
                        android.support.design.R.id.snackbar_text)).setSingleLine(false);
                snackbar.setAction(context.getString(R.string.dismiss), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {}
                });
            }
            snackbar.show();
        } else {
            int toastDuration = Toast.LENGTH_LONG;
            if (duration == Snackbar.LENGTH_SHORT) { toastDuration = Toast.LENGTH_SHORT; }
            Toast.makeText(context, message, toastDuration).show();
        }
    }


    // Display update error or success message after updating products or customers.
    static void processUpdateResult(Intent intent, CatalogSales application, View view) {

        String error = intent.getStringExtra(Util.ARG_SERVICE_UPDATE_ERROR);
        if (error == null) {
            boolean gotColumns = intent.getBooleanExtra(Util.ARG_GET_COLUMNS, false);
            boolean didProducts = intent.getBooleanExtra(Util.ARG_DO_PRODUCTS, false);
            boolean didCustomers = intent.getBooleanExtra(Util.ARG_DO_CUSTOMERS, false);
            String message;
            if (gotColumns) { return; }

            if (didProducts && didCustomers) {
                message = application.getString(R.string.message_updated_data);
                application.setProductColumnsAndCustomers(null);
            } else {
                if (didProducts) {
                    message = application.getString(R.string.message_updated_products);
                    application.setProductColumns(null);
                } else if (didCustomers) {
                    message = application.getString(R.string.message_updated_customers);
                    application.setCustomerColumnsAndCustomers(null);
                } else {
                    message = application.getString(R.string.message_updated_data);
                    application.setProductColumnsAndCustomers(null);
                }
            }
            snackOrToast(application, view, message, Snackbar.LENGTH_LONG);
        } else {
            int duration = Snackbar.LENGTH_INDEFINITE;
            if (application.getString(R.string.warning_cannot_update_offline).equals(error)) {
                duration = Snackbar.LENGTH_LONG;
            }
            snackOrToast(application, view, error, duration);
        }
    }


    static void showDialogConfirm(Activity activity, String title) {
        DialogConfirm dialog = (DialogConfirm) activity.getFragmentManager().findFragmentByTag
                (title);
        if (dialog == null) { dialog = DialogConfirm.newInstance(); }
        dialog.show(activity.getFragmentManager(), title);
    }


    /**
     * Find and show or add and show fragment if cannot find it (not previously created).
     *
     * @param newFragment    - instance of the fragment to be shown.
     * @param title          - title of the fragment to be shown - added to transaction and used
     *                       as tag later for finding.
     * @param addToBackStack - boolean determining whether to add to back stack.
     */
    static void showFragment(AppCompatActivity activity, Fragment newFragment, String title,
                             boolean addToBackStack, Bundle args) {
        // Create new fragment transaction
        FragmentTransaction transaction = activity.getSupportFragmentManager().beginTransaction();
        Fragment oldFragment = activity.getSupportFragmentManager().findFragmentByTag(title);
        // Check if fragment exists (title as tag) - if not create and add it.
        if (oldFragment == null) {
            if (args != null) { newFragment.setArguments(args); }
            // Add this fragment to fragment_container.
            transaction.add(R.id.fragmentContainer, newFragment, title);
            // If so specified, add the transaction to the back stack so the user can navigate back
            if (addToBackStack) { transaction.addToBackStack(title); }
            // Commit the transaction
            transaction.commit();
        } else {
            // If fragment already exists, show it.
            if (args != null) { oldFragment.setArguments(args); }
            transaction.show(oldFragment);
        }
    } // End showFragment method.


    // Pre-prepared initialization for user to be able to click skip when initializing.
    static void skipInit(Activity activity) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
        SharedPreferences.Editor editor = sp.edit();

        String imagesLocation = "shapesgalore.netau.net/shapes/img";
        editor.putString(activity.getString(R.string.pref_images_location), imagesLocation);

        String productsDataLocation = "shapesgalore.netau.net/shapes/products.csv";
        editor.putString(activity.getString(R.string.pref_products_data_location),
                productsDataLocation);

        String customersDataLocation = "shapesgalore.netau.net/shapes/customers.csv";
        editor.putString(activity.getString(R.string.pref_customers_data_location),
                customersDataLocation);

        String[] mainDefs = new String[]{"_id", "Name", "Image", "Price", "GST", "Pack", "Active"};
        for (int i = 1; i < DbContract.MAIN_PRODUCT_COLUMNS_COUNT; i++) {
            editor.putString(DbContract.PRODUCT_COLUMNS_MAIN_KEYS[i], mainDefs[i]);
        }
        String columns = "_id, Code, Image, Pack, Name, Price, Category, GST, Supplier, Active";
        editor.putString(DbContract.PREF_PRODUCT_COLUMNS_KEY, columns);
        columns = "_id, Co./Last Name, Addr 1 - Line 1, Addr 1 - Line 2, Addr 1 - Line 3, " +
                "Addr 1 - State, Addr 1 - Postcode, Addr 1 - City, Addr 1 - Phone No. 1, " +
                "Addr 1 - Phone No. 2, Volume Discount %, Salesperson, Addr 1 - Contact Name";
        editor.putString(DbContract.PREF_CUSTOMER_COLUMNS_KEY, columns);
        editor.putString(DbContract.COLUMN_CUSTOMER_NAME_KEY, "Co./Last Name");
        editor.putString(DbContract.COLUMN_CUSTOMER_IDENTIFIER_KEY, "Co./Last Name");
        Set<String> address = new HashSet<>(6);
        address.add("Addr 1 - Line 1");
        address.add("Addr 1 - Line 2");
        address.add("Addr 1 - Line 3");
        address.add("Addr 1 - State");
        address.add("Addr 1 - Postcode");
        address.add("Addr 1 - City");
        editor.putStringSet(DbContract.PREF_CUSTOMER_ADDRESS_COLUMNS_KEY, address);
        Set<String> phones = new HashSet<>(3);
        phones.add("Addr 1 - Phone No. 1");
        phones.add("Addr 1 - Phone No. 2");
        editor.putStringSet(DbContract.PREF_CUSTOMER_PHONES_COLUMNS_KEY, phones);
        Set<String> otherCustomerDetails = new HashSet<>(1);
        otherCustomerDetails.add("Addr 1 - Contact Name");
        editor.putStringSet(DbContract.PREF_CUSTOMER_OTHER_DETAILS_COLUMNS_KEY,
                otherCustomerDetails);
        editor.putString(DbContract.COLUMN_PRODUCT_IDENTIFIER_KEY, "Code");
        String[] otherColumns = {"Category", "Code", "Supplier"};
        editor.putString(DbContract.PRODUCT_COLUMNS_OTHER_KEY, TextUtils.join(", ",
                otherColumns));
        Set<String> defM = new HashSet<>(3);
        defM.add("Category");
        editor.putStringSet(DbContract.PRODUCT_COLUMNS_GROUPING_KEY, defM);
        defM.add("Code");
        defM.add("Supplier");
        editor.putStringSet(DbContract.PRODUCT_COLUMNS_OTHER_DETAILS_KEY, defM);
        String[] defs = new String[]{"Category", "Code", "Supplier"};
        for (int i = 0; i < defs.length; i++) {
            editor.putString(DbContract.OTHER_PRODUCT_COLUMNS_TITLE_PREF_PREFIX + otherColumns[i],
                    defs[i]);
        }
        editor.putString(DbContract.COLUMN_PRODUCT_ACTIVE_BOOL_KEY, "Y - N");
        editor.putString(activity.getString(R.string.pref_company_name), "Shapes Galore");
        editor.putString(activity.getString(R.string.pref_sales_person_first_name), "Sales");
        editor.putString(activity.getString(R.string.pref_sales_person_last_name), "Person");
        editor.putString(activity.getString(R.string.pref_email_subject), "New Orders");
        editor.putString(activity.getString(R.string.pref_email_text_message),
                "Please find new orders attached.");
        editor.apply();

        ((CatalogSales) activity.getApplication()).setProductColumnsAndCustomers(sp);

        // Start ServiceUpdate.
        Intent intent = new Intent(activity, ServiceUpdate.class);
        intent.putExtra(Util.ARG_DO_PRODUCTS, true);
        intent.putExtra(Util.ARG_DO_CUSTOMERS, true);
        activity.startService(intent);
    }


} // End Util class
