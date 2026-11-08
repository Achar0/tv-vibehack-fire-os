package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/** Settings > Phone control: the QR that opens the phone page. */
public class PhoneLinkActivity extends Activity {
    private static final int QR_PX = 640;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    @SuppressWarnings("deprecation")   // getConnectionInfo is the way to read this on Android 11's Wi-Fi API
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.BLACK);

        TextView hint = new TextView(this);
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(24);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint);

        String token = PhoneServer.start(this);
        int ip = ((WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE)).getConnectionInfo().getIpAddress();
        if (token == null || ip == 0) {
            hint.setText(R.string.not_available);
        } else {
            String url = "http://" + (ip & 255) + "." + (ip >> 8 & 255) + "." + (ip >> 16 & 255) + "." + (ip >>> 24 & 255)
                    + ":" + PhoneServer.PORT + "/?t=" + token;
            hint.setText(R.string.phone_scan);
            ImageView qr = new ImageView(this);
            qr.setImageBitmap(qrCode(url));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(QR_PX, QR_PX);
            lp.topMargin = 32;
            root.addView(qr, lp);
        }
        setContentView(root);
    }

    private static Bitmap qrCode(String text) {
        try {
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, QR_PX, QR_PX);
            Bitmap b = Bitmap.createBitmap(QR_PX, QR_PX, Bitmap.Config.RGB_565);
            for (int y = 0; y < QR_PX; y++) {
                for (int x = 0; x < QR_PX; x++) b.setPixel(x, y, m.get(x, y) ? Color.BLACK : Color.WHITE);
            }
            return b;
        } catch (WriterException e) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565);
        }
    }
}
