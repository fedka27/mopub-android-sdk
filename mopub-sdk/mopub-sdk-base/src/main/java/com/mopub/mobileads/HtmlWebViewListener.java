// Copyright 2018-2019 Twitter, Inc.
// Licensed under the MoPub SDK License Agreement
// http://www.mopub.com/legal/sdk-license-agreement/

package com.mopub.mobileads;

public interface HtmlWebViewListener {
    void onPageFinished();
    void onLoaded(BaseHtmlWebView mHtmlWebView);
    void onFailed(MoPubErrorCode unspecified);
    void onClicked();
    void onCollapsed();
}
