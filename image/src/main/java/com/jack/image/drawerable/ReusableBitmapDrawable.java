package com.jack.image.drawerable;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;

import com.jack.image.loader.MemCacheKey;

public final class ReusableBitmapDrawable extends BitmapDrawable implements ReusableDrawable {

    private MemCacheKey key;

    public ReusableBitmapDrawable(Resources res, Bitmap bitmap) {
        super(res, bitmap);
    }

    @Override
    public MemCacheKey getMemCacheKey() {
        return key;
    }

    @Override
    public void setMemCacheKey(MemCacheKey key) {
        this.key = key;
    }
}
