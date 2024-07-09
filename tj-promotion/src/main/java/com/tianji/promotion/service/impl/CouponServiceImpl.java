package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
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
        //5.如果优惠卷的 领取方式为 指定发放，需要生成兑换码
    }
}
