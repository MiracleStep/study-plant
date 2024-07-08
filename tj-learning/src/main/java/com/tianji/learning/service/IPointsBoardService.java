package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsBoard;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardVO;

import java.util.List;

/**
 * <p>
 * 学霸天梯榜 服务类
 * </p>
 *
 * @author mirac
 * @since 2024-07-05
 */
public interface IPointsBoardService extends IService<PointsBoard> {

    /**
     * 查询学霸积分榜-当前赛季和历史赛季都可用
     * @param query
     * @return
     */
    PointsBoardVO queryPointsBoardList(PointsBoardQuery query);

    /**
     * 查询当前赛季排行榜列表
     * @param key
     * @param pageNo
     * @param pageSize
     */
    public List<PointsBoard> queryCurrentBoard(String key, Integer pageNo, Integer pageSize);
}
