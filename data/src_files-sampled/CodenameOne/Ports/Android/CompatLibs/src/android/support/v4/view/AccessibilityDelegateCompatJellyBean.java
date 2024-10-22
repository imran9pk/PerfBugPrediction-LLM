package android.support.v4.view;

import android.os.Bundle;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

class AccessibilityDelegateCompatJellyBean {

    public interface AccessibilityDelegateBridgeJellyBean {
        public boolean dispatchPopulateAccessibilityEvent(View host, AccessibilityEvent event);
        public void onInitializeAccessibilityEvent(View host, AccessibilityEvent event);
        public void onInitializeAccessibilityNodeInfo(View host, Object info);
        public void onPopulateAccessibilityEvent(View host, AccessibilityEvent event);
        public boolean onRequestSendAccessibilityEvent(ViewGroup host, View child,
            AccessibilityEvent event);
        public void sendAccessibilityEvent(View host, int eventType);
        public void sendAccessibilityEventUnchecked(View host, AccessibilityEvent event);
        public Object getAccessibilityNodeProvider(View host);
        public boolean performAccessibilityAction(View host, int action, Bundle args);
    }

    public static Object newAccessibilityDelegateBridge(
            final AccessibilityDelegateBridgeJellyBean bridge) {
        return new AccessibilityDelegate() {

            @Override
            public boolean dispatchPopulateAccessibilityEvent(View host, AccessibilityEvent event) {
                return bridge.dispatchPopulateAccessibilityEvent(host, event);
            }

            @Override
            public void onInitializeAccessibilityEvent(View host, AccessibilityEvent event) {
                bridge.onInitializeAccessibilityEvent(host, event);
            }

            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                bridge.onInitializeAccessibilityNodeInfo(host, info);
            }

            @Override
            public void onPopulateAccessibilityEvent(View host, AccessibilityEvent event) {
                bridge.onPopulateAccessibilityEvent(host, event);
            }

            @Override
            public boolean onRequestSendAccessibilityEvent(ViewGroup host, View child,
                    AccessibilityEvent event) {
                return bridge.onRequestSendAccessibilityEvent(host, child, event);
            }

            @Override
            public void sendAccessibilityEvent(View host, int eventType) {
                bridge.sendAccessibilityEvent(host, eventType);
            }

            @Override
            public void sendAccessibilityEventUnchecked(View host, AccessibilityEvent event) {
                bridge.sendAccessibilityEventUnchecked(host, event);
            }

            @Override
            public AccessibilityNodeProvider getAccessibilityNodeProvider(View host) {
                return (AccessibilityNodeProvider) bridge.getAccessibilityNodeProvider(host);
            }

            @Override
            public boolean performAccessibilityAction(View host, int action, Bundle args) {
                return bridge.performAccessibilityAction(host, action, args);
            }
        };
    }

    public static Object getAccessibilityNodeProvider(Object delegate,
            View host) {
        return ((AccessibilityDelegate) delegate).getAccessibilityNodeProvider(host);
    }

    public static boolean performAccessibilityAction(Object delegate, View host, int action,
            Bundle args) {
        return ((AccessibilityDelegate) delegate).performAccessibilityAction(host, action, args);
    }
}
