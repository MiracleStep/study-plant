package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.*;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ObtainType;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 优惠券的规则信息 服务实现类
 * </p>
 *
 * @author mirac
 * @since 2024-07-09
 */
@Service
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {

    private final ICouponScopeService couponScopeService;
    private final IExchangeCodeService exchangeCodeService;
    private final IUserCouponService userCouponService;
    private final StringRedisTemplate redisTemplate;;


    @Override
    @Transactional
    public void saveCoupon(CouponFormDTO dto) {
        //1.dto转po 保存优惠卷 coupon表
        Coupon coupon = BeanUtils.copyBean(dto, Coupon.class);
        this.save(coupon);
        //2.判断是否限定了范围 dto.specific 如果为false直接return
        if (!dto.getSpecific()) {
            return;//说明没有限定优惠卷的使用范围
        }
        //3.如果dto.specific 为true 需要校验dto.scopes
        List<Long> scopes = dto.getScopes();
        if (CollUtils.isEmpty(scopes)) {
            throw new BadRequestException("分类id不能为空");
        }
        //4.保存优惠卷的限定范围 coupon_scope 批量新增
        /*List<CouponScope> csList = new ArrayList<>();
        for (Long scope : scopes) {
            CouponScope couponScope = new CouponScope();
            couponScope.setCouponId(coupon.getId());
            couponScope.setBizId(scope);
            couponScope.setType(1);//设置默认字段
            csList.add(couponScope);
        }*/
        List<CouponScope> csList = scopes
                .stream()
                .map(scope -> new CouponScope().setCouponId(coupon.getId()).setBizId(scope).setType(1))
                .collect(Collectors.toList());
        couponScopeService.saveBatch(csList);
    }

    @Override
    public PageDTO<CouponPageVO> queryCouponPage(CouponQuery query) {
        //1.分页条件查询优惠卷表 coupon
        Page<Coupon> page = this.lambdaQuery()
                .eq(query.getType() != null, Coupon::getDiscountType, query.getType())
                .eq(query.getStatus() != null, Coupon::getStatus, query.getStatus())
                .eq(StringUtils.isNotBlank(query.getName()), Coupon::getName, query.getName())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<Coupon> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        //2.封装vo返回
        List<CouponPageVO> voList = BeanUtils.copyList(records, CouponPageVO.class);
        return PageDTO.of(page, voList);
    }

    @Override
    @Transactional
    public void issueCoupon(CouponIssueFormDTO dto) {
        //1.校验id
        if (dto.getId() == null) {
            throw new BadRequestException("未传入优惠卷id");
        }
        //2.校验优惠卷id是否存在
        Coupon coupon = this.getById(dto.getId());
        if (coupon == null) {
            throw new BadRequestException("优惠卷不存在");
        }
        //3.校验优惠卷状态 只有待发放和暂停状态的优惠卷才能发放
        if (coupon.getStatus() != CouponStatus.DRAFT && coupon.getStatus() != CouponStatus.PAUSE) {
            throw new BizIllegalException("只有待发放和暂停中的优惠卷才能发放");
        }
        LocalDateTime now = LocalDateTime.now();
        //该变量代表优惠卷是否立刻发放
        boolean isBeginIssue = dto.getIssueBeginTime() == null || !dto.getIssueBeginTime().isAfter(now);
        //4.修改优惠卷的 领取开始和结束日期 使用有效期开始和结束日 天数 状态
        CouponStatus beforeStatus = coupon.getStatus();
        //方式1：
        if (isBeginIssue) {
            coupon.setIssueBeginTime(dto.getIssueBeginTime() == null ? now : LocalDateTime.now());
            coupon.setIssueEndTime(dto.getIssueEndTime());
            coupon.setStatus(CouponStatus.ISSUING);//如果是立刻发放 优惠卷状态需要修改为进行中
            coupon.setTermDays(dto.getTermDays());
            coupon.setTermBeginTime(dto.getTermBeginTime());
            coupon.setTermEndTime(dto.getTermEndTime());
        } else {
            coupon.setIssueBeginTime(dto.getIssueBeginTime() == null ? now : LocalDateTime.now());
            coupon.setIssueEndTime(dto.getIssueEndTime());
            coupon.setStatus(CouponStatus.UN_ISSUE);//如果不是立刻发放 优惠卷状态需要修改为未开始
            coupon.setTermDays(dto.getTermDays());
            coupon.setTermBeginTime(dto.getTermBeginTime());
            coupon.setTermEndTime(dto.getTermEndTime());
        }
        this.updateById(coupon);

        //5. 如果优惠卷是立刻发放，将优惠卷信息（优惠卷id、领劵开始时间结束时间、发行总数量、限领数量） 采用HASH存入redis
        if (isBeginIssue) {
            // 1.组织数据
            Map<String, String> map = new HashMap<>(4);
            map.put("issueBeginTime", String.valueOf(DateUtils.toEpochMilli(coupon.getIssueBeginTime())));
            map.put("issueEndTime", String.valueOf(DateUtils.toEpochMilli(coupon.getIssueEndTime())));
            map.put("totalNum", String.valueOf(coupon.getTotalNum()));
            map.put("userLimit", String.valueOf(coupon.getUserLimit()));
            // 2.写缓存
            redisTemplate.opsForHash().putAll(PromotionConstants.COUPON_CACHE_KEY_PREFIX + coupon.getId(), map);
        }

        //6.如果优惠卷的 领取方式为指定发放并且优惠卷之前的状态是待发放，需要生成兑换码
        if (coupon.getObtainWay() == ObtainType.ISSUE && beforeStatus == CouponStatus.DRAFT) {
            exchangeCodeService.asyncgenerateExchangeCode(coupon);//异步生成兑换码
        }
    }

    @Override
    public List<CouponVO> queryIssuingCoupons() {
        //1.查询db coupon 条件：发放中 手动领取
        List<Coupon> couponList = this.lambdaQuery()
                .eq(Coupon::getStatus, CouponStatus.ISSUING)
                .eq(Coupon::getObtainWay, ObtainType.PUBLIC)
                .list();
        if (CollUtils.isEmpty(couponList)) {
            return CollUtils.emptyList();
        }
        //2.查询用户卷表user_coupon 条件当前用户 发放中的优惠卷id
        Set<Long> couponIds = couponList.stream().map(Coupon::getId).collect(Collectors.toSet());
        //当前用户，针对正在发放中的优惠卷领取记录
        List<UserCoupon> list = userCouponService.lambdaQuery()
                .eq(UserCoupon::getUserId, UserContext.getUser())
                .in(UserCoupon::getCouponId, couponIds)
                .list();
        //2.1统计当前用户 针对每一个卷 的已领数量  key是优惠卷id value是当前登录用户针对该卷已领取数量
        /*HashMap<Long, Long> issueMap = new HashMap<>();//代表当前用户针对每一个卷 领取数量
        for (UserCoupon userCoupon : list) {
            Long num = issueMap.get(userCoupon.getCouponId());//优惠卷的领取数量
            if (num == null) {
                issueMap.put(userCoupon.getCouponId(), 1L);
            } else {
                issueMap.put(userCoupon.getCouponId(), (long) (num.intValue() + 1));
            }
        }*/
        Map<Long, Long> issueMap = list.stream()
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
        //2.2统计当前用户 针对每一个卷 的已领且未使用的数量  key是优惠卷id  value是当前登录用户针对该卷未使用的数量
        Map<Long, Long> unuseMap = list.stream()
                .filter(c -> c.getStatus() == UserCouponStatus.UNUSED)
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
        //2.po转vo返回
        List<CouponVO> voList = new ArrayList<>();
        for (Coupon coupon : couponList) {
            CouponVO vo = BeanUtils.copyBean(coupon, CouponVO.class);
            //优惠卷还有剩余 （issue_num < total_num） 且 （统计用户卷表user_coupon 取出当前用户已领数量 < user_limit）
            Long issNum = issueMap.getOrDefault(coupon.getId(), 0L);
            boolean available = coupon.getIssueNum() < coupon.getTotalNum() && issNum.intValue() < coupon.getUserLimit();
            vo.setAvailable(available);//是否可以领取 false：已领完
            //统计用户卷表 user_coupon 取出当前用户已经领取且未使用的卷数量
            boolean received = unuseMap.getOrDefault(coupon.getId(), 0L) > 0;
            vo.setReceived(received);//是否可以使用 true: 去使用
            voList.add(vo);
        }
        return voList;
    }
}
