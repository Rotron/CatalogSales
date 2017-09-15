/*
 * Copyright (C) 2017 Vladimir Markovic. All rights reserved.
 */
package com.humaneapps.catalogsales;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.preference.PreferenceManager;
import android.widget.Toast;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

/**
 * Class containing all order details necessary for display or saving: customer and list of
 * ordered items: product identifiers, names, quantities, prices, tax, and pack sizes.
 */

class Order {

    private final StringBuilder mCustomer = new StringBuilder();
    private final ArrayList<String> mIdentifiers = new ArrayList<>();
    private final ArrayList<String> mTitles = new ArrayList<>();
    private final ArrayList<Integer> mQuantities = new ArrayList<>();
    private final ArrayList<Double> mPrices = new ArrayList<>();
    private final ArrayList<Integer> mTaxes = new ArrayList<>();
    private final ArrayList<Integer> mPackSizes = new ArrayList<>();


    Order() {}


    void setCustomer(String customer) {
        mCustomer.setLength(0);
        mCustomer.append(customer);
    }


    void clear() {
        mCustomer.setLength(0);
        mIdentifiers.clear();
        mTitles.clear();
        mQuantities.clear();
        mPrices.clear();
        mTaxes.clear();
        mPackSizes.clear();
    }


    void startNewOrder(String customer) {
        clear();
        mCustomer.append(customer);
    }


    void swapOrder(Order order) {
        clear();
        if (order != null) {
            mCustomer.append(order.getCustomer());
            mIdentifiers.addAll(order.getIdentifiers());
            mTitles.addAll(order.getProductNames());
            mQuantities.addAll(order.getQuantities());
            mPrices.addAll(order.getPrices());
            mTaxes.addAll(order.getTaxes());
            mPackSizes.addAll(order.getPackSizes());
        }
    }


    void changeQuantity(String identifier, int quantity, String productName, Double price
            , int tax, int packSize) {
        int index = mIdentifiers.indexOf(identifier);
        if (index == -1) {
            // If new product, add all fields
            mIdentifiers.add(identifier);
            mTitles.add(productName);
            mQuantities.add(quantity);
            mPrices.add(price);
            mTaxes.add(tax);
            mPackSizes.add(packSize);
        } else {
            // If already added product, just update quantity.
            if (quantity == 0) {
                // Remove previously added product if quantity is reduced to zero.
                removeProduct(index);
                return;
            }
            mQuantities.set(index, quantity);
        }
    }


    void changePrice(String identifier, String productName, Double price, int tax, int packSize) {
        int index = mIdentifiers.indexOf(identifier);
        if (index == -1) {
            // If new product, add all fields
            mIdentifiers.add(identifier);
            mTitles.add(productName);
            mPrices.add(price);
            mQuantities.add(0);
            mTaxes.add(tax);
            mPackSizes.add(packSize);
        } else {
            // If already added product, just change price.
            mPrices.set(index, price);
        }
    }


    // Remove product using index
    void removeProduct(int index) {
        mIdentifiers.remove(index);
        mTitles.remove(index);
        mQuantities.remove(index);
        mPrices.remove(index);
        mTaxes.remove(index);
        mPackSizes.remove(index);
    }


    // Remove product using id: get index and then remove it using index.
    void removeProduct(String id) {
        int index = mIdentifiers.indexOf(id);
        if (index > -1) { removeProduct(index); }
    }


    // Field getters:

    int getCount() { return mIdentifiers.size(); }


    String getCustomer() { return mCustomer.toString(); }


    int getIndex(String identifier) { return mIdentifiers.indexOf(identifier); }


    String getTitle(int index) { return mTitles.get(index); }


    int getQuantity(int index) { return mQuantities.get(index); }


    double getPrice(int index) { return mPrices.get(index); }


    int getTaxPercent(int index) { return mTaxes.get(index); }


    @SuppressWarnings("unused")
    int getPackSize(int index) { return mPackSizes.get(index); }


    private double getTotalPrice() {
        double totalPrice = 0d;
        for (int i = 0; i < mQuantities.size(); i++) {
            totalPrice += mQuantities.get(i) * mPrices.get(i);
        }
        return totalPrice;
    }


    private double getTotalOrderTax() {
        double tax = 0d;
        for (int i = 0; i < mQuantities.size(); i++) {
            if (mTaxes.get(i) > 0) {
                tax += mQuantities.get(i) * mPrices.get(i) * (mTaxes.get(i) / 100d);
            }
        }
        return tax;
    }


    String getTotalString(Context context) {
        double totalOrderPrice = getTotalPrice();
        if (totalOrderPrice == 0) { return ""; }
        double totalOrderTax = getTotalOrderTax();
        if (totalOrderTax == 0d) {
            return String.format(Locale.getDefault(), "%1$s \t $%2$.2f",
                    context.getString(R.string.total_order_price), totalOrderPrice);
        } else {
            return String.format(Locale.getDefault(),
                    "%1$s \t $%2$.2f \t + \t $%3$.2f %4$s \t = \t $%5$.2f",
                    context.getString(R.string.total_order_price), totalOrderPrice, totalOrderTax,
                    context.getString(R.string.tax_acronym), (totalOrderPrice + totalOrderTax));
        }
    }


    String[] getIdentifiersAsArray() {
        return mIdentifiers.toArray(new String[mIdentifiers.size()]);
    }


    private ArrayList<String> getIdentifiers() { return mIdentifiers; }


    private ArrayList<String> getProductNames() { return mTitles; }


    private ArrayList<Integer> getQuantities() { return mQuantities; }


    private ArrayList<Double> getPrices() { return mPrices; }


    private ArrayList<Integer> getTaxes() { return mTaxes; }


    private ArrayList<Integer> getPackSizes() { return mPackSizes; }


    /**
     * @param application - used as context, to get order file name, locale, if editing.
     * @param showResultToast - message is shown on final save on save button click. On
     *                        intermediate saves (in onPause) message is not shown.
     * @return - saved order file name or null on error.
     */
    String saveOrder(CatalogSales application, boolean showResultToast) {

        if (mIdentifiers.size() == 0) { return null; }

        // If this is order being edited (was previously saved) order file name exists.
        String orderFileName = application.orderFileName;
        // Reflects order table - array of columns data in array list of products.
        ArrayList<String[]> orderForCsv = new ArrayList<>();
        // Get sales person - first and last name from settings.
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(application);
        String salesPersonFirstName = sp.getString(application.getString(R.string
                .pref_sales_person_first_name), null);
        String salesPersonLastName = sp.getString(application.getString(R.string
                .pref_sales_person_last_name), null);
        if (salesPersonFirstName == null) {
            salesPersonFirstName = "";
            salesPersonLastName = "";
        }
        if (salesPersonLastName == null) { salesPersonLastName = ""; }
        // Get date-time string to append to customer name to make order file name.
        Calendar cal = Calendar.getInstance();
        // Format date for appending to order file name.
        @SuppressWarnings("all")
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyMMdd-HHmmss");
        String strDate = dateFormat.format(cal.getTime());
        // If this is new order (saved for the first time), form order file name.
        if (orderFileName == null) {
            // Replace illegal file name characters if found in customer name.
            orderFileName = mCustomer.toString();
            orderFileName = orderFileName.replace("\\", "~");
            orderFileName = orderFileName.replace("\"", "~");
            orderFileName = orderFileName.replace("/", "~");
            orderFileName = orderFileName.replace("|", "~");
            orderFileName = orderFileName.replace(":", "~");
            orderFileName = orderFileName.replace("*", "~");
            orderFileName = orderFileName.replace("?", "~");
            orderFileName = orderFileName.replace("<", "~");
            orderFileName = orderFileName.replace(">", "~").trim();
            // Form order file name.
            orderFileName = orderFileName + "-" + strDate + ".csv";
        }
        // Form order file for saving using order file name.
        File orderFile = Util.getOrCreateFile(Util.getOrderDirPath(application), orderFileName);
        // Format date for input into order.
        dateFormat.applyPattern("d/MM/yyyy");
        strDate = dateFormat.format(cal.getTime()).trim();
        // Order columns
        int quantity, taxPercent, packSize, packs, remainder, packsLineIndex;
        String taxCode, packsLine, description;
        double totalPrice, taxAmount, incTaxTotal;
        // Traverse all products in order, prepare input data and input into order array for csv.
        for (int i = 0; i < mIdentifiers.size(); i++) {
            quantity = mQuantities.get(i);
            // If price is changed the order record is made with quantity 0, which stays unless
            // changed. Skip zero quantity products.
            if (quantity == 0) { continue; }
            String custPO = application.getString(R.string.order);
            taxPercent = mTaxes.get(i);
            taxCode = taxPercent == 0 ? "FRE" : "GST";
            totalPrice = mPrices.get(i) * quantity;
            if (taxPercent > 0) {
                taxAmount = totalPrice / taxPercent;
            } else {
                taxAmount = 0;
            }
            incTaxTotal = totalPrice + taxAmount;
            description = mTitles.get(i).trim();

            packsLineIndex = description.lastIndexOf("\r\n");
            if (packsLineIndex > -1) {
                description = description.substring(0, packsLineIndex).trim();
            }

            packSize = mPackSizes.get(i);

            // Add packs line on new line:
            //  - if quantity is whole number of packs -> packs x packSize, e.g. 2 x 6
            //  - if not (there is remainder) -> packs x packSize + remainder, e.g. 2 x 6 + 3
            //  - if quantity is less than pack size -> quantity only of packSize, e.g. 2 only of 6.
            packsLine = "\\r\\n";
            if (quantity < packSize) {
                packsLine += quantity + " only of " + packSize;
            } else {
                if (packSize > 0) {
                    packs = quantity / packSize;
                    packsLine += packs + " x " + packSize;
                    remainder = quantity % packSize;
                    if (remainder > 0) { packsLine += " + " + remainder; }
                } else {
                    packsLine += String.valueOf(quantity);
                }
            }
            description += packsLine;
            // Replace new line characters with escaped so they are input as text.
            mTitles.set(i, description.replace("\\r\\n", "\r\n"));

            // Add all prepared columns data as array to array list of products.
            Locale locale = application.getLocale();
            orderForCsv.add(new String[]{mCustomer.toString().trim(), strDate,
                    custPO.toUpperCase().trim(), mIdentifiers.get(i).trim(),
                    String.valueOf(quantity).trim(), description,
                    String.format(locale, "%.2f", mPrices.get(i)).trim(),
                    String.format(locale, "%.2f", totalPrice).trim(),
                    String.format(locale, "%.2f", incTaxTotal).trim(),
                    salesPersonLastName.trim(), salesPersonFirstName.trim(), taxCode,
                    String.format(locale, "%.2f", taxAmount).trim(),
            });
        }

        // Write order to file.
        CSVFormat csvFileFormat = CSVFormat.EXCEL.withHeader(Util.ORDER_COLUMNS);
        String result;
        CSVPrinter csvPrinter;
        FileWriter fileWriter;
        String savingOrder = application.getString(R.string.saving_order);
        try {
            if (!orderFile.exists() && !orderFile.createNewFile()) {
                throw new IOException();
            }
            fileWriter = new FileWriter(orderFile);
            csvPrinter = new CSVPrinter(fileWriter, csvFileFormat);

            csvPrinter.printRecords(orderForCsv);

            fileWriter.flush();
            fileWriter.close();
            csvPrinter.close();
            result = application.getString(R.string.success);
            savingOrder += " " + orderFileName;
        } catch (IOException e) {
            orderFileName = null;
            result = application.getString(R.string.failed);
        }
        if (showResultToast) {
            if (application.isEditingOrder()) {
                result += " " + application.getString(R.string.editing_and);
            }
            result += " " + savingOrder;
            Toast.makeText(application, result, Toast.LENGTH_LONG).show();
        }
        return orderFileName;
    }


} // End class Order.
