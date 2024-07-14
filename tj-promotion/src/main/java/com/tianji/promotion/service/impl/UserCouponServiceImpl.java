package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.tianji.promotion.utils.CodeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
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
//@Service //进行优化了 UserCouponRedissonServiceImpl
@Slf4j
@RequiredArgsConstructor
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {

    private final CouponMapper couponMapper;
    private final StringRedisTemplate redisTemplate;
    private final IExchangeCodeService exchangeCodeService;

    @Override
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
        /*Integer count = this.lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, coupon.getId())
                .count();
        if (coupon != null && count >= coupon.getUserLimit()) {
            throw new BadRequestException("已达到领取上限");
        }
        //2.优惠卷的已发放数量 + 1
        couponMapper.incrIssueNum(id);//#TODO 采用这种方式，后期考虑并发控制
        //3.生成优惠卷
        saveUserCoupon(userId, coupon);*/
        //Long.toString().intern() intern方法是强制从常量池中取字符串。
        //只要是不同的用户id就不存在锁竞争
        //userId.toString().intern()
        synchronized (userId.toString().intern()) {
            //此处调用是非事务方法调用事务方法，事务会失效，因此需要获取代理对象调用aop增强后事务方法
            //从aop上下文中 获取当前类的代理对象 代理对象中的
            IUserCouponService userCouponServiceProxy = (IUserCouponService) AopContext.currentProxy();
            userCouponServiceProxy.checkAndCreateUserCoupon(userId, coupon, null);
//                checkAndCreateUserCoupon(userId, coupon, serialNum);//这种写法是调用原对象的
        }
    }

    @Override
    @Transactional
    public void exchangeCoupon(String code) {
        //1.校验code是否为空
        if (StringUtils.isBlank(code)) {
            throw new BadRequestException("非法参数");
        }
        //2.解析兑换码得到自增id
        long serialNum = CodeUtil.parseCode(code);//自增id
        log.debug("自增id: {}", serialNum);
        //3.判断兑换码是否已兑换 采用redis的bitmap结构 setbit key offset 1 如果方法返回为true代表兑换码已兑换
        boolean result = exchangeCodeService.updateExchangeCodeMark(serialNum, true);
        if (result == true) {
            //说明兑换码已经被兑换了
            throw new BadRequestException("兑换码已被使用");
        }
        try {
            //4.判断兑换码是否存在 根据自增id查询 主键查询
            ExchangeCode exchangeCode = exchangeCodeService.getById(serialNum);
            if (exchangeCode == null) {
                throw new BizIllegalException("兑换码不存在");
            }
            //5.判断是否过期
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expiredTime = exchangeCode.getExpiredTime();
            if (now.isAfter(expiredTime)) {
                throw new BizIllegalException("兑换码已过期");
            }
            Long userId = UserContext.getUser();
            Coupon coupon = couponMapper.selectById(exchangeCode.getExchangeTargetId());
            if (coupon == null) {
                throw new BizIllegalException("优惠卷不存在");
            }
            //6.判断是否超出限领的数量
            //7.优惠卷已发放数量+1
            //8.生成用户优惠卷
            //9.更细兑换码状态
            checkAndCreateUserCoupon(userId, coupon, serialNum);
        } catch (Exception e) {
            //10.将兑换码状态重置
            exchangeCodeService.updateExchangeCodeMark(serialNum, false);
            throw e;
        }
    }

    /**
     * 校验并生成用户卷
     * @param userId
     * @param coupon
     */
    @Transactional
    @Override
    public void checkAndCreateUserCoupon(Long userId, Coupon coupon, Long serialNum) {
        //是否超过每人领取上限
        //获取当前用户 对该优惠 已领数量 user_coupon 条件userId couponId 统计数量
        //Long.toString().intern() intern方法是强制从常量池中取字符串。
        //只要是不同的用户id就不存在锁竞争
//        synchronized (userId.toString().intern()) {
            Integer count = this.lambdaQuery()
                    .eq(UserCoupon::getUserId, userId)
                    .eq(UserCoupon::getCouponId, coupon.getId())
                    .count();
            if (coupon != null && count >= coupon.getUserLimit()) {
                throw new BadRequestException("已达到领取上限");
            }
            //2.优惠卷的已发放数量 + 1
            couponMapper.incrIssueNum(coupon.getId());//#TODO 采用这种方式，后期考虑并发控制
            //3.生成用户优惠卷
            saveUserCoupon(userId, coupon);
            //4.更新兑换码状态
            if (serialNum != null) {
                //修改兑换码的状态
                exchangeCodeService.lambdaUpdate()
                        .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                        .set(ExchangeCode::getUserId, userId)
                        .eq(ExchangeCode::getId, serialNum)
                        .update();
            }
//            throw new BadRequestException("故意报错");
//        }
    }

    @Override
    public void checkAndCreateUserCouponNew(UserCouponDTO msg) {

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
