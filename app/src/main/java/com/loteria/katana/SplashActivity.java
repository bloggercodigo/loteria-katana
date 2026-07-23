package com.loteria.katana;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.LinearInterpolator;
import android.widget.ProgressBar;

public class SplashActivity extends Activity {

    private static final long DURACION_SPLASH_MS = 2200;
    private ObjectAnimator animacionBarra;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ProgressBar barra = findViewById(R.id.splashProgress);

        // Animación real: la barra se llena una y otra vez mientras carga.
        animacionBarra = ObjectAnimator.ofInt(barra, "progress", 0, 100);
        animacionBarra.setDuration(900);
        animacionBarra.setInterpolator(new LinearInterpolator());
        animacionBarra.setRepeatCount(ValueAnimator.INFINITE);
        animacionBarra.setRepeatMode(ValueAnimator.RESTART);
        animacionBarra.start();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
        }, DURACION_SPLASH_MS);
    }

    @Override
    protected void onDestroy() {
        if (animacionBarra != null) {
            animacionBarra.cancel();
        }
        super.onDestroy();
    }
}
