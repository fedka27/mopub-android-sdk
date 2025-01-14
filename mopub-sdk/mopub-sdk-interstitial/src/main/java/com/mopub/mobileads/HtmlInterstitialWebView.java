// Copyright 2018-2019 Twitter, Inc.
// Licensed under the MoPub SDK License Agreement
// http://www.mopub.com/legal/sdk-license-agreement/

package com.mopub.mobileads;

import android.content.Context;
import android.os.Handler;

import com.mopub.common.AdReport;

import static com.mopub.mobileads.CustomEventInterstitial.CustomEventInterstitialListener;

public class HtmlInterstitialWebView extends BaseHtmlWebView {
    private Handler mHandler;

    public HtmlInterstitialWebView(Context context, AdReport adReport) {
        super(context, adReport);

        mHandler = new Handler();
    }

    public void init(final CustomEventInterstitialListener customEventInterstitialListener, String clickthroughUrl, String dspCreativeId) {
        super.init();

        HtmlInterstitialWebViewListener htmlInterstitialWebViewListener = new HtmlInterstitialWebViewListener(customEventInterstitialListener);
        HtmlWebViewClient htmlWebViewClient = new HtmlWebViewClient(htmlInterstitialWebViewListener, this, clickthroughUrl, dspCreativeId);
        setWebViewClient(htmlWebViewClient);
    }

    private void postHandlerRunnable(Runnable r) {
        mHandler.post(r);
    }

    static class HtmlInterstitialWebViewListener implements HtmlWebViewListener {
        private final CustomEventInterstitialListener mCustomEventInterstitialListener;

        public HtmlInterstitialWebViewListener(CustomEventInterstitialListener customEventInterstitialListener) {
            mCustomEventInterstitialListener = customEventInterstitialListener;
        }

        @Override
        public void onPageFinished() {
            mCustomEventInterstitialListener.onInterstitialShown();
        }

        @Override
        public void onLoaded(BaseHtmlWebView mHtmlWebView) {
            mCustomEventInterstitialListener.onInterstitialLoaded();
        }

        @Override
        public void onFailed(MoPubErrorCode errorCode) {
            mCustomEventInterstitialListener.onInterstitialFailed(errorCode);
        }

        @Override
        public void onClicked() {
            mCustomEventInterstitialListener.onInterstitialClicked();
        }

        @Override
        public void onCollapsed() {
            // Ignored
        }
    }
}
