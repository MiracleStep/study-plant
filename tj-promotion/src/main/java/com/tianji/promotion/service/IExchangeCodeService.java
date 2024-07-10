package com.tianji.promotion.service;

import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 兑换码 服务类
 * </p>
 *
 * @author mirac
 * @since 2024-07-09
 */
public interface IExchangeCodeService extends IService<ExchangeCode> {

    /**
     * 异步生成兑换码
     * @param coupon
     */
    void asyncgenerateExchangeCode(Coupon coupon);
}
