package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    /** 缓存异步重建线程池，避免每次重建都 new Thread */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(
            Math.max(4, Runtime.getRuntime().availableProcessors()));

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // 获取锁
    private boolean tryLock(String key){
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10L, TimeUnit.SECONDS));
    }
    // 释放锁
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    /**
     * 设置缓存，TTL 带随机抖动，避免同批缓存同时过期造成雪崩
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),
                TtlUtils.jitterSeconds(time, unit), TimeUnit.SECONDS);
    }

    /**
     * 缓存空对象，用于解决缓存穿透。这里写入的是裸空串而不是 JSON，
     * 保证读取侧 StrUtil.isBlank 能识别为“空值标记”。
     * @param key
     * @param time
     * @param unit
     */
    public void setNull(String key, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, "",
                TtlUtils.jitterSeconds(time, unit), TimeUnit.SECONDS);
    }

    /**
     * 逻辑过期缓存
     * @param key
     * @param value
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期时间，同样叠加抖动
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(TtlUtils.jitterSeconds(time, unit)));
        // 写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    /**
     * 缓存穿透
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param unit
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        // 设置redis key
        String key = keyPrefix + id;
        // 1、从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2、判断是否存在,存在直接返回
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);// 存在，直接返回
        }
        if (json != null){
            return null;
        }

        // 3、不存在，执行数据库操作
        R r = dbFallback.apply(id);
        // 4、存在，写入redis,设置过期时间，不存在，返回错误信息
        if (r != null) {
            // 存在，写入redis,设置过期时间
            this.set(key, r, time, unit);
            return r;
        }else {
            // 不存在,缓存空对象，用独立的短 TTL，防止缓存穿透
            this.setNull(key, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
    }

    /**
     * 逻辑过期缓存
     * @param keyPreFix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param unit
     * @return
     * @param
     * @param <ID>
     */
    public <R,ID> R queryWithLogicalExpire(String keyPreFix, ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPreFix + id;
        // 1、从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2、判断缓存命中，未命中，返回空
        if (json == null || json.isEmpty()){
            return null;
        }
        // 3、命中了，判断是否缓存过期
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);  // 获取缓存数据
        R r = JSONUtil.toBean((JSONObject) redisData.getData(),type);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())){
            return r; // 未过期，直接返回
        }
        // 过期了，缓存重建，获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        if (tryLock(lockKey)){
            // 获取锁成功，开启独立线程，实现缓存重建
            new Thread(() -> {
                try {
                    // 缓存重建,先查数据库，再插入
                    R r1 = dbFallback.apply(id);
                    this.setWithLogicalExpire(key, r1, time, unit);
                    log.debug("缓存重建成功！");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(lockKey);// 释放锁
                }
            }).start();
        }
        // 获取锁失败，返回商铺信息
        return r;
    }

    /**
     * 互斥锁重建 + 空值缓存：同时解决缓存击穿与穿透。
     * 锁前缀由调用方传入，不同实体各用各的锁，避免商铺与笔记互相抢锁。
     *
     * @param keyPrefix   缓存 key 前缀
     * @param lockPrefix  重建锁 key 前缀
     * @param id          缓存对象 id
     * @param type        缓存对象类型
     * @param dbFallback  回源函数
     * @param time        TTL 基准值（内部叠加随机抖动）
     * @param unit        TTL 单位
     */
    public <R, ID> R queryWithMutex(String keyPrefix, String lockPrefix, ID id, Class<R> type,
                                    Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 命中真实数据
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 命中空值标记，说明数据本就不存在，直接返回，不打库
        if (json != null) {
            return null;
        }

        String lockKey = lockPrefix + id;
        for (int i = 0; i < RedisConstants.CACHE_LOCK_MAX_RETRY; i++) {
            if (tryLock(lockKey)) {
                try {
                    // 拿到锁后二次检查，避免重复重建
                    String second = stringRedisTemplate.opsForValue().get(key);
                    if (StrUtil.isNotBlank(second)) {
                        return JSONUtil.toBean(second, type);
                    }
                    if (second != null) {
                        return null;
                    }
                    R r = dbFallback.apply(id);
                    if (r == null) {
                        this.setNull(key, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                        return null;
                    }
                    this.set(key, r, time, unit);
                    return r;
                } finally {
                    unLock(lockKey);
                }
            }
            // 没抢到锁：有限次等待重试，不用递归，避免重建耗时把调用栈和线程拖住
            try {
                Thread.sleep(RedisConstants.CACHE_LOCK_WAIT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        // 重试用尽仍拿不到锁，宁可回源也不返回空结果
        log.warn("获取缓存重建锁超时，直接回源: key={}", key);
        R fallback = dbFallback.apply(id);
        if (fallback == null) {
            this.setNull(key, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
        } else {
            this.set(key, fallback, time, unit);
        }
        return fallback;
    }

    /**
     * 批量读缓存：命中的直接返回，未命中的 id 合并成一次回源，再回填缓存。
     * 用于列表接口，替换逐条查询造成的 N+1。
     *
     * @param dbBatchLoader 接收未命中的 id 集合，返回 id 到对象的映射（不存在的 id 不要放进结果）
     * @return 只包含真实存在的对象，按入参 id 索引
     */
    public <R, I> Map<I, R> listWithBatchCache(String keyPrefix, Collection<I> ids, Class<R> type,
                                               Function<Collection<I>, Map<I, R>> dbBatchLoader,
                                               Long time, TimeUnit unit) {
        Map<I, R> result = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        // 去重并保持入参顺序
        List<I> uniqueIds = new ArrayList<>(new LinkedHashSet<>(ids));
        List<String> keys = new ArrayList<>(uniqueIds.size());
        for (I id : uniqueIds) {
            keys.add(keyPrefix + id);
        }
        List<String> cached = stringRedisTemplate.opsForValue().multiGet(keys);

        List<I> missed = new ArrayList<>();
        for (int i = 0; i < uniqueIds.size(); i++) {
            String json = cached == null ? null : cached.get(i);
            I id = uniqueIds.get(i);
            if (StrUtil.isNotBlank(json)) {
                result.put(id, JSONUtil.toBean(json, type));
            } else if (json == null) {
                // 既不是真实数据也不是空值标记，才需要回源
                missed.add(id);
            }
        }
        if (missed.isEmpty()) {
            return result;
        }
        Map<I, R> loaded = dbBatchLoader.apply(missed);
        for (I id : missed) {
            R value = loaded == null ? null : loaded.get(id);
            if (value == null) {
                // 空值也缓存，防止同一批不存在的 id 反复穿透
                this.setNull(keyPrefix + id, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                continue;
            }
            this.set(keyPrefix + id, value, time, unit);
            result.put(id, value);
        }
        return result;
    }

    /**
     * 用线程池异步重建，替代裸 new Thread()
     */
    public void rebuildAsync(String key, String lockKey, Runnable rebuildTask) {
        if (!tryLock(lockKey)) {
            return;
        }
        CACHE_REBUILD_EXECUTOR.submit(() -> {
            try {
                rebuildTask.run();
            } catch (Exception e) {
                log.error("缓存异步重建失败: key={}", key, e);
            } finally {
                unLock(lockKey);
            }
        });
    }

    /**
     * 删除缓存，用于数据变更后的失效
     */
    public void evict(String key) {
        stringRedisTemplate.delete(key);
    }
}
