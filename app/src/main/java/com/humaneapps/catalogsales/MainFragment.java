/*
 * Copyright (C) 2017 Vladimir Markovic. All rights reserved.
 */

package com.humaneapps.catalogsales;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Sets containing recycler view which displays product images in a grid.
 */
public class MainFragment extends Fragment {

    // For displaying product images and order taking views when taking order (+,-,quantity,price).
    private RecyclerView mRecyclerView;


    public MainFragment() {}


    public static MainFragment newInstance() {return new MainFragment();}


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        mRecyclerView = rootView.findViewById(R.id.rvProducts);
        return rootView;
    }


    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Set RecyclerView and adapter.
        MainActivity mainActivity = (MainActivity) getActivity();
        mRecyclerView.setLayoutManager(mainActivity.imageAdapter.makeGridLayoutManager());
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setNestedScrollingEnabled(false);
        mRecyclerView.setAdapter(mainActivity.imageAdapter);
    }


} // End class MainFragment.