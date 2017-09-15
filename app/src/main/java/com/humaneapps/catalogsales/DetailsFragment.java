/*
 * Copyright (C) 2017 Vladimir Markovic. All rights reserved.
 */

package com.humaneapps.catalogsales;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.ShareActionProvider;
import android.text.Html;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ScrollView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import com.humaneapps.catalogsales.data.DbContract;

import java.io.File;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Displays product details: image, name, price, tax, pack size, pack price and other. Also has
 * a 'star switch button' for showing and changing if product is in favourites custom list. It
 * can be shown by itself (from DetailsActivity), or side by side with MainFragment in two pane
 * mode (from MainActivity).
 */
public class DetailsFragment extends Fragment {

    // For adding dynamically created other details text views.
    @BindView(R.id.llDetailFragmentLayout)
    LinearLayout mMainLayout;
    // For preserving scroll on rotation.
    @BindView(R.id.scvDetailsRoot)
    ScrollView mScvDetails;
    // Views for displaying the product details.
    @BindView(R.id.flImageDetail)
    FrameLayout mFlImage;
    @BindView(R.id.imvImageDetail)
    ImageView mImvImage;
    @BindView(R.id.txvTitleDetail)
    TextView mTxvProductName;
    @BindView(R.id.txvFavourite)
    TextView mTxvFavourite;
    // Custom object holding product details, passed in from ImageAdapter on click.
    private ReusableProduct mProduct;

    private Boolean mIsFavourite;
    private boolean mTwoPane;
    private int mImageHeight, mFragDetailsWidth;

    private CatalogSales mApplication;

    private ShareActionProvider mShareActionProvider;
    private Intent mShareIntent;


    public DetailsFragment() {}


    public static DetailsFragment newInstance() { return new DetailsFragment(); }


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mApplication = (CatalogSales) context.getApplicationContext();
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }


    @SuppressWarnings("deprecation")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle
            savedInstanceState) {
        // Inflate the layout for this fragment.
        View rootView = inflater.inflate(R.layout.fragment_details, container, false);
        ButterKnife.bind(this, rootView);

        // 'Star switch button' is a TextView with star symbol as text, changing text color to
        // indicate if product is in custom favourites list or not.
        final String STAR_SYMBOL = "&#10029;";
        mTxvFavourite.setText(Html.fromHtml(STAR_SYMBOL));
        mTxvFavourite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { favourite(); }
        });

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mApplication);
        // Get orientation.
        boolean isLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        // Set mode for detecting split screen (in tablet landscape). Two pane layout is shown only
        // in tablet landscape, if two pane mode is turned on in settings.
        mTwoPane = isLandscape && sp.getBoolean(getString(R.string.pref_two_pane), false);

        // Init display metrics for determining screen height for setting max image height.
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        // Set fragment width:
        if (mTwoPane) {
            // In tablet landscape two pane mode set width from resource.
            float fmParts = mApplication.getResources().getInteger(R.integer.parts_fm);
            float fdParts = mApplication.getResources().getInteger(R.integer.parts_fd);
            mFragDetailsWidth = (int) ((displayMetrics.widthPixels / (fmParts + fdParts)) *
                    fdParts);
        } else {
            // In phones fragment width corresponds to the screen width.
            mFragDetailsWidth = displayMetrics.widthPixels;
        }

        // Set image height using passed in toolbar height, as not to set toolbar height here in
        // order not to have to wait for activity to be created.
        Bundle passedArguments = getArguments();
        int toolbarHeight = passedArguments.getInt(Util.ARG_TOOLBAR_HEIGHT);
        mImageHeight = displayMetrics.heightPixels - (int) (displayMetrics.density * 20)
                - toolbarHeight;

        // Shown details are preserved in application level class for saving state on rotation in
        // two pane mode. In that case show saved product details. Otherwise show passed in
        // product details.
        Bundle shownDetails = mApplication.getShownProductDetails();
        if (savedInstanceState != null && shownDetails != null && shownDetails.size() > 0) {
            loadBundle(shownDetails);
        } else {
            loadBundle(passedArguments);
        }

        // Return inflated root view.
        return rootView;

    } // End onCreateView method


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        // Prevent showing share action in tablet portrait after rotation from two pane.
        if (getActivity() instanceof MainActivity && !mTwoPane) { return; }

        // Inflate menu_fragment_detail containing menu item 'share'.
        inflater.inflate(R.menu.menu_fragment_detail, menu);

        // Create ShareActionProvider for share menu option.
        MenuItem shareMenuItem = menu.findItem(R.id.action_share_product);

        mShareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider
                (shareMenuItem);
        // Set intent for created ShareActionProvider
        if (mShareActionProvider != null && mShareIntent != null) {
            mShareActionProvider.setShareIntent(mShareIntent);
        }
    }


    // Save state on rotation of the scroll position.
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(Util.ARG_DETAIL_SCROLL, mScvDetails.getScrollY());
    }


    @Override
    public void onResume() {
        super.onResume();
        // Change title in title bar to indicate showing product details, but only in one pane mode.
        if (!mTwoPane) {
            getActivity().setTitle(mApplication.getString(R.string.title_details));
        }

        // Set intent for created ShareActionProvider
        if (mShareActionProvider != null && mShareIntent != null) {
            mShareActionProvider.setShareIntent(mShareIntent);
        }

    }


    // Extract passed in or saved product details and display them in views.
    private void loadBundle(Bundle bundle) {

        // If ill passed details bundle, notify user that details are unavailable and return.
        if (bundle == null) {
            mTxvProductName.setText(mApplication.getString(R.string.error_details_missing));
            return;
        }
        // Product object is contained as parcelable in bundle; instantiate it.
        mProduct = bundle.getParcelable(Util.ARG_PRODUCT_PARCEL);
        if (mProduct == null) { return; }

        // If cannot show image because images location is not set, notify.
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mApplication);
        String specifiedImagesLocation = sp.getString(getString(R.string.pref_images_location),
                "").trim();

        // Get image path from specified location.
        String imageLocation, shareMessage;
        if ((new File(specifiedImagesLocation)).exists()) {
            imageLocation = Util.getFilePath(Util.addSlashes(specifiedImagesLocation));
            shareMessage = mProduct.getProductName() + "\n\n";
        } else {
            imageLocation = Util.formUrlDir(specifiedImagesLocation);
            shareMessage = mProduct.getProductName() + "\n\n"
                    + imageLocation + mProduct.getImage() + "\n";
        }
        // Set share intent for ShareActionProvider.
        mShareIntent = prepareShareIntent(getString(R.string.share_product_subject), shareMessage);
        if (mShareActionProvider != null) { mShareActionProvider.setShareIntent(mShareIntent); }

        int imvPadding = getResources().getDimensionPixelSize(R.dimen.gap_2l);
        mImvImage.setPadding(imvPadding, imvPadding, imvPadding, imvPadding);

        setImageLayout();

        // Set product name text.
        mTxvProductName.setText(mProduct.getProductName());

        // Determine and set max height for image, such that in portrait image and title together
        // are shown and in landscape only image is shown.
        mTxvProductName.post(new Runnable() {
            @Override
            public void run() {
                boolean isLandscape = getResources().getConfiguration().orientation
                        == Configuration.ORIENTATION_LANDSCAPE;
                if (isLandscape) {
                    mImvImage.setMaxHeight(mImageHeight);
                    mFlImage.setMinimumHeight(mImageHeight);
                } else {
                    int txvProductNameHeight = mTxvProductName.getHeight();
                    mImvImage.setMaxHeight(mImageHeight - txvProductNameHeight);
                    mFlImage.setMinimumHeight(mImageHeight - txvProductNameHeight);
                }
            }
        });

        // Show image from specified location using Glide
        Glide.with(getActivity()).load(imageLocation + mProduct.getImage())
                .override(mFragDetailsWidth, mImageHeight)
                .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                .listener(new RequestListener<String, GlideDrawable>() {

                    @Override
                    public boolean onException(Exception e, String model, Target<GlideDrawable>
                            target, boolean isFirstResource) {
                        // If cannot load image, show only the star favourite button.
                        mFlImage.post(new Runnable() {
                            @Override
                            public void run() {
                                mFlImage.setBackgroundColor(ContextCompat.getColor(mApplication,
                                        R.color.colorLightGray));
                                mFlImage.setMinimumHeight(0);
                                mImvImage.setMaxHeight(0);
                                mImvImage.setPadding(0, 0, 0, 0);
                            }
                        });
                        return false;
                    }


                    @Override
                    public boolean onResourceReady(GlideDrawable resource, String model,
                                                   Target<GlideDrawable> target,
                                                   boolean isFromMemoryCache,
                                                   boolean isFirstResource) { return false; }
                }).into(mImvImage);

        // Determine if product is in favourites and set favourite start button colour accordingly.
        changeFavColor(mIsFavourite = isItFavourite());

        // Set content description to image and star button.
        if (mIsFavourite) {
            mTxvFavourite.setContentDescription(
                    getString(R.string.is_favourite_content_description));
        } else {
            mTxvFavourite.setContentDescription(
                    getString(R.string.not_favourite_content_description));
        }
        mImvImage.setContentDescription(mProduct.getProductName() + " " + getString(R.string
                .image));

        // In two pane, to prevent multiplication of details of all clicked products, remove
        // details from previously shown. Keep top two views found in xml - image and title.
        if (mTwoPane) {
            mMainLayout.removeViews(2, mMainLayout.getChildCount() - 2);
        }

        // Add price and tax details to dynamically added text views.
        if (mProduct.getPrice() > 0) {
            // price
            TextView txvPriceText = makeTextView(1);
            txvPriceText.setText(getString(R.string.detail_unit_price));
            TextView txvPrice = makeTextView(1);
            txvPrice.setGravity(Gravity.END);
            txvPrice.setText(Util.getFormattedPrice(mApplication.getLocale(), mProduct.getPrice()));
            mMainLayout.addView(makeLinearLayout(txvPriceText, txvPrice));
            mMainLayout.addView(makeDivider());
            // tax
            String tax = mProduct.getTax() > 0 ? mProduct.getTax() + "%" : " - ";
            TextView txvTaxText = makeTextView(1);
            txvTaxText.setText(getString(R.string.tax_acronym));
            TextView txvTax = makeTextView(1);
            txvTax.setGravity(Gravity.END);
            txvTax.setText(tax);
            mMainLayout.addView(makeLinearLayout(txvTaxText, txvTax));
            mMainLayout.addView(makeDivider());
        }

        // Add pack size and pack price details to dynamically added text views.
        if (mProduct.getPackSize() > 1) {
            // pack size
            TextView txvPackSizeText = makeTextView(1);
            txvPackSizeText.setText(getString(R.string.detail_pack_size));
            TextView txvPackSize = makeTextView(1);
            txvPackSize.setGravity(Gravity.END);
            txvPackSize.setText(String.valueOf(mProduct.getPackSize()));
            mMainLayout.addView(makeLinearLayout(txvPackSizeText, txvPackSize));
            mMainLayout.addView(makeDivider());
            // pack price
            if (mProduct.getPrice() > 0) {
                TextView txvPackPriceText = makeTextView(1);
                txvPackPriceText.setText(getString(R.string.detail_pack_price));
                TextView txvPackPrice = makeTextView(1);
                txvPackPrice.setGravity(Gravity.END);
                txvPackPrice.setText(Util.getFormattedPrice(mApplication.getLocale(),
                        mProduct.getPackSize() * mProduct.getPrice()));
                mMainLayout.addView(makeLinearLayout(txvPackPriceText, txvPackPrice));
                mMainLayout.addView(makeDivider());
            }
        }

        // Add 'other' (to main) product details to dynamically added text views.

        String textTitle, textValue;
        // For determining space distribution between the title and value texts.
        int lenTitle, lenValue, weightTitle, weightValue;
        int maxLen = 8;

        int otherDetailsColumnsCount = mApplication.getOtherDetailsProductColumnsTitles().length;
        TextView[] txvsOtherDetailsTitles = new TextView[mApplication.getOtherProductColumnCount()];
        TextView[] txvsOtherDetailsValues = new TextView[mApplication.getOtherProductColumnCount()];

        // For all other product details:
        for (int i = 0; i < otherDetailsColumnsCount; i++) {
            // title
            textTitle = mApplication.getOtherDetailsProductColumnsTitles()[i];
            // value
            textValue = mProduct.getOtherDetailsArray()[i];
            // Determine space distribution (if both are long, split in half. If one is short
            // (less than 9 chars) assign to it 1/3 and to other max 2/3 of width and weight 1.
            lenTitle = textTitle.length();
            lenValue = textValue.length();
            weightTitle = lenTitle < maxLen ? 0 : 1;
            weightValue = weightTitle == 1 && lenValue < maxLen ? 0 : 1;
            // Make text view for detail title.
            txvsOtherDetailsTitles[i] = makeTextView(weightTitle);
            txvsOtherDetailsTitles[i].setSingleLine(false);
            txvsOtherDetailsTitles[i].setText(textTitle);
            // Make text view for detail value.
            txvsOtherDetailsValues[i] = makeTextView(weightValue);
            txvsOtherDetailsValues[i].setGravity(Gravity.END);
            txvsOtherDetailsValues[i].setSingleLine(false);
            txvsOtherDetailsValues[i].setText(textValue);
            if (lenTitle < maxLen) {
                txvsOtherDetailsValues[i].setWidth(mFragDetailsWidth * 2 / 3);
            } else if (lenValue < maxLen) {
                txvsOtherDetailsTitles[i].setWidth(mFragDetailsWidth * 2 / 3);
            }
            if (lenValue >= maxLen && lenTitle >= maxLen) {
                txvsOtherDetailsTitles[i].setWidth(mFragDetailsWidth / 2);
                txvsOtherDetailsValues[i].setWidth(mFragDetailsWidth / 2);
            }
            mMainLayout.addView(makeLinearLayout(txvsOtherDetailsTitles[i],
                    txvsOtherDetailsValues[i]));
            if (i < otherDetailsColumnsCount - 1) {
                mMainLayout.addView(makeDivider());
            }

        }

    } // End loadBundle


    // Helper method for making a text view for other product detail title or value.
    private TextView makeTextView(float weight) {
        LayoutParams params;
        TextView textView = new TextView(mApplication);
        params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, weight);
        params.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        textView.setGravity(Gravity.CENTER_VERTICAL);
        int paddingHor = getResources().getDimensionPixelSize(R.dimen.gap_l);
        int paddingVer = getResources().getDimensionPixelSize(R.dimen.gap_l);
        textView.setLayoutParams(params);
        textView.setPadding(paddingHor, paddingVer, paddingHor, paddingVer);
        textView.setTextColor(ContextCompat.getColor(mApplication, R.color.colorTextLight));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R
                .dimen.text_size_m));
        return textView;
    }


    // Helper method for making horizontal line to add after each detail.
    private LinearLayout makeDivider() {
        LinearLayout linearLayout = new LinearLayout(mApplication);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT
                , LayoutParams.WRAP_CONTENT));
        int dividerHeight = getResources().getDimensionPixelSize(R.dimen.gap_1dp);
        int margin = getResources().getDimensionPixelSize(R.dimen.gap_l);
        View dividerView = new View(mApplication);
        dividerView.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, dividerHeight, 1));
        dividerView.setBackgroundColor(ContextCompat.getColor(mApplication, R.color
                .colorTextLight));
        linearLayout.addView(makeMarginView(margin, dividerHeight));
        linearLayout.addView(dividerView);
        linearLayout.addView(makeMarginView(margin, dividerHeight));
        return linearLayout;
    }


    // For making divider line appear with padding. Used instead of adding padding to
    // LinearLayout in order to avoid overdraw.
    private View makeMarginView(int width, int height) {
        View marginView = new View(mApplication);
        marginView.setLayoutParams(new LayoutParams(width, height));
        marginView.setBackgroundColor(ContextCompat.getColor(mApplication, R.color.colorDarkGray));
        return marginView;
    }


    // Helper method for making horizontal linear layout for holding one details title and value
    // text views.
    private LinearLayout makeLinearLayout(TextView textView1, TextView textView2) {
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams
                .WRAP_CONTENT);
        LinearLayout linearLayout = new LinearLayout(getActivity());
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setLayoutParams(params);
        linearLayout.setBackgroundColor(ContextCompat.getColor(mApplication, R.color
                .colorDarkGray));
        linearLayout.addView(textView1);
        linearLayout.addView(textView2);
        return linearLayout;
    }


    // Start AsyncTask to add/remove favourite in db.
    private void favourite() {

        if (mIsFavourite == null) { return; }

        // Set color of the fav star button to correspond to 'favourite' status. Do it at start to
        // make the user feel that change is instantaneous, and revert change on completion
        // if unsuccessful.
        if (mIsFavourite) {
            mTxvFavourite.setTextColor(Color.BLACK);
        } else {
            mTxvFavourite.setTextColor(Color.YELLOW);
        }

        // Start AsyncTask to mark product as favourites in db or remove it if it already is.
        TaskFavourite taskFavourite = new TaskFavourite(mAsyncResponseFavourite,
                mApplication, mProduct.getIdentifier());
        taskFavourite.execute(mIsFavourite);

    } // End favourite method


    // Async Response from TaskFavourite
    private final TaskFavourite.AsyncResponseFavourite mAsyncResponseFavourite =
            new TaskFavourite.AsyncResponseFavourite() {
                @Override
                public void processFinish(Boolean isFavourite) {
                    if (isFavourite != null) {
                        mIsFavourite = isFavourite;
                    } else {
                        // If there was an error, revert changes made in favourite() method.
                        mIsFavourite = isItFavourite();
                        changeFavColor(mIsFavourite);
                        Util.snackOrToast(mApplication, getView(),
                                getString(R.string.error_storing_favourite), Snackbar.LENGTH_SHORT);
                    }
                }
            };


    // Look into db to see if product is marked as favourite.
    private Boolean isItFavourite() {
        Boolean isFavourite = null;
        String idColumn = "[" + mApplication.getProductColumnIdentifier() + "]";
        Cursor cursor = mApplication.getContentResolver().query(DbContract.getProductsTableUri(),
                new String[]{idColumn, getString(R.string.column_name_favourite)},
                idColumn + " = ? AND " + getString(R.string
                        .column_name_favourite) + " = ?",
                new String[]{mProduct.getIdentifier(), "Y"}, null);
        if (cursor != null) {
            isFavourite = cursor.moveToFirst();
            cursor.close();
        }
        return isFavourite;
    }


    // Change fav button star color to correspond to passed isFavourite boolean.
    private void changeFavColor(Boolean isFavourite) {
        if (isFavourite == null) { return; }
        if (isFavourite) {
            mTxvFavourite.setTextColor(Color.YELLOW);
        } else {
            mTxvFavourite.setTextColor(Color.BLACK);
        }
    }


    /* Create and return share intent to share this app */
    private Intent prepareShareIntent(String subject, String message) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        shareIntent.putExtra(Intent.EXTRA_TEXT, message);
        return shareIntent;
    }


    // Used in tablet two pane mode to display product when DetailsFragment is already created.
    void setBundle(Bundle bundle) {
        // show product clicked (via ImageAdapter).
        loadBundle(bundle);
    }


    void setImageLayout() {
        if (mTwoPane && !mApplication.isTakingOrder) {
            // In two pane, set border on image frame layout to separate main and details fragments.
            int padding = getResources().getDimensionPixelSize(R.dimen.details_image_border);
            mFlImage.setPadding(padding, 0, 0, 0);
            mFlImage.setBackground(ContextCompat.getDrawable(mApplication,
                    R.drawable.two_pane_details_border));
        } else {
            mFlImage.setBackgroundColor(ContextCompat.getColor(mApplication,
                    android.R.color.white));
            mFlImage.setPadding(0, 0, 0, 0);
        }
    }


} // End DetailsFragment class
