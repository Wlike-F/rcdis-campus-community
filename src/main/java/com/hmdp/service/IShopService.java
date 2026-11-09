package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    Result queryById(Long id);

    Result updateShop(Shop shop);

    /**
     * 按类型分页查询商铺，可选按坐标做 GEO 周边检索
     *
     * @param typeId  商铺类型
     * @param current 页码
     * @param x       中心点经度，为空则不走 GEO
     * @param y       中心点纬度，为空则不走 GEO
     * @param radius  检索半径（米），为空或非法时取默认值
     */
    Result queryShopByType(Integer typeId, Integer current, Double x, Double y, Double radius);
}
