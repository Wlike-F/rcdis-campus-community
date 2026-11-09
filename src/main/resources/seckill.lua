-- 1、参数列表
-- 1-1参数列表 优惠劵id
local voucherId = ARGV[1]
-- 1-2参数列表 用户id
local userId = ARGV[2]

-- 2、数据key
-- 2-1 库存key：按券维度
local stockKey = "seckill:stock:" .. voucherId
-- 2-2 已购用户集合key：同样按券维度，成员是 userId
--     这样 Redis 的“一人一单”判定粒度与数据库唯一索引 uniq_user_voucher(user_id, voucher_id) 一致，
--     同一用户可以抢购不同券，不会再被用户级的 key 误拦
local boughtKey = "seckill:bought:" .. voucherId

-- 3、脚本逻辑
-- 3-1 库存未预热时返回业务错误码，避免 tonumber(nil) 让脚本报错
local stock = tonumber(redis.call("get", stockKey))
if (stock == nil) then
    -- 库存未初始化
    return 3
end

-- 3-2 判断库存是否充足
if (stock <= 0) then
    -- 库存不足
    return 1
end

-- 3-3 判断用户是否已抢过该券
if (redis.call("sismember", boughtKey, userId) == 1) then
    -- 用户重复下单
    return 2
end

-- 3-4 扣减库存
redis.call("incrby", stockKey, -1)
-- 3-5 下单成功，登记该券的已购用户
redis.call("sadd", boughtKey, userId)
return 0