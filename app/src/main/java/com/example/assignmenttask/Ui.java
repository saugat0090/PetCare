package com.example.assignmenttask;

import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Small helpers for screens that do not use the BaseActivity layout (welcome, sign-in and the full map). */
final class Ui {

    private Ui() { }

    /** Keeps a screen clear of the system bars, the display cutout and the keyboard (edge-to-edge on Android 15+). */
    static void applyInsets(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
            return insets;
        });
    }
}
