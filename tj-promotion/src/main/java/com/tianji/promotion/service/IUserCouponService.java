package com.tianji.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.promotion.domain.po.UserCoupon;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务类
 * </p>
 *
 * @author mirac
 * @since 2024-07-11
 */
public interface IUserCouponService extends IService<UserCoupon> {

    /**
     * 领取优惠卷
     * @param id
     */
    void receiveCoupon(Long id);

    /**
     * 兑换码兑换优惠卷
     * @param code
     */
    void exchangeCoupon(String code);
}
