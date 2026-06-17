package com.example.penhook;

import android.app.Activity;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;

import java.util.Arrays;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class PenHook implements IXposedHookLoadPackage {

    private static final String TAG = "PenHook";

    private static final int SOURCE_STYLUS = InputDevice.SOURCE_STYLUS;
    private static final int TOOL_TYPE_STYLUS = MotionEvent.TOOL_TYPE_STYLUS;
    private static final int TOOL_TYPE_ERASER = MotionEvent.TOOL_TYPE_ERASER;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        String packageName = lpparam.packageName;

        if (packageName.equals("com.miui.gamebooster") || packageName.equals("com.miui.securitycenter")) {
            hookGameModeService(lpparam);
        }

        if (packageName.equals("com.android.systemui")) {
            hookSystemUI(lpparam);
        }

        hookGameApps(lpparam);
        hookInputManager(lpparam);
    }

    private void hookGameModeService(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> gameInputFilter = XposedHelpers.findClassIfExists(
                "com.miui.gamebooster.input.GameInputFilter", 
                lpparam.classLoader
            );

            if (gameInputFilter != null) {
                XposedHelpers.findAndHookMethod(gameInputFilter, "onInputEvent", 
                    InputEvent.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        InputEvent event = (InputEvent) param.args[0];
                        if (isStylusEvent(event)) {
                            param.setResult(null);
                        }
                    }
                });
                log("Hooked GameInputFilter");
            }

            Class<?> penBlocker = XposedHelpers.findClassIfExists(
                "com.miui.gamebooster.pen.PenBlocker",
                lpparam.classLoader
            );

            if (penBlocker != null) {
                XposedHelpers.findAndHookMethod(penBlocker, "shouldBlockPen", 
                    MotionEvent.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(false);
                    }
                });
                log("Hooked PenBlocker");
            }

            Class<?> stylusPolicy = XposedHelpers.findClassIfExists(
                "com.miui.gamebooster.stylus.StylusPolicy",
                lpparam.classLoader
            );

            if (stylusPolicy != null) {
                for (String methodName : Arrays.asList("isStylusDisabled", 
                    "shouldDisableStylus", "blockStylusInput", "interceptStylus")) {
                    try {
                        XposedHelpers.findAndHookMethod(stylusPolicy, methodName, 
                            new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                param.setResult(false);
                            }
                        });
                    } catch (NoSuchMethodError ignored) {}
                }
                log("Hooked StylusPolicy");
            }

        } catch (Exception e) {
            logE("GameMode hook failed", e);
        }
    }

    private void hookSystemUI(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> edgeBackGestureHandler = XposedHelpers.findClassIfExists(
                "com.android.systemui.navigationbar.gestural.EdgeBackGestureHandler",
                lpparam.classLoader
            );

            if (edgeBackGestureHandler != null) {
                XposedHelpers.findAndHookMethod(edgeBackGestureHandler, 
                    "onMotionEvent", MotionEvent.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        MotionEvent event = (MotionEvent) param.args[0];
                        if (isStylusEvent(event)) {
                            param.setResult(null);
                        }
                    }
                });
            }

            Class<?> systemGestures = XposedHelpers.findClassIfExists(
                "com.android.systemui.statusbar.phone.SystemGestures",
                lpparam.classLoader
            );

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

        } catch (Exception e) {
            logE("SystemUI hook failed", e);
        }
    }

    private void hookGameApps(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onWindowFocusChanged",
                boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Activity activity = (Activity) param.thisObject;
                    if (param.args[0].equals(true)) {
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

        } catch (Exception e) {
            logE("GameApp hook failed", e);
        }
    }

    private void hookInputManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> inputManager = XposedHelpers.findClassIfExists(
                "android.hardware.input.InputManager",
                lpparam.classLoader
            );

            if (inputManager != null) {
                XposedHelpers.findAndHookMethod(inputManager, "injectInputEvent",
                    InputEvent.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        InputEvent event = (InputEvent) param.args[0];
                        if (isStylusEvent(event)) {
                            param.args[1] = 0;
                        }
                    }
                });
            }

            Class<?> nativeInputManager = XposedHelpers.findClassIfExists(
                "com.android.server.input.NativeInputManager",
                lpparam.classLoader
            );

            if (nativeInputManager != null) {
                XposedHelpers.findAndHookMethod(nativeInputManager, 
                    "interceptKeyBeforeDispatching",
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

        } catch (Exception e) {
            logE("InputManager hook failed", e);
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
                || (source & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE;
        }
        return false;
    }

    private void fixStylusEvent(MotionEvent event) {
        try {
            int buttonState = event.getButtonState();
            if ((buttonState & MotionEvent.BUTTON_STYLUS_PRIMARY) != 0) {
                // 保留侧键状态
            }
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
