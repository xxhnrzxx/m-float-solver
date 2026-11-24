package com.trae.floatsolver;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("float_solver", Context.MODE_PRIVATE);
    }

    public static String base(Context c) {
        return sp(c).getString("base", "").trim();
    }

    public static String model(Context c) {
        return sp(c).getString("model", "").trim();
    }

    public static String key(Context c) {
        return sp(c).getString("key", "").trim();
    }

    public static void save(Context c, String base, String model, String key) {
        sp(c).edit()
                .putString("base", base == null ? "" : base.trim())
                .putString("model", model == null ? "" : model.trim())
                .putString("key", key == null ? "" : key.trim())
                .apply();
    }

    public static boolean configured(Context c) {
        return !base(c).isEmpty() && !model(c).isEmpty() && !key(c).isEmpty();
    }

    /** 外观：0 不透明，1 半透明，2 全透明（只剩白色 m） */
    public static final int STYLE_SOLID = 0;
    public static final int STYLE_TRANSLUCENT = 1;
    public static final int STYLE_GHOST = 2;

    public static int style(Context c) {
        SharedPreferences p = sp(c);
        if (!p.contains("style")) {
            return p.getBoolean("translucent", false) ? STYLE_TRANSLUCENT : STYLE_SOLID;
        }
        return p.getInt("style", STYLE_SOLID);
    }

    public static void setStyle(Context c, int style) {
        sp(c).edit().putInt("style", style).apply();
    }

    /** 自动模式：定时检测画面，换题就自动请求答案 */
    public static boolean autoOn(Context c) {
        return sp(c).getBoolean("auto_on", false);
    }

    public static void setAutoOn(Context c, boolean on) {
        sp(c).edit().putBoolean("auto_on", on).apply();
    }

    public static int autoSec(Context c) {
        int v = sp(c).getInt("auto_sec", 5);
        return Math.max(2, Math.min(60, v));
    }

    public static void setAutoSec(Context c, int sec) {
        sp(c).edit().putInt("auto_sec", Math.max(2, Math.min(60, sec))).apply();
    }

    /** 只显示选项：答案字母 + 一句话理由 + 选项原文 */
    public static boolean brief(Context c) {
        return sp(c).getBoolean("brief", false);
    }

    public static void setBrief(Context c, boolean on) {
        sp(c).edit().putBoolean("brief", on).apply();
    }

    /** 只要答案：只回一行结论 */
    public static boolean answerOnly(Context c) {
        return sp(c).getBoolean("answerOnly", false);
    }

    public static void setAnswerOnly(Context c, boolean on) {
        sp(c).edit().putBoolean("answerOnly", on).apply();
    }
}
