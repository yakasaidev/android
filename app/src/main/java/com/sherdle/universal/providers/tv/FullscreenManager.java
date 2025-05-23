package com.sherdle.universal.providers.tv;

import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class FullscreenManager implements View.OnApplyWindowInsetsListener {

    private final Window window;
    private final Runnable onUiVisible;

    private final int navBarType = WindowInsetsCompat.Type.navigationBars();
    private final int systemBarType = WindowInsetsCompat.Type.systemBars();

    private final WindowInsetsControllerCompat insetsController;

    public FullscreenManager(Window window, Runnable onUiVisible) {
        this.window = window;
        this.onUiVisible = onUiVisible;

        this.insetsController = new WindowInsetsControllerCompat(window, window.getDecorView());
        this.insetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_TOUCH);

        window.getDecorView().setOnApplyWindowInsetsListener(this);
    }

    public void enterFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false);
        insetsController.hide(systemBarType);
    }

    public void exitFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, true);
        insetsController.show(systemBarType);
    }

    @Override
    public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
        WindowInsetsCompat insetsCompat = WindowInsetsCompat.toWindowInsetsCompat(insets, v);
        if (insetsCompat.isVisible(navBarType)) {
            onUiVisible.run();
        }

        return insets;
    }
}
