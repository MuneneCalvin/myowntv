package com.shawnxe7en.myowntv;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
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
    private long lastHome;

    @Override protected boolean onKeyEvent(KeyEvent event) {
        return RemoteKeys.onKey(this, event);
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
        startActivity(new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }

    @Override public void onInterrupt() {}
}
