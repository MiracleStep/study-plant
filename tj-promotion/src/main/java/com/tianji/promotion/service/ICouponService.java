package com.tianji.promotion.service;

import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 优惠券的规则信息 服务类
 * </p>
 *
 * @author mirac
 * @since 2024-07-09
 */
public interface ICouponService extends IService<Coupon> {

    /**
     * 新增优惠卷
     * @param dto
     */
    void saveCoupon(CouponFormDTO dto);
}
