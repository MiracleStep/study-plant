package com.tianji.promotion.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponPageVO;

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

    /**
     * 分页查询优惠卷
     * @param query
     * @return
     */
    PageDTO<CouponPageVO> queryCouponPage(CouponQuery query);
}
