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

            XposedHelpers.findAndHookMethod(Window.class, "getCallback", 
                new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Window.Callback original = (Window.Callback) param.getResult();
                    if (original != null && !(original instanceof StylusCallbackProxy)) {
                        param.setResult(new StylusCallbackProxy(original));
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

    private static class StylusCallbackProxy implements Window.Callback {
        private final Window.Callback original;

        StylusCallbackProxy(Window.Callback original) {
            this.original = original;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            return original.dispatchTouchEvent(event);
        }

        @Override
        public boolean dispatchTrackballEvent(MotionEvent event) {
            return original.dispatchTrackballEvent(event);
        }

        @Override
        public boolean dispatchGenericMotionEvent(MotionEvent event) {
            return original.dispatchGenericMotionEvent(event);
        }

        @Override public void onWindowAttributesChanged(android.view.WindowManager.LayoutParams attrs) { original.onWindowAttributesChanged(attrs); }
        @Override public void onContentChanged() { original.onContentChanged(); }
        @Override public void onWindowFocusChanged(boolean hasFocus) { original.onWindowFocusChanged(hasFocus); }
        @Override public void onAttachedToWindow() { original.onAttachedToWindow(); }
        @Override public void onDetachedFromWindow() { original.onDetachedFromWindow(); }
        @Override public void onPanelClosed(int featureId, android.view.Menu menu) { original.onPanelClosed(featureId, menu); }
        @Override public boolean onCreatePanelMenu(int featureId, android.view.Menu menu) { return original.onCreatePanelMenu(featureId, menu); }
        @Override public android.view.View onCreatePanelView(int featureId) { return original.onCreatePanelView(featureId); }
        @Override public boolean onPreparePanel(int featureId, android.view.View view, android.view.Menu menu) { return original.onPreparePanel(featureId, view, menu); }
        @Override public boolean onMenuOpened(int featureId, android.view.Menu menu) { return original.onMenuOpened(featureId, menu); }
        @Override public boolean onMenuItemSelected(int featureId, android.view.MenuItem item) { return original.onMenuItemSelected(featureId, item); }
        @Override public void onActionModeStarted(android.view.ActionMode mode) { original.onActionModeStarted(mode); }
        @Override public void onActionModeFinished(android.view.ActionMode mode) { original.onActionModeFinished(mode); }
        @Override public boolean onSearchRequested(android.view.SearchEvent searchEvent) { return original.onSearchRequested(searchEvent); }
        @Override public boolean onSearchRequested() { return original.onSearchRequested(); }
        @Override public android.view.ActionMode onWindowStartingActionMode(android.view.ActionMode.Callback callback) { return original.onWindowStartingActionMode(callback); }
        @Override public android.view.ActionMode onWindowStartingActionMode(android.view.ActionMode.Callback callback, int type) { return original.onWindowStartingActionMode(callback, type); }
    }

    private void log(String msg) {
        XposedBridge.log("[" + TAG + "] " + msg);
    }

    private void logE(String msg, Throwable t) {
        XposedBridge.log("[" + TAG + "] ERROR: " + msg);
        XposedBridge.log(t);
    }
}
