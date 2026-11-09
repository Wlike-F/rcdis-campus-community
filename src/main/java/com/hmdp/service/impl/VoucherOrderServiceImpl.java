package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherOrderService.CreateStatus;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.SECKILL_BUGHT_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_BATCH_SIZE;
import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_DLQ_STREAM;
import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_MAX_RETRY;
import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_POLL_INTERVAL_MS;
import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_RETRY_HASH;
import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_STREAM;
import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_STREAM_CONSUMER;
import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_STREAM_GROUP;
import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ApplicationContext applicationContext;

    // 创建一个Lua脚本对象，加载秒杀脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua")); // 获取Lua脚本文件
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /** 订单消息消费线程 */
    private Thread consumerThread;
    /** 消费开关，@PreDestroy 时置 false 让线程退出 */
    private volatile boolean consuming = false;
    @org.springframework.beans.factory.annotation.Value("${hmdp.seckill.consumer-enabled:true}")
    private boolean consumerEnabled;
    /**
     * 自身在容器中的 AOP 代理对象。
     * 旧实现是在请求线程里给实例字段 proxy 赋值、再由异步线程读取，消费线程可能在赋值之前就取到订单，存在竞态；
     * 这里改为惰性从容器解析，拿到的就是代理对象，@Transactional 才会生效。
     */
    private volatile IVoucherOrderService selfProxy;

    // ==================== 生命周期 ====================

    @PostConstruct
    public void startConsumer() {
        if (!consumerEnabled) {
            log.info("Seckill order consumer disabled by configuration");
            return;
        }
        ensureConsumerGroup();
        consuming = true;
        consumerThread = new Thread(this::consumeLoop, "seckill-order-consumer");
        consumerThread.setDaemon(true);
        consumerThread.start();
        log.info("秒杀订单 Stream 消费者已启动: stream={}, group={}, consumer={}",
                SECKILL_ORDER_STREAM, SECKILL_ORDER_STREAM_GROUP, SECKILL_ORDER_STREAM_CONSUMER);
    }

    @PreDestroy
    public void stopConsumer() {
        consuming = false;
        if (consumerThread != null) {
            consumerThread.interrupt();
        }
        log.info("秒杀订单 Stream 消费者停止中");
    }

    // ==================== 消费组 + 手动 ACK + 重试 + 死信 ====================

    /**
     * 消费主循环。
     * 这里用「非阻塞 XREADGROUP + 空轮询休眠」而不是 BLOCK：XREADGROUP 的 BLOCK 参数要求 Redis 6.2+，
     * 非阻塞写法在 Redis 5.0+ 都可用。
     */
    private void consumeLoop() {
        while (consuming && !Thread.currentThread().isInterrupted()) {
            int handled = 0;
            try {
                // 1、先重读本消费者未 ACK 的消息（进程重启、上一轮处理失败的都会在这里被捞回来）
                handled += redeliverPending();
                // 2、再拉取新消息
                List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                        Consumer.from(SECKILL_ORDER_STREAM_GROUP, SECKILL_ORDER_STREAM_CONSUMER),
                        StreamReadOptions.empty().count(SECKILL_ORDER_BATCH_SIZE),
                        StreamOffset.create(SECKILL_ORDER_STREAM, ReadOffset.lastConsumed()));
                if (records != null && !records.isEmpty()) {
                    for (MapRecord<String, Object, Object> record : records) {
                        handleAndAck(record);
                    }
                    handled += records.size();
                }
                if (handled == 0) {
                    Thread.sleep(SECKILL_ORDER_POLL_INTERVAL_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                if (isNoGroupError(e)) {
                    // 首次启动时 stream 还不存在导致消费组没建成，这里补建后继续
                    ensureConsumerGroup();
                }
                log.error("秒杀订单消费循环异常", e);
                sleepQuietly(1000L);
            }
        }
        log.info("秒杀订单 Stream 消费者已退出");
    }

    /**
     * XREADGROUP group consumer STREAMS key 0：读取本消费者已投递但未确认的消息
     * @return 处理的消息数
     */
    private int redeliverPending() {
        List<MapRecord<String, Object, Object>> pending = stringRedisTemplate.opsForStream().read(
                Consumer.from(SECKILL_ORDER_STREAM_GROUP, SECKILL_ORDER_STREAM_CONSUMER),
                StreamReadOptions.empty().count(SECKILL_ORDER_BATCH_SIZE),
                StreamOffset.create(SECKILL_ORDER_STREAM, ReadOffset.from("0")));
        if (pending == null || pending.isEmpty()) {
            return 0;
        }
        for (MapRecord<String, Object, Object> record : pending) {
            handleAndAck(record);
        }
        return pending.size();
    }

    /**
     * 处理单条消息，并按处理结果决定是否 XACK
     */
    private void handleAndAck(MapRecord<String, Object, Object> record) {
        RecordId recordId = record.getId();
        VoucherOrder order = parseOrder(record);
        if (order == null) {
            log.error("Stream 消息字段不合法，丢弃并 ACK: recordId={}, body={}", recordId, record.getValue());
            resetRetry(recordId);
            acknowledge(recordId);
            return;
        }
        try {
            CreateStatus status = handleVoucherOrder(order);
            if (status == CreateStatus.DUPLICATED) {
                // DB 里已经有这个 (user, voucher) 的订单：本次 Lua 扣掉的库存没有对应新订单，回补库存；
                // 用户确实已领过，已购标记保留
                restoreSeckillStock(order.getVoucherId());
                log.warn("DB 已存在订单，回补 Redis 库存并 ACK: userId={}, voucherId={}, orderId={}",
                        order.getUserId(), order.getVoucherId(), order.getId());
            } else if (status == CreateStatus.STOCK_EMPTY_IN_DB) {
                // DB 库存为 0 而 Redis 却放行了，说明两边库存不一致：以 DB 为准修正，
                // 清掉这个用户的已购标记让他还能再抢（若 DB 真的还有货），并把消息投死信留痕
                Integer dbStock = syncSeckillStockFromDb(order.getVoucherId());
                removeBoughtMark(order.getVoucherId(), order.getUserId());
                moveToDlq(record, "DB 库存为 0，已按 DB 修正 Redis 库存=" + dbStock);
                resetRetry(recordId);
                acknowledge(recordId);
                log.error("DB 库存耗尽导致订单无法创建，已修正 Redis 库存并转投死信流: recordId={}, voucherId={}, dbStock={}",
                        recordId, order.getVoucherId(), dbStock);
                return;
            }
            resetRetry(recordId);
            acknowledge(recordId);
        } catch (DuplicateKeyException e) {
            if (isUniqUserVoucherConflict(e)) {
                // 唯一索引 uniq_user_voucher 拦住并发下的重复领取：本次事务（含扣库存）已整体回滚，
                // 但 Lua 预扣的 Redis 库存要还回去；用户确实已有订单，已购标记保留
                restoreSeckillStock(order.getVoucherId());
                log.warn("唯一索引拦截到重复领取，回补 Redis 库存并 ACK: userId={}, voucherId={}, orderId={}",
                        order.getUserId(), order.getVoucherId(), order.getId());
                resetRetry(recordId);
                acknowledge(recordId);
                return;
            }
            // 其它唯一约束冲突（比如 orderId 撞主键）不是幂等重复，而是真实丢单，必须按失败重试并留痕
            log.error("非预期的唯一约束冲突，按失败处理: recordId={}, cause={}", recordId, e.getMessage());
            handleFailure(record, order, recordId, e);
        } catch (Exception e) {
            handleFailure(record, order, recordId, e);
        }
    }

    /**
     * 统一失败处理：未达上限则不 ACK 等待重投，达到上限则回补预留并转投死信流
     */
    private void handleFailure(MapRecord<String, Object, Object> record, VoucherOrder order, RecordId recordId, Exception e) {
        long retries = increaseRetry(recordId);
        if (retries >= SECKILL_ORDER_MAX_RETRY) {
            // 永久失败：订单没落库，Lua 扣掉的库存和已购标记全部回滚，用户可以重新抢购
            restoreSeckillStock(order.getVoucherId());
            removeBoughtMark(order.getVoucherId(), order.getUserId());
            moveToDlq(record, e.getMessage());
            resetRetry(recordId);
            acknowledge(recordId);
            log.error("订单处理连续失败 {} 次，已回补预留并转投死信流: recordId={}", SECKILL_ORDER_MAX_RETRY, recordId, e);
        } else {
            // 不 ACK，消息留在 PEL 里，下一轮 redeliverPending 会重新投递
            log.error("订单处理失败，等待重投: recordId={}, 已重试 {} 次", recordId, retries, e);
        }
    }

    /**
     * 判断冲突是否来自订单唯一索引 uniq_user_voucher
     */
    private boolean isUniqUserVoucherConflict(Throwable e) {
        return containsMessage(e, "uniq_user_voucher");
    }

    private CreateStatus handleVoucherOrder(VoucherOrder order) {
        // 使用Redisson 实现分布式锁，保证同一用户的订单串行处理
        RLock lock = redissonClient.getLock("lock:order:" + order.getUserId());
        if (!lock.tryLock()) {
            // 必须抛异常让消息不 ACK、稍后重投；直接 return 会把这条订单永久丢掉
            throw new IllegalStateException("获取用户订单锁失败，稍后重投");
        }
        try {
            return selfProxy().createVoucherOrder(order);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 惰性解析自身代理对象，保证异步线程里调用 createVoucherOrder 时 @Transactional 生效
     */
    private IVoucherOrderService selfProxy() {
        IVoucherOrderService proxy = this.selfProxy;
        if (proxy == null) {
            synchronized (this) {
                proxy = this.selfProxy;
                if (proxy == null) {
                    proxy = applicationContext.getBean(IVoucherOrderService.class);
                    this.selfProxy = proxy;
                    log.info("秒杀订单消费者已绑定事务代理: {}", proxy.getClass().getName());
                }
            }
        }
        return proxy;
    }

    private VoucherOrder parseOrder(MapRecord<String, Object, Object> record) {
        Map<?, ?> body = record.getValue();
        String orderId = stringValue(body, "orderId");
        String userId = stringValue(body, "userId");
        String voucherId = stringValue(body, "voucherId");
        if (StrUtil.hasBlank(orderId, userId, voucherId)) {
            return null;
        }
        try {
            return new VoucherOrder()
                    .setId(Long.valueOf(orderId))
                    .setUserId(Long.valueOf(userId))
                    .setVoucherId(Long.valueOf(voucherId));
        } catch (NumberFormatException e) {
            log.error("Stream 消息字段不是合法数字: recordId={}", record.getId(), e);
            return null;
        }
    }

    private String stringValue(Map<?, ?> body, String field) {
        Object value = body.get(field);
        return value == null ? null : value.toString();
    }

    /**
     * HINCRBY 累加重试次数
     * @return 这是第几次失败
     */
    private long increaseRetry(RecordId recordId) {
        try {
            Long times = stringRedisTemplate.opsForHash().increment(SECKILL_ORDER_RETRY_HASH, recordId.getValue(), 1L);
            return times == null ? SECKILL_ORDER_MAX_RETRY : times;
        } catch (Exception e) {
            log.error("累加重试次数失败: recordId={}", recordId, e);
            return SECKILL_ORDER_MAX_RETRY;
        }
    }

    private void resetRetry(RecordId recordId) {
        try {
            stringRedisTemplate.opsForHash().delete(SECKILL_ORDER_RETRY_HASH, recordId.getValue());
        } catch (Exception e) {
            log.warn("清理重试次数失败: recordId={}", recordId, e);
        }
    }

    /**
     * 超过重试上限，原样转投死信流，保留现场供人工排查与后续补偿
     */
    private void moveToDlq(MapRecord<String, Object, Object> record, String reason) {
        Map<String, String> body = new HashMap<>();
        record.getValue().forEach((k, v) -> body.put(String.valueOf(k), String.valueOf(v)));
        body.put("sourceRecordId", record.getId().getValue());
        body.put("failReason", StrUtil.blankToDefault(reason, "unknown"));
        body.put("deadTime", LocalDateTime.now().toString());
        try {
            stringRedisTemplate.opsForStream().add(MapRecord.create(SECKILL_ORDER_DLQ_STREAM, body));
        } catch (Exception e) {
            log.error("写入死信流失败: recordId={}", record.getId(), e);
        }
    }

    private void acknowledge(RecordId recordId) {
        try {
            stringRedisTemplate.opsForStream().acknowledge(
                    SECKILL_ORDER_STREAM, SECKILL_ORDER_STREAM_GROUP, recordId.getValue());
        } catch (Exception e) {
            log.error("XACK 失败: recordId={}", recordId, e);
        }
    }

    /**
     * XGROUP CREATE：从 0 开始，保证建组之前已经进流的消息不会被跳过。
     * 重复执行返回 BUSYGROUP，属于幂等，直接忽略。
     */
    private void ensureConsumerGroup() {
        try {
            stringRedisTemplate.opsForStream().createGroup(
                    SECKILL_ORDER_STREAM, ReadOffset.from("0"), SECKILL_ORDER_STREAM_GROUP);
            log.info("秒杀订单消费组创建成功: {}", SECKILL_ORDER_STREAM_GROUP);
        } catch (Exception e) {
            if (isBusyGroupError(e)) {
                return;
            }
            log.warn("创建消费组失败，稍后重试: {}", e.getMessage());
        }
    }

    private boolean isBusyGroupError(Exception e) {
        return containsMessage(e, "BUSYGROUP");
    }

    private boolean isNoGroupError(Exception e) {
        return containsMessage(e, "NOGROUP");
    }

    private boolean containsMessage(Throwable e, String keyword) {
        Throwable cause = e;
        while (cause != null) {
            if (cause.getMessage() != null && cause.getMessage().contains(keyword)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================== 生产：把订单投递进 Redis Stream ====================

    /**
     * 秒杀优惠券
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 从数据库中查询优惠券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("秒杀活动不存在");
        }
        //1、判断优惠券是否在秒杀时间内
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("优惠券尚未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("优惠券已过期");
        }
        // 获取当前用户ID
        Long userId = UserHolder.getUser().getId();

        // 执行LUA脚本：判库存、判重复下单，并原子扣减 Redis 库存
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        if (result == null) {
            return Result.fail("秒杀校验失败，请稍后重试");
        }
        int r = result.intValue();
        if (r != 0) {
            return Result.fail(seckillFailReason(r));
        }
        // 为0表示资格校验通过，生成订单并投递到 Redis Stream，由消费者异步落库。
        // 序列号必须用全局的 "order" 前缀：按券分序列（"order:"+voucherId）时两张券的计数器在同一秒都会从 1 开始，
        // 拼出来的 orderId 会完全相同，落库时撞主键，导致抢购成功却丢单
        long orderId = redisIdWorker.nextId("order");
        VoucherOrder voucherOrder = new VoucherOrder().setId(orderId).setUserId(userId).setVoucherId(voucherId);
        try {
            publishOrder(voucherOrder);
        } catch (Exception e) {
            // 投递失败要回补 Lua 已经扣掉的 Redis 库存和下单标记，否则这批库存永久丢失
            rollbackRedisReservation(voucherId, userId);
            log.error("秒杀订单投递 Stream 失败, voucherId={}, userId={}", voucherId, userId, e);
            return Result.fail("活动太火爆，请稍后重试");
        }

        // 返回订单ID
        return Result.ok(orderId);
    }

    /**
     * XADD：把订单投递进 Stream
     */
    private void publishOrder(VoucherOrder order) {
        Map<String, String> body = new HashMap<>(4);
        body.put("orderId", order.getId().toString());
        body.put("userId", order.getUserId().toString());
        body.put("voucherId", order.getVoucherId().toString());
        body.put("submitTime", String.valueOf(System.currentTimeMillis()));
        stringRedisTemplate.opsForStream().add(MapRecord.create(SECKILL_ORDER_STREAM, body));
    }

    /**
     * Lua 返回码翻译成业务提示
     */
    private String seckillFailReason(int code) {
        switch (code) {
            case 1:
                return "库存不足";
            case 2:
                return "不能重复下单";
            case 3:
                return "秒杀库存未预热，活动暂不可用";
            default:
                return "秒杀失败，请稍后重试";
        }
    }

    /**
     * 回滚 Lua 在 Redis 中的预留：库存 +1，并移除用户的已购标记
     */
    private void rollbackRedisReservation(Long voucherId, Long userId) {
        restoreSeckillStock(voucherId);
        removeBoughtMark(voucherId, userId);
    }

    /**
     * 补偿之一：Lua 预扣的库存没有换来一条真实订单时，把库存还给 Redis
     */
    private void restoreSeckillStock(Long voucherId) {
        try {
            stringRedisTemplate.opsForValue().increment(SECKILL_STOCK_KEY + voucherId, 1L);
            log.warn("已回补 Redis 秒杀库存: voucherId={}, 当前库存={}",
                    voucherId, stringRedisTemplate.opsForValue().get(SECKILL_STOCK_KEY + voucherId));
        } catch (Exception e) {
            log.error("回补 Redis 秒杀库存失败, voucherId={}", voucherId, e);
        }
    }

    /**
     * 补偿之二：用户最终没能成单，移除「该券已购」标记，允许他重新抢购
     */
    private void removeBoughtMark(Long voucherId, Long userId) {
        try {
            stringRedisTemplate.opsForSet().remove(SECKILL_BUGHT_KEY + voucherId, userId.toString());
        } catch (Exception e) {
            log.error("移除 Redis 已购标记失败, voucherId={}, userId={}", voucherId, userId, e);
        }
    }

    /**
     * 补偿之三：DB 与 Redis 库存不一致时，以 DB 为准修正 Redis 库存
     * @return 修正后的库存；查不到返回 null
     */
    private Integer syncSeckillStockFromDb(Long voucherId) {
        try {
            SeckillVoucher dbVoucher = seckillVoucherService.getById(voucherId);
            if (dbVoucher == null || dbVoucher.getStock() == null) {
                return null;
            }
            Integer dbStock = dbVoucher.getStock();
            stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + voucherId, dbStock.toString());
            return dbStock;
        } catch (Exception e) {
            log.error("按 DB 修正 Redis 库存失败, voucherId={}", voucherId, e);
            return null;
        }
    }

    /**
     * 创建订单(加锁)
     * @param voucherId
     * @return
     */
    @Transactional
    public synchronized Result createVoucherOrder(Long voucherId) {
        //3、下单（扣库存、创建订单）
        Long userId = UserHolder.getUser().getId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("用户已抢购过该优惠券");
        }
        //4、扣减库存
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1") // setSql()
                .eq("voucher_id", voucherId) // where条件
                .gt("stock", 0).update();
        if (!success) {
            return Result.fail("库存不足");
        }

        VoucherOrder order = new VoucherOrder();
        long order_id = redisIdWorker.nextId("order");
        order.setId(order_id);
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        order.setCreateTime(LocalDateTime.now());
        save(order);
        return Result.ok(order.getId());
    }


    /**
     * 创建订单，用于异步处理订单
     * @param order
     * @return 处理结果，消费者据此决定是否回补 Redis 预留
     */
    @Override
    @Transactional
    public CreateStatus createVoucherOrder(VoucherOrder order) {
        //3、下单（扣库存、创建订单）
        Long userId = order.getUserId();
        int count = query().eq("user_id", userId).eq("voucher_id", order.getVoucherId()).count();
        if (count > 0) {
            log.error("用户已抢购过该优惠券, userId={}, voucherId={}", userId, order.getVoucherId());
            return CreateStatus.DUPLICATED;
        }
        //4、扣减库存
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1") // setSql()
                .eq("voucher_id", order.getVoucherId()) // where条件
                .gt("stock", 0).update();
        if (!success) {
            log.error("DB 库存不足, voucherId={}", order.getVoucherId());
            return CreateStatus.STOCK_EMPTY_IN_DB;
        }
        //5、并发下两个线程的 count 都可能读到 0，此时靠唯一索引 uniq_user_voucher 拦后者：
        // save 冲突会抛 DuplicateKeyException，本次事务（含刚扣减的库存）整体回滚
        save(order);
        return CreateStatus.ORDER_CREATED;
    }

    // 原版
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 从数据库中查询优惠券信息
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        //1、判断优惠券是否在秒杀时间内
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("优惠券尚未开始");
//        }
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("优惠券已过期");
//        }
//        //2、判断优惠券库存是否充足
//        if (voucher.getStock() < 1) {
//            return Result.fail("优惠券已抢购完!");
//        }
//
//        Long userId = UserHolder.getUser().getId();
//
//        // 使用Redisson 实现分布式锁
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        boolean isLock = lock.tryLock();
//        if (!isLock) {
//            return Result.fail("不允许重复下单");
//        }
//        try {
//            // 获取代理对象（事务）
//            IVoucherOrderService currentProxy = (IVoucherOrderService) AopContext.currentProxy();
//            return currentProxy.createVoucherOrder(voucherId);
//        } finally {
//            lock.unlock();
//        }
//    }
}
