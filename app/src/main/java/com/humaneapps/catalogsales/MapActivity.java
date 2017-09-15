/*
 * Copyright (C) 2017 Vladimir Markovic. All rights reserved.
 */
package com.humaneapps.catalogsales;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.humaneapps.catalogsales.data.DbContract;

import butterknife.BindView;
import butterknife.ButterKnife;


/**
 * Display all customers as pins on a map with selected info from db. When customer is selected
 * from top spinner, it's pin's data is displayed.
 */
public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {


    private CatalogSales mApplication;

    @BindView(R.id.spnMapCustomers)
    Spinner mSpnCustomers;

    private GoogleMap mMap;
    // For holding customer markers with snippets.
    private Marker[] mPins;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        ButterKnife.bind(this);
        mApplication = (CatalogSales) getApplication();
        mApplication.setCustomerColumnsAndCustomers(null);
        // Populate customers spinner
        Util.populateSpinnerSimpleMediumLightText(mSpnCustomers, Util.getCustomers(mApplication)
                , mApplication);
        // Start map fragment
        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(MapActivity.this);

    } // End onCreate


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        // Get all customer data from the customers table in 'customer name' ascending order.
        String nameColumn = "[" + mApplication.getCustomerColumnIdentifier() + "]";
        Cursor cursor = mApplication.getContentResolver().query(DbContract.getCustomersTableUri()
                , null,
                null, null, nameColumn + " ASC");

        if (cursor == null) { return; }

        mPins = new Marker[cursor.getCount() + 1];
        // Indices for getting columns data
        int columnNameIndex = mApplication.getCustomerColumnIdentifierIndex();
        int columnLatitudeIndex = mApplication.getCustomerColumnLatIndex();
        int columnLongitudeIndex = mApplication.getCustomerColumnLngIndex();
        int columnAddressIndex = mApplication.getCustomerColumnAddressIndex();
        int[] columnsPhoneIndices = mApplication.getCustomerPhoneColumnsIndices();
        int[] columnsOtherDetailsIndices = mApplication.getCustomerOtherDetailsColumnsIndices();
        // Data holders
        String phone, otherDetails;
        StringBuilder sbSnippet = new StringBuilder();
        double lat, lng;
        LatLng latLng;

        int i = 0;

        // For each customer get latitude and longitude to show pin on the map, and put details
        // in snippet - name, address, phones, other details specified in settings.
        while (cursor.moveToNext()) {
            lat = cursor.getDouble(columnLatitudeIndex);
            lng = cursor.getDouble(columnLongitudeIndex);
            latLng = new LatLng(lat, lng);
            // Reset snippet
            sbSnippet.setLength(0);
            // Add address to snippet
            sbSnippet.append(cursor.getString(columnAddressIndex));
            sbSnippet.append("\n");
            // Add phones to snippet
            for (int columnPhoneIndex : columnsPhoneIndices) {
                phone = cursor.getString(columnPhoneIndex);
                if (!"".equals(phone)) {
                    sbSnippet.append(phone);
                    sbSnippet.append("\n");
                }
            }
            // Add other details to snippet
            for (int columnOtherDetailsIndex : columnsOtherDetailsIndices) {
                otherDetails = cursor.getString(columnOtherDetailsIndex);
                if (!"".equals(otherDetails)) {
                    sbSnippet.append(otherDetails);
                    sbSnippet.append("\n");
                }
            }
            // Make first position ('All' and starting camera position) to show first
            // customer pin (zoom 9 without showing the snippet).
            if (i == 0) {
                mPins[i] = mMap.addMarker(new MarkerOptions()
                        .position(latLng)
                        .draggable(false));
                i++;
            }
            // Add customer marker with lat/lng, title and snippet.
            mPins[i] = mMap.addMarker(new MarkerOptions()
                    .position(latLng)
                    .title(cursor.getString(columnNameIndex))
                    .snippet(sbSnippet.toString())
                    .draggable(false));

            i++;

        } // End for each customer in cursor.

        cursor.close();

        // Start camera at first or passed in customer pin position at zoom 9.
        Intent intent = getIntent();
        if (intent.hasExtra(Util.ARG_CUSTOMER_INDEX)) {
            final int customerIndex = intent.getIntExtra(Util.ARG_CUSTOMER_INDEX, -1) + 1;
            mSpnCustomers.setSelection(customerIndex);
        } else {
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition
                    (CameraPosition.builder()
                            .target(mPins[0].getPosition())
                            .zoom(9)
                            .bearing(0)
                            .build()));
        }


        // Set snippet
        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {

            @Override
            public View getInfoWindow(Marker marker) {
                return null;
            }


            @Override
            public View getInfoContents(Marker marker) {

                LinearLayout snippetLayout = new LinearLayout(mApplication);
                snippetLayout.setOrientation(LinearLayout.VERTICAL);
                // Set title
                TextView title = new TextView(mApplication);
                title.setTextColor(Color.BLACK);
                title.setTypeface(null, Typeface.BOLD);
                title.setText(marker.getTitle());
                // Set details
                TextView details = new TextView(mApplication);
                details.setTextColor(Color.DKGRAY);
                details.setText(marker.getSnippet());

                snippetLayout.addView(title);
                snippetLayout.addView(details);

                return snippetLayout;
            }
        });

        // Set customer spinner listener to zoom on customer pin and snippet on selection.
        mSpnCustomers.setOnItemSelectedListener(new AdapterView
                .OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // For position zero (All) show starting camera position - first customer pin at
                // zoom 9 (city size). For all other positions show  selected customer pin and
                // snippet at zoom 13 (street level).
                int zoom = 9;
                if (position > 0) {
                    mPins[position].showInfoWindow();
                    zoom = 13;
                }
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition
                        (CameraPosition.builder()
                                .target(mPins[position].getPosition())
                                .zoom(zoom)
                                .bearing(0)
                                .build()));
            }


            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

    } // End onMapReady method.


    @Override
    public void onResume() {
        super.onResume();
        // For receiving result from service update.
        LocalBroadcastManager.getInstance(this).registerReceiver(mLocalServiceUpdateReceiver,
                new IntentFilter(Util.ARG_UPDATE_SERVICE_BROADCAST));
    }


    @Override
    public void onPause() {
        super.onPause();
        // For receiving result from service update.
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocalServiceUpdateReceiver);
    }


    // Show update (products/customers) message if views order/s when starting the app.
    private final BroadcastReceiver mLocalServiceUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, final Intent intent) {
            Util.processUpdateResult(intent, (CatalogSales) getApplication(),
                    findViewById(android.R.id.content));
        }
    };


} // End MapActivity class

