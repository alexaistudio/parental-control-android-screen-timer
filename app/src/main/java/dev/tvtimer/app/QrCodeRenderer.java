package dev.tvtimer.app;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

final class QrCodeRenderer {
    private QrCodeRenderer() {
    }

    static Bitmap render(String content, int sizePixels) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 2);
        try {
            BitMatrix matrix = new QRCodeWriter().encode(
                    content,
                    BarcodeFormat.QR_CODE,
                    sizePixels,
                    sizePixels,
                    hints
            );
            int[] pixels = new int[sizePixels * sizePixels];
            for (int y = 0; y < sizePixels; y++) {
                for (int x = 0; x < sizePixels; x++) {
                    pixels[y * sizePixels + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }
            return Bitmap.createBitmap(pixels, sizePixels, sizePixels, Bitmap.Config.RGB_565);
        } catch (WriterException exception) {
            throw new IllegalStateException("Unable to create QR code", exception);
        }
    }
}
