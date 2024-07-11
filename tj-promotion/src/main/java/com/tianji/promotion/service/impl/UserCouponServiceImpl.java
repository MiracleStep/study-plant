package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.IUserCouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author mirac
 * @since 2024-07-11
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {

    private final CouponMapper couponMapper;

    @Override
    @Transactional
    public void receiveCoupon(Long id) {
        //1.根据id查询优惠卷信息 做相关校验

        //优惠卷是否存在
        if (id == null) {
            throw new BadRequestException("非法参数");
        }
        Coupon coupon = couponMapper.selectById(id);
        if (coupon == null) {
            throw new BadRequestException("优惠卷不存在");
        }

        //是否正在发放
        if (coupon.getStatus() != CouponStatus.ISSUING) {
            throw new BadRequestException("该优惠卷不在发放中");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getIssueBeginTime()) || now.isAfter(coupon.getIssueEndTime())) {
            throw new BadRequestException("该优惠卷已过期或未开始发放");
        }

        //库存是否充足
        if (coupon.getTotalNum() <= 0 || coupon.getIssueNum() >= coupon.getTotalNum()) {
            throw new BadRequestException("该优惠卷库存已不足");
        }

        //是否超过每人领取上限
        //获取当前用户 对该优惠 已领数量 user_coupon 条件userId couponId 统计数量
        Long userId = UserContext.getUser();
        Integer count = this.lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, coupon.getId())
                .count();
        if (coupon != null && count >= coupon.getUserLimit()) {
            throw new BadRequestException("已达到领取上限");
        }
        //2.优惠卷的已发放数量 + 1
        couponMapper.incrIssueNum(id);//#TODO 采用这种方式，后期考虑并发控制
        //3.生成优惠卷
        saveUserCoupon(userId, coupon);
    }

    //保存用户卷
    private void saveUserCoupon(Long userId, Coupon coupon) {
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(coupon.getId());
        LocalDateTime termBeginTime = coupon.getTermBeginTime();//优惠卷使用开始时间
        LocalDateTime termEndTime = coupon.getTermEndTime();//优惠卷使用结束时间
        if (termBeginTime == null && termEndTime == null) {
            termBeginTime = LocalDateTime.now();
            termEndTime = termBeginTime.plusDays(coupon.getTermDays());
        }
        userCoupon.setTermBeginTime(termBeginTime);
        userCoupon.setTermEndTime(termEndTime);
        this.save(userCoupon);
    }
}
