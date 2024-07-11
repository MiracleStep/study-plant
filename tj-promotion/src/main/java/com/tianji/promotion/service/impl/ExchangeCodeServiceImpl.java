package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.mapper.ExchangeCodeMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.utils.CodeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 兑换码 服务实现类
 * </p>
 *
 * @author mirac
 * @since 2024-07-09
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeCodeServiceImpl extends ServiceImpl<ExchangeCodeMapper, ExchangeCode> implements IExchangeCodeService {

    private final StringRedisTemplate redisTemplate;


    @Override
    @Async("generateExchangeCodeExecutor")//指定自己声明的线程池
    public void asyncgenerateExchangeCode(Coupon coupon) {
        log.debug("生成兑换码 线程名 {}", Thread.currentThread().getName());
        Integer totalNum = coupon.getTotalNum();//代表优惠卷的发放总数量，也就是需要生成兑换码的总数量
        //方式1：循环兑换码的总数量 循环中单个获取自增id incr 效率不高 不推荐
        //方式2：调用incrby(一下增加num个数) 批量生成兑换码 推荐
        //1.生成自增id 借助于redis incr
        Long increment = redisTemplate.opsForValue()
                .increment(PromotionConstants.COUPON_CODE_SERIAL_KEY, totalNum);
        if (increment == null) {
            return;
        }
        int maxSerialNum = increment.intValue();
        int begin = maxSerialNum - totalNum + 1;
        //2.调用工具类生成兑换码
        List<ExchangeCode> list = new ArrayList<>();
        for (int serialNum = begin; serialNum <= maxSerialNum; serialNum++) {
            String code = CodeUtil.generateCode(serialNum, coupon.getId());//参数1是自增id值，参数2为优惠卷id（内部会计算出0-15之间的数字）
            ExchangeCode exchangeCode = new ExchangeCode();
            exchangeCode.setId(serialNum);
            exchangeCode.setCode(code);
            exchangeCode.setExchangeTargetId(coupon.getId());
            exchangeCode.setExpiredTime(coupon.getIssueEndTime());//兑换码兑换的截止时间 就是优惠卷领取的截止时间
            list.add(exchangeCode);
        }
        //3.将兑换码信息批量保存db exchange_code
        this.saveBatch(list);
        //4.写入Redis缓存，member：couponId，score：兑换码的最大序列号  (本次生成兑换码 可省略)
        redisTemplate.opsForZSet().add(PromotionConstants.COUPON_RANGE_KEY, coupon.getId().toString(), maxSerialNum);
    }

    @Override
    public boolean updateExchangeCodeMark(long serialNum, boolean flag) {
        String key = PromotionConstants.COUPON_CODE_MAP_KEY;
        Boolean setBit = redisTemplate.opsForValue().setBit(key, serialNum, flag);//return的是之前的值
        return setBit != null && setBit;
    }
}
