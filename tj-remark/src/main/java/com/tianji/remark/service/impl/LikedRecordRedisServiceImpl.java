package com.tianji.remark.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.dto.msg.LikedTimesDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.constants.RedisConstants;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import io.reactivex.internal.operators.flowable.FlowableReduceSeedSingle;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.C;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * <p>
 * 改进的 点赞记录表 服务实现类 基于Redis来实现
 * </p>
 *
 * @author mirac
 * @since 2024-07-04
 */
@Service
@Slf4j
@AllArgsConstructor
public class LikedRecordRedisServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper rabbitMqHelper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void addLikeRecord(LikeRecordFormDTO dto) {
        //1.获取当前登录用户
        Long userId = UserContext.getUser();
        //2.判断是否点赞 dto.liked 为true则是点赞
        boolean flag = dto.getLiked() ? liked(dto, userId) : unliked(dto, userId);
        if (!flag) {//说明点赞或取消赞失败
            return;
        }
        //3.统计该业务id的总点赞数
        /*Integer totalLikesNum = this.lambdaQuery()
                .eq(LikedRecord::getBizId, dto.getBizId())
                .count();*/
        //基于redis统计 业务id的总点赞量
        String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizId();
        Long totalLikedNum = redisTemplate.opsForSet().size(key);
        //4.采用zset结构缓存点赞的总数  .e.g likes:times:type:QA
        String bizTypeTotalLikeKey = RedisConstants.LIKE_COUNT_KEY_PREFIX + dto.getBizType();
        redisTemplate.opsForZSet().add(bizTypeTotalLikeKey, dto.getBizId().toString(), totalLikedNum);

        /*LikedTimesDTO msg = LikedTimesDTO.builder()
                .bizId(dto.getBizId())
                .likedTimes(totalLikesNum)
                .build();
        log.debug("发送点赞消息, 消息内容：{}", msg);
        String routingKey = StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, "QA");
        rabbitMqHelper.send(
                MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
                routingKey,
                msg
        );*/
    }

    @Override
    public Set<Long> getLikesStatusByBizIds(List<Long> bizIds) {
        /*//1.获取用户
        Long userId = UserContext.getUser();
        //2.循环bizIds
        if (CollUtils.isEmpty(bizIds)) {
            return null;
        }
        Set<Long> likedBizIds = new HashSet<>();
        for (Long bizId : bizIds) {
            //判断该业务id 的点赞用户集合中是否包含当前用户
            Boolean member = redisTemplate.opsForSet().isMember(RedisConstants.LIKE_BIZ_KEY_PREFIX + bizId, userId.toString());
            if (member) {
                likedBizIds.add(bizId);
            }
        }
        return likedBizIds;*/
        // 1.获取登录用户id
        Long userId = UserContext.getUser();
        // 2.查询点赞状态  短时间执行大量的redis命令  这里用的管道技术，后面可以改为用lua脚本
        List<Object> objects = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection src = (StringRedisConnection) connection;
            for (Long bizId : bizIds) {
                String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + bizId;
                src.sIsMember(key, userId.toString());
            }
            return null;
        });
        // 3.返回结果
        return IntStream.range(0, objects.size()) // 创建从0到集合size的流
                .filter(i -> (boolean) objects.get(i)) // 遍历每个元素，保留结果为true的角标i
                .mapToObj(bizIds::get)// 用角标i取bizIds中的对应数据，就是点赞过的id
                .collect(Collectors.toSet());// 收集
        /*//1.获取用户id
        Long userId = UserContext.getUser();
        //2.查点赞记录表 in bizIds
        List<LikedRecord> recordList = this.lambdaQuery()
                .in(LikedRecord::getBizId, bizIds)
                .eq(LikedRecord::getUserId, userId)
                .list();
        //3.将查询到的bizIds转集合返回
        return recordList.stream().map(LikedRecord::getBizId).collect(Collectors.toSet());*/
    }



    //取消赞 redis版本
    private boolean unliked(LikeRecordFormDTO dto, Long userId) {
        //拼接key
        String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizId();
        //从set结构中删除 当前userId
        Long result = redisTemplate.opsForSet().remove(key, userId.toString());
        return result != null && result > 0;
    }

    //点赞 redis版本
    private boolean liked(LikeRecordFormDTO dto, Long userId) {
        //基于Redis做点赞
        //拼接key getBizId()
        String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizId();
        // redisTemplate  往redis 的set结构添加点赞记录
        Long result = redisTemplate.opsForSet().add(key, userId.toString());
        return result != null && result > 0;
    }

    @Override
    public void readLikedTimesAndSendMessage(String bizType, int maxBizSize) {
        //1.拼接key
        String bizTypeTotalLikeKey = RedisConstants.LIKE_COUNT_KEY_PREFIX + bizType;

        List<LikedTimesDTO> list = new ArrayList<>();
        //2.从redis的zset结构 按分数排序取 maxBizSize 的业务点赞信息  popMin取的同时还会删除掉已经取的
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet().popMin(bizTypeTotalLikeKey, maxBizSize);
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            String bizId = typedTuple.getValue();
            Double likedTimes = typedTuple.getScore();
            if (StringUtils.isBlank(bizId) || likedTimes == null) {
                continue;
            }
            //3.封装LikedTimesDTO 消息数据
            LikedTimesDTO msg = LikedTimesDTO.of(Long.valueOf(bizId), likedTimes.intValue());
            list.add(msg);
        }
        //4.发送消息到mq
        if (CollUtils.isNotEmpty(list)) {
            log.debug("批量发送点赞数量消息到MQ, 消息内容：{}", list);
            String routingKey = StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, bizType);
            rabbitMqHelper.send(
                    MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
                    routingKey,
                    list);
        }
    }
}
