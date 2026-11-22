package com.newsvoice.tts;

import android.content.Context;
import android.content.SharedPreferences;

/** Local-only configuration; no default URL or credential is provisioned. */
public final class AppPrefs {
    private AppPrefs() { }
    private static final String NAME = "gwangmyeong_news_v11";
    public static SharedPreferences get(Context c) {
        return c.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }
    public static void status(Context c, String value) {
        get(c).edit().putString("status", value).putLong("status_at", System.currentTimeMillis()).apply();
    }
    public static String enginePackage(Context c) {return get(c).getString("engine", "");}
    public static int presetIndex(Context c) {return get(c).getInt("preset", 0);}
    public static boolean videoEnabled(Context c) {return get(c).getBoolean("video", true);}
    public static String anchorComment(Context c) {return get(c).getString("anchor_comment","");}
    public static String voiceName(Context c) {return get(c).getString("voice", "");}
    public static float speed(Context c) {return get(c).getFloat("speed", 0.95f);}
    public static float pitch(Context c) {return get(c).getFloat("pitch", 1.00f);}
}
