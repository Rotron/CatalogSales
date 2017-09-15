/*
 * Copyright (C) 2017 Vladimir Markovic. All rights reserved.
 */

package com.humaneapps.catalogsales;


import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.humaneapps.catalogsales.helper.TextWrapDrawable;

import java.io.File;

import butterknife.BindView;
import butterknife.ButterKnife;


/**
 * For showing product images (contains code for resolving image size) and order taking views
 * (pluses, minuses, quantity and price - and logic to show / hide them if order is being taken
 * or not).
 */
class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.MyViewHolder> {


    RecyclerView recyclerView;
    private final CatalogSales mApplication;
    private final AppCompatActivity mActivity;
    private Cursor mCursor;
    private int mItemCount;
    private final boolean mTwoPane, mTablet;
    // Populated from cursor to hold all the product data.
    private final ReusableProduct mProduct;
    // Grid column measures
    private int mGridColumnWidth, mGridColumnHeight, mNumberOfGridColumns;
    // Used in two pane mode to show (first) product (only once) in details screen when starting.
    private boolean mDetailsShown = false;

    // For making TextWrapDrawable for showing product name when image is unavailable.
    private float mTitleTextSize;
    private int mTitleTextPadding;
    // Toolbar height to pass to DetailsFragment for determining the image height.
    private int mToolbarHeight;


    // Constructor
    ImageAdapter(AppCompatActivity activity, boolean twoPane) {
        // Instantiate fields
        mActivity = activity;
        mApplication = (CatalogSales) activity.getApplicationContext();
        mTwoPane = twoPane;
        mTablet = mApplication.getResources().getBoolean(R.bool.tablet);
        mProduct = new ReusableProduct();

        // Get preferred poster size.
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mApplication);
        int imageSize = Integer.parseInt(sp.getString(mApplication.getString(R.string
                        .pref_column_size),
                mActivity.getString(R.string.pref_column_size_default)));
        setImagesSource(sp.getString(mApplication.getString(R.string.pref_images_location), "")
                .trim());

        // Using preferred poster size set number of columns and store their width and height.
        setGridColumns(imageSize);

        // Get toolbar height to pass to DetailsFragment for determining the image height.
        final Toolbar toolbar = mActivity.findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.post(new Runnable() {
                @Override
                public void run() {
                    mToolbarHeight = toolbar.getHeight();
                }
            });
        }

    }


    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;
    }


    // Create and return custom ViewHolder.
    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        View rootView = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout
                .product_item, viewGroup, false);
        return new MyViewHolder(rootView);
    }


    // Order taking details - not local to avoid recreating new variables in onBindViewHolder.
    @SuppressWarnings("FieldCanBeLocal")
    private double mNewPrice;
    @SuppressWarnings("FieldCanBeLocal")
    private int mQuantity, mQuantityBgdColor, mQuantityTextColor, mPriceBgdColor, mPriceTextColor,
            mIndexInOrder;


    @Override
    public void onBindViewHolder(final MyViewHolder myViewHolder, int position) {

        // If something went wrong skip - don't display that product.
        if (position == -1 || myViewHolder == null || myViewHolder.image == null) { return; }

        mCursor.moveToPosition(position);
        mProduct.swapCursor(mApplication, mCursor);
        mNewPrice = mProduct.getPrice();

        // In two pane show last shown product on start (only once).
        if (mTwoPane && !mDetailsShown) {
            startDetailsFragment(mApplication.getShownProductDetails());
            mDetailsShown = true;
        }

        // Add product image to RecyclerView using Glide. Show product name if image is unavailable.
        glideImage(mProduct.getImage(), myViewHolder.image, mProduct.getProductName());

        // Set listener for clicking on the poster image.
        myViewHolder.image.setOnClickListener(mImageListener);

        // If taking/editing order extra views are shown (+,-,quantity,price) - show and set them.
        if (mApplication.isTakingOrder) {

            if (myViewHolder.txvQuantity == null) { return; }

            showOrderViews(myViewHolder);

            // Set default background and text color for extra (order) views.
            mQuantityBgdColor = mPriceBgdColor = ContextCompat.getColor(mApplication, R.color
                    .colorSmallScrim);
            mQuantityTextColor = mPriceTextColor = ContextCompat.getColor(mApplication, R.color
                    .colorDarkGray);
            mQuantity = 0;
            // If product is present in the order
            mIndexInOrder = mApplication.order.getIndex(mProduct.getIdentifier());
            if (mIndexInOrder > -1) {
                // Get current quantity and price in order.
                mQuantity = mApplication.order.getQuantity(mIndexInOrder);
                mNewPrice = mApplication.order.getPrice(mIndexInOrder);
                // For quantity changed from zero and price changed from default, color the view
                // background and text to make it noticeable.
                if (mQuantity > 0) {
                    mQuantityBgdColor = Color.YELLOW;
                    mQuantityTextColor = Color.RED;
                } else if (mQuantity == 0) {
                    if (mNewPrice == mProduct.getPrice()) {
                        mApplication.order.removeProduct(mIndexInOrder);
                    }
                }
                if (mNewPrice != mProduct.getPrice()) {
                    mPriceBgdColor = Color.YELLOW;
                    mPriceTextColor = Color.RED;
                }
            }
            // Set prepared values to quantity and price text views.
            myViewHolder.txvQuantity.setText(String.valueOf(mQuantity));
            myViewHolder.txvPrice.setText(Util.getFormattedPrice(mApplication.getLocale(),
                    mNewPrice));
            // Set prepared background and text color to quantity and price text views.
            myViewHolder.txvQuantity.setBackgroundColor(mQuantityBgdColor);
            myViewHolder.txvQuantity.setTextColor(mQuantityTextColor);
            myViewHolder.txvPrice.setBackgroundColor(mPriceBgdColor);
            myViewHolder.txvPrice.setTextColor(mPriceTextColor);
            // Set tags to views to be able later to refer to product code, product name, tax
            // percentage, price and pack size, in views on click listeners.
            myViewHolder.txvQuantity.setTag(R.string.tag_id, mProduct.getIdentifier());
            myViewHolder.txvQuantity.setTag(R.string.tag_product_name, mProduct.getProductName());
            myViewHolder.txvQuantity.setTag(R.string.tag_tax, mProduct.getTax());
            myViewHolder.txvQuantity.setTag(R.string.tag_price, mNewPrice);
            myViewHolder.txvQuantity.setTag(R.string.tag_pack_size, mProduct.getPackSize());
            myViewHolder.btnPlusPack.setTag(R.string.tag_pack_size, mProduct.getPackSize());
            myViewHolder.btnMinusPack.setTag(R.string.tag_pack_size, mProduct.getPackSize());
            myViewHolder.txvPrice.setTag(R.string.tag_price, mProduct.getPrice());
            // Do not show price if it's zero (not specified).
            if (mProduct.getPrice() == 0) { myViewHolder.txvPrice.setVisibility(View.INVISIBLE); }

            setListenersToOrderViews(myViewHolder);
        } else {
            hideOrderViews(myViewHolder);
        }

    } // onBindViewHolder


    // Show image using Glide.
    private void glideImage(final String imageName, final ImageView imageView,
                            final String productName) {

        imageView.setContentDescription(productName);

        // Show image from specified location using Glide.
        Glide.with(mApplication).load(mImagesLocation + imageName)
                .placeholder(new TextWrapDrawable(mGridColumnWidth, mGridColumnHeight,
                        mTitleTextPadding, productName, mTitleTextSize))
                .dontAnimate()
                .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                .listener(new RequestListener<String, GlideDrawable>() {

                    @Override
                    public boolean onException(Exception e, String model, Target<GlideDrawable>
                            target,
                                               boolean isFirstResource) {
                        // If unable to load image, show product name instead.
                        imageView.setImageDrawable(new TextWrapDrawable(mGridColumnWidth,
                                mGridColumnHeight, mTitleTextPadding, productName,
                                mTitleTextSize));
                        return false;
                    }


                    @Override
                    public boolean onResourceReady(GlideDrawable resource, String model,
                                                   Target<GlideDrawable> target,
                                                   boolean isFromMemoryCache,
                                                   boolean isFirstResource) {
                        return false;
                    }
                }).into(imageView); // Load image if successfully fetched.


    }


    @Override
    public void onViewRecycled(MyViewHolder holder) {
        super.onViewRecycled(holder);
        resizeViews(holder);
    }


    @Override
    public int getItemCount() { return mItemCount; }


    int swapCursor(Cursor cursor) {
        if (cursor == null) {
            mCursor.close();
            mCursor = null;
            return 0;
        }
        mItemCount = cursor.getCount();
        mCursor = cursor;
        notifyDataSetChanged();
        return mItemCount;
    }


    private String mImagesLocation;


    // Set full images location path using specified location on start and when location is changed.
    void setImagesSource(String specifiedImagesLocation) {
        if ("".equals(specifiedImagesLocation) || mApplication.getString(
                R.string.warning_images_location_not_set).equals(specifiedImagesLocation)) {
            Util.snackOrToast(mApplication, null, mApplication.getString(R.string
                    .warning_images_location_not_set), Snackbar.LENGTH_LONG);
        }
        // If accessing image files from local dir
        if ((new File(specifiedImagesLocation)).exists()) {
            // If storage permission was not granted, request it.
            Util.requestPermission(mActivity, Manifest.permission.READ_EXTERNAL_STORAGE,
                    mApplication.getResources().getInteger(R.integer
                            .permission_external_storage_id));
            mImagesLocation = Util.getFilePath(Util.addSlashes(specifiedImagesLocation));
        } else {
            mImagesLocation = Util.formUrlDir(specifiedImagesLocation);
        }
    }


    // Remove all views to force RecyclerView redraw. Also destroy drawing cache and refresh
    // recycled view pool.
    void refreshRecyclerView() {
        recyclerView.destroyDrawingCache();
        recyclerView.removeAllViews();
        recyclerView.removeAllViewsInLayout();
        recyclerView.setRecycledViewPool(new RecyclerView.RecycledViewPool());
        recyclerView.invalidate();
    }


    // Set number of columns and store grid columns width and height and product name text size
    // as well as main fragment width for use in two pane layout.
    void setGridColumns(int prefGridColumnWidth) {

        mPrefGridColumnWidth = prefGridColumnWidth;

        DisplayMetrics displayMetrics = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        int screenWidth = displayMetrics.widthPixels;

        mTitleTextPadding = (int) (50 * displayMetrics.density);

        int fragMainWidth;
        // Set fragment width:
        if (mTwoPane) {
            // In tablet landscape set widths from resource - set to be 2/5 Main and 3/5 Details
            // fragment.
            // Get MainFragment width.
            float fmParts = mApplication.getResources().getInteger(R.integer.parts_fm);
            float fdParts = mApplication.getResources().getInteger(R.integer.parts_fd);
            fragMainWidth = (int) ((screenWidth / (fmParts + fdParts)) * fmParts);
        } else {
            // In phones fragment widths correspond to the screen width.
            fragMainWidth = screenWidth;
        }

        int approximateColumnWidth = (int) (prefGridColumnWidth * displayMetrics.density);
        mNumberOfGridColumns = fragMainWidth / approximateColumnWidth;
        // Get image spacing from resource. It is called half_image_spacing because it is applied
        // as image margin and as RecyclerView padding thus the space between posters will be
        // twice that.
        int gridColumnSpacing =
                (int) (mApplication.getResources().getDimension(R.dimen.half_image_spacing) * 2);
        mGridColumnWidth = (fragMainWidth - gridColumnSpacing
                * (mNumberOfGridColumns + 1)) / mNumberOfGridColumns;
        mGridColumnHeight = (int) (mGridColumnWidth * Util.getGridColumnRatio());
        // This ration scales nicely into most sizes.
        mTitleTextSize = mGridColumnHeight / 9f;
    }


    // Used to re-set layout manager to recycler view for changing number of columns - on rotation.
    GridLayoutManager makeGridLayoutManager() {
        GridLayoutManager gridLayoutManager = new GridLayoutManager(mApplication,
                mNumberOfGridColumns);
        gridLayoutManager.setSmoothScrollbarEnabled(true);
        gridLayoutManager.setAutoMeasureEnabled(false);
        gridLayoutManager.setSpanCount(mNumberOfGridColumns);
        return gridLayoutManager;
    }


    // Used in two pane on poster click to show details screen.
    private void showDetailsFragment() {
        // Used in two pane on poster click to show detail fragment.
        Bundle bundle = new Bundle();
        bundle.putParcelable(Util.ARG_PRODUCT_PARCEL, mProduct);
        bundle.putInt(Util.ARG_TOOLBAR_HEIGHT, mToolbarHeight);

        DetailsFragment detailsFragment = startDetailsFragment(bundle);
        if (detailsFragment != null) {
            // If details fragment is already added, just swap its data.
            if (detailsFragment.isResumed()) { detailsFragment.setBundle(bundle); }
            if (mTablet) { mApplication.setShownProductDetails(bundle); }
        }
    }


    private DetailsFragment startDetailsFragment(Bundle bundle) {

        String fragTitle = mApplication.getString(R.string.title_details);

        // Try to get details fragment finding it by tag (title).
        DetailsFragment detailsFragment = (DetailsFragment) mActivity.getSupportFragmentManager()
                .findFragmentByTag(fragTitle);
        // If fragment is already created don't add another on top.
        if (detailsFragment == null) {

            if (bundle == null || bundle.size() == 0) {
                // Used if starting from scratch
                bundle = new Bundle();
                ReusableProduct product = new ReusableProduct();
                product.swapProduct(mProduct);
                bundle.putParcelable(Util.ARG_PRODUCT_PARCEL, product);
                if (mTablet) { mApplication.setShownProductDetails(bundle); }
            }
            bundle.putInt(Util.ARG_TOOLBAR_HEIGHT, mToolbarHeight);
            // Create details fragment.
            detailsFragment = DetailsFragment.newInstance();
            detailsFragment.setArguments(bundle);
            // Create new fragment transaction
            FragmentTransaction transaction = mActivity.getSupportFragmentManager()
                    .beginTransaction();
            // Add details fragment to fragment_container.
            transaction.replace(R.id.detailsFragmentContainer, detailsFragment, fragTitle);
            // Commit the transaction
            transaction.commit();
            return null;
        } else { return detailsFragment; }

    }


    // Used in one pane on poster click to show details screen.
    private void showDetailsActivity(View view) {
        Intent intent = new Intent(mApplication, DetailsActivity.class);
        intent.putExtra(Util.ARG_PRODUCT_PARCEL, mProduct);
        intent.putExtra(Util.ARG_TOOLBAR_HEIGHT, mToolbarHeight);
        ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                mActivity, view, mApplication.getString(R.string.shared_element_image));
        mActivity.startActivity(intent, options.toBundle());
        if (mTablet) { mApplication.setShownProductDetails(intent.getExtras()); }
    }


    private void showOrderViews(MyViewHolder myViewHolder) {
        myViewHolder.flPosterItem.setBackgroundColor(Color.WHITE);
        myViewHolder.btnPlus.setVisibility(View.VISIBLE);
        myViewHolder.btnMinus.setVisibility(View.VISIBLE);
        myViewHolder.btnPlusPack.setVisibility(View.VISIBLE);
        myViewHolder.btnMinusPack.setVisibility(View.VISIBLE);
        myViewHolder.txvQuantity.setVisibility(View.VISIBLE);
        myViewHolder.txvPrice.setVisibility(View.VISIBLE);
    }


    private void hideOrderViews(MyViewHolder myViewHolder) {
        myViewHolder.flPosterItem.setBackgroundColor(Color.TRANSPARENT);
        myViewHolder.btnPlus.setVisibility(View.GONE);
        myViewHolder.btnMinus.setVisibility(View.GONE);
        myViewHolder.btnPlusPack.setVisibility(View.GONE);
        myViewHolder.btnMinusPack.setVisibility(View.GONE);
        myViewHolder.txvQuantity.setVisibility(View.GONE);
        myViewHolder.txvPrice.setVisibility(View.GONE);
    }


    private void setListenersToOrderViews(MyViewHolder holder) {
        holder.btnPlus.setOnClickListener(mPlusListener);
        holder.btnMinus.setOnClickListener(mMinusListener);
        holder.btnPlusPack.setOnClickListener(mPlusPackListener);
        holder.btnMinusPack.setOnClickListener(mMinusPackListener);
        holder.txvQuantity.setOnClickListener(mQuantityListener);
        holder.txvPrice.setOnClickListener(mPriceListener);
    }


    private boolean mChangeQuantity = true;
    private boolean mDirectionNegative = false;
    private int mQuantityStep = 1;
    private double mPriceStep = 0.05;

    // Custom ViewHolder class for use in RecyclerView.Adapter pattern for holding views.
    class MyViewHolder extends RecyclerView.ViewHolder {

        @BindView(R.id.flProductItem)
        FrameLayout flPosterItem;
        @BindView(R.id.imvProductImage)
        ImageView image;
        @BindView(R.id.btnPlusInMain)
        Button btnPlus;
        @BindView(R.id.btnMinusInMain)
        Button btnMinus;
        @BindView(R.id.btnPlusPackInMain)
        Button btnPlusPack;
        @BindView(R.id.btnMinusPackInMain)
        Button btnMinusPack;
        @BindView(R.id.txvQuantityInMain)
        TextView txvQuantity;
        @BindView(R.id.txvPriceInMain)
        TextView txvPrice;


        MyViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);

            resizeViews(this);

            if (mApplication.isTakingOrder) {
                showOrderViews(this);
                setListenersToOrderViews(this);
            } else {
                hideOrderViews(this);
            }
        }

    } // End inner MyViewHolder class.


    private int mPrefGridColumnWidth;


    // Resize views size and text size to scale them accordingly to fit with image size.
    private void resizeViews(MyViewHolder myViewHolder) {

        String[] strPrefColumnSizes =
                mApplication.getResources().getStringArray(R.array.pref_column_size_values);
        int[] intPrefColumnSizes = new int[strPrefColumnSizes.length];
        for (int i = 0; i < strPrefColumnSizes.length; i++) {
            intPrefColumnSizes[i] = Integer.parseInt(strPrefColumnSizes[i]);
        }
        float divider = 20f;
        float priceDivider = 24f;
        if (mPrefGridColumnWidth == intPrefColumnSizes[0]) {
            divider = 28f;
            priceDivider = 26f;
        } else if (mPrefGridColumnWidth == intPrefColumnSizes[1]) {
            divider = 20f;
            priceDivider = 22f;
        } else if (mPrefGridColumnWidth > intPrefColumnSizes[1]) {
            divider = 16f;
            priceDivider = 28f;
        }
        myViewHolder.btnPlus.setTextSize(mGridColumnWidth / divider);
        myViewHolder.btnMinus.setTextSize(mGridColumnWidth / divider);
        myViewHolder.btnPlusPack.setTextSize(mGridColumnWidth / divider);
        myViewHolder.btnMinusPack.setTextSize(mGridColumnWidth / divider);
        myViewHolder.txvQuantity.setTextSize(mGridColumnWidth / divider);
        myViewHolder.txvPrice.setTextSize(mGridColumnWidth / priceDivider);

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) myViewHolder.image
                .getLayoutParams();
        params.width = mGridColumnWidth - params.leftMargin - params.rightMargin;
        params.height = mGridColumnHeight - params.topMargin - params.bottomMargin;
        myViewHolder.image.setLayoutParams(params);

    } // End resizeViews method.


    // Show details screen on image click.
    private final View.OnClickListener mImageListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (mApplication.blockDetails) { return; }
            MyViewHolder holder = (MyViewHolder) recyclerView.findContainingViewHolder(view);
            if (holder == null) { return; }
            // Get ViewHolder for this view.
            MyViewHolder myViewHolder = (MyViewHolder) recyclerView.findContainingViewHolder(view);

            if (myViewHolder == null) { return; }
            // Find position of this view in adapter.
            final int position = myViewHolder.getAdapterPosition();
            // On error display message to user and return.
            if (position >= mCursor.getCount()) {
                Toast.makeText(mApplication, mApplication.getString(
                        R.string.error_details_missing), Toast.LENGTH_SHORT).show();
                return;
            }

            mCursor.moveToPosition(position);
            mProduct.swapCursor(mApplication, mCursor);

            if (mTwoPane) {
                showDetailsFragment();
            } else {
                showDetailsActivity(view);
            }
        }
    };


    // Add to quantity (1) or price (0.05) (which ever is selected).
    private final View.OnClickListener mPlusListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            mDirectionNegative = false;
            MyViewHolder holder = (MyViewHolder) recyclerView.findContainingViewHolder(view);
            if (holder == null) { return; }
            if (mChangeQuantity) {
                mQuantityStep = 1;
                changeQuantity(holder.txvQuantity);
            } else {
                mPriceStep = 0.05;
                changePrice(holder.txvPrice, holder.txvQuantity);
            }
            animateView(view);
        }
    };


    // Deduct from quantity (1) or price (0.05) (which ever is selected).
    private final View.OnClickListener mMinusListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            mDirectionNegative = true;
            MyViewHolder holder = (MyViewHolder) recyclerView.findContainingViewHolder(view);
            if (holder == null) { return; }
            if (mChangeQuantity) {
                mQuantityStep = 1;
                changeQuantity(holder.txvQuantity);
            } else {
                mPriceStep = 0.05;
                changePrice(holder.txvPrice, holder.txvQuantity);
            }
            animateView(view);
        }
    };


    // Add to quantity (pack size) or price (0.50) (which ever is selected).
    private final View.OnClickListener mPlusPackListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            mDirectionNegative = false;
            MyViewHolder holder = (MyViewHolder) recyclerView.findContainingViewHolder(view);
            if (holder == null) { return; }
            if (mChangeQuantity) {
                mQuantityStep = (Integer) view.getTag(R.string.tag_pack_size);
                changeQuantity(holder.txvQuantity);
            } else {
                mPriceStep = 0.50;
                changePrice(holder.txvPrice, holder.txvQuantity);
            }
            animateView(view);
        }
    };


    // Deduct from quantity (pack size) or price (0.50) (which ever is selected).
    private final View.OnClickListener mMinusPackListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            mDirectionNegative = true;
            MyViewHolder holder = (MyViewHolder) recyclerView.findContainingViewHolder(view);
            if (holder == null) { return; }
            if (mChangeQuantity) {
                mQuantityStep = (Integer) view.getTag(R.string.tag_pack_size);
                changeQuantity(holder.txvQuantity);
            } else {
                mPriceStep = 0.50;
                changePrice(holder.txvPrice, holder.txvQuantity);
            }
            animateView(view);
        }
    };


    // Select price for changing. Add or deduct 1 from it on consecutive clicks. Whether to add or
    // to deduct is determined by whether plus or minus was last clicked.
    private final View.OnClickListener mPriceListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            MyViewHolder holder = (MyViewHolder) recyclerView.findContainingViewHolder(view);
            if (holder == null) { return; }
            if (!mChangeQuantity && mPriceStep == 1.00) {
                changePrice(holder.txvPrice, holder.txvQuantity);
            }
            mChangeQuantity = false;
            mPriceStep = 1.00;
            animateView(view);
        }
    };


    // Select quantity for changing. Add or deduct 10 from it on consecutive clicks. Whether to
    // add or to deduct is determined by whether plus or minus was last clicked.
    private final View.OnClickListener mQuantityListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            MyViewHolder holder = (MyViewHolder) recyclerView.findContainingViewHolder(view);
            if (holder == null) { return; }
            if (mChangeQuantity && mQuantityStep == 10) {
                changeQuantity(holder.txvQuantity);
            }
            mChangeQuantity = true;
            mQuantityStep = 10;
            animateView(view);
        }
    };


    // Helper method for changing quantity using data passed in as tags.
    private void changeQuantity(TextView txvQuantity) {

        int quantity = Integer.parseInt(txvQuantity.getText().toString());
        if (mDirectionNegative) { mQuantityStep = -mQuantityStep; }
        quantity += mQuantityStep;
        if (quantity < 0) { quantity = 0; mDirectionNegative = false; }

        txvQuantity.setText(String.valueOf(quantity));
        String id = (String) txvQuantity.getTag(R.string.tag_id);
        String productName = (String) txvQuantity.getTag(R.string.tag_product_name);
        Double price = (Double) txvQuantity.getTag(R.string.tag_price);
        int tax = (int) txvQuantity.getTag(R.string.tag_tax);
        int packSize = (int) txvQuantity.getTag(R.string.tag_pack_size);
        mApplication.order.changeQuantity(id, quantity, productName, price, tax, packSize);

        setViewBgdAndTextColor(quantity == 0, txvQuantity);

    }


    // Helper method for changing price using data passed in as tags.
    private void changePrice(TextView txvPrice, TextView txvQuantity) {

        double originalPrice = Double.parseDouble(txvPrice.getTag(R.string.tag_price).toString());
        double price = Double.parseDouble(txvPrice.getText().toString().substring(1));

        if (mDirectionNegative) { mPriceStep = -mPriceStep; }
        price += mPriceStep;
        if (price < 0) { price = 0d; mDirectionNegative = false; }
        txvPrice.setText(Util.getFormattedPrice(mApplication.getLocale(), price));
        txvQuantity.setTag(R.string.tag_price, price);
        String id = (String) txvQuantity.getTag(R.string.tag_id);
        String productName = (String) txvQuantity.getTag(R.string.tag_product_name);
        int tax = (int) txvQuantity.getTag(R.string.tag_tax);
        int packSize = (int) txvQuantity.getTag(R.string.tag_pack_size);
        mApplication.order.changePrice(id, productName, price, tax, packSize);

        if (price == originalPrice) { mApplication.order.removeProduct(id); }

        setViewBgdAndTextColor(price == originalPrice, txvPrice);
    }


    // Helper method for setting background and text color to order view.
    private void setViewBgdAndTextColor(boolean unchanged, TextView textView) {
        int textColor, bgdColor;
        if (unchanged) {
            textColor = ContextCompat.getColor(mApplication, R.color.colorDarkGray);
            bgdColor = ContextCompat.getColor(mApplication, R.color.colorSmallScrim);
        } else {
            textColor = Color.RED;
            bgdColor = Color.YELLOW;
        }
        textView.setTextColor(textColor);
        textView.setBackgroundColor(bgdColor);
    }


    // Helper method for animating order view clicks.
    private void animateView(final View view) {
        final int initColorId = ((ColorDrawable) view.getBackground()).getColor();
        view.setBackgroundColor(ContextCompat.getColor(mApplication, R.color.colorAccent));
        view.postDelayed(new Runnable() {
            @Override
            public void run() {
                view.setBackgroundColor(initColorId);
            }
        }, 120);
    }


} // End ImageAdapter class.