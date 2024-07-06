package com.tianji.learning.service.impl;

import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.vo.SignRecordVO;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.service.ISignRecordService;
import io.swagger.v3.oas.annotations.servers.Server;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class SignRecordServiceImpl implements ISignRecordService {
    private final StringRedisTemplate redisTemplate;

    @Override
    public SignResultVO addSignRecords() {
        //1.获取用户的id
        Long userId = UserContext.getUser();
        //2.拼接key
        LocalDate now = LocalDate.now();
        String format = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId.toString() + format;
        //3.利用bitset命令 将签到记录保存到redis的bitmap结构中 需要校验是否已签到
        int offset = now.getDayOfMonth() - 1;//偏移量
        Boolean setBit = redisTemplate.opsForValue().setBit(key, offset, true);
        if (setBit) {
            //证明当前已经签到了
            throw new BizIllegalException("不能重复签到");
        }
        //4.计算连续签到的天数
        int days = countSignDay(key, now.getDayOfMonth());
        //5.计算连续签到的奖励积分
        int rewardPoints = 0;
        switch (days) {
           case 7:
               rewardPoints = 10;
               break;
           case 14:
               rewardPoints = 20;
               break;
           case 28:
               rewardPoints = 40;
               break;
       }
        //#TODO 6.保存积分 （积分还没做）
        //7.封装vo 然后返回
        SignResultVO vo = new SignResultVO();
        vo.setSignPoints(days);
        vo.setRewardPoints(rewardPoints);
        return vo;
    }

    /**
     * 计算连续签到多少天
     * @param key 缓存中的key
     * @param dayOfMonth 本月第一天到今天的天数
     * @return
     */
    private int countSignDay(String key, int dayOfMonth) {
        //1.求本月第一天到今天所有的签到数据 bitfield 得到的是十进制
        List<Long> bitField = redisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if (CollUtils.isEmpty(bitField)) {
            return 0;
        }
        Long num = bitField.get(0);//本月第一天到今天的签到数据 拿到的十进制
        log.debug("num {}", num);
        //2.计算有多少连续签到的天数 从后往前推  与运算与右移
        int counter = 0;
        while ((num & 1) == 1) {
            counter++;
            num >>>= 1;//右移一位
        }
        return counter;
    }
}
