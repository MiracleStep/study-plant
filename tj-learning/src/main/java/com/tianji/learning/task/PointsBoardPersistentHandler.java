package com.tianji.learning.task;

import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDate;


/**
 * 赛季积分榜持久化处理器
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PointsBoardPersistentHandler {

    private final IPointsBoardSeasonService pointsBoardSeasonService;

    /**
     * 创建上赛季（上个月）榜单表
     */
//    @Scheduled(cron = "0 0 3 1 * ?")//每个月1号凌晨3点执行  单机版定时任务调度
//    @Scheduled(cron = "0 * * * * ?")
    @XxlJob("createTableJob")//xxl-job任务名称
    public void createPointsBoardTableOfLastSeason() {

        log.debug("创建上赛季榜单表任务执行了");
        //1.获取上个月当前时间点
        LocalDate time = LocalDate.now().minusMonths(1);
        //2.查询赛季表获取赛季id  条件 begin_time <= time and end_time >= time
        PointsBoardSeason one = pointsBoardSeasonService
                .lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, time)
                .ge(PointsBoardSeason::getEndTime, time)
                .one();
        log.debug("上赛季信息 {}", one);
        if (one == null) {
            return;
        }

        //3.创建上赛季榜单表 points_board_7
        pointsBoardSeasonService.createPointsBoardLatestTable(one.getId());
        //4.
    }
}
