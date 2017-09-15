/*
 * Copyright (C) 2017 Vladimir Markovic. All rights reserved.
 */

package com.humaneapps.catalogsales;

import android.database.Cursor;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;

/**
 * Product data holder class.
 */
class ReusableProduct implements Parcelable {

    private final StringBuilder mIdentifier = new StringBuilder();
    private final StringBuilder mImage = new StringBuilder();
    private final StringBuilder mTitle = new StringBuilder();
    private double mPrice;
    private int mTax;
    private int mPackSize;
    private boolean mFavourite;
    private final ArrayList<String> mOtherDetails = new ArrayList<>();


    public static final Creator<ReusableProduct> CREATOR
            = new Creator<ReusableProduct>() {
        public ReusableProduct createFromParcel(Parcel in) {
            return new ReusableProduct(in);
        }


        public ReusableProduct[] newArray(int size) {
            return new ReusableProduct[size];
        }
    };


    ReusableProduct() {}


    // Import product data from cursor for the set position.
    void swapCursor(CatalogSales application, Cursor cursor) {
        clear();
        mIdentifier.append(cursor.getString(application.getProductColumnIdentifierIndex()));
        if (application.getProductColumnImageIndex() > -1)  {
            mImage.append(cursor.getString(application.getProductColumnImageIndex()));
        } else { mImage.append(""); }
        mTitle.append(cursor.getString(application.getProductColumnTitleIndex()));
        if (application.getProductColumnPriceIndex() > -1)  {
            mPrice = cursor.getDouble(application.getProductColumnPriceIndex());
        } else { mPrice = 0; }
        if (application.getProductColumnTaxIndex() > -1)  {
            mTax = cursor.getInt(application.getProductColumnTaxIndex());
        } else { mTax = 0; }
        if (application.getProductColumnPackSizeIndex() > -1)  {
            mPackSize = cursor.getInt(application.getProductColumnPackSizeIndex());
        } else { mPackSize = 1; }
        mFavourite = cursor.getInt(application.getProductColumnCount() - 1) == 1;
        int getOtherDetailsProductColumnsCount =
                application.getOtherDetailsProductColumnsIndices().length;
        for (int i = 0; i < getOtherDetailsProductColumnsCount; i++) {
            mOtherDetails.add(cursor.getString(application.getOtherProductColumnsIndices()
                    [application.getOtherDetailsProductColumnsIndices()[i]] ));
        }
    }


    // Import product data from another product object.
    void swapProduct(ReusableProduct product) {
        clear();
        mIdentifier.append(product.getIdentifier());
        mImage.append(product.getImage());
        mTitle.append(product.getProductName());
        mPrice = product.getPrice();
        mTax = product.getTax();
        mPackSize = product.getPackSize();
        mFavourite = product.getFavourite();
        mOtherDetails.addAll(product.getOtherDetails());
    }


    // Clear all product data.
    private void clear() {
        mIdentifier.setLength(0);
        mImage.setLength(0);
        mTitle.setLength(0);
        mOtherDetails.clear();
    }


    private ReusableProduct(Parcel in) {
        mIdentifier.append(in.readString());
        mImage.append(in.readString());
        mTitle.append(in.readString());
        mPrice = in.readDouble();
        mTax = in.readInt();
        mPackSize = in.readInt();
        mFavourite = in.readInt() == 1;
        int otherDetailCount = in.readInt();
        for (int i = 0; i < otherDetailCount; i++) {
            mOtherDetails.add(in.readString());
        }
    }


    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mIdentifier.toString());
        out.writeString(mImage.toString());
        out.writeString(mTitle.toString());
        out.writeDouble(mPrice);
        out.writeInt(mTax);
        out.writeInt(mPackSize);
        out.writeInt(mFavourite ? 1 : 0);
        int otherDetailCount = mOtherDetails.size();
        out.writeInt(otherDetailCount);
        for (String value : mOtherDetails) {
            out.writeString(value);
        }
    }


    String getIdentifier() { return mIdentifier.toString(); }


    public String getImage() { return mImage.toString(); }


    String getProductName() { return mTitle.toString(); }


    double getPrice() { return mPrice; }


    int getTax() { return mTax; }


    int getPackSize() { return mPackSize; }


    @SuppressWarnings("WeakerAccess")
    boolean getFavourite() { return mFavourite; }


    @SuppressWarnings("WeakerAccess")
    ArrayList<String> getOtherDetails() { return mOtherDetails; }


    String[] getOtherDetailsArray() {
        return mOtherDetails.toArray(new String[mOtherDetails.size()]);
    }


    @Override
    public int describeContents() {
        return 0;
    }


} // End class ReusableProduct

