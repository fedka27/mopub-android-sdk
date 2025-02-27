// Copyright 2018-2019 Twitter, Inc.
// Licensed under the MoPub SDK License Agreement
// http://www.mopub.com/legal/sdk-license-agreement/

package com.mopub.mobileads;

import android.content.Context;

import com.mopub.common.AdReport;

import static com.mopub.mobileads.CustomEventBanner.CustomEventBannerListener;

public class HtmlBannerWebView extends BaseHtmlWebView {
    public static final String EXTRA_AD_CLICK_DATA = "com.mopub.intent.extra.AD_CLICK_DATA";

    public HtmlBannerWebView(Context context, AdReport adReport) {
        super(context, adReport);
    }

    public void init(CustomEventBannerListener customEventBannerListener, String clickthroughUrl, String dspCreativeId) {
        super.init();

        setWebViewClient(new HtmlWebViewClient(new HtmlBannerWebViewListener(customEventBannerListener), this, clickthroughUrl, dspCreativeId));
    }

    static class HtmlBannerWebViewListener implements HtmlWebViewListener {
        private final CustomEventBannerListener mCustomEventBannerListener;
        private boolean isLoaded = false;
        private boolean isPageFinished = false;

        public HtmlBannerWebViewListener(CustomEventBannerListener customEventBannerListener) {
            mCustomEventBannerListener = customEventBannerListener;
        }

        @Override
        public void onPageFinished() {
            isPageFinished = true;
            if (isLoaded) {
                mCustomEventBannerListener.onBannerShown();
            }
        }

        @Override
        public void onLoaded(BaseHtmlWebView htmlWebView) {
            isLoaded = true;
            mCustomEventBannerListener.onBannerLoaded(htmlWebView);
            if (isPageFinished){
                mCustomEventBannerListener.onBannerShown();
            }
        }

        @Override
        public void onFailed(MoPubErrorCode errorCode) {
            mCustomEventBannerListener.onBannerFailed(errorCode);
        }

        @Override
        public void onClicked() {
            mCustomEventBannerListener.onBannerClicked();
        }

        @Override
        public void onCollapsed() {
            mCustomEventBannerListener.onBannerCollapsed();
        }

    }
}
