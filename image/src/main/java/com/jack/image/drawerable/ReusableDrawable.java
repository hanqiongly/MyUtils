package com.jack.image.drawerable;

import com.jack.image.loader.MemCacheKey;

/**
 * Created by wyouflf on 15/10/20.
 * 使已被LruCache移除, 但还在被ImageView使用的Drawable可以再次被回收使用.
 */
public interface ReusableDrawable {

    MemCacheKey getMemCacheKey();

    void setMemCacheKey(MemCacheKey key);
}
