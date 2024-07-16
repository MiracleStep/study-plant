package com.tianji.promotion.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.discount.Discount;
import com.tianji.promotion.discount.DiscountStrategy;
import com.tianji.promotion.domain.dto.CouponDiscountDTO;
import com.tianji.promotion.domain.dto.OrderCourseDTO;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.enums.MyLockType;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.tianji.promotion.utils.CodeUtil;
import com.tianji.promotion.utils.MyLock;
import com.tianji.promotion.utils.MyLockStrategy;
import com.tianji.promotion.utils.PermuteUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
public class UserCouponMqServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {

    private final CouponMapper couponMapper;
    private final StringRedisTemplate redisTemplate;
    private final IExchangeCodeService exchangeCodeService;
    private final RedissonClient redissonClient;
    private final RabbitMqHelper mqHelper;
    private final ICouponScopeService couponScopeService;

    @Override
    //分布式锁可以对优惠卷id加锁
    @MyLock(name = "lock:coupon:uid:#{id}")//现在改成异步的了，这部分操作redis，所以要加锁。收到消息说明肯定可以新增优惠卷
    public void receiveCoupon(Long id) {
        //1.根据id查询优惠卷信息 做相关校验
        //优惠卷是否存在
        if (id == null) {
            throw new BadRequestException("非法参数");
        }
        //从数据库获取优惠卷信息
//        Coupon coupon = couponMapper.selectById(id);
        // 从redis中获取优惠卷信息
        Coupon coupon = queryCouponByCache(id);
        if (coupon == null) {
            throw new BadRequestException("优惠卷不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getIssueBeginTime()) || now.isAfter(coupon.getIssueEndTime())) {
            throw new BadRequestException("该优惠卷已过期或未开始发放");
        }

        //库存是否充足
//        if (coupon.getTotalNum() <= 0 || coupon.getIssueNum() >= coupon.getTotalNum()) {
        if (coupon.getTotalNum() <= 0) {
            throw new BadRequestException("该优惠卷库存已不足");
        }

        //是否超过每人领取上限
        //获取当前用户 对该优惠 已领数量 user_coupon 条件userId couponId 统计数量
        Long userId = UserContext.getUser();

        //统计已领取数量
        String key = PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX + id;//prs:user:coupon:优惠卷id
        //返回已领取后的领取数量
        Long increment = redisTemplate.opsForHash().increment(key, userId.toString(), 1);
        //校验是否超过限领数量
        if (increment > coupon.getUserLimit()) {
            throw new BizIllegalException("超出限领数量！");
        }

        //修改优惠卷库存 -1
        String couponKey = PromotionConstants.COUPON_CACHE_KEY_PREFIX + id;
        redisTemplate.opsForHash().increment(couponKey, "totalNum", -1);
        //发送消息到mq 消息的内容为 userId couponId
        UserCouponDTO msg = new UserCouponDTO();
        msg.setUserId(userId);
        msg.setCouponId(id);
        mqHelper.send(MqConstants.Exchange.PROMOTION_EXCHANGE, MqConstants.Key.COUPON_RECEIVE, msg);
    }

    /**
     * 从redis获取优惠卷信息（领取开始恶化结束时间、发行总数量、限领数量）
     * @param id
     * @return
     */
    private Coupon queryCouponByCache(Long id) {
        //1.拼接key
        String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + id;
        //2.从redis获取信息
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        Coupon coupon = BeanUtils.mapToBean(entries, Coupon.class, false, CopyOptions.create());
        return coupon;
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
    @MyLock(name = "lock:coupon:uid:#{userId}", lockType = MyLockType.RE_ENTRANT_LOCK, lockStrategy = MyLockStrategy.FAIL_FAST)
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

    @Transactional
    @Override
    public void checkAndCreateUserCouponNew(UserCouponDTO msg) {
        //1.从db中查询优惠卷信息
        Coupon coupon = couponMapper.selectById(msg.getCouponId());
        if (coupon == null) {
            return;
        }
        //2.优惠卷的已发放数量 + 1
        int num = couponMapper.incrIssueNum(coupon.getId());
        if (num == 0) {
            return;
        }
        //3.生成用户优惠卷
        saveUserCoupon(msg.getUserId(), coupon);
    }

    @Override
    public List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> courses) {
        //1.查询当前用户可用的优惠卷 coupon 和 user_coupon表 条件：userId、status = 1
        List<Coupon> coupons = this.baseMapper.queryMyCoupon(UserContext.getUser());
        if (CollUtils.isEmpty(coupons)) {
            return CollUtils.emptyList();
        }
        log.debug("用户的优惠卷共有 {} 张", coupons.size());
        //2.初筛 (筛选掉totalAmount < 优惠卷可用阈值金额)
        //2.1计算订单的总金额，对course的price累加
        int totalAmount = courses.stream().mapToInt(OrderCourseDTO::getPrice).sum();
        //2.2校验优惠卷是否可用
        List<Coupon> availableCoupons = coupons.stream()
                .filter(coupon -> DiscountStrategy.getDiscount(coupon.getDiscountType()).canUse(totalAmount, coupon))
                .collect(Collectors.toList());
        if (CollUtils.isEmpty(availableCoupons)) {
            return CollUtils.emptyList();
        }
        log.debug("经过初筛之后，还是 {} 张", availableCoupons.size());
        //3.细筛 （需要考虑优惠卷的限定范围）排列组合
        //优惠卷及其可用的课程集合
        Map<Coupon, List<OrderCourseDTO>> avaMap = findAvailableCoupons(availableCoupons, courses);
        if (avaMap.isEmpty()) {
            return CollUtils.emptyList();
        }
        availableCoupons = new ArrayList<>(avaMap.keySet());//才是真正可用优惠卷集合
        log.debug("经过细筛之后的优惠卷的个数: {}", availableCoupons.size());
        for (Coupon coupon : availableCoupons) {
            log.debug("优惠卷：{}, {}",
                    DiscountStrategy.getDiscount(coupon.getDiscountType()).getRule(coupon),
                    coupon);
        }
        /**
         * 排列组合
         * [1664678776523046914, 1664679070791221250]
         * [1664679070791221250, 1664678776523046914]
         * [1664678776523046914]
         * [1664679070791221250]
         */
        List<List<Coupon>> solutions = PermuteUtil.permute(availableCoupons);
        for (Coupon availableCoupon : availableCoupons) {
            solutions.add(List.of(availableCoupon));//添加单卷到方案中
        }
        log.debug("排列组合");
        for (List<Coupon> solution : solutions) {
            List<Long> cids = solution.stream().map(Coupon::getId).collect(Collectors.toList());
            log.debug("{}", cids);
        }
        //4.计算每一种组合的优惠明细
        //5.使用多线程改造第4步 并行计算每一种组合的优惠明细
        //5.筛选最优解
        return null;
    }

    /**
     * 细筛，查询每一个优惠卷 对应的可用课程
     * @param coupons 细筛之后的优惠卷集合
     * @param orderCourses 订单中的课程集合
     * @return 优惠卷及其可用的课程集合
     */
    private Map<Coupon, List<OrderCourseDTO>> findAvailableCoupons(List<Coupon> coupons, List<OrderCourseDTO> orderCourses) {
        Map<Coupon, List<OrderCourseDTO>> map = new HashMap<>();
        //1.循环遍历初筛后的优惠卷集合
        for (Coupon coupon : coupons) {
            //2.找出每一个优惠卷的可用课程
            List<OrderCourseDTO> availableCourses = orderCourses;
            //2.1找出每一个优惠卷的限定范围 coupon.specific 为 true
            if (coupon.getSpecific()) {
                //2.2查询限定范围 查询coupon_scope表，条件coupon_id
                List<CouponScope> scopeList = couponScopeService.lambdaQuery()
                        .eq(CouponScope::getCouponId, coupon.getId())
                        .list();
                //2.3得到限定范围的id集合
                List<Long> scopeIds = scopeList.stream().map(CouponScope::getBizId).collect(Collectors.toList());
                //2.4从 orderCourses 订单中所有的课程集合 筛选该范围内的课程
                availableCourses = orderCourses.stream()
                        .filter(orderCourseDTO -> scopeIds.contains(orderCourseDTO.getCateId()))
                        .collect(Collectors.toList());
            }
            if (CollUtils.isEmpty(availableCourses)) {
                continue;//说明当前优惠卷限定了分类课程使用范围，但是在订单中的课程没有找到可用的课程说明该卷不可用，忽略改卷，进行下一个优惠卷的处理
            }
            //3.计算该优惠卷可用课程的总金额
            int totalAmount = availableCourses.stream().mapToInt(OrderCourseDTO::getPrice).sum();
            //4.判断该优惠卷是否可用
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if (discount.canUse(totalAmount, coupon)) {
                map.put(coupon, availableCourses);
            }
        }
        return map;
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
