package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
