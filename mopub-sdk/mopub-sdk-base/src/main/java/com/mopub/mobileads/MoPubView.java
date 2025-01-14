// Copyright 2018-2019 Twitter, Inc.
// Licensed under the MoPub SDK License Agreement
// http://www.mopub.com/legal/sdk-license-agreement/

package com.mopub.mobileads;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Point;
import android.location.Location;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;

import com.mopub.common.AdFormat;
import com.mopub.common.AdReport;
import com.mopub.common.MoPub;
import com.mopub.common.logging.MoPubLog;
import com.mopub.common.util.ManifestUtils;
import com.mopub.common.util.Reflection;
import com.mopub.common.util.Visibility;
import com.mopub.mobileads.base.R;
import com.mopub.mobileads.factories.AdViewControllerFactory;

import java.util.Map;
import java.util.TreeMap;

import static com.mopub.common.logging.MoPubLog.AdLogEvent.CLICKED;
import static com.mopub.common.logging.MoPubLog.AdLogEvent.CUSTOM;
import static com.mopub.common.logging.MoPubLog.AdLogEvent.DID_DISAPPEAR;
import static com.mopub.common.logging.MoPubLog.AdLogEvent.LOAD_ATTEMPTED;
import static com.mopub.common.logging.MoPubLog.AdLogEvent.LOAD_FAILED;
import static com.mopub.common.logging.MoPubLog.AdLogEvent.LOAD_SUCCESS;
import static com.mopub.common.logging.MoPubLog.AdLogEvent.SHOW_ATTEMPTED;
import static com.mopub.common.logging.MoPubLog.AdLogEvent.SHOW_FAILED;
import static com.mopub.common.logging.MoPubLog.AdLogEvent.SHOW_SUCCESS;
import static com.mopub.common.logging.MoPubLog.SdkLogEvent.CUSTOM_WITH_THROWABLE;
import static com.mopub.common.logging.MoPubLog.SdkLogEvent.ERROR;
import static com.mopub.mobileads.MoPubErrorCode.ADAPTER_NOT_FOUND;

public class MoPubView extends FrameLayout {
    public interface BannerAdListener {
        public void onBannerShown();
        public void onBannerLoaded(MoPubView banner);
        public void onBannerFailed(MoPubView banner, MoPubErrorCode errorCode);
        public void onBannerClicked(MoPubView banner);
        public void onBannerExpanded(MoPubView banner);
        public void onBannerCollapsed(MoPubView banner);
    }

    /**
     * MoPubAdSizeInt
     *
     * Integer values that represent the possible predefined ad heights in dp.
     */
    interface MoPubAdSizeInt {
        int MATCH_VIEW_INT = -1;
        int HEIGHT_50_INT = 50;
        int HEIGHT_90_INT = 90;
        int HEIGHT_250_INT = 250;
        int HEIGHT_280_INT = 280;
    }

    /**
     * MoPubAdSize
     *
     * These predefined constants are used to specify the desired height for an ad.
     */
    public enum MoPubAdSize implements MoPubAdSizeInt {

        MATCH_VIEW(MATCH_VIEW_INT),
        HEIGHT_50(HEIGHT_50_INT),
        HEIGHT_90(HEIGHT_90_INT),
        HEIGHT_250(HEIGHT_250_INT),
        HEIGHT_280(HEIGHT_280_INT);

        final private int mSizeInt;

        MoPubAdSize(final int sizeInt) {
            this.mSizeInt = sizeInt;
        }

        /**
         * This valueOf overload is used to get the associated the MoPubAdSize enum from an int (likely
         * from XML layout).
         *
         * @param adSizeInt The int value for which the MoPubAdSize is needed.
         * @return The MoPubAdSize associated with the level. Will return CUSTOM by default.
         */
        @NonNull
        public static MoPubAdSize valueOf(final int adSizeInt) {
            switch (adSizeInt) {
                case HEIGHT_50_INT:
                    return HEIGHT_50;
                case HEIGHT_90_INT:
                    return HEIGHT_90;
                case HEIGHT_250_INT:
                    return HEIGHT_250;
                case HEIGHT_280_INT:
                    return HEIGHT_280;
                case MATCH_VIEW_INT:
                default:
                    return MATCH_VIEW;
            }
        }

        public int toInt() {
            return mSizeInt;
        }
    }

    private static final String CUSTOM_EVENT_BANNER_ADAPTER_FACTORY =
            "com.mopub.mobileads.factories.CustomEventBannerAdapterFactory";

    @Nullable
    protected AdViewController mAdViewController;
    // mCustomEventBannerAdapter must be a CustomEventBannerAdapter
    protected Object mCustomEventBannerAdapter;

    private Context mContext;
    private int mScreenVisibility;
    private BroadcastReceiver mScreenStateReceiver;
    private MoPubView.MoPubAdSize mMoPubAdSize;
    private BannerAdListener mBannerAdListener;

    public MoPubView(Context context) {
        this(context, null);
    }

    public MoPubView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mMoPubAdSize = getMoPubAdSizeFromAttributeSet(context, attrs,
                MoPubAdSize.MATCH_VIEW);

        ManifestUtils.checkWebViewActivitiesDeclared(context);

        mContext = context;
        mScreenVisibility = getVisibility();

        setHorizontalScrollBarEnabled(false);
        setVerticalScrollBarEnabled(false);

        mAdViewController = AdViewControllerFactory.create(context, this);
        registerScreenStateBroadcastReceiver();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            setWindowInsets(getRootWindowInsets());
        }
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            setWindowInsets(insets);
        }
        return super.onApplyWindowInsets(insets);
    }

    private void registerScreenStateBroadcastReceiver() {
        mScreenStateReceiver = new BroadcastReceiver() {
            public void onReceive(final Context context, final Intent intent) {
                if (!Visibility.isScreenVisible(mScreenVisibility) || intent == null) {
                    return;
                }

                final String action = intent.getAction();

                if (Intent.ACTION_USER_PRESENT.equals(action)) {
                    setAdVisibility(View.VISIBLE);
                } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    setAdVisibility(View.GONE);
                }
            }
        };

        final IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        mContext.registerReceiver(mScreenStateReceiver, filter);
    }

    private void unregisterScreenStateBroadcastReceiver() {
        try {
            mContext.unregisterReceiver(mScreenStateReceiver);
        } catch (Exception IllegalArgumentException) {
            MoPubLog.log(CUSTOM, "Failed to unregister screen state broadcast receiver (never registered).");
        }
    }

    public void loadAd(final MoPubAdSize moPubAdSize) {
        setAdSize(moPubAdSize);
        loadAd();
    }

    public void loadAd() {
        if (mAdViewController != null) {
            MoPubLog.log(LOAD_ATTEMPTED);
            mAdViewController.setRequestedAdSize(resolveAdSize());
            mAdViewController.loadAd();
        }
    }

    /*
     * Tears down the ad view: no ads will be shown once this method executes. The parent
     * Activity's onDestroy implementation must include a call to this method.
     */
    public void destroy() {
        MoPubLog.log(CUSTOM, "Destroy() called");
        unregisterScreenStateBroadcastReceiver();
        removeAllViews();

        if (mAdViewController != null) {
            mAdViewController.cleanup();
            mAdViewController = null;
        }

        if (mCustomEventBannerAdapter != null) {
            invalidateAdapter();
            mCustomEventBannerAdapter = null;
        }
    }

    private void invalidateAdapter() {
        if (mCustomEventBannerAdapter != null) {
            try {
                new Reflection.MethodBuilder(mCustomEventBannerAdapter, "invalidate")
                        .setAccessible()
                        .execute();
            } catch (Exception e) {
                MoPubLog.log(ERROR, "Error invalidating adapter", e);
            }
        }
    }

    @NonNull
    Integer getAdTimeoutDelay(int defaultValue) {
        if (mAdViewController == null) {
            return defaultValue;
        }
        return mAdViewController.getAdTimeoutDelay(defaultValue);
    }

    protected boolean loadFailUrl(@NonNull final MoPubErrorCode errorCode) {
        if (mAdViewController == null) {
            return false;
        }
        return mAdViewController.loadFailUrl(errorCode);
    }

    protected void loadCustomEvent(String customEventClassName, Map<String, String> serverExtras) {
        if (mAdViewController == null) {
            return;
        }
        if (TextUtils.isEmpty(customEventClassName)) {
            MoPubLog.log(CUSTOM, "Couldn't invoke custom event because the server did not specify one.");
            loadFailUrl(ADAPTER_NOT_FOUND);
            return;
        }

        if (mCustomEventBannerAdapter != null) {
            invalidateAdapter();
        }

        MoPubLog.log(CUSTOM, "Loading custom event adapter.");

        if (Reflection.classFound(CUSTOM_EVENT_BANNER_ADAPTER_FACTORY)) {
            try {
                final Class<?> adapterFactoryClass = Class.forName(CUSTOM_EVENT_BANNER_ADAPTER_FACTORY);
                mCustomEventBannerAdapter = new Reflection.MethodBuilder(null, "create")
                        .setStatic(adapterFactoryClass)
                        .addParam(MoPubView.class, this)
                        .addParam(String.class, customEventClassName)
                        .addParam(Map.class, serverExtras)
                        .addParam(long.class, mAdViewController.getBroadcastIdentifier())
                        .addParam(AdReport.class, mAdViewController.getAdReport())
                        .execute();
                new Reflection.MethodBuilder(mCustomEventBannerAdapter, "loadAd")
                        .setAccessible()
                        .execute();
            } catch (Exception e) {
                MoPubLog.log(ERROR, "Error loading custom event", e);
            }
        } else {
            MoPubLog.log(CUSTOM, "Could not load custom event -- missing banner module");
        }
    }

    protected void registerClick() {
        if (mAdViewController != null) {
            mAdViewController.registerClick();

            // Let any listeners know that an ad was clicked
            adClicked();
        }
    }

    protected void trackNativeImpression() {
        MoPubLog.log(CUSTOM, "Tracking impression. MoPubView internal.");
        if (mAdViewController != null) mAdViewController.trackImpression();
    }

    @Override
    protected void onWindowVisibilityChanged(final int visibility) {
        // Ignore transitions between View.GONE and View.INVISIBLE
        if (Visibility.hasScreenVisibilityChanged(mScreenVisibility, visibility)) {
            mScreenVisibility = visibility;
            setAdVisibility(mScreenVisibility);
        }
    }

    private void setAdVisibility(final int visibility) {
        if (mAdViewController == null) {
            return;
        }

        if (Visibility.isScreenVisible(visibility)) {
            mAdViewController.resumeRefresh();
        } else {
            mAdViewController.pauseRefresh();
        }
    }

    protected void adShown(){
        MoPubLog.log(SHOW_SUCCESS);
        if (mBannerAdListener != null) {
            mBannerAdListener.onBannerShown();
        }
    }

    protected void adLoaded() {
        MoPubLog.log(LOAD_SUCCESS);
        if (mBannerAdListener != null) {
            mBannerAdListener.onBannerLoaded(this);
        }
    }

    protected void adFailed(MoPubErrorCode errorCode) {
        MoPubLog.log(LOAD_FAILED, errorCode.getIntCode(), errorCode);
        if (mBannerAdListener != null) {
            mBannerAdListener.onBannerFailed(this, errorCode);
        }
    }

    protected void adPresentedOverlay() {
        if (mBannerAdListener != null) {
            mBannerAdListener.onBannerExpanded(this);
        }
    }

    protected void adClosed() {
        MoPubLog.log(DID_DISAPPEAR);
        if (mBannerAdListener != null) {
            mBannerAdListener.onBannerCollapsed(this);
        }
    }

    protected void adClicked() {
        MoPubLog.log(CLICKED);
        if (mBannerAdListener != null) {
            mBannerAdListener.onBannerClicked(this);
        }
    }

    protected void creativeDownloaded() {
        if (mAdViewController != null) {
            mAdViewController.creativeDownloadSuccess();
        }
        adLoaded();
    }

    private MoPubAdSize getMoPubAdSizeFromAttributeSet(
            final Context context,
            final AttributeSet attrs,
            MoPubAdSize defaultMoPubAdSize) {
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.MoPubView,
                0, 0);

        MoPubAdSize returnValue = defaultMoPubAdSize;

        try {
            final int moPubAdSizeInt = a.getInteger(R.styleable.MoPubView_moPubAdSize,
                    defaultMoPubAdSize.toInt());
            returnValue = MoPubAdSize.valueOf(moPubAdSizeInt);
        } catch(Resources.NotFoundException rnfe) {
            MoPubLog.log(CUSTOM_WITH_THROWABLE,
                    "Encountered a problem while setting the MoPubAdSize", 
                    rnfe);
        } finally {
            a.recycle();
        }

        return returnValue;
    }

    protected Point resolveAdSize() {
        final Point resolvedAdSize = new Point(getWidth(), getHeight());
        final ViewGroup.LayoutParams layoutParams = getLayoutParams();

        // If WRAP_CONTENT or MATCH_PARENT
        if (layoutParams != null && layoutParams.width < 0) {
            resolvedAdSize.x = ((View) getParent()).getWidth();
        }

        // MoPubAdSize only applies to height
        if (mMoPubAdSize != MoPubAdSize.MATCH_VIEW) {
            resolvedAdSize.y = mMoPubAdSize.toInt();
        } else if (layoutParams != null && layoutParams.height < 0) {
            resolvedAdSize.y = ((View) getParent()).getHeight();
        }

        return resolvedAdSize;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public void setAdUnitId(String adUnitId) {
        if (mAdViewController != null) mAdViewController.setAdUnitId(adUnitId);
    }

    public String getAdUnitId() {
        return (mAdViewController != null) ? mAdViewController.getAdUnitId() : null;
    }

    public void setKeywords(String keywords) {
        if (mAdViewController != null) mAdViewController.setKeywords(keywords);
    }

    public String getKeywords() {
        return (mAdViewController != null) ? mAdViewController.getKeywords() : null;
    }

    public void setUserDataKeywords(String userDataKeywords) {
        if (mAdViewController != null && MoPub.canCollectPersonalInformation()) {
            mAdViewController.setUserDataKeywords(userDataKeywords);
        }
    }

    public String getUserDataKeywords() {
        return (mAdViewController != null && MoPub.canCollectPersonalInformation()) ? mAdViewController.getUserDataKeywords() : null;
    }

    public void setLocation(Location location) {
        if (mAdViewController != null && MoPub.canCollectPersonalInformation()) {
            mAdViewController.setLocation(location);
        }
    }

    public Location getLocation() {
        return (mAdViewController != null && MoPub.canCollectPersonalInformation()) ? mAdViewController.getLocation() : null;
    }

    public int getAdWidth() {
        return (mAdViewController != null) ? mAdViewController.getAdWidth() : 0;
    }

    public int getAdHeight() {
        return (mAdViewController != null) ? mAdViewController.getAdHeight() : 0;
    }

    public Activity getActivity() {
        return (Activity) mContext;
    }

    public void setBannerAdListener(BannerAdListener listener) {
        mBannerAdListener = listener;
    }

    public BannerAdListener getBannerAdListener() {
        return mBannerAdListener;
    }

    public void setLocalExtras(Map<String, Object> localExtras) {
        if (mAdViewController != null) mAdViewController.setLocalExtras(localExtras);
    }

    public Map<String, Object> getLocalExtras() {
        if (mAdViewController != null) {
            return mAdViewController.getLocalExtras();
        }
        return new TreeMap<String, Object>();
    }

    public void setAutorefreshEnabled(boolean enabled) {
        if (mAdViewController != null) {
            mAdViewController.setShouldAllowAutoRefresh(enabled);
        }
    }

    void pauseAutoRefresh() {
        if (mAdViewController != null) {
            mAdViewController.pauseRefresh();
        }
    }

    void resumeAutoRefresh() {
        if (mAdViewController != null) {
            mAdViewController.resumeRefresh();
        }
    }

    void engageOverlay() {
        if (mAdViewController != null) {
            mAdViewController.engageOverlay();
        }
    }

    void dismissOverlay() {
        if (mAdViewController != null) {
            mAdViewController.dismissOverlay();
        }
    }

    public boolean getAutorefreshEnabled() {
        if (mAdViewController != null) return mAdViewController.getCurrentAutoRefreshStatus();
        else {
            MoPubLog.log(CUSTOM, "Can't get autorefresh status for destroyed MoPubView. " +
                    "Returning false.");
            return false;
        }
    }

    public void setAdContentView(View view) {
        MoPubLog.log(SHOW_ATTEMPTED);
        if (mAdViewController != null) {
            mAdViewController.setAdContentView(view);
            MoPubLog.log(SHOW_SUCCESS);
        } else {
            MoPubLog.log(SHOW_FAILED);
        }
    }

    public void setTesting(boolean testing) {
        if (mAdViewController != null) mAdViewController.setTesting(testing);
    }

    public boolean getTesting() {
        if (mAdViewController != null) return mAdViewController.getTesting();
        else {
            MoPubLog.log(CUSTOM, "Can't get testing status for destroyed MoPubView. " +
                    "Returning false.");
            return false;
        }
    }

    public void forceRefresh() {
        if (mCustomEventBannerAdapter != null) {
            invalidateAdapter();
            mCustomEventBannerAdapter = null;
        }

        if (mAdViewController != null) {
            mAdViewController.forceRefresh();
        }
    }

    public void setAdSize(final MoPubAdSize moPubAdSize) {
        mMoPubAdSize = moPubAdSize;
    }

    public MoPubAdSize getAdSize() {
        return mMoPubAdSize;
    }

    void setWindowInsets(@NonNull final WindowInsets windowInsets) {
        if (mAdViewController != null) {
            mAdViewController.setWindowInsets(windowInsets);
        }
    }

    AdViewController getAdViewController() {
        return mAdViewController;
    }

    public AdFormat getAdFormat() {
        return AdFormat.BANNER;
    }

    /**
     * @deprecated As of release 4.4.0
     */
    @Deprecated
    public void setTimeout(int milliseconds) {
    }

    @Deprecated
    public String getResponseString() {
        return null;
    }

    @Deprecated
    public String getClickTrackingUrl() {
        return null;
    }
}
