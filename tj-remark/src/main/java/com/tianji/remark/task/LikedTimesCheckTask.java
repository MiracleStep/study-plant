package com.tianji.remark.task;

import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikedTimesCheckTask {
    private static final List<String> BIZ_TYPES = List.of("QA", "NOTE");//业务类型
    private static final int MAX_BIZ_SIZE = 30;//任务每次取的biz数量

    private final ILikedRecordService likedRecordService;

    //每20秒执行一次 将redi中 业务类型下面 某业务的点赞总数取出 发送消息到mq
    @Scheduled(fixedDelay = 40000)//每间隔40执行一次
    public void checkLikedTimes() {
        for (String bizType : BIZ_TYPES) {
            likedRecordService.readLikedTimesAndSendMessage(bizType, MAX_BIZ_SIZE);
        }
    }
}
