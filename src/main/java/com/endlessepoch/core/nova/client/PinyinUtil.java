/*
 * Pinyin search powered by PinIn (MIT License)
 * Copyright (c) 2023 Juntong Liu (Towdium)
 * https://github.com/Towdium/PinIn
 */
package com.endlessepoch.core.nova.client;

import me.towdium.pinin.PinIn;

public final class PinyinUtil {

    private static final PinIn ctx = new PinIn();

    static {
        ctx.config()
                .fZh2Z(true).fSh2S(true).fCh2C(true)
                .fAng2An(true).fIng2In(true).fEng2En(true)
                .accelerate(true)
                .commit();
    }

    public static boolean matches(String query, String target) {
        if (query == null || target == null || query.length() < 2) return false;
        return ctx.contains(target, query);
    }
}
