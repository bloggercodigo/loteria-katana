package com.loterias.katana;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.LinearInterpolator;
import android.widget.ProgressBar;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.appopen.AppOpenAd;

public class SplashActivity extends Activity {

    private static final String APP_OPEN_AD_UNIT_ID = "ca-app-pub-4168853691867413/9747679843";

    // Tiempo máximo de espera por si no hay internet o el anuncio no llega a tiempo.
    // Pasado este tiempo, se continúa al juego de todas formas.
    private static final long TIEMPO_MAXIMO_ESPERA_MS = 4500;

    private ObjectAnimator animacionBarra;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean yaContinuo = false;
    private AppOpenAd appOpenAd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ProgressBar barra = findViewById(R.id.splashProgress);
        animacionBarra = ObjectAnimator.ofInt(barra, "progress", 0, 100);
        animacionBarra.setDuration(900);
        animacionBarra.setInterpolator(new LinearInterpolator());
        animacionBarra.setRepeatCount(ValueAnimator.INFINITE);
        animacionBarra.setRepeatMode(ValueAnimator.RESTART);
        animacionBarra.start();

        // Respaldo de seguridad: si el anuncio tarda demasiado o falla,
        // igual continuamos al juego (nunca se queda atorado).
        handler.postDelayed(this::continuarAlJuego, TIEMPO_MAXIMO_ESPERA_MS);

        MobileAds.initialize(this, initStatus ->
            AppOpenAd.load(this, APP_OPEN_AD_UNIT_ID, new AdRequest.Builder().build(),
                new AppOpenAd.AppOpenAdLoadCallback() {
                    @Override
                    public void onAdLoaded(AppOpenAd ad) {
                        appOpenAd = ad;
                        mostrarAnuncio();
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError loadAdError) {
                        continuarAlJuego();
                    }
                })
        );
    }

    private void mostrarAnuncio() {
        if (appOpenAd == null) {
            continuarAlJuego();
            return;
        }
        appOpenAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdShowedFullScreenContent() {
                // El anuncio ya se está mostrando de verdad: cancelamos el
                // respaldo de tiempo para que el usuario pueda tomarse el
                // tiempo que necesite y lo cierre él mismo (tocando "Continuar").
                handler.removeCallbacksAndMessages(null);
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                continuarAlJuego();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(AdError adError) {
                continuarAlJuego();
            }
        });
        appOpenAd.show(this);
    }

    private void continuarAlJuego() {
        if (yaContinuo) return;
        yaContinuo = true;
        handler.removeCallbacksAndMessages(null);
        startActivity(new Intent(SplashActivity.this, MainActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        if (animacionBarra != null) {
            animacionBarra.cancel();
        }
        super.onDestroy();
    }
}
