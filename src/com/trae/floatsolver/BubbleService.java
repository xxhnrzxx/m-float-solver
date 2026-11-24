package com.trae.floatsolver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;

/**
 * 悬浮球 + 答案浮层。点一下：自动截屏 -> 调用 AI -> 在当前界面直接看到答案。
 */
public class BubbleService extends Service {

    private WindowManager wm;
    private TextView bubble;
    private WindowManager.LayoutParams bubbleLp;

    private LinearLayout panel;
    private WindowManager.LayoutParams panelLp;
    private TextView panelTitle;
    private TextView panelText;
    private TextView answerBadge;
    private ScrollView panelScroll;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean busy = false;
    private String lastDataUrl = null;
    private View followWrap = null;

    /** 悬浮球直径（dp），比默认的浮窗按钮更小一点，少挡内容 */
    private static final int BUBBLE_DP = 42;
    /** 悬浮球 alpha：半透明模式下更淡 */
    private static final float ALPHA_BUBBLE = 0.55f;
    private static final float ALPHA_BUBBLE_SOLID = 1f;
    private static final float ALPHA_PANEL = 0.92f;

    public static volatile boolean RUNNING = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        applyStyle();
        syncAuto();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        RUNNING = true;
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        startForegroundNotice();
        addBubble();
        syncAuto();
    }

    @Override
    public void onDestroy() {
        RUNNING = false;
        closeFollowUp();
        try {
            if (panel != null) wm.removeView(panel);
        } catch (Throwable ignore) {
        }
        try {
            if (bubble != null) wm.removeView(bubble);
        } catch (Throwable ignore) {
        }
        panel = null;
        bubble = null;
        super.onDestroy();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private GradientDrawable roundBg(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private void startForegroundNotice() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channel = "float";
        NotificationChannel c = new NotificationChannel(channel, "悬浮答题", NotificationManager.IMPORTANCE_MIN);
        c.setShowBadge(false);
        nm.createNotificationChannel(c);
        Notification n = new Notification.Builder(this, channel)
                .setContentTitle("悬浮答题已开启")
                .setContentText("点悬浮球即可截图并拿到答案")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true)
                .build();
        startForeground(7, n);
    }

    /* ---------------- 悬浮球 ---------------- */

    private void addBubble() {
        bubble = new TextView(this);
        bubble.setText("m");
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(20);
        bubble.setTypeface(null, Typeface.BOLD);
        bubble.setGravity(Gravity.CENTER);
        bubble.setIncludeFontPadding(false);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF6366F1, 0xFF8B5CF6});
        g.setShape(GradientDrawable.OVAL);
        bubble.setBackground(g);
        bubble.setElevation(dp(9));

        bubbleLp = new WindowManager.LayoutParams(
                dp(BUBBLE_DP), dp(BUBBLE_DP),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        bubbleLp.gravity = Gravity.TOP | Gravity.START;
        bubbleLp.x = getResources().getDisplayMetrics().widthPixels - dp(BUBBLE_DP + 12);
        bubbleLp.y = getResources().getDisplayMetrics().heightPixels / 2;

        wm.addView(bubble, bubbleLp);
        applyStyle();

        bubble.setOnTouchListener(new View.OnTouchListener() {
            float downRawX, downRawY;
            int startX, startY;
            long downTime;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = e.getRawX();
                        downRawY = e.getRawY();
                        startX = bubbleLp.x;
                        startY = bubbleLp.y;
                        downTime = System.currentTimeMillis();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = e.getRawX() - downRawX;
                        float dy = e.getRawY() - downRawY;
                        if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) moved = true;
                        if (moved) {
                            int maxX = getResources().getDisplayMetrics().widthPixels - dp(BUBBLE_DP);
                            int maxY = getResources().getDisplayMetrics().heightPixels - dp(BUBBLE_DP);
                            bubbleLp.x = clamp(startX + (int) dx, 0, maxX);
                            bubbleLp.y = clamp(startY + (int) dy, 0, maxY);
                            try {
                                wm.updateViewLayout(bubble, bubbleLp);
                            } catch (Throwable ignore) {
                            }
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            if (System.currentTimeMillis() - downTime > 650) {
                                toast("已关闭悬浮球，需要时从 App 重新开启");
                                stopSelf();
                            } else {
                                ask();
                            }
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private GradientDrawable bubbleGradient() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF6366F1, 0xFF8B5CF6});
        g.setShape(GradientDrawable.OVAL);
        return g;
    }

    /** 按设置刷新外观：不透明 / 半透明 / 全透明（只剩一个白色小 m） */
    private void applyStyle() {
        int style = Prefs.style(this);
        try {
            if (bubble != null) {
                if (style == Prefs.STYLE_GHOST) {
                    bubble.setBackground(null);
                    bubble.setElevation(0);
                    bubble.setTextColor(0xFFFFFFFF);
                    bubble.setTextSize(23);
                    bubble.setShadowLayer(dp(5), 0, dp(1), 0xCC000000);
                    bubble.setAlpha(1f);
                } else {
                    bubble.setBackground(bubbleGradient());
                    bubble.setElevation(dp(9));
                    bubble.setTextColor(Color.WHITE);
                    bubble.setTextSize(20);
                    bubble.setShadowLayer(0, 0, 0, 0);
                    bubble.setAlpha(style == Prefs.STYLE_TRANSLUCENT ? ALPHA_BUBBLE : ALPHA_BUBBLE_SOLID);
                }
            }
        } catch (Throwable ignore) {
        }
        try {
            if (panel != null) panel.setAlpha(style == Prefs.STYLE_SOLID ? 1f : ALPHA_PANEL);
        } catch (Throwable ignore) {
        }
    }

    /* ---------------- 答案浮层 ---------------- */

    private void ensurePanel() {
        if (panel != null) return;

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(roundBg(0xFFFFFFFF, 16));
        panel.setElevation(dp(12));
        panel.setPadding(dp(14), dp(10), dp(14), dp(12));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        panelTitle = new TextView(this);
        panelTitle.setText("AI 解答");
        panelTitle.setTextSize(14);
        panelTitle.setTypeface(null, Typeface.BOLD);
        panelTitle.setTextColor(0xFF14172B);
        head.addView(panelTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        head.addView(tinyBtn("追问", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFollowUp();
            }
        }));
        head.addView(tinyBtn("复制", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyAnswer();
            }
        }));
        head.addView(tinyBtn("重截", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ask();
            }
        }));
        head.addView(tinyBtn("关闭", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (panel != null) panel.setVisibility(View.GONE);
            }
        }));
        panel.addView(head);

        answerBadge = new TextView(this);
        answerBadge.setTextSize(26);
        answerBadge.setTypeface(null, Typeface.BOLD);
        answerBadge.setTextColor(0xFFFFFFFF);
        answerBadge.setGravity(Gravity.CENTER);
        answerBadge.setBackground(roundBg(0xFF4F46E5, 14));
        answerBadge.setPadding(dp(20), dp(5), dp(20), dp(7));
        answerBadge.setVisibility(View.GONE);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeLp.topMargin = dp(8);
        panel.addView(answerBadge, badgeLp);

        panelScroll = new ScrollView(this);
        panelText = new TextView(this);
        panelText.setTextSize(14.5f);
        panelText.setTextColor(0xFF23263B);
        panelText.setLineSpacing(dp(3), 1.05f);
        panelScroll.addView(panelText);
        panel.addView(panelScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int width = getResources().getDisplayMetrics().widthPixels - dp(24);
        panelLp = new WindowManager.LayoutParams(
                width,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        panelLp.gravity = Gravity.TOP | Gravity.START;
        panelLp.x = dp(12);
        panelLp.y = dp(90);

        wm.addView(panel, panelLp);
        panel.setVisibility(View.GONE);
        applyStyle();

        head.setOnTouchListener(new View.OnTouchListener() {
            float downY;
            int startY;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downY = e.getRawY();
                        startY = panelLp.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        panelLp.y = clamp(startY + (int) (e.getRawY() - downY), 0,
                                getResources().getDisplayMetrics().heightPixels - dp(160));
                        try {
                            wm.updateViewLayout(panel, panelLp);
                        } catch (Throwable ignore) {
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private TextView tinyBtn(String text, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12);
        t.setTextColor(0xFF4F46E5);
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(dp(9), dp(5), dp(9), dp(5));
        t.setBackground(roundBg(0xFFEEF0FF, 9));
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(6);
        t.setLayoutParams(lp);
        return t;
    }

    private void showPanelBelowBubble() {
        ensurePanel();
        int maxY = getResources().getDisplayMetrics().heightPixels - dp(360);
        panelLp.y = clamp(bubbleLp.y + dp(66), dp(20), Math.max(dp(20), maxY));
        try {
            wm.updateViewLayout(panel, panelLp);
        } catch (Throwable ignore) {
        }
        panel.setVisibility(View.VISIBLE);
    }

    private void setPanelText(String text) {
        ensurePanel();
        panelText.setText(text);
        panelTitle.setText("AI 解答");
        if (answerBadge != null) answerBadge.setVisibility(View.GONE);
        panelScroll.scrollTo(0, 0);
        fitPanelHeight();
    }

    /** 从答案文字里抠出选项字母，显示成大号角标 */
    private void updateBadge(String text) {
        if (answerBadge == null) return;
        String letters = extractLetters(text);
        if (letters == null) {
            answerBadge.setVisibility(View.GONE);
            return;
        }
        answerBadge.setText("答案 " + letters);
        answerBadge.setVisibility(View.VISIBLE);
    }

    private static final java.util.regex.Pattern P_ANSWER = java.util.regex.Pattern.compile(
            "(?:正确答案|答案)\\s*[：:]?\\s*(?:应?选|是|为)?\\s*"
                    + "([A-Ha-h](?:\\s*[、，,/和]?\\s*[A-Ha-h]){0,3})");
    private static final java.util.regex.Pattern P_PICK = java.util.regex.Pattern.compile(
            "(?:应?选择?|选)(?:了)?\\s*[：:]?\\s*"
                    + "([A-Ha-h](?:\\s*[、，,/和]?\\s*[A-Ha-h]){0,3})");

    private String extractLetters(String text) {
        if (text == null || text.isEmpty()) return null;
        String src = text.length() > 400 ? text.substring(0, 400) : text;
        java.util.regex.Matcher m = P_ANSWER.matcher(src);
        if (!m.find()) {
            m = P_PICK.matcher(src);
            if (!m.find()) return null;
        }
        String raw = m.group(1).toUpperCase();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length() && sb.length() < 4; i++) {
            char ch = raw.charAt(i);
            if (ch >= 'A' && ch <= 'H' && sb.indexOf(String.valueOf(ch)) < 0) sb.append(ch);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** 「答案：」那一行标红加粗，一眼能找到 */
    private void highlightAnswer() {
        if (panelText == null) return;
        CharSequence cs = panelText.getText();
        if (cs == null || cs.length() == 0) return;
        String s = cs.toString();
        int idx = s.indexOf("答案：");
        if (idx < 0) idx = s.indexOf("答案:");
        if (idx < 0) return;
        int end = s.indexOf('\n', idx);
        if (end < 0) end = s.length();
        SpannableString sp = new SpannableString(s);
        sp.setSpan(new ForegroundColorSpan(0xFFE11D48), idx, end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sp.setSpan(new StyleSpan(Typeface.BOLD), idx, end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        panelText.setText(sp);
        fitPanelHeight();
    }

    /** 面板随内容高度自适应，最多 260dp，避免短答案时留下一大片空白 */
    private void fitPanelHeight() {
        if (panelScroll == null || panelText == null) return;
        panelScroll.post(new Runnable() {
            @Override
            public void run() {
                int max = dp(260);
                int h = panelText.getMeasuredHeight();
                ViewGroup.LayoutParams lp = panelScroll.getLayoutParams();
                int target = Math.max(dp(40), Math.min(h, max));
                if (lp.height != target) {
                    lp.height = target;
                    panelScroll.setLayoutParams(lp);
                }
            }
        });
    }

    private void copyAnswer() {
        if (panelText == null) return;
        CharSequence text = panelText.getText();
        if (text == null || text.length() == 0) {
            toast("还没有内容");
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("AI 解答", text));
        toast("已复制");
    }

    /* ---------------- 核心流程 ---------------- */

    private void ask() {
        if (busy) {
            toast("正在回答中，稍等一下");
            return;
        }
        if (!Prefs.configured(this)) {
            toast("先回到「悬浮答题」填写 API");
            openApp();
            return;
        }
        final ShotService shot = ShotService.INSTANCE;
        if (shot == null) {
            toast("请先在系统设置里开启「悬浮答题」无障碍服务");
            openApp();
            return;
        }

        busy = true;
        if (bubble != null) bubble.setVisibility(View.GONE);
        if (panel != null) panel.setVisibility(View.GONE);

        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                shot.takeShot(new ShotService.ShotCallback() {
                    @Override
                    public void onShot(final Bitmap bitmap) {
                        ui.post(new Runnable() {
                            @Override
                            public void run() {
                                if (bubble != null) bubble.setVisibility(View.VISIBLE);
                                if (bitmap == null) {
                                    showPanelBelowBubble();
                                    setPanelText("截图失败。请确认无障碍服务已开启；部分系统需要关掉再重新开启一次。");
                                    busy = false;
                                    return;
                                }
                                requestAnswer(bitmap, "正在思考…");
                            }
                        });
                    }
                });
            }
        }, 260);
    }

    /** 把截图发给 AI，并流式写进浮层（点球提问和自动模式共用） */
    private void requestAnswer(final Bitmap bitmap, String placeholder) {
        String dataUrl = toDataUrl(bitmap);
        bitmap.recycle();
        lastDataUrl = dataUrl;
        busy = true;
        showPanelBelowBubble();
        setPanelText(placeholder);
        final boolean[] first = {true};
        final StringBuilder acc = new StringBuilder();
        ChatClient.ask(Prefs.base(this), Prefs.model(this), Prefs.key(this), dataUrl,
                Prefs.brief(this),
                Prefs.answerOnly(this),
                new ChatClient.Sink() {
                    @Override
                    public void onDelta(String text) {
                        acc.append(text);
                        String shown = Tex.plain(acc.toString());
                        if (first[0]) {
                            first[0] = false;
                            panelText.setText(shown);
                            fitPanelHeight();
                        } else {
                            panelText.setText(shown);
                        }
                        updateBadge(panelText.getText().toString());
                        panelScroll.post(new Runnable() {
                            @Override
                            public void run() {
                                panelScroll.fullScroll(View.FOCUS_DOWN);
                            }
                        });
                    }

                    @Override
                    public void onDone() {
                        busy = false;
                        if (first[0]) panelText.setText("（没有返回内容，请检查模型是否支持识图）");
                        updateBadge(panelText.getText().toString());
                        highlightAnswer();
                        fitPanelHeight();
                        Prefs.addHistory(BubbleService.this, panelText.getText().toString());
                    }

                    @Override
                    public void onError(String message) {
                        busy = false;
                        panelText.append("\n\n出错：" + message);
                        fitPanelHeight();
                    }
                });
    }

    /* ---------------- 追问 ---------------- */

    private void showFollowUp() {
        if (lastDataUrl == null) {
            toast("先点一下小球截题，再追问");
            return;
        }
        if (followWrap != null) return;

        final android.widget.FrameLayout wrap = new android.widget.FrameLayout(this);
        wrap.setBackgroundColor(0x66000000);
        wrap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeFollowUp();
            }
        });

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundBg(0xFFFFFFFF, 16));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setOnClickListener(null);

        TextView title = new TextView(this);
        title.setText("追问这道题");
        title.setTextSize(15);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(0xFF14172B);
        card.addView(title);

        final android.widget.EditText edit = new android.widget.EditText(this);
        edit.setHint("例如：为什么不是 C？第 3 步再讲讲");
        edit.setTextSize(14.5f);
        edit.setTextColor(0xFF23263B);
        edit.setHintTextColor(0xFF9AA0B6);
        edit.setBackground(roundBg(0xFFF4F5FA, 10));
        edit.setPadding(dp(12), dp(10), dp(12), dp(10));
        edit.setMaxLines(4);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.topMargin = dp(10);
        card.addView(edit, elp);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.END);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(10);

        TextView cancel = tinyBtn("取消", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeFollowUp();
            }
        });
        TextView send = tinyBtn("发送", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String q = edit.getText().toString().trim();
                if (q.isEmpty()) {
                    toast("先写要问的内容");
                    return;
                }
                closeFollowUp();
                followUpRequest(q);
            }
        });
        send.setTextColor(0xFFFFFFFF);
        send.setBackground(roundBg(0xFF4F46E5, 9));
        btns.addView(cancel);
        btns.addView(send);
        card.addView(btns, blp);

        android.widget.FrameLayout.LayoutParams clp = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.BOTTOM;
        clp.leftMargin = dp(14);
        clp.rightMargin = dp(14);
        clp.bottomMargin = dp(90);
        wrap.addView(card, clp);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                0, PixelFormat.TRANSLUCENT);
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE;
        wm.addView(wrap, lp);
        followWrap = wrap;

        edit.requestFocus();
        edit.postDelayed(new Runnable() {
            @Override
            public void run() {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager)
                                getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(edit, 0);
            }
        }, 250);
    }

    private void closeFollowUp() {
        if (followWrap == null) return;
        try {
            wm.removeView(followWrap);
        } catch (Throwable ignore) {
        }
        followWrap = null;
    }

    private void followUpRequest(final String question) {
        if (busy) {
            toast("正在回答中，稍等一下");
            return;
        }
        final String baseText = panelText == null ? "" : panelText.getText().toString();
        busy = true;
        showPanelBelowBubble();
        final StringBuilder acc = new StringBuilder();
        ChatClient.followUp(Prefs.base(this), Prefs.model(this), Prefs.key(this), lastDataUrl,
                baseText, question, new ChatClient.Sink() {
                    @Override
                    public void onDelta(String text) {
                        acc.append(text);
                        panelText.setText(baseText + "\n\n【问】" + question + "\n"
                                + Tex.plain(acc.toString()));
                        panelScroll.post(new Runnable() {
                            @Override
                            public void run() {
                                panelScroll.fullScroll(View.FOCUS_DOWN);
                            }
                        });
                    }

                    @Override
                    public void onDone() {
                        busy = false;
                        highlightAnswer();
                        fitPanelHeight();
                        Prefs.addHistory(BubbleService.this, panelText.getText().toString());
                    }

                    @Override
                    public void onError(String message) {
                        busy = false;
                        panelText.setText(baseText + "\n\n追问出错：" + message);
                        fitPanelHeight();
                    }
                });
    }

    /* ---------------- 自动模式 ---------------- */

    private boolean autoRunning = false;
    private byte[] lastSig;

    private final Runnable autoTick = new Runnable() {
        @Override
        public void run() {
            if (!autoRunning) return;
            autoShot();
            ui.postDelayed(this, Math.max(2, Prefs.autoSec(BubbleService.this)) * 1000L);
        }
    };

    private void syncAuto() {
        if (Prefs.autoOn(this)) startAuto();
        else stopAuto();
    }

    private void startAuto() {
        if (autoRunning) return;
        autoRunning = true;
        lastSig = null;
        ui.postDelayed(autoTick, 900);
    }

    private void stopAuto() {
        autoRunning = false;
        ui.removeCallbacks(autoTick);
        lastSig = null;
    }

    /** 定时截一张图：只有画面变化（换题）才请求，省费用也不刷屏 */
    private void autoShot() {
        if (busy) return;
        final ShotService shot = ShotService.INSTANCE;
        if (shot == null || !Prefs.configured(this)) return;

        final boolean panelVisible = panel != null && panel.getVisibility() == View.VISIBLE;
        if (panelVisible) panel.setAlpha(0f);

        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                shot.takeShot(new ShotService.ShotCallback() {
                    @Override
                    public void onShot(final Bitmap bitmap) {
                        ui.post(new Runnable() {
                            @Override
                            public void run() {
                                if (panelVisible && panel != null) applyStyle();
                                if (bitmap == null) return;
                                boolean changed = isChanged(bitmap);
                                if (changed && !busy) {
                                    requestAnswer(bitmap, "识别到新题目，正在解答…");
                                } else {
                                    bitmap.recycle();
                                }
                            }
                        });
                    }
                });
            }
        }, 160);
    }

    /** 用缩小后的灰度指纹判断画面是否换了题 */
    private boolean isChanged(Bitmap bmp) {
        Bitmap small;
        try {
            small = Bitmap.createScaledBitmap(bmp, 32, 64, false);
        } catch (Throwable t) {
            return true;
        }
        int[] px = new int[32 * 64];
        small.getPixels(px, 0, 32, 0, 0, 32, 64);
        small.recycle();
        byte[] sig = new byte[px.length];
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int lum = (((c >> 16) & 0xff) * 77 + ((c >> 8) & 0xff) * 150 + (c & 0xff) * 29) >> 8;
            sig[i] = (byte) (lum >> 4);
        }
        boolean changed;
        if (lastSig == null || lastSig.length != sig.length) {
            changed = true;
        } else {
            int diff = 0;
            for (int i = 0; i < sig.length; i++) {
                if (Math.abs(sig[i] - lastSig[i]) > 1) diff++;
            }
            changed = diff > sig.length * 6 / 100;
        }
        lastSig = sig;
        return changed;
    }

    private String toDataUrl(Bitmap bmp) {
        int mode = Prefs.cropMode(this);
        if (mode == 1) {
            int h = bmp.getHeight();
            int top = h * 6 / 100;
            int bottom = h * 95 / 100;
            if (bottom - top > 100) {
                Bitmap cut = Bitmap.createBitmap(bmp, 0, top, bmp.getWidth(), bottom - top);
                bmp.recycle();
                bmp = cut;
            }
        } else if (mode == 2) {
            bmp = trimBlank(bmp);
        }
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int maxSide = Prefs.maxSide(this);
        float k = Math.min(1f, maxSide / (float) Math.max(w, h));
        Bitmap scaled = k < 1f
                ? Bitmap.createScaledBitmap(bmp, Math.round(w * k), Math.round(h * k), true)
                : bmp;
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, maxSide >= 1920 ? 92 : 88, bo);
        if (scaled != bmp) scaled.recycle();
        return "data:image/jpeg;base64," + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
    }

    /**
     * 智能裁边：从四周往里逐行/逐列扫描，只有整行（整列）几乎同色（纯空白）才裁掉。
     * 题目铺满屏幕时第一行就是文字，一行都裁不掉，所以不会误切题干或选项。
     */
    private Bitmap trimBlank(Bitmap src) {
        final int w = src.getWidth();
        final int h = src.getHeight();
        final int maxTop = h * 20 / 100;
        final int maxBottom = h * 20 / 100;
        final int maxSide = w * 8 / 100;

        int[] line = new int[w];
        int top = 0;
        while (top < maxTop) {
            src.getPixels(line, 0, w, 0, top, w, 1);
            if (!isBlank(line, w)) break;
            top++;
        }
        int bottom = h;
        while (h - bottom < maxBottom) {
            src.getPixels(line, 0, w, 0, bottom - 1, w, 1);
            if (!isBlank(line, w)) break;
            bottom--;
        }

        int[] col = new int[Math.max(1, bottom - top)];
        int left = 0;
        while (left < maxSide) {
            src.getPixels(col, 0, 1, left, top, 1, bottom - top);
            if (!isBlank(col, bottom - top)) break;
            left++;
        }
        int right = w;
        while (w - right < maxSide) {
            src.getPixels(col, 0, 1, right - 1, top, 1, bottom - top);
            if (!isBlank(col, bottom - top)) break;
            right--;
        }

        if (bottom - top < 120 || right - left < 120) return src;
        if (top == 0 && left == 0 && bottom == h && right == w) return src;
        Bitmap cut = Bitmap.createBitmap(src, left, top, right - left, bottom - top);
        src.recycle();
        return cut;
    }

    /** 一行/一列采样后是否接近同一种颜色（空白边） */
    private static boolean isBlank(int[] px, int len) {
        if (len <= 0) return true;
        int step = Math.max(1, len / 64);
        int ref = px[0];
        int r0 = (ref >> 16) & 0xFF;
        int g0 = (ref >> 8) & 0xFF;
        int b0 = ref & 0xFF;
        for (int i = 0; i < len; i += step) {
            int p = px[i];
            if (Math.abs(((p >> 16) & 0xFF) - r0) > 14
                    || Math.abs(((p >> 8) & 0xFF) - g0) > 14
                    || Math.abs((p & 0xFF) - b0) > 14) {
                return false;
            }
        }
        return true;
    }

    private void openApp() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
