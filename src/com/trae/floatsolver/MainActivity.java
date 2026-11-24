package com.trae.floatsolver;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private TextView statusView;
    private EditText fBase, fModel, fKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFFF4F5FA);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(30));
        sv.addView(root);
        setContentView(sv);

        TextView title = new TextView(this);
        title.setText("悬浮答题");
        title.setTextSize(23);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(0xFF14172B);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("刷题、网课、看文档时，屏幕上会有一个 AI 小球。点一下：自动读取屏幕上的题目（读不到时改截图）→ AI 直接在你当前界面给出答案，不用切屏。");
        sub.setTextSize(13);
        sub.setTextColor(0xFF6B7189);
        sub.setLineSpacing(dp(3), 1f);
        sub.setPadding(0, dp(6), 0, dp(14));
        root.addView(sub);

        statusView = new TextView(this);
        statusView.setTextSize(13.5f);
        statusView.setTextColor(0xFF3B3F57);
        statusView.setLineSpacing(dp(6), 1f);
        statusView.setBackground(round(0xFFFFFFFF, 14));
        statusView.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(statusView);

        root.addView(section("第一步 权限"));
        root.addView(primaryBtn("授予悬浮窗权限", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())));
                } catch (Throwable t) {
                    toast("请到系统设置中手动开启悬浮窗");
                }
            }
        }));
        root.addView(ghostBtn("开启无障碍（用于自动截图）", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                } catch (Throwable t) {
                    toast("请到系统设置中手动开启无障碍");
                }
            }
        }));

        root.addView(section("第二步 启动"));
        root.addView(primaryBtn("启动悬浮球", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!Settings.canDrawOverlays(MainActivity.this)) {
                    toast("请先授予悬浮窗权限");
                    return;
                }
                Intent i = new Intent(MainActivity.this, BubbleService.class);
                startForegroundService(i);
                toast("悬浮球已出现，可以回到刷题 App 了");
                moveTaskToBack(true);
            }
        }));
        root.addView(ghostBtn("停止悬浮球", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService(new Intent(MainActivity.this, BubbleService.class));
                toast("已停止");
            }
        }));

        root.addView(section("外观"));
        final String[] styleNames = {"不透明（紫色圆球）", "半透明（整球变淡）", "全透明（只有一个白色小 m）"};
        RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(RadioGroup.VERTICAL);
        for (int i = 0; i < styleNames.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(200 + i);
            rb.setText(styleNames[i]);
            rb.setTextSize(14f);
            rb.setTextColor(0xFF23263B);
            rb.setPadding(dp(4), dp(6), 0, dp(6));
            rg.addView(rb);
        }
        rg.check(200 + Prefs.style(this));
        rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                int style = checkedId - 200;
                Prefs.setStyle(MainActivity.this, style);
                sendReload();
                toast("外观：" + styleNames[style]);
            }
        });
        root.addView(rg);

        TextView cropTip = new TextView(this);
        cropTip.setText("截图范围");
        cropTip.setTextSize(13.5f);
        cropTip.setTextColor(0xFF4A4F68);
        cropTip.setPadding(0, dp(10), 0, dp(2));
        root.addView(cropTip);

        RadioGroup cropGroup = new RadioGroup(this);
        cropGroup.setOrientation(RadioGroup.VERTICAL);
        String[] cropNames = {"全屏（保留状态栏，最保险）", "裁掉状态栏和导航栏（推荐）",
                "智能裁边（只裁空白处，题目满屏时不裁）"};
        for (int i = 0; i < cropNames.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(cropNames[i]);
            rb.setTextSize(14f);
            rb.setTextColor(0xFF23263B);
            rb.setId(3000 + i);
            rb.setPadding(dp(4), dp(4), 0, dp(4));
            cropGroup.addView(rb);
        }
        ((android.widget.RadioButton) cropGroup.getChildAt(
                Math.min(2, Math.max(0, Prefs.cropMode(this))))).setChecked(true);
        cropGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Prefs.setCropMode(MainActivity.this, checkedId - 3000);
                toast("截图范围已更新");
            }
        });
        root.addView(cropGroup);

        TextView resTip = new TextView(this);
        resTip.setText("截图清晰度（越高越准、越费流量）");
        resTip.setTextSize(13.5f);
        resTip.setTextColor(0xFF4A4F68);
        resTip.setPadding(0, dp(8), 0, dp(2));
        root.addView(resTip);

        RadioGroup resGroup = new RadioGroup(this);
        resGroup.setOrientation(RadioGroup.VERTICAL);
        String[] resNames = {"高清 1920（推荐，识别率最高）", "省流 1280（费用约省一半，文字题基本够用）"};
        for (int i = 0; i < resNames.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(resNames[i]);
            rb.setTextSize(14f);
            rb.setTextColor(0xFF23263B);
            rb.setId(3100 + i);
            rb.setPadding(dp(4), dp(4), 0, dp(4));
            resGroup.addView(rb);
        }
        ((android.widget.RadioButton) resGroup.getChildAt(
                Prefs.maxSide(this) >= 1920 ? 0 : 1)).setChecked(true);
        resGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Prefs.setMaxSide(MainActivity.this, checkedId - 3100 == 0 ? 1920 : 1280);
                toast(checkedId - 3100 == 0 ? "已切到高清" : "已切到省流");
            }
        });
        root.addView(resGroup);

        root.addView(section("自动模式"));
        CheckBox auto = new CheckBox(this);
        auto.setText("自动识别：不用点球，换题自动出答案");
        auto.setTextSize(14f);
        auto.setTextColor(0xFF23263B);
        auto.setChecked(Prefs.autoOn(this));
        auto.setPadding(dp(4), dp(8), 0, dp(8));
        auto.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setAutoOn(MainActivity.this, isChecked);
                sendReload();
                toast(isChecked ? "自动模式已开启" : "自动模式已关闭");
            }
        });
        root.addView(auto);

        TextView secLabel = new TextView(this);
        secLabel.setText("识别间隔（秒，2–60）");
        secLabel.setTextSize(13f);
        secLabel.setTextColor(0xFF5B6072);
        secLabel.setPadding(0, dp(10), 0, dp(4));
        root.addView(secLabel);

        final EditText fSec = input("间隔秒数，默认 5", InputType.TYPE_CLASS_NUMBER);
        fSec.setText(String.valueOf(Prefs.autoSec(this)));
        fSec.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    int v = Integer.parseInt(s.toString().trim());
                    if (v >= 2 && v <= 60) {
                        Prefs.setAutoSec(MainActivity.this, v);
                        sendReload();
                    }
                } catch (Throwable ignore) {
                }
            }
        });
        root.addView(fSec);

        TextView autoTip = new TextView(this);
        autoTip.setText("勾选后每隔设定秒数检测一次画面，检测到换题才重新请求，同题不重复烧钱。自动模式只把答案浮出来，不会替你点选项。");
        autoTip.setTextSize(12.5f);
        autoTip.setTextColor(0xFF7A809A);
        autoTip.setLineSpacing(dp(3), 1f);
        autoTip.setPadding(0, dp(6), 0, 0);
        root.addView(autoTip);

        root.addView(section("答案显示"));
        CheckBox brief = new CheckBox(this);
        brief.setText("只显示选项：答案字母 + 一句话理由 + 选项原文");
        brief.setTextSize(14f);
        brief.setTextColor(0xFF23263B);
        brief.setChecked(Prefs.brief(this));
        brief.setPadding(dp(4), dp(8), 0, dp(8));
        brief.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setBrief(MainActivity.this, isChecked);
                toast(isChecked ? "已切到只看选项" : "已切回完整解析");
            }
        });
        root.addView(brief);

        CheckBox only = new CheckBox(this);
        only.setText("只要答案：只回一行结论，不解释");
        only.setTextSize(14f);
        only.setTextColor(0xFF23263B);
        only.setChecked(Prefs.answerOnly(this));
        only.setPadding(dp(4), dp(8), 0, dp(8));
        only.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setAnswerOnly(MainActivity.this, isChecked);
                toast(isChecked ? "已切到只要答案" : "已切回上方模式");
            }
        });
        root.addView(only);

        TextView briefTip = new TextView(this);
        briefTip.setText("浮层顶部会显示大号答案角标，解析里的「答案：」那行标红加粗，省得在一段文字里找。");
        briefTip.setTextSize(12.5f);
        briefTip.setTextColor(0xFF7A809A);
        briefTip.setLineSpacing(dp(3), 1f);
        briefTip.setPadding(0, dp(6), 0, 0);
        root.addView(briefTip);

        root.addView(section("第三步 填 AI 接口（只需一次）"));
        fBase = input("接口地址 Base URL，如 https://api.deepseek.com/v1",
                InputType.TYPE_TEXT_VARIATION_URI);
        fModel = input("模型名，如 deepseek-flash（需支持识图）", InputType.TYPE_CLASS_TEXT);
        fKey = input("API Key", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(fBase);
        root.addView(fModel);
        root.addView(fKey);
        root.addView(primaryBtn("保存配置", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.save(MainActivity.this, fBase.getText().toString(),
                        fModel.getText().toString(), fKey.getText().toString());
                toast(Prefs.configured(MainActivity.this) ? "已保存" : "已保存，但三项都要填全");
                updateStatus();
            }
        }));

        final TextView testOut = new TextView(this);
        testOut.setTextSize(12.5f);
        testOut.setTextColor(0xFF4A4F68);
        testOut.setBackground(roundBg(0xFFF7F8FC, 10));
        testOut.setPadding(dp(12), dp(9), dp(12), dp(9));
        testOut.setLineSpacing(dp(2), 1f);
        testOut.setTextIsSelectable(true);
        testOut.setVisibility(View.GONE);
        final LinearLayout.LayoutParams testLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        testLp.topMargin = dp(9);
        root.addView(testOut, testLp);

        final Button testBtn = ghostBtn("测试连接（不截图，只发一句话）", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testConnection(testOut, (Button) v);
            }
        });
        root.addView(testBtn);

        TextView tip = new TextView(this);
        tip.setText("推荐：DeepSeek deepseek-flash（支持识图，快且便宜）\n"
                + "接口地址 https://api.deepseek.com/v1\n"
                + "模型名 deepseek-flash\n\n"
                + "用法：回到任何 App，点紫色「m」小球 → 屏幕自动截图并给出答案，答案浮在当前界面，可滚动、可复制、可拖到你喜欢的位置。\n"
                + "长按小球 2 秒可关闭悬浮球。");
        tip.setTextSize(12.5f);
        tip.setTextColor(0xFF7A809A);
        tip.setLineSpacing(dp(3), 1f);
        tip.setPadding(0, dp(16), 0, 0);
        root.addView(tip);

        root.addView(section("最近题目和答案（最多 20 条）"));
        final LinearLayout historyBox = new LinearLayout(this);
        historyBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(historyBox);
        final Runnable fillHistory = new Runnable() {
            @Override
            public void run() {
                historyBox.removeAllViews();
                org.json.JSONArray arr = Prefs.history(MainActivity.this);
                if (arr.length() == 0) {
                    TextView empty = new TextView(MainActivity.this);
                    empty.setText("还没有记录。用悬浮球解答过的题目会自动出现在这里（题目和答案都存文字）。");
                    empty.setTextSize(12.5f);
                    empty.setTextColor(0xFF7A809A);
                    historyBox.addView(empty);
                    return;
                }
                java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("MM-dd HH:mm");
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    final String q = o.optString("q", "").trim();
                    final String a = o.optString("a", "");

                    LinearLayout card = new LinearLayout(MainActivity.this);
                    card.setOrientation(LinearLayout.VERTICAL);
                    card.setBackground(roundBg(0xFFF7F8FC, 10));
                    card.setPadding(dp(12), dp(9), dp(12), dp(9));

                    TextView meta = new TextView(MainActivity.this);
                    meta.setText(fmt.format(new java.util.Date(o.optLong("t"))));
                    meta.setTextSize(11.5f);
                    meta.setTextColor(0xFF9AA0B4);
                    card.addView(meta);

                    TextView qv = new TextView(MainActivity.this);
                    qv.setText(q.isEmpty() ? "题目：（截图题，未存文字）" : "题目：" + cut(q, 90));
                    qv.setTextSize(12.5f);
                    qv.setTextColor(0xFF4A4F68);
                    qv.setLineSpacing(dp(2), 1f);
                    qv.setPadding(0, dp(3), 0, 0);
                    card.addView(qv);

                    TextView av = new TextView(MainActivity.this);
                    av.setText("答案：" + cut(a, 120));
                    av.setTextSize(13f);
                    av.setTextColor(0xFF23263B);
                    av.setLineSpacing(dp(2), 1f);
                    av.setPadding(0, dp(3), 0, 0);
                    card.addView(av);

                    LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    olp.bottomMargin = dp(6);
                    card.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            String text = (q.isEmpty() ? "" : "题目：" + q + "\n") + "答案：" + a;
                            ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                                    .setPrimaryClip(android.content.ClipData.newPlainText("题目和答案", text));
                            toast("已复制题目和答案");
                        }
                    });
                    historyBox.addView(card, olp);
                }
            }
        };
        fillHistory.run();

        LinearLayout historyBtns = new LinearLayout(this);
        historyBtns.setOrientation(LinearLayout.HORIZONTAL);
        historyBtns.addView(tinyBtn("刷新", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fillHistory.run();
                toast("已刷新");
            }
        }));
        historyBtns.addView(tinyBtn("清空", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.clearHistory(MainActivity.this);
                fillHistory.run();
                toast("已清空");
            }
        }));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(4);
        root.addView(historyBtns, hlp);

        root.addView(section("关于"));
        TextView ver = new TextView(this);
        ver.setText("版本 3.0");
        ver.setTextSize(13f);
        ver.setTextColor(0xFF7A809A);
        root.addView(ver);
        root.addView(primaryBtn("检查更新", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkUpdate();
            }
        }));

        if (Build.VERSION.SDK_INT >= 33) {
            try {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 11);
            } catch (Throwable ignore) {
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        String b = Prefs.base(this), m = Prefs.model(this), k = Prefs.key(this);
        if (fBase != null && fBase.getText().length() == 0) fBase.setText(b);
        if (fModel != null && fModel.getText().length() == 0) fModel.setText(m);
        if (fKey != null && fKey.getText().length() == 0) fKey.setText(k);
        updateStatus();
    }

    private void updateStatus() {
        boolean overlay = Settings.canDrawOverlays(this);
        boolean acc = isAccessibilityOn();
        boolean cfg = Prefs.configured(this);
        statusView.setText(
                (overlay ? "已开启" : "未开启") + " · 悬浮窗权限\n"
                        + (acc ? "已开启" : "未开启") + " · 无障碍（自动截图）\n"
                        + (cfg ? "已填写" : "未填写") + " · AI 接口配置");
        statusView.setTextColor(overlay && acc && cfg ? 0xFF15803D : 0xFFB45309);
    }

    private boolean isAccessibilityOn() {
        try {
            String s = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (s == null) return false;
            String needle = getPackageName() + "/" + ShotService.class.getName();
            return s.toLowerCase().contains(needle.toLowerCase());
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /* ---------------- UI 小工具 ---------------- */

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView section(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(0xFF8A90A8);
        t.setPadding(dp(4), dp(20), 0, dp(2));
        return t;
    }

    private Button primaryBtn(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(0xFFFFFFFF);
        b.setBackground(round(0xFF4F46E5, 12));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.topMargin = dp(9);
        b.setLayoutParams(lp);
        return b;
    }

    private Button ghostBtn(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14.5f);
        b.setTextColor(0xFF14172B);
        b.setBackground(round(0xFFFFFFFF, 12));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.topMargin = dp(9);
        b.setLayoutParams(lp);
        return b;
    }

    private EditText input(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(0xFFA6ABC0);
        e.setTextSize(14);
        e.setInputType(type);
        e.setBackground(round(0xFFFFFFFF, 12));
        e.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(9);
        e.setLayoutParams(lp);
        return e;
    }

    /** 通知正在运行的悬浮球重新读取外观 / 自动模式设置 */
    private void sendReload() {
        if (BubbleService.RUNNING) {
            Intent i = new Intent(this, BubbleService.class);
            i.setAction("reload");
            startService(i);
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private TextView tinyBtn(String text, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12.5f);
        t.setTextColor(0xFF4F46E5);
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(dp(12), dp(6), dp(12), dp(6));
        t.setBackground(roundBg(0xFFEEF0FF, 9));
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    private GradientDrawable roundBg(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private static String cut(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) + "…" : s;
    }

    /** 用当前填写的地址、模型、Key 发一句纯文字，验证接口通不通 */
    private void testConnection(final TextView out, final Button btn) {
        final String base = fBase.getText().toString().trim();
        final String model = fModel.getText().toString().trim();
        final String key = fKey.getText().toString().trim();
        if (base.isEmpty() || model.isEmpty() || key.isEmpty()) {
            toast("接口地址、模型名、Key 三项都要填");
            return;
        }
        Prefs.save(this, base, model, key);
        btn.setEnabled(false);
        btn.setText("测试中…");
        out.setVisibility(View.VISIBLE);
        out.setTextColor(0xFF4A4F68);
        out.setText("正在请求：\n" + base + "\n模型：" + model);

        new Thread(new Runnable() {
            @Override
            public void run() {
                String msg;
                int color = 0xFFB45309;
                String url = (base.endsWith("/") ? base : base + "/") + "chat/completions";
                try {
                    java.net.HttpURLConnection c =
                            (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                    c.setConnectTimeout(20000);
                    c.setReadTimeout(60000);
                    c.setRequestMethod("POST");
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json");
                    c.setRequestProperty("Authorization", "Bearer " + key);
                    String body = "{\"model\":" + org.json.JSONObject.quote(model)
                            + ",\"messages\":[{\"role\":\"user\",\"content\":\"你好\"}]"
                            + ",\"max_tokens\":16}";
                    java.io.OutputStream os = c.getOutputStream();
                    os.write(body.getBytes("UTF-8"));
                    os.close();

                    int code = c.getResponseCode();
                    java.io.InputStream is = (code >= 200 && code < 300)
                            ? c.getInputStream() : c.getErrorStream();
                    StringBuilder sb = new StringBuilder();
                    if (is != null) {
                        java.io.BufferedReader br = new java.io.BufferedReader(
                                new java.io.InputStreamReader(is, "UTF-8"));
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                        br.close();
                    }
                    String resp = sb.toString();
                    if (code >= 200 && code < 300) {
                        String content = "";
                        try {
                            content = new org.json.JSONObject(resp).getJSONArray("choices")
                                    .getJSONObject(0).getJSONObject("message")
                                    .optString("content", "");
                        } catch (Throwable ignore) {
                        }
                        msg = "连接成功（HTTP 200）\n模型：" + model
                                + "\n回复：" + (content.trim().isEmpty()
                                ? cut(resp, 80) : cut(content.trim(), 80));
                        color = 0xFF15803D;
                    } else {
                        msg = "连接失败 HTTP " + code + "\n地址：" + url
                                + "\n模型：" + model + "\n" + cut(resp, 500);
                    }
                } catch (Throwable e) {
                    msg = "连接失败：" + e.getClass().getSimpleName()
                            + "\n" + cut(String.valueOf(e.getMessage()), 200)
                            + "\n地址：" + url;
                }

                final String m = msg;
                final int col = color;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        out.setTextColor(col);
                        out.setText(m);
                        btn.setEnabled(true);
                        btn.setText("测试连接（不截图，只发一句话）");
                    }
                });
            }
        }).start();
    }

    /** 读 GitHub Releases 最新版本号并和本地版本比较 */
    private void checkUpdate() {
        toast("正在检查…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String result;
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(
                            "https://api.github.com/repos/xxhnrzxx/m-float-solver/releases/latest")
                            .openConnection();
                    c.setConnectTimeout(12000);
                    c.setReadTimeout(12000);
                    c.setRequestProperty("Accept", "application/vnd.github+json");
                    int code = c.getResponseCode();
                    if (code == 200) {
                        java.io.BufferedReader br = new java.io.BufferedReader(
                                new java.io.InputStreamReader(c.getInputStream(), "UTF-8"));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                        br.close();
                        String tag = new org.json.JSONObject(sb.toString()).optString("tag_name", "");
                        String now = "v3.0";
                        if (tag.isEmpty()) {
                            result = "没读到版本号";
                        } else if (tag.equalsIgnoreCase(now)) {
                            result = "已是最新版本（" + now + "）";
                        } else {
                            result = "发现新版本 " + tag + "，当前 " + now
                                    + "，可在 GitHub Releases 下载";
                        }
                    } else if (code == 404) {
                        result = "读不到更新信息（仓库需设为公开）";
                    } else {
                        result = "检查失败 HTTP " + code;
                    }
                } catch (Throwable e) {
                    result = "检查失败：" + e.getClass().getSimpleName();
                }
                final String msg = result;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        toast(msg);
                    }
                });
            }
        }).start();
    }
}
