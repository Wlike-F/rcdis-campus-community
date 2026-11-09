package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 异步下单的处理结果，消费者据此决定要不要回补 Redis 预留
     */
    enum CreateStatus {
        /** 订单已落库 */
        ORDER_CREATED,
        /** 数据库里该 (user, voucher) 已有订单：本次没有产生新订单，需要回补库存但保留购买标记 */
        DUPLICATED,
        /** 数据库库存已耗尽：Redis 与 DB 库存不一致，需要以 DB 为准修正 */
        STOCK_EMPTY_IN_DB
    }

    Result seckillVoucher(Long voucherId);

    Result createVoucherOrder(Long voucherId);

    CreateStatus createVoucherOrder(VoucherOrder order);
}
