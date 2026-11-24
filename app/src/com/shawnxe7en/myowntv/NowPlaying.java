package com.shawnxe7en.myowntv;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;

import java.util.ArrayList;
import java.util.List;

/**
 * Now Playing: what the TV's apps are playing, from their media sessions. Android hands the sessions only to an enabled
 * notification listener, so this empty one exists for that and nothing else: it never reads a notification and lets go
 * of the system at once (MyOwnTV grants it through the TV's own ADB only while the setting is on; see HomeMode). The
 * way of it follows Free My TV's NowPlaying (MIT).
 */
public class NowPlaying extends NotificationListenerService {
    @Override public void onListenerConnected() {
        requestUnbind();
    }

    static ComponentName component(Context c) {
        return new ComponentName(c, NowPlaying.class);
    }

    /** Whether Android has this listener switched on (the grant took effect). */
    static boolean allowed(Context c) {
        String on = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return on != null && on.contains(component(c).flattenToString());
    }

    /** The last line seen playing, while it's under a day old: what to show after a restart. */
    static String last(Context c) {
        NowPlayingText.Saved s = NowPlayingText.unpack(Prefs.lastPlayed(), System.currentTimeMillis());
        return s == null ? null : s.line;
    }

    static String lastPackage(Context c) {
        NowPlayingText.Saved s = NowPlayingText.unpack(Prefs.lastPlayed(), System.currentTimeMillis());
        return s == null || s.pkg.isEmpty() ? null : s.pkg;
    }

    /** Follows the sessions while a screen that shows them is up (start and stop), and says when the line changes. */
    static final class Watch {
        private final Context ctx;
        private final Runnable onChange;
        private final Handler main = new Handler(Looper.getMainLooper());
        private final List<MediaController> watched = new ArrayList<>();
        private MediaSessionManager manager;
        private String line, pkg;
        private boolean playing;

        private final MediaSessionManager.OnActiveSessionsChangedListener sessions = list -> follow(list);
        private final MediaController.Callback callback = new MediaController.Callback() {
            @Override public void onPlaybackStateChanged(PlaybackState state) { refresh(); }
            @Override public void onMetadataChanged(MediaMetadata metadata) { refresh(); }
        };

        Watch(Context ctx, Runnable onChange) {
            this.ctx = ctx.getApplicationContext();
            this.onChange = onChange;
        }

        String line() { return line; }
        String pkg() { return pkg; }
        boolean playing() { return line != null && playing; }

        void start() {
            if (manager != null || !Prefs.nowPlaying() || !allowed(ctx)) return;
            try {
                manager = ctx.getSystemService(MediaSessionManager.class);
                manager.addOnActiveSessionsChangedListener(sessions, component(ctx), main);
                follow(manager.getActiveSessions(component(ctx)));
            } catch (SecurityException e) {   // the grant isn't in effect (yet)
                AppLog.w("now playing: " + e.getMessage());
                manager = null;
            }
        }

        void stop() {
            if (manager == null) return;
            manager.removeOnActiveSessionsChangedListener(sessions);
            for (MediaController c : watched) c.unregisterCallback(callback);
            watched.clear();
            manager = null;
        }

        private void follow(List<MediaController> list) {
            for (MediaController c : watched) c.unregisterCallback(callback);
            watched.clear();
            if (list != null) {
                for (MediaController c : list) {
                    c.registerCallback(callback, main);
                    watched.add(c);
                }
            }
            refresh();
        }

        private void refresh() {
            List<NowPlayingText.Session> all = new ArrayList<>();
            for (MediaController c : watched) {
                MediaMetadata m = c.getMetadata();
                PlaybackState st = c.getPlaybackState();
                String title = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_TITLE);
                if (title == null && m != null) title = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
                String artist = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (artist == null && m != null) artist = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                all.add(new NowPlayingText.Session(st == null ? 0 : st.getState(), title, artist, label(c.getPackageName()),
                        c.getPackageName()));
            }
            NowPlayingText.Session top = NowPlayingText.pick(all);
            String now = top == null ? null : NowPlayingText.line(top);
            String nowPkg = now == null ? null : top.pkg;
            boolean changed = !java.util.Objects.equals(now, line) || !java.util.Objects.equals(nowPkg, pkg);
            line = now;
            pkg = nowPkg;
            playing = now != null;
            if (now != null && changed) Prefs.setLastPlayed(NowPlayingText.pack(now, nowPkg, System.currentTimeMillis()));
            if (changed) onChange.run();
        }

        private String label(String pkgName) {
            try {
                PackageManager pm = ctx.getPackageManager();
                return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkgName, 0)));
            } catch (PackageManager.NameNotFoundException e) {
                return pkgName;
            }
        }
    }
}
