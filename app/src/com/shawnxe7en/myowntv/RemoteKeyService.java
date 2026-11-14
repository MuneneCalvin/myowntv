package com.shawnxe7en.myowntv;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Sees the remote's keys before any app does, so the app buttons work from inside Netflix, Disney and the rest. The
 * system waits for it on every key (up to half a second), so it runs in a process of its own and does next to nothing.
 * It is also the Home-button watch of the safe home switch: when the TV's own home screen comes to the front, MyOwnTV
 * is brought back (HomeSwitch.isTvHome); Google TV's home and setup wizard stay on, so they still answer the Home key
 * first, for a moment.
 */
public class RemoteKeyService extends AccessibilityService {
    private static final long BOOT_MS = 3 * 60_000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BroadcastReceiver wake;
    private long lastHome;

    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_HOME && HomeMode.watching(this)) {
            // Taken here the Home key never reaches Google TV's home, so nothing flashes before ours.
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                lastHome = SystemClock.uptimeMillis();
                AppLog.w("home key -> MyOwnTV");
                openHome();
            }
            return true;
        }
        return RemoteKeys.onKey(this, event);
    }

    /** Just after boot the TV's own home is already up: cover it as soon as this service is, not at its next event. */
    @Override protected void onServiceConnected() {
        if (wake == null) {
            wake = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    if (!HomeMode.watching(c)) return;
                    // Waking from standby, Google TV's home puts itself in front (a second time, a moment later).
                    AppLog.w("screen on -> MyOwnTV");
                    openHome();
                    handler.postDelayed(RemoteKeyService.this::openHome, 1200);
                }
            };
            registerReceiver(wake, new IntentFilter(Intent.ACTION_SCREEN_ON));
        }
        if (SystemClock.elapsedRealtime() < BOOT_MS && HomeMode.watching(this)) {
            AppLog.w("boot -> MyOwnTV");
            openHome();
        }
    }

    @Override public void onDestroy() {
        if (wake != null) unregisterReceiver(wake);
        wake = null;
        super.onDestroy();
    }

    private void openHome() {
        startActivity(new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName() == null
                || event.getClassName() == null) {
            return;
        }
        if (!HomeSwitch.isTvHome(event.getPackageName().toString(), event.getClassName().toString())) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastHome < 700 || !HomeMode.watching(this)) return;
        lastHome = now;
        openHome();
    }

    @Override public void onInterrupt() {}
}
