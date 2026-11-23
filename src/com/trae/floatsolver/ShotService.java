package com.trae.floatsolver;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityService.ScreenshotResult;
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;

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
