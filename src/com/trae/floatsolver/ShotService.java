package com.trae.floatsolver;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityService.ScreenshotResult;
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * 只负责一件事：在无障碍服务开启后，为悬浮球提供一次截屏能力。
 */
public class ShotService extends AccessibilityService {

    public interface ShotCallback {
        void onShot(Bitmap bitmap);
    }

    public static volatile ShotService INSTANCE;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        INSTANCE = this;
    }

    @Override
    public void onDestroy() {
        INSTANCE = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    /**
     * 读取当前界面上的文字（无障碍节点树）。刷题 App 的题干、选项大多是原生控件，
     * 直接取纯文字发给 AI 比让它从截图里认字准得多；读不到时返回 null，调用方回退到截图。
     */
    public String readScreenText() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return null;
            StringBuilder sb = new StringBuilder();
            try {
                collect(root, sb, 0);
            } finally {
                try {
                    root.recycle();
                } catch (Throwable ignore) {
                }
            }
            String s = sb.toString().trim();
            return s.isEmpty() ? null : s;
        } catch (Throwable t) {
            return null;
        }
    }

    private void collect(AccessibilityNodeInfo node, StringBuilder sb, int depth) {
        if (node == null || depth > 40 || sb.length() > 4000) return;
        CharSequence t = node.getText();
        if (t == null || t.length() == 0) t = node.getContentDescription();
        if (t != null) {
            String s = t.toString().trim();
            if (s.length() > 0) sb.append(s).append('\n');
        }
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            AccessibilityNodeInfo child = null;
            try {
                child = node.getChild(i);
                collect(child, sb, depth + 1);
            } catch (Throwable ignore) {
            } finally {
                if (child != null) {
                    try {
                        child.recycle();
                    } catch (Throwable ignore) {
                    }
                }
            }
        }
    }

    public void takeShot(final ShotCallback callback) {
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult result) {
                    Bitmap out = null;
                    HardwareBuffer buffer = null;
                    try {
                        buffer = result.getHardwareBuffer();
                        Bitmap wrap = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
                        if (wrap != null) {
                            out = wrap.copy(Bitmap.Config.ARGB_8888, false);
                        }
                    } catch (Throwable t) {
                        out = null;
                    } finally {
                        if (buffer != null) {
                            try {
                                buffer.close();
                            } catch (Throwable ignore) {
                            }
                        }
                    }
                    callback.onShot(out);
                }

                @Override
                public void onFailure(int errorCode) {
                    callback.onShot(null);
                }
            });
        } catch (Throwable t) {
            callback.onShot(null);
        }
    }
}
