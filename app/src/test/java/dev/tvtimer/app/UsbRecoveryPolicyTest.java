package dev.tvtimer.app;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class UsbRecoveryPolicyTest {
    @Test
    public void directUsbAttachAlwaysTriggersRecovery() {
        assertTrue(UsbRecoveryPolicy.shouldRecover(
                UsbRecoveryPolicy.ACTION_USB_DEVICE_ATTACHED,
                false
        ));
        assertTrue(UsbRecoveryPolicy.shouldRecover(
                UsbRecoveryPolicy.ACTION_USB_ACCESSORY_ATTACHED,
                false
        ));
    }

    @Test
    public void mediaMountTriggersOnlyForRemovableStorage() {
        assertTrue(UsbRecoveryPolicy.shouldRecover(
                UsbRecoveryPolicy.ACTION_MEDIA_MOUNTED,
                true
        ));
        assertFalse(UsbRecoveryPolicy.shouldRecover(
                UsbRecoveryPolicy.ACTION_MEDIA_MOUNTED,
                false
        ));
        assertFalse(UsbRecoveryPolicy.shouldRecover("unexpected", true));
    }
}
