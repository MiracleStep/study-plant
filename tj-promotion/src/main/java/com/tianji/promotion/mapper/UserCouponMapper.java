package com.tianji.promotion.mapper;

import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.UserCoupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 Mapper 接口
 * </p>
 *
 * @author mirac
 * @since 2024-07-11
 */
public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    @Select("SELECT\n" +
            "\tc.id,\n" +
            "\tc.discount_type,\n" +
            "\tc. `specific`,\n" +
            "\tc.threshold_amount,\n" +
            "\tc.discount_value,\n" +
            "\tc.max_discount_amount,\n" +
            "\tuc.id AS creater\n" +
            "FROM\n" +
            "\tcoupon c\n" +
            "\tINNER JOIN user_coupon uc ON c.id = uc.coupon_id\n" +
            "WHERE\n" +
            "\tuc.user_id = #{userId} AND uc.`status`=1")
    List<Coupon> queryMyCoupon(Long userId);
}
