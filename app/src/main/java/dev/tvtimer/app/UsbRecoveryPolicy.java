package dev.tvtimer.app;

final class UsbRecoveryPolicy {
    static final String ACTION_USB_DEVICE_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";
    static final String ACTION_USB_ACCESSORY_ATTACHED = "android.hardware.usb.action.USB_ACCESSORY_ATTACHED";
    static final String ACTION_MEDIA_MOUNTED = "android.intent.action.MEDIA_MOUNTED";

    private UsbRecoveryPolicy() {
    }

    static boolean shouldRecover(String action, boolean removableMountedMedia) {
        return ACTION_USB_DEVICE_ATTACHED.equals(action)
                || ACTION_USB_ACCESSORY_ATTACHED.equals(action)
                || (ACTION_MEDIA_MOUNTED.equals(action) && removableMountedMedia);
    }
}
