package com.hmdp.utils;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 随机 TTL 的分布校验：全部落在基准值 ±20% 内，且多次调用取值足够分散（防雪崩的前提）。
 */
public class TtlUtilsTest {

    @Test
    public void jitterShouldStayWithinRatioRange() {
        long baseSeconds = TimeUnit.MINUTES.toSeconds(30); // 30 分钟 = 1800 秒
        long span = baseSeconds / 5; // ±20%
        for (int i = 0; i < 1000; i++) {
            long ttl = TtlUtils.jitterSeconds(30L, TimeUnit.MINUTES);
            Assert.assertTrue("超出抖动下界: " + ttl, ttl >= baseSeconds - span);
            Assert.assertTrue("超出抖动上界: " + ttl, ttl <= baseSeconds + span);
        }
    }

    @Test
    public void jitterShouldSpreadValues() {
        long base = TimeUnit.MINUTES.toSeconds(30);
        Set<Long> distinct = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            distinct.add(TtlUtils.jitterSeconds(base, TimeUnit.MINUTES));
        }
        // 固定 TTL 时这里只会得到 1，抖动后应明显打散
        Assert.assertTrue("取值过于集中: " + distinct.size(), distinct.size() > 50);
    }

    @Test
    public void shortTtlShouldNotBeJitteredToZero() {
        // 1 秒及以下的短缓存不抖动，避免抖成 0
        Assert.assertEquals(1L, TtlUtils.jitterSeconds(1L, TimeUnit.SECONDS));
        Assert.assertEquals(1L, TtlUtils.jitterSeconds(0L, TimeUnit.SECONDS));
        // 2 分钟（120 秒）会被打散到 [96, 144]
        for (int i = 0; i < 200; i++) {
            long ttl = TtlUtils.jitterSeconds(2L, TimeUnit.MINUTES);
            Assert.assertTrue("超出抖动区间: " + ttl, ttl >= 96L && ttl <= 144L);
        }
    }
}
