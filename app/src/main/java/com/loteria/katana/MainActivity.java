package com.loteria.katana;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
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

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.nativead.MediaView;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;

import java.util.Locale;

public class MainActivity extends Activity {

    // ---- IDs reales de AdMob del usuario ----
    private static final String BANNER_AD_UNIT_ID = "ca-app-pub-4168853691867413/6873130724";
    private static final String APP_OPEN_AD_UNIT_ID = "ca-app-pub-4168853691867413/5766902342";
    private static final String INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-4168853691867413/1925794489";
    private static final String NATIVE_AD_UNIT_ID = "ca-app-pub-4168853691867413/4663638461";

    private static final int CLICKS_PER_INTERSTITIAL = 5;

    private WebView webView;
    private AdView bannerAdView;
    private TextToSpeech textToSpeech;

    private InterstitialAd interstitialAd;
    private int clickCount = 0;

    private NativeAd currentNativeAd;

    private AppOpenAd appOpenAd;
    private boolean isShowingAppOpenAd = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MobileAds.initialize(this, initializationStatus -> {
            // SDK listo; cargamos todos los formatos de anuncio
            loadInterstitial();
            loadNativeAd();
            loadAppOpenAd();
        });

        // ---- Voz nativa de Android (reemplaza al speechSynthesis del WebView) ----
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS && textToSpeech != null) {
                int resultado = textToSpeech.setLanguage(new Locale("es", "MX"));
                if (resultado == TextToSpeech.LANG_MISSING_DATA || resultado == TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech.setLanguage(Locale.forLanguageTag("es"));
                }
            }
        });
        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) { }

            @Override
            public void onDone(String utteranceId) {
                runOnUiThread(() -> webView.evaluateJavascript("window.__ttsOnDone && window.__ttsOnDone();", null));
            }

            @Override
            public void onError(String utteranceId) {
                runOnUiThread(() -> webView.evaluateJavascript("window.__ttsOnDone && window.__ttsOnDone();", null));
            }
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
                injectPuente();
            }
        });
        webView.setWebChromeClient(new WebChromeClient());

        webView.loadUrl("file:///android_asset/loteria.html");
    }

    /**
     * Inyecta JS (sin tocar el archivo loteria.html) que:
     * 1) Redirige las llamadas a speechSynthesis del juego hacia el
     *    TextToSpeech nativo de Android (para que SÍ se escuche la voz).
     * 2) Avisa a Android cuando el usuario toca Siguiente / Reiniciar / Play-Pausa,
     *    y detecta específicamente cuándo el juego queda en PAUSA.
     */
    private void injectPuente() {
        String js =
            "(function(){"
            + "if (window.speechSynthesis) {"
            + "  window.speechSynthesis.speak = function(utterance) {"
            + "    window.__pendingUtterance = utterance;"
            + "    if (window.AndroidBridge && AndroidBridge.speak) {"
            + "      AndroidBridge.speak(utterance.text || '');"
            + "    }"
            + "  };"
            + "  window.speechSynthesis.cancel = function() {"
            + "    if (window.AndroidBridge && AndroidBridge.stopSpeaking) { AndroidBridge.stopSpeaking(); }"
            + "  };"
            + "  window.speechSynthesis.getVoices = function() { return []; };"
            + "}"
            + "window.SpeechSynthesisUtterance = function(text) {"
            + "  this.text = text; this.lang = ''; this.rate = 1; this.pitch = 1;"
            + "  this.voice = null; this.onend = null; this.onerror = null;"
            + "};"
            + "window.__ttsOnDone = function() {"
            + "  if (window.__pendingUtterance && typeof window.__pendingUtterance.onend === 'function') {"
            + "    window.__pendingUtterance.onend();"
            + "  }"
            + "};"
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

    // ---------------- Apertura de app ----------------

    private void loadAppOpenAd() {
        AppOpenAd.load(this, APP_OPEN_AD_UNIT_ID, new AdRequest.Builder().build(),
            new AppOpenAd.AppOpenAdLoadCallback() {
                @Override
                public void onAdLoaded(AppOpenAd ad) {
                    appOpenAd = ad;
                    mostrarAppOpenAdSiDisponible();
                }

                @Override
                public void onAdFailedToLoad(LoadAdError loadAdError) {
                    appOpenAd = null;
                }
            });
    }

    private void mostrarAppOpenAdSiDisponible() {
        if (isShowingAppOpenAd || appOpenAd == null) return;

        appOpenAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                appOpenAd = null;
                isShowingAppOpenAd = false;
            }

            @Override
            public void onAdFailedToShowFullScreenContent(AdError adError) {
                appOpenAd = null;
                isShowingAppOpenAd = false;
            }

            @Override
            public void onAdShowedFullScreenContent() {
                isShowingAppOpenAd = true;
            }
        });
        appOpenAd.show(this);
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
                        public void onAdFailedToShowFullScreenContent(AdError adError) {
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

        @JavascriptInterface
        public void speak(String texto) {
            runOnUiThread(() -> {
                if (textToSpeech != null && texto != null && !texto.isEmpty()) {
                    textToSpeech.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "loteria_tts");
                }
            });
        }

        @JavascriptInterface
        public void stopSpeaking() {
            runOnUiThread(() -> {
                if (textToSpeech != null) {
                    textToSpeech.stop();
                }
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
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}
