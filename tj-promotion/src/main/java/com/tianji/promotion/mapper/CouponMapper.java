package com.tianji.promotion.mapper;

import com.tianji.promotion.domain.po.Coupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 * 优惠券的规则信息 Mapper 接口
 * </p>
 *
 * @author mirac
 * @since 2024-07-09
 */
public interface CouponMapper extends BaseMapper<Coupon> {

    //更新优惠卷已领取数量
    @Update("update coupon set issue_num = issue_num + 1 where id = #{id}")
    void incrIssueNum(@Param("id") Long id);
}
