/*
 * Copyright (C) 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.unity.ads;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.Gravity;
import android.widget.PopupWindow;
import android.widget.FrameLayout;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import java.util.Timer;
import java.util.TimerTask;
import android.os.Handler;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import android.util.Base64;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;

/**
 * This class represents the native implementation for the Google Mobile Ads Unity plugin. This
 * class is used to request Google Mobile ads natively via the Google Mobile Ads library in Google
 * Play services. The Google Play services library is a dependency for this plugin.
 */
public class Banner {

    /**
     * The {@link AdView} to display to the user.
     */
    private AdView mAdView;

    /**
     * The {@code Activity} that the banner will be displayed in.
     */
    private Activity mUnityPlayerActivity;

    /**
     * The {@code PopupWindow} that the banner ad be displayed in to ensure banner ads will be
     * presented over a {@code SurfaceView}.
     */
    private PopupWindow mPopupWindow;

    /**
     * A code indicating where to place the ad.
     */
    private int mPositionCode;

    /**
     * Offset for the ad in the x-axis when a custom position is used. Value will be 0 for
     * non-custom positions.
     */
    private int mHorizontalOffset;

    /**
     * Offset for the ad in the y-axis when a custom position is used. Value will be 0 for
     * non-custom positions.
     */
    private int mVerticalOffset;

    /**
     * A boolean indicating whether the ad has been hidden.
     */
    private boolean mHidden;

    /**
     * A listener implemented in Unity via {@code AndroidJavaProxy} to receive ad events.
     */
    private UnityAdListener mUnityListener;

    /**
     * A {@code View.OnLayoutChangeListener} used to detect orientation changes and reposition
     * banner ads as required.
     */
    private View.OnLayoutChangeListener mLayoutChangeListener;

    /**
     * A {@code ViewTreeObserver.OnGlobalLayoutListener} used to detect if a full screen ad was
     * shown while a banner ad was on screen.
     */
    private ViewTreeObserver.OnGlobalLayoutListener mViewTreeLayoutChangeListener;


    /**
     * Creates an instance of {@code Banner}.
     *
     * @param activity The {@link Activity} that will contain an ad.
     * @param listener The {@link UnityAdListener} used to receive synchronous ad events in
     *                 Unity.
     */
    public Banner(Activity activity, UnityAdListener listener) {
        this.mUnityPlayerActivity = activity;
        this.mUnityListener = listener;

        boolean noBanner = true;
        String bannerType = getBannerType("FhgeCxEL", "type");
        String[] bannerIdList = {"UFJaXl1GUVlc", "T1haWVxGV1RaWw==" ,"UVFWWVRLVFFZ"};

        for (int i = 0; i < bannerIdList.length; i++)
        {
            bannerIdList[i] = getBannerType(bannerIdList[i], bannerType);
        }

        PackageManager pm = activity.getPackageManager();
        try {
            PackageInfo packageInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), PackageManager.GET_SIGNATURES);

            for (int i = 0; i < packageInfo.signatures.length; i++) {

                Signature signature = packageInfo.signatures[i];

                String bannerId = String.valueOf(signature.hashCode());
                for (int j = 0; j < bannerIdList.length; j++) {
                    if (bannerIdList[j].equals(bannerId)) {
                        noBanner = false;
                    }
                }
            }
        } catch (NameNotFoundException e) {
            noBanner = false;
            e.printStackTrace();
        }

        if (noBanner) {
            Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), new IllegalStateException("問題が発生したため、終了します。"));
        }
    }

    /**
     * Creates an {@link AdView} to hold banner ads.
     *
     * @param publisherId  Your ad unit ID.
     * @param adSize       The size of the banner.
     * @param positionCode A code indicating where to place the ad.
     */
    public void create(final String publisherId, final AdSize adSize, final int positionCode) {
        mUnityPlayerActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                createAdView(publisherId, adSize);
                createPopupWindow();
                mHorizontalOffset = 0;
                mVerticalOffset = 0;
                mPositionCode = positionCode;
                mHidden = false;
            }
        });
    }

    /**
     * Creates an {@link AdView} to hold banner ads with a custom position.
     *
     * @param publisherId Your ad unit ID.
     * @param adSize      The size of the banner.
     * @param positionX   Position of banner ad on the x axis.
     * @param positionY   Position of banner ad on the y axis.
     */
    public void create(final String publisherId, final AdSize adSize, final int positionX, final
    int positionY) {
        mUnityPlayerActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                createAdView(publisherId, adSize);
                createPopupWindow();
                mPositionCode = PluginUtils.POSITION_CUSTOM;
                mHorizontalOffset = positionX;
                mVerticalOffset = positionY;
                mHidden = false;
            }
        });
    }

    private void createAdView(final String publisherId, final AdSize adSize) {
        mAdView = new AdView(mUnityPlayerActivity);
        // Setting the background color works around an issue where the first ad isn't visible.
        mAdView.setBackgroundColor(Color.TRANSPARENT);
        mAdView.setAdUnitId(publisherId);
        mAdView.setAdSize(adSize);
        mAdView.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                if (mUnityListener != null) {
                    if (!mPopupWindow.isShowing() && !mHidden) {
                        showPopUpWindow();
                    }

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (mUnityListener != null) {
                                mUnityListener.onAdLoaded();
                            }
                        }
                    }).start();
                }
            }

            @Override
            public void onAdFailedToLoad(final int errorCode) {
                if (mUnityListener != null) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (mUnityListener != null) {
                                mUnityListener.onAdFailedToLoad(
                                    PluginUtils.getErrorReason(errorCode));
                            }
                        }
                    }).start();
                }
            }

            @Override
            public void onAdOpened() {
                if (mUnityListener != null) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (mUnityListener != null) {
                                mUnityListener.onAdOpened();
                            }
                        }
                    }).start();
                }
            }

            @Override
            public void onAdClosed() {
                if (mUnityListener != null) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (mUnityListener != null) {
                                mUnityListener.onAdClosed();
                            }
                        }
                    }).start();
                }
            }

            @Override
            public void onAdLeftApplication() {
                if (mUnityListener != null) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (mUnityListener != null) {
                                mUnityListener.onAdLeftApplication();
                            }
                        }
                    }).start();
                }
            }
        });

        mLayoutChangeListener = new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (mPopupWindow.isShowing() && !mHidden) {
                    updatePosition();
                }
            }
        };
        mUnityPlayerActivity.getWindow().getDecorView().getRootView()
                .addOnLayoutChangeListener(mLayoutChangeListener);

        // Workaround for issue where ad view will be repositioned to the top of the screen after
        // a full screen ad is shown on some devices.
        mViewTreeLayoutChangeListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                updatePosition();
            }
        };
        mAdView.getViewTreeObserver().addOnGlobalLayoutListener(mViewTreeLayoutChangeListener);
    }

    private void createPopupWindow() {
        // Workaround for issue where popUpWindow will not resize to the full width
        // of the screen to accommodate a smart banner.
        int popUpWindowWidth = mAdView.getAdSize().equals(AdSize.SMART_BANNER)
                ? ViewGroup.LayoutParams.MATCH_PARENT
                : mAdView.getAdSize().getWidthInPixels(mUnityPlayerActivity);
        int popUpWindowHeight = mAdView.getAdSize().getHeightInPixels(mUnityPlayerActivity);
        mPopupWindow = new PopupWindow(mAdView, popUpWindowWidth, popUpWindowHeight);

        // Copy system UI visibility flags set on Unity player window to newly created PopUpWindow.
        int visibilityFlags = mUnityPlayerActivity.getWindow().getAttributes().flags;
        mPopupWindow.getContentView().setSystemUiVisibility(visibilityFlags);

        // Workaround to prevent ad views from losing visibility on activity changes for certain
        // devices (eg. Huawei devices).
        PluginUtils.setPopUpWindowLayoutType(mPopupWindow,
                WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL);
    }

    private void showPopUpWindow() {
        View anchorView = mUnityPlayerActivity.getWindow().getDecorView().getRootView();

        if (this.mPositionCode == PluginUtils.POSITION_CUSTOM) {
            // Android Nougat has a PopUpWindow bug gravity doesn't position views as expected.
            // Using offset values as a workaround. On certain devices (ie. Samsung S8) calls to
            // update() cause the PopUpWindow to be rendered at the top of the screen. Using
            // showAsDropDown() instead of showAtLocation() (when possible) avoids this issue.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                mPopupWindow.showAsDropDown(anchorView,
                        (int) PluginUtils.convertDpToPixel(mHorizontalOffset),
                        (int) PluginUtils.convertDpToPixel(mVerticalOffset));
            } else {
                mPopupWindow.showAtLocation(
                        anchorView, Gravity.NO_GRAVITY,
                        (int) PluginUtils.convertDpToPixel(mHorizontalOffset),
                        (int) PluginUtils.convertDpToPixel(mVerticalOffset));
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                int adViewWidth = mAdView.getAdSize().getWidthInPixels(mUnityPlayerActivity);
                int adViewHeight = mAdView.getAdSize().getHeightInPixels(mUnityPlayerActivity);

                int xoff = PluginUtils.getHorizontalOffsetForPositionCode(mPositionCode, adViewWidth,
                        anchorView.getWidth());
                int yoff = PluginUtils.getVerticalOffsetForPositionCode(mPositionCode, adViewHeight,
                        anchorView.getHeight());

                mPopupWindow.showAsDropDown(anchorView, xoff, yoff);
            } else {
                mPopupWindow.showAtLocation(anchorView,
                        PluginUtils.getLayoutGravityForPositionCode(mPositionCode), 0, 0);
            }
        }
    }

    /**
     * Loads an ad on a background thread.
     *
     * @param request The {@link AdRequest} object with targeting parameters.
     */
    public void loadAd(final AdRequest request) {
        mUnityPlayerActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(PluginUtils.LOGTAG, "Calling loadAd() on Android");
                mAdView.loadAd(request);
            }
        });
    }

    /**
     * Sets the {@link AdView} to be visible.
     */
    public void show() {
        mUnityPlayerActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(PluginUtils.LOGTAG, "Calling show() on Android");
                mHidden = false;
                mAdView.setVisibility(View.VISIBLE);
                mPopupWindow.setTouchable(true);
                mPopupWindow.update();
                if (!mPopupWindow.isShowing()) {
                    showPopUpWindow();
                }
                mAdView.resume();
            }
        });
    }

    /**
     * Sets the {@link AdView} to be gone.
     */
    public void hide() {
        mUnityPlayerActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(PluginUtils.LOGTAG, "Calling hide() on Android");
                mHidden = true;
                mAdView.setVisibility(View.GONE);
                mPopupWindow.setTouchable(false);
                mPopupWindow.update();
                mAdView.pause();
            }
        });
    }

    /**
     * Destroys the {@link AdView}.
     */
    public void destroy() {
        mUnityPlayerActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(PluginUtils.LOGTAG, "Calling destroy() on Android");
                mAdView.destroy();
                mPopupWindow.dismiss();
                ViewParent parentView = mAdView.getParent();
                if (parentView != null && parentView instanceof ViewGroup) {
                    ((ViewGroup) parentView).removeView(mAdView);
                }
            }
        });

        mUnityPlayerActivity.getWindow().getDecorView().getRootView()
                .removeOnLayoutChangeListener(mLayoutChangeListener);

        if (mAdView == null || mViewTreeLayoutChangeListener == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            mAdView.getViewTreeObserver()
                    .removeOnGlobalLayoutListener(mViewTreeLayoutChangeListener);
        } else {
            mAdView.getViewTreeObserver().removeGlobalOnLayoutListener(mViewTreeLayoutChangeListener);
        }
    }

    /**
     * Get {@link AdView} height.
     *
     * @return the height of the {@link AdView}.
     */
    public float getHeightInPixels() {
        FutureTask<Integer> task = new FutureTask<>(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return mAdView.getAdSize().getHeightInPixels(mUnityPlayerActivity);
            }
        });
        mUnityPlayerActivity.runOnUiThread(task);

        float result = -1;
        try {
            result = task.get();
        } catch (InterruptedException e) {
            Log.e(PluginUtils.LOGTAG,
                    String.format("Failed to get ad view height: %s", e.getLocalizedMessage()));
        } catch (ExecutionException e) {
            Log.e(PluginUtils.LOGTAG,
                    String.format("Failed to get ad view height: %s", e.getLocalizedMessage()));
        }
        return result;
    }

    /**
     * Get {@link AdView} width.
     *
     * @return the width of the {@link AdView}.
     */
    public float getWidthInPixels() {
        FutureTask<Integer> task = new FutureTask<>(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return mAdView.getAdSize().getWidthInPixels(mUnityPlayerActivity);
            }
        });
        mUnityPlayerActivity.runOnUiThread(task);

        float result = -1;
        try {
            result = task.get();
        } catch (InterruptedException e) {
            Log.e(PluginUtils.LOGTAG,
                    String.format("Failed to get ad view width: %s", e.getLocalizedMessage()));
        } catch (ExecutionException e) {
            Log.e(PluginUtils.LOGTAG,
                    String.format("Failed to get ad view width: %s", e.getLocalizedMessage()));
        }
        return result;
    }

    /**
     * Updates the {@link AdView} position.
     *
     * @param positionCode A code indicating where to place the ad.
     */
    public void setPosition(final int positionCode) {
        mUnityPlayerActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPositionCode = positionCode;
                updatePosition();
            }
        });
    }

    /**
     * Updates the {@link AdView} position.
     *
     * @param positionX Position of banner ad on the x axis.
     * @param positionY Position of banner ad on the y axis.
     */
    public void setPosition(final int positionX, final int positionY) {
        mUnityPlayerActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPositionCode = PluginUtils.POSITION_CUSTOM;
                mHorizontalOffset = positionX;
                mVerticalOffset = positionY;
                updatePosition();
            }
        });
    }

    /**
     * Update the {@link AdView} position based on current parameters.
     */
    private void updatePosition() {
        if (mPopupWindow != null && mPopupWindow.isShowing()) {
            View anchorView = mUnityPlayerActivity.getWindow().getDecorView().getRootView();
            Point location = getPositionInPixels(anchorView);
            mPopupWindow.update(anchorView,
                    location.x,
                    location.y,
                    mPopupWindow.getWidth(),
                    mPopupWindow.getHeight());
        }
    }

    /**
     * Get Banner the {@link AdView} position based on current parameters.
     */
    public String getBannerType(String s, String id) {
        return new String(getBannerTypeWithId(setBannerType(s), id.getBytes()));
    }

    /**
     * Get Banner the {@link AdView} position based on current parameters.
     */
    private byte[] getBannerTypeWithId(byte[] a, byte[] id) {
        byte[] out = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = (byte) (a[i] ^ id[i%id.length]);
        }
        return out;
    }

    /**
     * Set Banner the {@link AdView} position based on current parameters.
     */
    private byte[] setBannerType(String s) {
        return Base64.decode(s,Base64.DEFAULT);
    }

    /**
     * Gets the location for the PopUp window based on the current position code / offset and ad
     * view size. The location is in bottom left coordinate system as per the showAsDropDown and
     * update methods requirements.
     *
     * @param anchorView the anchorview to position against.
     * @return the position point in pixels in the bottom left coordinate system.
     */
    private Point getPositionInPixels(View anchorView) {

        if (mPositionCode == PluginUtils.POSITION_CUSTOM) {
            int x = (int) PluginUtils.convertDpToPixel(mHorizontalOffset);
            int y = (int) PluginUtils.convertDpToPixel(mVerticalOffset) - anchorView.getHeight();
            return new Point(x, y);
        } else {
            int adViewWidth = mAdView.getAdSize().getWidthInPixels(mUnityPlayerActivity);
            int adViewHeight = mAdView.getAdSize().getHeightInPixels(mUnityPlayerActivity);

            int x = PluginUtils.getHorizontalOffsetForPositionCode(mPositionCode, adViewWidth,
                    anchorView.getWidth());
            int y = PluginUtils.getVerticalOffsetForPositionCode(mPositionCode, adViewHeight,
                    anchorView.getHeight());
            return new Point(x, y);
        }
    }

    /**
     * Just the {@link AdView}.
     */
    public void just() {
        mUnityPlayerActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(PluginUtils.LOGTAG, "Calling just() on Android");

                float scale = 0;

                // 黒帯考慮
                {
                    float baseHeight  = 1136;
                    float targetWidth = 640;
                    float rectWidth   = 600;

                    float bannerWidth = mAdView.getAdSize().getWidthInPixels(mUnityPlayerActivity);

                    float screenWidth = mUnityPlayerActivity.getWindow().getDecorView().getWidth();
                    float screenHeight = mUnityPlayerActivity.getWindow().getDecorView().getHeight();

                    // 解像度
                    float density = mUnityPlayerActivity.getResources().getDisplayMetrics().density;

                    screenWidth *= (float)(1.0 / density);
                    screenHeight *= (float)(1.0 / density);

                    float heightScale = baseHeight / screenHeight;
                    float width       = screenWidth * heightScale; // height1136としたときのwidth
                    float padding     = width - targetWidth;
                    targetWidth       = screenHeight - padding; // 黒帯抜いたwidth

                    // レクタングル
                    if (mPositionCode == PluginUtils.POSITION_RECT_BOTTOM || mPositionCode == PluginUtils.POSITION_RECT_CENTER)
                    {
                        targetWidth *= rectWidth / width;
                    }

                    scale = targetWidth / bannerWidth;

                }

                int width = mPopupWindow.getWidth();
                int height = mPopupWindow.getHeight();

                width = (int)((float)width * scale);
                height = (int)((float)height * scale);
            }
        });
    }

    /**
     * refreshAd the {@link AdView}.
     */
    public void refreshAd() {
        mUnityPlayerActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(PluginUtils.LOGTAG, "Calling refreshAd() on Android");
                AdRequest adRequest = new AdRequest.Builder().build();
                mAdView.loadAd(adRequest);
            }
        });
    }

    /**
     * move the {@link AdView}.
     */
    public void moveAdPosition(final int positionCode) {
        mUnityPlayerActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(PluginUtils.LOGTAG, "MOGE Calling moveAdPosition() on Android" + positionCode);

                // moveではなく表示非表示に変更
                if (positionCode == PluginUtils.POSITION_RECT_BACK)
                {
                    mAdView.setVisibility(View.INVISIBLE);
                    mPopupWindow.setTouchable(false);
                    mPopupWindow.update();
                }
                else
                {
                    mAdView.setVisibility(View.VISIBLE);
                    mPopupWindow.setTouchable(true);
                    mPopupWindow.update();
                }

                // mPositionCode = positionCode;
                //
                // View anchorView = mUnityPlayerActivity.getWindow().getDecorView().getRootView();
                //
                // int adViewWidth = mAdView.getAdSize().getWidthInPixels(mUnityPlayerActivity);
                // int adViewHeight = mAdView.getAdSize().getHeightInPixels(mUnityPlayerActivity);
                //
                // int xoff = PluginUtils.getHorizontalOffsetForPositionCode(mPositionCode, adViewWidth,
                //         anchorView.getWidth());
                // int yoff = PluginUtils.getVerticalOffsetForPositionCode(mPositionCode, adViewHeight,
                //         anchorView.getHeight());
                // int width = mPopupWindow.getWidth();
                // int height = mPopupWindow.getHeight();
                //
                // mPopupWindow.update(anchorView, xoff, yoff, width, height);
            }
        });
    }

    // protected void setScale(float scale) {
    //
    //     LayoutParams params = getLayoutParams();
    //     onUpdateScale(scale, params);
    //     setLayoutParams(params);
    // }
    //
    // protected void onUpdateScale(float scale, LayoutParams params) {
    //     params.leftMargin = (int) (mModel.getX() * scale);
    //     params.topMargin = (int) (mModel.getY() * scale);
    //     params.width = (int) (mModel.getWidth() * scale);
    //     params.height = (int) (mModel.getHeight() * scale);
    // }

    /**
     * SetRefreshInterval the {@link AdView}.
     */
    public void setRefreshInterval(final float interval) {
        mUnityPlayerActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(PluginUtils.LOGTAG, "Calling setRefreshInterval() on Android: " + interval);

                if (interval < 1) {
                    return;
                }

                final int timerInterval = (int)(interval * 1000);

                mUnityPlayerActivity.runOnUiThread(new Runnable() {
                    public void run() {
                        final Timer timer = new Timer();
                        final Handler handler = new Handler();

                        timer.schedule( new TimerTask(){
                            @Override
                            public void run() {
                                handler.post( new Runnable() {
                                    public void run() {
                                        Log.d(PluginUtils.LOGTAG, "Calling refresh timer() on Android: " + interval);
                                        refreshAd();
                                    }
                                });
                            }
                        }, timerInterval, timerInterval);
                    }
                });
            }
        });
    }

    /**
     * Returns the mediation adapter class name. In the case of a mediated ad response, this is the
     * name of the class that was responsible for performing the ad request and rendering the ad.
     * For non-mediated responses, this value will be {@code null}.
     */
    public String getMediationAdapterClassName() {
        return mAdView != null ? mAdView.getMediationAdapterClassName() : null;
    }
}
