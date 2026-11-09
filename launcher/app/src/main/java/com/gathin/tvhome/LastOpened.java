package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.format.DateUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * When an app was last opened from Home, for the cinematic shelf's "Last opened" line: the launcher notes the
 * time whenever it opens an app. No permission and nothing read from other apps, so an app started some other
 * way (a remote's own Netflix button) shows the last time Home opened it.
 *
 * Also tracks apps that were explicitly closed from background (e.g. via Recents or Rocket Boost)
 * so that when launched next time, they start clean from scratch (CLEAR_TASK) instead of resuming
 * an old activity backstack.
 */
final class LastOpened {
    private static final String PREFS = "last_opened";
    private static final String PREFS_CLOSED = "closed_apps_state";
    private static final String KEY_CLOSED_PACKAGES = "closed_pkgs";
    private static final String KEY_CLOSED_ALL_SINCE = "closed_all_since";

    private LastOpened() {
    }

    /** Notes that Home just opened this app. */
    static void record(Context ctx, String pkg) {
        if (pkg == null) return;
        prefs(ctx).edit().putLong(pkg, System.currentTimeMillis()).apply();
        unmarkClosed(ctx, pkg);
    }

    /** Milliseconds since the epoch, or 0 when Home never opened the app. */
    static long get(Context ctx, String pkg) {
        return pkg == null ? 0L : prefs(ctx).getLong(pkg, 0L);
    }

    /** Removes an app from recents (e.g. after being closed from background). */
    static void remove(Context ctx, String pkg) {
        if (pkg != null) {
            prefs(ctx).edit().remove(pkg).apply();
            markClosed(ctx, pkg);
        }
    }

    /** Clears all recent app records. */
    static void clear(Context ctx) {
        prefs(ctx).edit().clear().apply();
        markAllClosed(ctx);
    }

    /** Marks a specific app package as closed from background. */
    static void markClosed(Context ctx, String pkg) {
        if (pkg == null) return;
        SharedPreferences sp = closedPrefs(ctx);
        Set<String> set = sp.getStringSet(KEY_CLOSED_PACKAGES, null);
        Set<String> updated = set != null ? new HashSet<>(set) : new HashSet<>();
        updated.add(pkg);
        sp.edit().putStringSet(KEY_CLOSED_PACKAGES, updated).apply();
    }

    /** Unmarks an app as closed once it has been relaunched fresh. */
    static void unmarkClosed(Context ctx, String pkg) {
        if (pkg == null) return;
        SharedPreferences sp = closedPrefs(ctx);
        Set<String> set = sp.getStringSet(KEY_CLOSED_PACKAGES, null);
        if (set != null && set.contains(pkg)) {
            Set<String> updated = new HashSet<>(set);
            updated.remove(pkg);
            sp.edit().putStringSet(KEY_CLOSED_PACKAGES, updated).apply();
        }
    }

    /** Marks all apps as closed (e.g. after Close All or Rocket Boost). */
    static void markAllClosed(Context ctx) {
        closedPrefs(ctx).edit().putLong(KEY_CLOSED_ALL_SINCE, System.currentTimeMillis()).apply();
    }

    /**
     * Checks if an app was marked closed, or was never opened, or was closed in a Close All / Boost.
     * When true, the app should be launched with FLAG_ACTIVITY_CLEAR_TASK to start completely from scratch.
     */
    static boolean isClosed(Context ctx, String pkg) {
        if (pkg == null) return true;
        SharedPreferences sp = closedPrefs(ctx);
        Set<String> set = sp.getStringSet(KEY_CLOSED_PACKAGES, null);
        if (set != null && set.contains(pkg)) {
            return true;
        }
        long closedAllSince = sp.getLong(KEY_CLOSED_ALL_SINCE, 0L);
        if (closedAllSince > 0) {
            long openedAt = get(ctx, pkg);
            if (openedAt < closedAllSince) {
                return true;
            }
        }
        return get(ctx, pkg) == 0L;
    }

    /** Returns all recently opened package names, sorted newest first. */
    static java.util.List<String> getRecentPackages(Context ctx) {
        java.util.Map<String, ?> all = prefs(ctx).getAll();
        java.util.List<java.util.Map.Entry<String, Long>> entries = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, ?> entry : all.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof Long) {
                entries.add(new java.util.AbstractMap.SimpleEntry<>(entry.getKey(), (Long) val));
            }
        }
        java.util.Collections.sort(entries, (a, b) -> Long.compare(b.getValue(), a.getValue()));
        java.util.List<String> result = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, Long> e : entries) {
            result.add(e.getKey());
        }
        return result;
    }

    /**
     * "5 minutes ago" within the hour, then the day and the time: "today, 11:40 am", "yesterday, 9:40 pm",
     * "3 days ago, 9:40 pm", "Oct 12, 9:40 pm". The parts are Android's own wording in the TV's language.
     */
    static String describe(Context ctx, long when) {
        long now = System.currentTimeMillis();
        // Under a minute Android says "0 minutes ago"; one minute reads better.
        when = Math.min(when, now - DateUtils.MINUTE_IN_MILLIS);
        if (now - when < DateUtils.HOUR_IN_MILLIS) {
            return DateUtils.getRelativeTimeSpanString(when, now, DateUtils.MINUTE_IN_MILLIS).toString();
        }
        String day = DateUtils.getRelativeTimeSpanString(when, now, DateUtils.DAY_IN_MILLIS).toString();
        // It follows "Last opened" mid-sentence: "yesterday", not "Yesterday". Month names keep their capital.
        if (Locale.getDefault().getLanguage().equals("en") && (day.startsWith("Yesterday") || day.startsWith("Today"))) {
            day = Character.toLowerCase(day.charAt(0)) + day.substring(1);
        }
        return day + ", " + DateUtils.formatDateTime(ctx, when, DateUtils.FORMAT_SHOW_TIME);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static SharedPreferences closedPrefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS_CLOSED, Context.MODE_PRIVATE);
    }
}
