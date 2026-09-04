package dev.tvtimer.app;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class UsbRecoveryPolicyTest {
    @Test
    public void ordinaryUsbEventsNeverTriggerRecovery() {
        assertFalse(UsbRecoveryPolicy.shouldOpenRecovery(
                "android.hardware.usb.action.USB_DEVICE_ATTACHED",
                true
        ));
        assertFalse(UsbRecoveryPolicy.shouldOpenRecovery(
                "android.hardware.usb.action.USB_ACCESSORY_ATTACHED",
                true
        ));
    }

    @Test
    public void mountedMediaRequiresRecoveryFile() {
        assertTrue(UsbRecoveryPolicy.shouldOpenRecovery(
                UsbRecoveryPolicy.ACTION_MEDIA_MOUNTED,
                true
        ));
        assertFalse(UsbRecoveryPolicy.shouldOpenRecovery(
                UsbRecoveryPolicy.ACTION_MEDIA_MOUNTED,
                false
        ));
        assertFalse(UsbRecoveryPolicy.shouldOpenRecovery("unexpected", true));
    }
}
