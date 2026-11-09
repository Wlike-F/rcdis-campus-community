package com.hmdp.utils;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 缓存过期时间抖动工具。
 * <p>
 * 同一批缓存（例如全部商铺、全部商铺类型）如果用完全相同的 TTL，会在同一时刻集体过期，
 * 瞬间把压力全部打到数据库上，形成缓存雪崩。这里给基准 TTL 叠加正负抖动，
 * 把过期时间点打散。
 */
public final class TtlUtils {

    /** 抖动比例：基准 TTL 的 ±20% */
    private static final double JITTER_RATIO = 0.2D;

    private TtlUtils() {
    }

    /**
     * 计算带抖动的过期秒数，结果落在 [base*(1-ratio), base*(1+ratio)] 区间内。
     *
     * @param time 基准过期时间
     * @param unit 基准时间单位
     * @return 抖动后的过期秒数，至少为 1 秒
     */
    public static long jitterSeconds(long time, TimeUnit unit) {
        long base = unit.toSeconds(time);
        if (base <= 1L) {
            // 秒级以下的短缓存（如空值缓存）不做抖动，避免抖成 0
            return Math.max(base, 1L);
        }
        long span = Math.max(1L, (long) (base * JITTER_RATIO));
        return ThreadLocalRandom.current().nextLong(base - span, base + span + 1L);
    }
}
