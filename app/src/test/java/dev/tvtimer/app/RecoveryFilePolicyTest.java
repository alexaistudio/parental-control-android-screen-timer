package dev.tvtimer.app;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RecoveryFilePolicyTest {
    @Test
    public void acceptsRecoveryNameRegardlessOfCaseOrTxtExtension() {
        assertTrue(RecoveryFilePolicy.isRecoveryFileName("Recovery"));
        assertTrue(RecoveryFilePolicy.isRecoveryFileName("RECOVERY.TXT"));
        assertTrue(RecoveryFilePolicy.isRecoveryFileName("file recovery"));
        assertTrue(RecoveryFilePolicy.isRecoveryFileName("File Recovery.txt"));
    }

    @Test
    public void rejectsUnrelatedFiles() {
        assertFalse(RecoveryFilePolicy.isRecoveryFileName(null));
        assertFalse(RecoveryFilePolicy.isRecoveryFileName("recover.txt"));
        assertFalse(RecoveryFilePolicy.isRecoveryFileName("Recovery.jpg"));
    }
}
