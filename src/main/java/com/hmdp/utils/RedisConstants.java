package com.hmdp.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 30L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:"; // 秒杀商品库存key（后接 voucherId）
    public static final String SECKILL_BUGHT_KEY = "seckill:bought:"; // 某张券的已下单用户集合key（后接 voucherId，成员为 userId）
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
    public static final String FOLLOWS_KEY = "follows:";


    public static final String CACHE_SHOP_TYPE_KEY = "cache:shop:type:"; // 缓存商铺类型key
    public static final Long CACHE_SHOP_TYPE_TTL = 3600L; // 缓存商铺类型TTL

    // ==================== 秒杀订单 Redis Stream ====================
    public static final String SECKILL_ORDER_STREAM = "seckill:order:stream"; // 订单消息流
    public static final String SECKILL_ORDER_STREAM_GROUP = "seckill-order-group"; // 消费者组
    // 消费者名必须稳定：重启后用同名读取自己的 pending，才能找回未 ACK 的消息
    public static final String SECKILL_ORDER_STREAM_CONSUMER = "seckill-consumer-1";
    public static final String SECKILL_ORDER_DLQ_STREAM = "seckill:order:dlq"; // 死信流
    public static final String SECKILL_ORDER_RETRY_HASH = "seckill:order:retry"; // 重试计数
    public static final long SECKILL_ORDER_MAX_RETRY = 3L; // 超过次数转投死信
    public static final long SECKILL_ORDER_BATCH_SIZE = 10L; // 单次拉取消息数
    public static final long SECKILL_ORDER_POLL_INTERVAL_MS = 200L; // 空轮询间隔

    // ==================== 社区内容缓存 ====================
    public static final String CACHE_BLOG_KEY = "cache:blog:"; // 笔记详情缓存key
    public static final Long CACHE_BLOG_TTL = 30L; // 笔记详情缓存TTL（分钟）
    public static final String LOCK_BLOG_KEY = "lock:blog:"; // 笔记缓存重建互斥锁，独立于商铺锁
    public static final String CACHE_BLOG_USER_KEY = "cache:blog:user:"; // 笔记作者信息缓存key
    public static final Long CACHE_BLOG_USER_TTL = 60L; // 作者信息缓存TTL（分钟）
    public static final String BLOG_HOT_KEY = "blog:hot"; // 热榜 ZSet，score 为点赞数
    public static final int BLOG_HOT_LIMIT = 200; // 热榜 ZSet 最多保留条目数
    public static final long CACHE_LOCK_WAIT_MS = 50L; // 重建锁重试间隔
    public static final int CACHE_LOCK_MAX_RETRY = 20; // 重建锁最大重试次数
}
