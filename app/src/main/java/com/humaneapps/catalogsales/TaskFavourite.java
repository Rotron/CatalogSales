/*
 * Copyright (C) 2017 Vladimir Markovic. All rights reserved.
 */

package com.humaneapps.catalogsales;

import android.content.ContentValues;
import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.Nullable;

import com.humaneapps.catalogsales.data.DbContract;

/**
 * Used to update product favourite status.
 */

class TaskFavourite extends AsyncTask<Boolean, Void, Boolean> {

    // Used to get content resolver.
    private final CatalogSales mApplication;
    // Used as selection argument for deleting and updating.
    private final String mIdentifier;

    // Async response for returning the result.
    private AsyncResponseFavourite mAsyncResponse = null;

    // 'Return' Boolean isFavourite: true  - favourite (after insert and update,
    //                               false - not favourite (after delete),
    //                               null  - on error.
    interface AsyncResponseFavourite {
        void processFinish(Boolean isFavourite);
    }


    // Constructor
    TaskFavourite(AsyncResponseFavourite asyncResponse, Context context, String identifier) {
        mAsyncResponse = asyncResponse;
        mApplication = (CatalogSales) context.getApplicationContext();
        mIdentifier = identifier;
    }


    /**
     * @param params - boolean param[0] tells if the product was in favourites or not.
     * @return - boolean showing if the product is in favourites after task completion. Return
     * null if updating failed.
     */
    @Override
    protected Boolean doInBackground(Boolean... params) {

        // For returning result.
        Boolean becameFavourite = null;

        final ContentValues contentValues = new ContentValues();

        if (params.length > 0) {

            boolean isFavourite = params[0];

            if (isFavourite) {
                contentValues.put(mApplication.getString(R.string.column_name_favourite), Util.FAVOURITE_NEGATIVE);
            } else {
                contentValues.put(mApplication.getString(R.string.column_name_favourite), Util.FAVOURITE_POSITIVE);
            }

            int rows = mApplication.getContentResolver().update(DbContract.getProductsTableUri(),
                    contentValues, "[" + mApplication.getProductColumnIdentifier() + "] = ?",
                    new String[]{mIdentifier});

            if (rows > 0) { becameFavourite = !isFavourite; }
        }

        return becameFavourite;

    }


    // Pass result to caller.
    @Override
    protected void onPostExecute(@Nullable Boolean isFavourite) {
        mAsyncResponse.processFinish(isFavourite);
    }


} // End AsyncTask class TaskFavourite.
