package com.tianji.learning.task;

import com.tianji.common.utils.CollUtils;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.tianji.learning.constants.LearningConstants.POINTS_BOARD_TABLE_PREFIX;


/**
 * 赛季积分榜持久化处理器
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PointsBoardPersistentHandler {

    private final IPointsBoardSeasonService pointsBoardSeasonService;
    private final IPointsBoardService pointsBoardService;
    private final StringRedisTemplate redisTemplate;

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
    }

    //持久化上赛季（上个月的）排行榜数据 到db中
    @XxlJob("savePointsBoard2DB")//任务名字要和 xxljob控制台保持一致
    public void savePointsBoardData() {
        //1.获取上个月 当前时间点
        LocalDate time = LocalDate.now().minusMonths(1);
        //2.查询赛季表points_board_season 获取上赛季信息
        //select * from points_board_season where begin_time <= '2023-05-01' and end_time >= '2023-05-01'
        PointsBoardSeason one = pointsBoardSeasonService
                .lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, time)
                .ge(PointsBoardSeason::getEndTime, time)
                .one();
        log.debug("上赛季信息 {}", one);
        if (one == null) {
            return;
        }
        //3.计算动态表名 并存入ThreadLocal
        String tableName = POINTS_BOARD_TABLE_PREFIX + one.getId();
        log.debug("动态表名为 {}", tableName);
        TableInfoContext.setInfo(tableName);
        //3.判断redis上赛季积分排行榜数据
        String format = time.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + format;//boards:上赛季的年月  board:202407
        //采用任务分片进行优化
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();
        int pageNo = shardIndex + 1;
        int pageSize = 5;//一点一点的来，不能太大，太大数据库和内存就会压力大。
        while (true) {
            List<PointsBoard> pointsBoardList = pointsBoardService.queryCurrentBoard(key, pageNo, pageSize);//复用这个方法
            if (CollUtils.isEmpty(pointsBoardList)) {
                break;//跳出循环
            }
            pageNo += shardTotal;
            //5.持久化到db相应的赛季表中  批量新增
            for (PointsBoard board : pointsBoardList) {
                board.setId(Long.valueOf(board.getRank()));
                board.setRank(null);
            }
            pointsBoardService.saveBatch(pointsBoardList);
        }
        //6.情况ThreadLocal中数据
        TableInfoContext.remove();
    }


    @XxlJob("clearPointsBoardFromRedis")
    public void clearPointsBoardFromRedis(){
        // 1.获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 2.计算key
        String format = time.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + format;
        // 3.删除  异步删除 （适用于删除大量的键）
        redisTemplate.unlink(key);
    }
}
