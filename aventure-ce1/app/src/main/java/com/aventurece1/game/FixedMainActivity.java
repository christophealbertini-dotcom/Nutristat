package com.aventurece1.game;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/** Startup-safe activity for the final graphical build. */
public class FixedMainActivity extends MainActivity {
    @Override
    Bitmap atlas() {
        if (atlas == null) {
            Bitmap source = BitmapFactory.decodeResource(getResources(), R.drawable.final_charter);
            if (source == null) {
                // Last-resort bitmap prevents a startup crash if the drawable cannot decode.
                atlas = Bitmap.createBitmap(1536, 1024, Bitmap.Config.ARGB_8888);
            } else if (source.getWidth() < 1536 || source.getHeight() < 1024) {
                atlas = Bitmap.createScaledBitmap(source, 1536, 1024, true);
                if (atlas != source) source.recycle();
            } else {
                atlas = source;
            }
        }
        return atlas;
    }
}
