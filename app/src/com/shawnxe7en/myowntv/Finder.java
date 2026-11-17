package com.shawnxe7en.myowntv;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Search's data: a title found on TMDB, and which of this TV's streaming apps carry it in the person's region (their own
 * subscription, with ads, or free; renting and buying don't count). Everything here blocks: call it off the UI thread.
 */
final class Finder {
    /** An installed app that has the title: SERVICES' key, its package, and its name. */
    static final class App {
        final String key, pkg, name;

        App(String key, String pkg, String name) {
            this.key = key;
            this.pkg = pkg;
            this.name = name;
        }
    }

    static final class Hit {
        final String kind;      // "movie" or "tv"
        final int id;
        String title, year = "", overview = "", poster = "";   // poster: the path on TMDB's image server, or ""
        List<App> apps = new ArrayList<>();
        boolean checked;        // apps is known (else it's still being asked)

        Hit(String kind, int id, String title) {
            this.kind = kind;
            this.id = id;
            this.title = title;
        }
    }

    /** The most results asked for the apps of: each costs one request. */
    static final int MAX_HITS = 12;

    /** Movies and shows matching `query`, in TMDB's order; no apps yet (fill). */
    static List<Hit> search(String query) throws IOException, org.json.JSONException {
        JSONObject r = Recs.tmdb("/search/multi?query=" + URLEncoder.encode(query, "UTF-8") + "&language=" + Recs.lang()
                + "&include_adult=false&page=1");
        List<Hit> out = new ArrayList<>();
        JSONArray results = r.optJSONArray("results");
        for (int i = 0; results != null && i < results.length() && out.size() < MAX_HITS; i++) {
            JSONObject o = results.getJSONObject(i);
            String kind = o.optString("media_type");
            if (!kind.equals("movie") && !kind.equals("tv")) continue;   // people
            out.add(hit(kind, o));
        }
        return out;
    }

    private static Hit hit(String kind, JSONObject o) {
        Hit h = new Hit(kind, o.optInt("id"), o.optString(kind.equals("movie") ? "title" : "name"));
        String date = o.optString(kind.equals("movie") ? "release_date" : "first_air_date");
        h.year = date.length() >= 4 ? date.substring(0, 4) : "";
        h.overview = o.optString("overview");
        h.poster = o.isNull("poster_path") ? "" : o.optString("poster_path");
        return h;
    }

    /** A saved title, as a Hit with its poster and story (a second request) but no apps yet. */
    static Hit details(MyList.Entry e) throws IOException, org.json.JSONException {
        Hit h = hit(e.kind, Recs.tmdb("/" + e.kind + "/" + e.id + "?language=" + Recs.lang()));
        if (h.title.isEmpty()) h.title = e.title;   // keep the name that was saved
        return h;
    }

    /** Sets h.apps (the TV's apps that stream it in the region) and h.checked. */
    static void fill(Context c, Hit h) throws IOException, org.json.JSONException {
        JSONObject region = Recs.tmdb("/" + h.kind + "/" + h.id + "/watch/providers").getJSONObject("results").optJSONObject(Recs.REGION);
        List<App> apps = new ArrayList<>();
        for (String type : new String[] {"flatrate", "ads", "free"}) {
            JSONArray list = region == null ? null : region.optJSONArray(type);
            for (int i = 0; list != null && i < list.length(); i++) {
                JSONObject p = list.getJSONObject(i);
                String[] s = Recs.serviceFor(c, p.optString("provider_name"), p.optInt("provider_id"));
                if (s == null || HomeApps.launchIntent(c.getPackageManager(), s[2]) == null || Prefs.serviceOff(s[1])) continue;
                boolean twice = false;
                for (App a : apps) twice |= a.key.equals(s[1]);
                String name = Recs.name(s[1]);
                if (!twice) apps.add(new App(s[1], s[2], name.isEmpty() ? (s.length > 3 ? s[3] : s[1]) : name));
            }
        }
        h.apps = apps;
        h.checked = true;
    }

    /** The title's own page in the app (Watchmode, 2 requests), or null: open the app's home. Null too without a key or on an error. */
    static String link(Hit h, App a) {
        if (Recs.watchmodeKey().isEmpty()) return null;
        try {
            String link = Recs.watchmodeLink(h.kind, h.id, a.key);
            return link.isEmpty() ? null : link;
        } catch (Exception e) {
            AppLog.w("search link: " + e.getMessage());
            return null;
        }
    }
}
