package com.loteria.katana;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.nativead.AdLoader;
import com.google.android.gms.ads.nativead.MediaView;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;

public class MainActivity extends Activity {

    // ---- IDs reales de AdMob del usuario ----
    private static final String BANNER_AD_UNIT_ID = "ca-app-pub-4168853691867413/6873130724";
    private static final String INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-4168853691867413/6465936613";
    private static final String NATIVE_AD_UNIT_ID = "ca-app-pub-4168853691867413/2526691609";

    private static final int CLICKS_PER_INTERSTITIAL = 5;

    private WebView webView;
    private AdView bannerAdView;

    private InterstitialAd interstitialAd;
    private int clickCount = 0;

    private NativeAd currentNativeAd;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MobileAds.initialize(this, initializationStatus -> {
            // SDK listo; cargamos los formatos de anuncio
            loadInterstitial();
            loadNativeAd();
        });

        // ---- Banner ----
        bannerAdView = findViewById(R.id.adView);
        bannerAdView.loadAd(new AdRequest.Builder().build());

        // ---- WebView con el juego ----
        webView = findViewById(R.id.webview);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setAllowFileAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectClickListeners();
            }
        });
        webView.setWebChromeClient(new WebChromeClient());

        webView.loadUrl("file:///android_asset/loteria.html");
    }

    /**
     * Inyecta JS (sin tocar el archivo loteria.html) que avisa a Android
     * cuando el usuario toca Siguiente / Reiniciar / Play-Pausa,
     * y detecta específicamente cuándo el juego queda en PAUSA.
     */
    private void injectClickListeners() {
        String js =
            "(function(){"
            + "function bind(id){"
            + "  var el=document.getElementById(id);"
            + "  if(!el) return;"
            + "  el.addEventListener('click', function(){"
            + "    if(id==='btnPlayPause'){"
            + "      setTimeout(function(){"
            + "        var iconPause=document.getElementById('iconPause');"
            + "        var pausado = iconPause && iconPause.classList.contains('hidden');"
            + "        if(window.AndroidBridge){"
            + "          if(pausado){ AndroidBridge.onGamePaused(); }"
            + "          else { AndroidBridge.onAction(); }"
            + "        }"
            + "      }, 30);"
            + "    } else if(window.AndroidBridge){"
            + "      AndroidBridge.onAction();"
            + "    }"
            + "  });"
            + "}"
            + "bind('btnSiguiente');"
            + "bind('btnReiniciar');"
            + "bind('btnPlayPause');"
            + "})();";
        webView.evaluateJavascript(js, null);
    }

    // ---------------- Intersticial ----------------

    private void loadInterstitial() {
        InterstitialAd.load(this, INTERSTITIAL_AD_UNIT_ID, new AdRequest.Builder().build(),
            new InterstitialAdLoadCallback() {
                @Override
                public void onAdLoaded(InterstitialAd ad) {
                    interstitialAd = ad;
                    interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                        @Override
                        public void onAdDismissedFullScreenContent() {
                            interstitialAd = null;
                            loadInterstitial();
                        }

                        @Override
                        public void onAdFailedToShowFullScreenContent(com.google.android.gms.ads.AdError adError) {
                            interstitialAd = null;
                            loadInterstitial();
                        }
                    });
                }

                @Override
                public void onAdFailedToLoad(LoadAdError loadAdError) {
                    interstitialAd = null;
                }
            });
    }

    private void registerClickForInterstitial() {
        clickCount++;
        if (clickCount >= CLICKS_PER_INTERSTITIAL) {
            clickCount = 0;
            if (interstitialAd != null) {
                interstitialAd.show(MainActivity.this);
            }
        }
    }

    // ---------------- Nativo avanzado (popup al pausar) ----------------

    private void loadNativeAd() {
        AdLoader adLoader = new AdLoader.Builder(this, NATIVE_AD_UNIT_ID)
            .forNativeAd(nativeAd -> currentNativeAd = nativeAd)
            .withAdListener(new AdListener() {
                @Override
                public void onAdFailedToLoad(LoadAdError adError) {
                    currentNativeAd = null;
                }
            })
            .build();
        adLoader.loadAd(new AdRequest.Builder().build());
    }

    private void showNativeAdDialog() {
        if (currentNativeAd == null) {
            loadNativeAd();
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        View adContent = inflater.inflate(R.layout.native_ad_dialog, null);
        NativeAdView nativeAdView = adContent.findViewById(R.id.native_ad_view);

        TextView headline = adContent.findViewById(R.id.ad_headline);
        TextView body = adContent.findViewById(R.id.ad_body);
        Button cta = adContent.findViewById(R.id.ad_call_to_action);
        ImageView icon = adContent.findViewById(R.id.ad_app_icon);
        MediaView media = adContent.findViewById(R.id.ad_media);

        nativeAdView.setHeadlineView(headline);
        nativeAdView.setBodyView(body);
        nativeAdView.setCallToActionView(cta);
        nativeAdView.setIconView(icon);
        nativeAdView.setMediaView(media);

        headline.setText(currentNativeAd.getHeadline());

        if (currentNativeAd.getBody() != null) {
            body.setText(currentNativeAd.getBody());
            body.setVisibility(View.VISIBLE);
        } else {
            body.setVisibility(View.GONE);
        }

        if (currentNativeAd.getCallToAction() != null) {
            cta.setText(currentNativeAd.getCallToAction());
            cta.setVisibility(View.VISIBLE);
        } else {
            cta.setVisibility(View.GONE);
        }

        if (currentNativeAd.getIcon() != null) {
            icon.setImageDrawable(currentNativeAd.getIcon().getDrawable());
            icon.setVisibility(View.VISIBLE);
        } else {
            icon.setVisibility(View.GONE);
        }

        nativeAdView.setNativeAd(currentNativeAd);

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(adContent);
        dialog.setCancelable(true);
        dialog.show();

        // Precargamos el siguiente nativo para la próxima pausa
        currentNativeAd = null;
        loadNativeAd();
    }

    // ---------------- Puente JS <-> Android ----------------

    private class AndroidBridge {
        @JavascriptInterface
        public void onAction() {
            runOnUiThread(MainActivity.this::registerClickForInterstitial);
        }

        @JavascriptInterface
        public void onGamePaused() {
            runOnUiThread(() -> {
                registerClickForInterstitial();
                showNativeAdDialog();
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (bannerAdView != null) bannerAdView.destroy();
        super.onDestroy();
    }
}
