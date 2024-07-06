package com.tianji.learning;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

@SpringBootTest
public class RedisBitMapTest {
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    public void test() {
        //对test116 第五天 做签到
        //返回结果代表offset为4 原来的值
        Boolean setBit = redisTemplate.opsForValue()
                .setBit("test116", 4, true);
        if (setBit) {
            //说明当前已经签过到了
            //抛出异常
        }
        System.out.println(setBit);
    }

    @Test
    public void test1() {
        //去第一天到第三天的 签到记录 redis的bitmap存的是二进制 取出来是10禁止
        List<Long> list = redisTemplate.opsForValue().bitField("test116",
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(5)).valueAt(0));
        Long l = list.get(0);
        System.out.println(l);
    }
}
