package com.example.penhook;

import android.app.Activity;
import android.hardware.input.InputManager;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class PenHook implements IXposedHookLoadPackage {

    private static final String TAG = "PenHook";
    
    private static final int SOURCE_STYLUS = InputDevice.SOURCE_STYLUS;
    private static final int SOURCE_MOUSE = InputDevice.SOURCE_MOUSE;
    private static final int TOOL_TYPE_STYLUS = MotionEvent.TOOL_TYPE_STYLUS;
    private static final int TOOL_TYPE_ERASER = MotionEvent.TOOL_TYPE_ERASER;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        String packageName = lpparam.packageName;
        
        if (packageName.equals("com.miui.gamebooster") || 
            packageName.equals("com.miui.securitycenter") ||
            packageName.equals("com.xiaomi.gamecenter") ||
            packageName.contains("game")) {
            hookGameTurbo(lpparam);
        }
        
        if (packageName.equals("com.android.systemui")) {
            hookSystemUI(lpparam);
        }
        
        if (packageName.equals("android")) {
            hookSystemFramework(lpparam);
        }
        
        hookAllApps(lpparam);
    }

    private void hookGameTurbo(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            ClassLoader cl = lpparam.classLoader;
            
            String[] possibleClasses = {
                "com.miui.gamebooster.input.GameInputFilter",
                "com.miui.gamebooster.pen.PenBlocker",
                "com.miui.gamebooster.stylus.StylusPolicy",
                "com.miui.gamebooster.input.InputFilter",
                "com.xiaomi.gamebooster.input.GameInputFilter",
                "com.xiaomi.gamebooster.pen.PenBlocker",
                "com.miui.securitycenter.input.InputFilter",
            };
            
            for (String className : possibleClasses) {
                try {
                    Class<?> clazz = XposedHelpers.findClassIfExists(className, cl);
                    if (clazz != null) {
                        hookAllMethodsReturnFalse(clazz);
                        log("Hooked class: " + className);
                    }
                } catch (Exception e) {}
            }
            
            try {
                Class<?> gameBoosterService = XposedHelpers.findClassIfExists(
                    "com.miui.gamebooster.GameBoosterService", cl);
                if (gameBoosterService != null) {
                    hookMethodsContaining(gameBoosterService, 
                        Arrays.asList("pen", "stylus", "input", "touch"));
                }
            } catch (Exception ignored) {}

        } catch (Exception e) {
            logE("GameTurbo hook failed", e);
        }
    }

    private void hookSystemUI(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            ClassLoader cl = lpparam.classLoader;
            
            String[] gestureClasses = {
                "com.android.systemui.navigationbar.gestural.EdgeBackGestureHandler",
                "com.android.systemui.navigationbar.gestural.NavigationBarEdgePanel",
                "com.android.systemui.statusbar.phone.SystemGestures",
                "com.android.systemui.statusbar.phone.StatusBar",
            };
            
            for (String className : gestureClasses) {
                try {
                    Class<?> clazz = XposedHelpers.findClassIfExists(className, cl);
                    if (clazz != null) {
                        XposedHelpers.findAndHookMethod(clazz, "onMotionEvent",
                            MotionEvent.class, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                MotionEvent event = (MotionEvent) param.args[0];
                                if (isStylusEvent(event)) {
                                    param.setResult(null);
                                }
                            }
                        });
                        log("Hooked gesture: " + className);
                    }
                } catch (Exception ignored) {}
            }
            
            try {
                Class<?> systemGestures = XposedHelpers.findClassIfExists(
                    "com.android.systemui.statusbar.phone.SystemGestures", cl);
                if (systemGestures != null) {
                    XposedHelpers.findAndHookMethod(systemGestures, "onInputEvent",
                        InputEvent.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            InputEvent event = (InputEvent) param.args[0];
                            if (isStylusEvent(event)) {
                                param.setResult(null);
                            }
                        }
                    });
                }
            } catch (Exception ignored) {}

        } catch (Exception e) {
            logE("SystemUI hook failed", e);
        }
    }

    private void hookSystemFramework(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            ClassLoader cl = lpparam.classLoader;
            
            try {
                Class<?> inputManagerService = XposedHelpers.findClassIfExists(
                    "com.android.server.input.InputManagerService", cl);
                if (inputManagerService != null) {
                    XposedHelpers.findAndHookMethod(inputManagerService,
                        "interceptKeyBeforeDispatching",
                        android.os.IBinder.class, KeyEvent.class, int.class,
                        new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            KeyEvent event = (KeyEvent) param.args[1];
                            if (isStylusEvent(event)) {
                                param.setResult(null);
                            }
                        }
                    });
                    log("Hooked InputManagerService");
                }
            } catch (Exception ignored) {}
            
            try {
                Class<?> windowManagerService = XposedHelpers.findClassIfExists(
                    "com.android.server.wm.WindowManagerService", cl);
                if (windowManagerService != null) {
                    for (Method method : windowManagerService.getDeclaredMethods()) {
                        String name = method.getName();
                        if (name.contains("Input") || name.contains("Touch") || 
                            name.contains("Stylus") || name.contains("Pen")) {
                            try {
                                XposedHelpers.findAndHookMethod(windowManagerService, name,
                                    new XC_MethodHook() {
                                    @Override
                                    protected void beforeHookedMethod(MethodHookParam param) {
                                        for (Object arg : param.args) {
                                            if (arg instanceof InputEvent && isStylusEvent((InputEvent) arg)) {
                                            }
                                        }
                                    }
                                });
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Exception ignored) {}

        } catch (Exception e) {
            logE("SystemFramework hook failed", e);
        }
    }

    private void hookAllApps(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onWindowFocusChanged",
                boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if ((Boolean) param.args[0]) {
                        Activity activity = (Activity) param.thisObject;
                        enableStylusForWindow(activity.getWindow());
                    }
                }
            });
            
            XposedHelpers.findAndHookMethod(View.class, "dispatchTouchEvent",
                MotionEvent.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    MotionEvent event = (MotionEvent) param.args[0];
                    if (isStylusEvent(event)) {
                        fixStylusEvent(event);
                    }
                }
            });
            
            try {
                XposedHelpers.findAndHookMethod(InputDevice.class, "getSources",
                    new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        int sources = (Integer) param.getResult();
                        if ((sources & SOURCE_MOUSE) == SOURCE_MOUSE) {
                            param.setResult(sources | SOURCE_STYLUS);
                        }
                    }
                });
            } catch (Exception ignored) {}

        } catch (Exception e) {
            logE("AllApps hook failed", e);
        }
    }

    private void hookMethodsContaining(Class<?> clazz, java.util.List<String> keywords) {
        for (Method method : clazz.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            for (String keyword : keywords) {
                if (name.contains(keyword.toLowerCase())) {
                    try {
                        XposedHelpers.findAndHookMethod(clazz, method.getName(),
                            method.getParameterTypes(), new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                for (Object arg : param.args) {
                                    if (arg instanceof MotionEvent) {
                                        if (isStylusEvent((MotionEvent) arg)) {
                                            param.setResult(false);
                                            return;
                                        }
                                    }
                                }
                                if (method.getReturnType() == boolean.class) {
                                    param.setResult(false);
                                }
                            }
                        });
                        log("Hooked method: " + clazz.getName() + "." + method.getName());
                    } catch (Exception ignored) {}
                    break;
                }
            }
        }
    }

    private void hookAllMethodsReturnFalse(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getReturnType() == boolean.class && 
                method.getParameterTypes().length <= 1) {
                try {
                    XposedHelpers.findAndHookMethod(clazz, method.getName(),
                        method.getParameterTypes(), new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(false);
                        }
                    });
                } catch (Exception ignored) {}
            }
        }
    }

    private boolean isStylusEvent(InputEvent event) {
        if (event instanceof MotionEvent) {
            MotionEvent me = (MotionEvent) event;
            int toolType = me.getToolType(0);
            int source = me.getSource();
            
            return toolType == TOOL_TYPE_STYLUS 
                || toolType == TOOL_TYPE_ERASER
                || (source & SOURCE_STYLUS) == SOURCE_STYLUS
                || (source & SOURCE_MOUSE) == SOURCE_MOUSE;
        }
        return false;
    }

    private void fixStylusEvent(MotionEvent event) {
        try {
            int buttonState = event.getButtonState();
        } catch (Exception e) {
            logE("Fix stylus event failed", e);
        }
    }

    private void enableStylusForWindow(Window window) {
        try {
            View decor = window.getDecorView();
            decor.setOnHoverListener((v, event) -> {
                if (isStylusEvent(event)) {
                    return true;
                }
                return false;
            });
        } catch (Exception e) {
            logE("Enable stylus for window failed", e);
        }
    }

    private void log(String msg) {
        XposedBridge.log("[" + TAG + "] " + msg);
    }

    private void logE(String msg, Throwable t) {
        XposedBridge.log("[" + TAG + "] ERROR: " + msg);
        XposedBridge.log(t);
    }
}
